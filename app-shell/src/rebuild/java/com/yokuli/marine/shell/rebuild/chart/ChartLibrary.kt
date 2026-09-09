package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Docs
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class ChartFile(
    val id: String, val uri: String, val name: String, val source: String,
    val minZoom: Int, val maxZoom: Int, val tileSize: Int, val scheme: String,
    val focus: GeoPoint, val bytes: Long, val attribution: String = "", val enabled: Boolean = true,
    val error: String? = null, val modified: Long = 0, val previewZoom:Double = minZoom.toDouble()
) {
    fun json() = JSONObject().put("id",id).put("uri",uri).put("name",name).put("source",source)
        .put("min",minZoom).put("max",maxZoom).put("size",tileSize).put("scheme",scheme)
        .put("focus",focus.json()).put("bytes",bytes).put("attribution",attribution)
        .put("enabled",enabled).put("error",error ?: "").put("modified",modified).put("previewZoom",previewZoom)
    companion object {
        fun from(j: JSONObject) = ChartFile(j.getString("id"),j.getString("uri"),j.getString("name"),j.optString("source"),
            j.getInt("min"),j.getInt("max"),j.getInt("size"),j.getString("scheme"),GeoPoint.from(j.getJSONObject("focus")),
            j.optLong("bytes"),j.optString("attribution"),j.optBoolean("enabled",true),j.optString("error").takeIf { it.isNotBlank() },j.optLong("modified"),j.optDouble("previewZoom",j.getInt("min").toDouble()))
    }
}

