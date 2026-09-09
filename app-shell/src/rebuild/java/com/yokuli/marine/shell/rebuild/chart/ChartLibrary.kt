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
    val error: String? = null, val modified: Long = 0, val previewZoom:Double = minZoom.toDouble(),
    val priority: Int = 0, val filename: String = name
) {
    fun json() = JSONObject().put("id",id).put("uri",uri).put("name",name).put("source",source)
        .put("min",minZoom).put("max",maxZoom).put("size",tileSize).put("scheme",scheme)
        .put("focus",focus.json()).put("bytes",bytes).put("attribution",attribution)
        .put("enabled",enabled).put("error",error ?: "").put("modified",modified).put("previewZoom",previewZoom)
        .put("priority",priority).put("filename",filename)
    companion object {
        fun from(j: JSONObject) = ChartFile(j.getString("id"),j.getString("uri"),j.getString("name"),j.optString("source"),
            j.getInt("min"),j.getInt("max"),j.getInt("size"),j.getString("scheme"),GeoPoint.from(j.getJSONObject("focus")),
            j.optLong("bytes"),j.optString("attribution"),j.optBoolean("enabled",true),j.optString("error").takeIf { it.isNotBlank() },j.optLong("modified"),j.optDouble("previewZoom",j.getInt("min").toDouble()),j.optInt("priority"),j.optString("filename",j.getString("name")))
    }
}

private fun decodeTile(bytes:ByteArray):Bitmap? {
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    if(bounds.outWidth !in listOf(256,512) || bounds.outHeight!=bounds.outWidth) return null
    return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
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
            metadata["attribution"]?.take(700) ?: "",modified=modified,previewZoom=previewZoom,filename=name)
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
        val original=decodeTile(data) ?: return null
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

data class ChartFolder(
    val id: String, val uri: String, val name: String,
    val layerName: String? = null, val enabled: Boolean = true
) {
    fun json() = JSONObject().put("id",id).put("uri",uri).put("name",name)
        .put("layerName",layerName ?: "").put("enabled",enabled)
    companion object {
        fun from(j:JSONObject) = ChartFolder(j.getString("id"),j.getString("uri"),j.getString("name"),
            j.optString("layerName").takeIf {it.isNotBlank()},j.optBoolean("enabled",true))
        fun linked(uri:String,name:String = Uri.decode(uri.substringAfterLast('/')).substringAfter(':')) =
            ChartFolder(java.util.UUID.nameUUIDFromBytes(uri.toByteArray()).toString(),uri,name.ifBlank {"charts"})
    }
}

data class ChartLayer(val id:String,val name:String,val files:List<ChartFile>) {
    val rasterSize get() = files.maxOfOrNull {it.tileSize} ?: 256
    val minZoom get() = files.minOfOrNull {it.minZoom} ?: 0
    // A 512-pixel archive contributes one more level of native detail when served as
    // 256-pixel composite tiles. Keep that level instead of prematurely blurring it.
    val maxZoom get() = files.maxOfOrNull {(it.maxZoom+if(it.tileSize==512) 1 else 0).coerceAtMost(24)} ?: 24
}