/** Read the same raster MBTiles table/view and TMS/XYZ conventions as Anchor Watch. */
class ChartReader(context: Context, uri: Uri) : AutoCloseable {
    private var descriptor: ParcelFileDescriptor? = null
    private val db: SQLiteDatabase
    init {
        val cache=if(uri.scheme=="content") ChartCache.target(context,uri) else null
        val path = if (uri.scheme == "file") requireNotNull(uri.path) else if(cache?.isFile==true) cache.path else {
            descriptor = context.contentResolver.openFileDescriptor(uri,"r") ?: error("unreadable")
            "/proc/self/fd/${descriptor!!.fd}"
        }
        db = try { SQLiteDatabase.openDatabase(path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS) }
        catch (e: Exception) {
            descriptor?.close();descriptor=null
            if(uri.scheme!="content") throw e
            val local=ChartCache.copy(context,uri,requireNotNull(cache))
            SQLiteDatabase.openDatabase(local.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        }
    }
    @Synchronized fun inspect(uri: String, name: String, source: String, bytes: Long, modified: Long): ChartFile {
        val columns = db.rawQuery("PRAGMA table_info(tiles)",null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
        require(columns.containsAll(listOf("zoom_level","tile_column","tile_row","tile_data"))) { "schema" }
        val metadata = runCatching { db.rawQuery("SELECT name,value FROM metadata LIMIT 256",null).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0),c.getString(1)) } } }.getOrDefault(emptyMap())
        require(metadata["format"]?.lowercase() !in listOf("pbf","mvt")) { "vector" }
        val min = db.rawQuery("SELECT zoom_level FROM tiles ORDER BY zoom_level ASC LIMIT 1",null).use { require(it.moveToFirst()) { "empty" }; it.getInt(0) }
        val max = db.rawQuery("SELECT zoom_level FROM tiles ORDER BY zoom_level DESC LIMIT 1",null).use { require(it.moveToFirst()); it.getInt(0) }
        require(min in 0..24 && max in min..24) { "zoom" }
        val sample = db.rawQuery("SELECT tile_column,tile_row,tile_data FROM tiles WHERE zoom_level=? LIMIT 1",arrayOf(min.toString())).use {
            require(it.moveToFirst()); Triple(it.getInt(0),it.getInt(1),it.getBlob(2))
        }
        require(sample.third.size <= 8*1024*1024) { "tile" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sample.third,0,sample.third.size,options)
        require(options.outWidth in listOf(256,512) && options.outWidth == options.outHeight) { "raster" }
        val scheme = metadata["scheme"]?.lowercase() ?: "tms"
        require(scheme in listOf("tms","xyz")) { "scheme" }
        val n = 2.0.pow(min); val y = if(scheme=="tms") n-1-sample.second else sample.second.toDouble()
        require(sample.first >= 0 && sample.first < n && y >= 0 && y < n) { "coordinate" }
        // A real tile centre is always useful, even when bounds/center metadata is absent or wrong.
        var focus = GeoPoint(Math.toDegrees(atan(sinh(PI*(1-2*(y+.5)/n)))),(sample.first+.5)/n*360-180)
        var previewZoom=(min-if(options.outWidth==256) 1 else 0).toDouble().coerceAtLeast(1.0)
        val declared=metadata["center"]?.split(',')?.mapNotNull {it.trim().toDoubleOrNull()}
        if(declared!=null && declared.size==3 && declared.all {it.isFinite()} && declared[1] in -85.0..85.0 && declared[0] in -180.0..180.0) {
            val level=declared[2].toInt().coerceIn(min,max);val extent=2.0.pow(level)
            val cx=floor((declared[0]+180)/360*extent).toInt();val cy=floor((1-asinh(tan(Math.toRadians(declared[1])))/PI)/2*extent).toInt()
            if(tile(cx,cy,level,scheme)!=null) {focus=GeoPoint(declared[1],declared[0]);previewZoom=(level-if(options.outWidth==256) 1 else 0).toDouble().coerceAtLeast(1.0)}
        }
        return ChartFile(java.util.UUID.nameUUIDFromBytes(uri.toByteArray()).toString(),uri,
            metadata["name"]?.takeIf { it.isNotBlank() }?.take(120) ?: name,source,min,max,options.outWidth,scheme,focus,bytes,
            metadata["attribution"]?.take(700) ?: "",modified=modified,previewZoom=previewZoom)
    }
    @Synchronized fun tile(x: Int, y: Int, z: Int, scheme: String): ByteArray? {
        if (z !in 0..24 || x < 0 || y < 0 || x >= (1 shl z) || y >= (1 shl z)) return null
        val row = if(scheme=="xyz") y else (1 shl z)-1-y
        return db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",arrayOf(z.toString(),x.toString(),row.toString())).use {
            if(it.moveToFirst()) it.getBlob(0).takeIf { b -> b.size <= 8*1024*1024 } else null
        }
    }
    @Synchronized fun coverageZoom(x:Int,y:Int,z:Int,chart:ChartFile):Int? {
        if(z<chart.minZoom || z>24 || x<0 || y<0 || x>=(1 shl z) || y>=(1 shl z)) return null
        for(level in minOf(z,chart.maxZoom) downTo chart.minZoom) {
            val shift=z-level;val col=x shr shift;val xyz=y shr shift
            val row=if(chart.scheme=="xyz") xyz else (1 shl level)-1-xyz
            val found=db.rawQuery("SELECT 1 FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",arrayOf(level.toString(),col.toString(),row.toString())).use {it.moveToFirst()}
            if(found) return level
        }
        return null
    }
    @Synchronized fun raster(x:Int,y:Int,z:Int,chart:ChartFile):ByteArray? {
        val level=coverageZoom(x,y,z,chart) ?: return null
        val shift=z-level
        val data=tile(x shr shift,y shr shift,level,chart.scheme) ?: return null
        if(shift==0) return data
        // Sparse zoom levels are common in real archives. Reuse the correct parent area,
        // never the whole parent tile and never a neighbouring tile.
        val original=BitmapFactory.decodeByteArray(data,0,data.size) ?: return null
        val scale=2.0.pow(shift);val mask=(1 shl shift)-1
        val left=floor((x and mask)/scale*original.width).toInt().coerceIn(0,original.width-1)
        val top=floor((y and mask)/scale*original.height).toInt().coerceIn(0,original.height-1)
        val right=ceil(((x and mask)+1)/scale*original.width).toInt().coerceIn(left+1,original.width)
        val bottom=ceil(((y and mask)+1)/scale*original.height).toInt().coerceIn(top+1,original.height)
        val output=Bitmap.createBitmap(chart.tileSize,chart.tileSize,Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(original,Rect(left,top,right,bottom),Rect(0,0,chart.tileSize,chart.tileSize),Paint(Paint.FILTER_BITMAP_FLAG))
        val bytes=java.io.ByteArrayOutputStream();output.compress(Bitmap.CompressFormat.PNG,100,bytes)
        original.recycle();output.recycle();return bytes.toByteArray()
    }
    @Synchronized override fun close() { db.close(); descriptor?.close() }
}

/** SAF permission does not grant SQLite a path permission. A versioned read-only copy bridges that gap. */
private object ChartCache {
    private val locks=Array(16) {Any()}
    fun target(context:Context,uri:Uri):File {
        // Querying the provider also verifies that access still exists before using a cached revision.
        val stamp=context.contentResolver.query(uri,arrayOf(Docs.Document.COLUMN_SIZE,Docs.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {
            require(it.moveToFirst()) {"unreadable"};"${it.getLong(0)}:${it.getLong(1)}"
        } ?: error("unreadable")
        val id=java.util.UUID.nameUUIDFromBytes((uri.toString()+stamp).toByteArray()).toString()
        return File(File(context.filesDir,"chart-source-cache").apply {mkdirs()},"$id.mbtiles")
    }
    fun copy(context:Context,uri:Uri,target:File):File = synchronized(locks[(uri.hashCode() and Int.MAX_VALUE)%locks.size]) {
        if(target.isFile) return@synchronized target
        val temp=File(target.parentFile,target.name+".partial")
        try {
            context.contentResolver.openInputStream(uri)?.use {input->temp.outputStream().buffered().use {out->
                val buffer=ByteArray(1024*1024);var total=0L
                while(true) {
                    if(Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                    val count=input.read(buffer);if(count<0) break
                    total+=count;require(target.parentFile!!.usableSpace>count+128_000_000 && total<=8_000_000_000L) {"space"}
                    out.write(buffer,0,count)
                }
            }} ?: error("unreadable")
            require(temp.length()>=16) {"empty"}
            temp.inputStream().use { val header=ByteArray(16);require(it.read(header)==16 && String(header,Charsets.US_ASCII)=="SQLite format 3\u0000") {"schema"} }
            check(temp.renameTo(target)) {"space"}
        } catch(e:Exception) {temp.delete();throw e}
        target
    }
}

class ChartLibrary(private val context: Context, private val scope: CoroutineScope) {
    private val index = AtomicFile(File(context.filesDir,"charts-v1.json"))
    private val initial = runCatching { JSONObject(index.openRead().bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject())
    private val writeMutex = Mutex()
    var files by mutableStateOf(initial.optJSONArray("files")?.objects()?.mapNotNull { runCatching { ChartFile.from(it) }.getOrNull() } ?: emptyList())
    var folders by mutableStateOf(initial.optJSONArray("folders")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList())
    var busy by mutableStateOf(false)
    var progress by mutableStateOf("")
    var failure by mutableStateOf<String?>(null)
    var rejected by mutableIntStateOf(0)
    var revision by mutableIntStateOf(0)
    val selected get() = files.filter { it.enabled && it.error == null }.take(12)
    fun errorText(code: String?, zh: Boolean): String = when(code) {
        "vector" -> if(zh) "这是矢量海图；请使用栅格 MBTiles" else "Vector chart. Use raster MBTiles."
        "empty" -> if(zh) "文件没有图块" else "No tiles in this file"
        "space" -> if(zh) "空间不足，未完成导入" else "Not enough storage to import"
        "schema","raster","tile","scheme","coordinate","zoom" -> if(zh) "不支持的海图格式，请使用 PNG/JPEG/WebP MBTiles" else "Use PNG/JPEG/WebP raster MBTiles"
        "permission" -> if(zh) "文件夹访问授权已失效，请重新选择" else "Folder access expired. Select it again."
        "limit" -> if(zh) "文件夹过大，请选择较小的子文件夹" else "Choose a smaller subfolder"
        "save" -> if(zh) "目录保存失败，请检查存储空间" else "Could not save the library. Check storage."
        else -> if(zh) "无法读取文件；可尝试导入应用内副本" else "Cannot read this file. Try importing a local copy."
    }
    private fun persist() {
        revision++
        val snapshot = JSONObject().put("files",JSONArray(files.map { it.json() })).put("folders",JSONArray(folders)).toString()
        scope.launch(Dispatchers.IO) { writeMutex.withLock {
            runCatching {
                val out = index.startWrite()
                try { out.write(snapshot.toByteArray()); index.finishWrite(out) }
                catch(e:Exception) { index.failWrite(out); throw e }
            }.onFailure { withContext(Dispatchers.Main) { failure = "save" } }
        } }
    }
    fun toggle(file: ChartFile) { files = files.map { if(it.id==file.id) it.copy(enabled=!it.enabled) else it }; persist() }
    fun showOnly(file: ChartFile) { files = files.map { it.copy(enabled=it.id==file.id) }; persist() }
    fun forget(file: ChartFile) { files = files.filter { it.id!=file.id }; persist() } // Never deletes the source file.
    fun forgetFolder(uri: String) { folders = folders-uri; files = files.filter { it.source!=uri }; persist() }
    fun rescan() { if(!busy) scan(folders) }
    fun linkFolder(uri: Uri) {
        if(busy) return
        runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { failure="permission"; return }
        folders = (folders+uri.toString()).distinct(); persist(); scan(listOf(uri.toString()))
    }
    private fun scan(trees: List<String>) {
        busy=true; failure=null; rejected=0
        scope.launch {
            try {
                for(tree in trees) {
                    val collected = mutableListOf<ChartFile>()
                    withContext(Dispatchers.IO) {
                        val treeUri = Uri.parse(tree)
                        val queue = ArrayDeque<Pair<String,Int>>()
                        queue.add(Docs.getTreeDocumentId(treeUri) to 0)
                        var visited=0
                        while(queue.isNotEmpty()) {
                            ensureActive()
                            val (parent,depth) = queue.removeFirst()
                            require(depth <= 12 && visited < 5000) { "limit" }
                            val childUri=Docs.buildChildDocumentsUriUsingTree(treeUri,parent)
                            val projection=arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_MIME_TYPE,Docs.Document.COLUMN_SIZE,Docs.Document.COLUMN_LAST_MODIFIED)
                            val cursor=context.contentResolver.query(childUri,projection,null,null,null) ?: error("unreadable")
                            cursor.use { c -> while(c.moveToNext()) {
                                visited++; require(visited<=5000) { "limit" }
                                val id=c.getString(0); val name=c.getString(1); val mime=c.getString(2)
                                if(mime==Docs.Document.MIME_TYPE_DIR) { queue.add(id to depth+1); continue }
                                if(!name.endsWith(".mbtiles",true)) continue
                                val uri=Docs.buildDocumentUriUsingTree(treeUri,id)
                                withContext(Dispatchers.Main) { progress=name }
                                val result=runCatching { ChartReader(context,uri).use { it.inspect(uri.toString(),name,tree,c.getLong(3),c.getLong(4)) } }
                                result.onSuccess { collected.add(it) }.onFailure { error ->
                                    withContext(Dispatchers.Main) { rejected++; failure=error.message?.takeIf { it in listOf("vector","schema","raster","empty","zoom","scheme","tile") } ?: "unreadable" }
                                }
                            } }
                        }
                    }
                    // Only reconcile membership after a complete traversal; a failed scan retains prior entries.
                    val previous = files.associateBy { it.id }
                    files=files.filter { it.source!=tree }+collected.map { f -> f.copy(enabled=previous[f.id]?.enabled ?: true) }
                    persist()
                }
            } catch(e:Exception) { failure=if(e is SecurityException) "permission" else e.message ?: "unreadable" }
            finally { busy=false; progress="" }
        }
    }
    fun importCopy(uri: Uri) {
        if(busy) return
        busy=true; failure=null
        scope.launch {
            val directory=File(context.filesDir,"chart-copies").apply { mkdirs() }
            val temp=File(directory,"${uid()}.partial")
            try {
                val chart=withContext(Dispatchers.IO) {
                    val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "chart.mbtiles"
                    withContext(Dispatchers.Main) { progress=name }
                    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().buffered().use { output ->
                        val buffer=ByteArray(1024*1024); var total=0L
                        while(true) {
                            ensureActive(); val n=input.read(buffer); if(n<0) break
                            total+=n; require(directory.usableSpace > n+128_000_000 && total<=8_000_000_000L) { "space" }
                            output.write(buffer,0,n)
                        }
                    } } ?: error("unreadable")
                    val checked=ChartReader(context,Uri.fromFile(temp)).use { it.inspect(Uri.fromFile(temp).toString(),name,"copy",temp.length(),0) }
                    val target=File(directory,"${uid()}.mbtiles")
                    check(temp.renameTo(target)) { "space" }
                    checked.copy(id=uid(),uri=Uri.fromFile(target).toString(),modified=target.lastModified())
                }
                files=files+chart; persist()
            } catch(e:Exception) { failure=e.message ?: "unreadable"; temp.delete() }
            finally { busy=false; progress="" }
        }
    }
}

/** A bounded, process-private adapter. No document URI or filesystem path is exposed. */
class TileGateway(private val context: Context) : AutoCloseable {
    private val token=uid()
    private val server=ServerSocket(0,24,InetAddress.getByName("127.0.0.1"))
    private val entries=ConcurrentHashMap<String,Pair<ChartFile,ChartReader>>()
    private val cache=object:android.util.LruCache<String,ByteArray>(24*1024*1024) {override fun sizeOf(key:String,value:ByteArray)=value.size}
    private val workers=ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,ArrayBlockingQueue(48),{ r -> Thread(r,"chart-tile").apply { isDaemon=true } })
    @Volatile var closed=false
    @Volatile var lastError: String?=null
    init { Thread({ while(!closed) {
        val socket=try { server.accept() } catch(_:Exception) { break }
        try { workers.execute { socket.use { runCatching { serve(it) } } } } catch(_:Exception) { socket.close() }
    } },"chart-loopback").apply { isDaemon=true; start() } }
    fun register(file: ChartFile): String {
        entries[file.id]=file to ChartReader(context,Uri.parse(file.uri))
        return "http://127.0.0.1:${server.localPort}/$token/${file.id}/{z}/{x}/{y}"
    }
    private fun serve(socket: Socket) {
        socket.soTimeout=4000
        val input=socket.getInputStream().buffered()
        val request=StringBuilder()
        while(request.length<4096) { val b=input.read(); if(b<0) return; request.append(b.toChar()); if(request.endsWith("\r\n\r\n")) break }
        val parts=request.lineSequence().firstOrNull()?.split(' ').orEmpty()
        if(parts.size!=3 || parts[0]!="GET") return
        val p=parts[1].split('/'); if(p.size!=6 || p[1]!=token) return
        val entry=entries[p[2]] ?: return
        val z=p[3].toIntOrNull() ?: return; val x=p[4].toIntOrNull() ?: return; val y=p[5].toIntOrNull() ?: return
        val key="${p[2]}/$z/$x/$y"
        val data=try { cache.get(key) ?: if(z in entry.first.minZoom..entry.first.maxZoom) entry.second.raster(x,y,z,entry.first)?.also {cache.put(key,it)} else null }
            catch(_:Exception) { lastError=entry.first.id; null }
        val status=if(data==null) "404 Not Found" else "200 OK"
        socket.getOutputStream().buffered().use { out ->
            out.write("HTTP/1.1 $status\r\nContent-Type: image/png\r\nContent-Length: ${data?.size ?: 0}\r\nCache-Control: max-age=86400\r\nConnection: close\r\n\r\n".toByteArray())
            if(data!=null) out.write(data)
        }
    }
    override fun close() { closed=true; server.close(); workers.shutdownNow(); entries.values.forEach { runCatching { it.second.close() } }; entries.clear();cache.evictAll() }
}