class ChartLibrary(private val context: Context, private val scope: CoroutineScope) {
    private val index = AtomicFile(File(context.filesDir,"charts-v1.json"))
    private val initial = runCatching { JSONObject(index.openRead().bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject())
    private val writeMutex = Mutex()
    var files by mutableStateOf(initial.optJSONArray("files")?.objects()?.mapIndexedNotNull { i,j ->
        runCatching {ChartFile.from(j).let {if(j.has("priority")) it else it.copy(priority=i)}}.getOrNull()
    } ?: emptyList())
    var folders by mutableStateOf(loadFolders())
    var busy by mutableStateOf(false)
    var progress by mutableStateOf("")
    var failure by mutableStateOf<String?>(null)
    var rejected by mutableIntStateOf(0)
    var revision by mutableIntStateOf(0)
    val layers get() = folders.filter {it.layerName!=null}.map {folder ->
        ChartLayer(folder.id,folder.layerName!!,folderFiles(folder).filter {it.enabled && it.error==null})
    }
    val selectedLayers get() = layers.filter {layer -> folders.any {it.id==layer.id && it.enabled} && layer.files.isNotEmpty()}
    val selected get() = selectedLayers.flatMap {it.files}
    fun folderFiles(folder:ChartFolder) = files.filter {it.source==folder.uri}.sortedWith(compareBy<ChartFile> {it.priority}.thenBy {it.filename.lowercase()})
    private fun loadFolders():List<ChartFolder> {
        val array=initial.optJSONArray("folders") ?: JSONArray()
        val linked=(0 until array.length()).mapNotNull {i -> runCatching {
            val item=array.get(i)
            if(item is JSONObject) ChartFolder.from(item) else ChartFolder.linked(item.toString()).let {it.copy(layerName=it.name)}
        }.getOrNull()}.toMutableList()
        // Existing imported copies and old flat selections remain available in a named local folder.
        if(files.any {it.source=="copy"} && linked.none {it.uri=="copy"}) linked.add(ChartFolder("local-copies","copy","imported charts","imported charts"))
        return linked
    }
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
        val snapshot = JSONObject().put("version",2).put("files",JSONArray(files.map { it.json() })).put("folders",JSONArray(folders.map {it.json()})).toString()
        scope.launch(Dispatchers.IO) { writeMutex.withLock {
            runCatching {
                val out = index.startWrite()
                try { out.write(snapshot.toByteArray()); index.finishWrite(out) }
                catch(e:Exception) { index.failWrite(out); throw e }
            }.onFailure { withContext(Dispatchers.Main) { failure = "save" } }
        } }
    }
    fun toggle(file: ChartFile) { files = files.map { if(it.id==file.id) it.copy(enabled=!it.enabled) else it }; persist() }
    fun setLayer(folder:ChartFolder,name:String) {
        val title=name.trim().take(100);if(title.isBlank()) return
        folders=folders.map {if(it.id==folder.id) it.copy(layerName=title,enabled=if(it.layerName==null) true else it.enabled) else it};persist()
    }
    fun removeLayer(folder:ChartFolder) {folders=folders.map {if(it.id==folder.id) it.copy(layerName=null) else it};persist()}
    fun toggleLayer(folder:ChartFolder) {folders=folders.map {if(it.id==folder.id) it.copy(enabled=!it.enabled) else it};persist()}
    fun enableLayer(folder:ChartFolder) {folders=folders.map {if(it.id==folder.id) it.copy(enabled=true) else it};persist()}
    fun moveLayer(folder:ChartFolder,delta:Int) {
        val arranged=folders.toMutableList();val from=arranged.indexOfFirst {it.id==folder.id};if(from<0) return
        val visible=arranged.indices.filter {arranged[it].layerName!=null};val position=visible.indexOf(from)
        if(position<0) return
        val next=(position+delta).coerceIn(0,visible.lastIndex);if(next==position) return
        val to=visible[next];arranged.add(to,arranged.removeAt(from));folders=arranged;persist()
    }
    fun moveFile(file:ChartFile,delta:Int) {
        val ordered=files.filter {it.source==file.source}.sortedBy {it.priority}.toMutableList()
        val from=ordered.indexOfFirst {it.id==file.id};if(from<0) return
        val to=(from+delta).coerceIn(0,ordered.lastIndex);if(from==to) return
        ordered.add(to,ordered.removeAt(from));val priorities=ordered.mapIndexed {i,f -> f.id to i}.toMap()
        files=files.map {f -> priorities[f.id]?.let {f.copy(priority=it)} ?: f};persist()
    }
    /** Viewing one chart enables its folder layer without disabling unrelated chart folders. */
    fun showOnly(file: ChartFile) {
        files=files.map {if(it.id==file.id) it.copy(enabled=true) else it}
        folders=folders.map {if(it.uri==file.source) it.copy(enabled=true,layerName=it.layerName ?: it.name) else it};persist()
    }
    fun forget(file: ChartFile) { files = files.filter { it.id!=file.id }; persist() }
    fun forgetFolder(uri: String) { folders = folders.filter {it.uri!=uri}; files = files.filter { it.source!=uri }; persist() }
    fun rescan(folder:ChartFolder?=null) { if(!busy) scan((folder?.let {listOf(it)} ?: folders).filter {it.uri!="copy"}.map {it.uri}) }
    fun linkFolder(uri: Uri):ChartFolder? {
        if(busy) return null
        runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { failure="permission"; return null }
        val existing=folders.firstOrNull {it.uri==uri.toString()}
        val folder=existing ?: ChartFolder.linked(uri.toString(),runCatching {
            val document=Docs.buildDocumentUriUsingTree(uri,Docs.getTreeDocumentId(uri))
            context.contentResolver.query(document,arrayOf(Docs.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null}
        }.getOrNull() ?: Uri.decode(uri.toString().substringAfterLast('/')).substringAfter(':'))
        if(existing==null) folders=folders+folder
        persist();scan(listOf(uri.toString()));return folder
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
                                    val code=error.message?.takeIf {it in listOf("vector","schema","raster","empty","zoom","scheme","tile")} ?: "unreadable"
                                    collected.add(ChartFile(java.util.UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
                                        uri.toString(),name,tree,0,0,256,"tms",GeoPoint(0.0,0.0),c.getLong(3),error=code,modified=c.getLong(4),filename=name))
                                    withContext(Dispatchers.Main) { rejected++;failure=code }
                                }
                            } }
                        }
                    }
                    // Reconcile only a completed traversal; preserve explicit user priorities on refresh.
                    val previous = files.associateBy { it.id }
                    var nextPriority=(files.filter {it.source==tree}.maxOfOrNull {it.priority} ?: -1)+1
                    val discovered=collected.sortedBy {it.filename.lowercase()}.map {f ->
                        f.copy(enabled=previous[f.id]?.enabled ?: true,priority=previous[f.id]?.priority ?: nextPriority++)
                    }
                    files=files.filter {it.source!=tree}+discovered
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
                if(folders.none {it.uri=="copy"}) folders=folders+ChartFolder("local-copies","copy","imported charts","imported charts")
                files=files+chart.copy(priority=(files.filter {it.source=="copy"}.maxOfOrNull {it.priority} ?: -1)+1); persist()
            } catch(e:Exception) { failure=e.message ?: "unreadable"; temp.delete() }
            finally { busy=false; progress="" }
        }
    }
}

/** A bounded, process-private adapter. No document URI or filesystem path is exposed. */
class TileGateway(private val context: Context,serveHttp:Boolean=true) : AutoCloseable {
    private val token=uid()
    private val server=if(serveHttp) ServerSocket(0,24,InetAddress.getByName("127.0.0.1")) else null
    private val entries=ConcurrentHashMap<String,ChartLayer>()
    private val readers=LinkedHashMap<String,ChartReader>(16,.75f,true)
    private val cache=object:android.util.LruCache<String,ByteArray>(24*1024*1024) {override fun sizeOf(key:String,value:ByteArray)=value.size}
    private val workers=ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,ArrayBlockingQueue(48),{ r -> Thread(r,"chart-tile").apply { isDaemon=true } })
    @Volatile var closed=false
    @Volatile var lastError: String?=null
    init { if(server!=null) Thread({ while(!closed) {
        val socket=try { server.accept() } catch(_:Exception) { break }
        try { workers.execute { socket.use { runCatching { serve(it) } } } } catch(_:Exception) { socket.close() }
    } },"chart-loopback").apply { isDaemon=true; start() } }
    fun register(layer: ChartLayer): String {
        entries[layer.id]=layer
        return "http://127.0.0.1:${requireNotNull(server).localPort}/$token/${layer.id}/{z}/{x}/{y}"
    }
    private fun read(file:ChartFile,x:Int,y:Int,z:Int):ByteArray? = synchronized(readers) {
        if(closed) return@synchronized null
        val reader=readers[file.id] ?: ChartReader(context,Uri.parse(file.uri)).also {
            readers[file.id]=it
            if(readers.size>12) { val first=readers.entries.iterator();val expired=first.next();expired.value.close();first.remove() }
        }
        reader.raster(x,y,z,file)
    }
    fun raster(layer:ChartLayer,x:Int,y:Int,z:Int):ByteArray? {
        if(closed) return null
        val key="${layer.id}/$z/$x/$y"
        return cache.get(key) ?: render(layer,x,y,z)?.also {cache.put(key,it)}
    }
    private fun render(layer:ChartLayer,x:Int,y:Int,z:Int):ByteArray? {
        if(z !in 0..24 || x<0 || y<0 || x>=(1 shl z) || y>=(1 shl z)) return null
        val size=layer.rasterSize
        val result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        var found=false
        try {
            val canvas=Canvas(result)
            val paint=Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OVER)
            }
            val pixels=IntArray(size*size)
            // Highest priority first, falling through only where this archive has no coverage.
            // Drawing beneath the accumulated image preserves transparent chart margins while
            // bounding memory even for a folder containing many overlapping transparent files.
            for(file in layer.files) {
                if(closed) return null
                if(z<file.minZoom) continue
                val bytes=try {read(file,x,y,z)} catch(_:Exception) {lastError=file.id;null} ?: continue
                val bitmap=decodeTile(bytes) ?: continue
                try {canvas.drawBitmap(bitmap,null,Rect(0,0,size,size),paint)} finally {bitmap.recycle()}
                found=true
                result.getPixels(pixels,0,size,0,0,size,size)
                if(pixels.all {it ushr 24 == 255}) break
            }
            if(!found) return null
            return java.io.ByteArrayOutputStream().use {out -> result.compress(Bitmap.CompressFormat.PNG,100,out);out.toByteArray()}
        } finally {result.recycle()}
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
        val data=try { raster(entry,x,y,z) }
            catch(_:Exception) { lastError=entry.id; null }
        val status=if(data==null) "404 Not Found" else "200 OK"
        socket.getOutputStream().buffered().use { out ->
            out.write("HTTP/1.1 $status\r\nContent-Type: image/png\r\nContent-Length: ${data?.size ?: 0}\r\nCache-Control: max-age=86400\r\nConnection: close\r\n\r\n".toByteArray())
            if(data!=null) out.write(data)
        }
    }
    override fun close() {
        closed=true;server?.close();workers.shutdownNow()
        synchronized(readers) {readers.values.forEach {runCatching {it.close()}};readers.clear()}
        entries.clear();cache.evictAll()
    }
}

/** The same folder ordering and raster compositor powers every legacy monitoring map. */
class FolderTileProvider(context:Context,layers:List<ChartLayer>) : com.google.android.gms.maps.model.TileProvider,AutoCloseable {
    private val gateway=TileGateway(context,serveHttp=false)
    private val composite=ChartLayer("all-folder-layers","folder charts",layers.flatMap {it.files})
    override fun getTile(x:Int,y:Int,zoom:Int):com.google.android.gms.maps.model.Tile = runCatching {
        val bytes=gateway.raster(composite,x,y,zoom) ?: return com.google.android.gms.maps.model.TileProvider.NO_TILE
        com.google.android.gms.maps.model.Tile(composite.rasterSize,composite.rasterSize,bytes)
    }.getOrDefault(com.google.android.gms.maps.model.TileProvider.NO_TILE)
    override fun close() = gateway.close()
}
