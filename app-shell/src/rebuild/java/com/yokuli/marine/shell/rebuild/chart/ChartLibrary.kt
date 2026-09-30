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
import com.yokuli.chartpackage.ChartPackageSource
import com.yokuli.chartpackage.YokuliChartPackage
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

/** Package metadata and document-provider names are plain display text, never markup. */
internal fun chartDisplayText(value:String,limit:Int=200):String = value
    .filterNot {it.isISOControl() || Character.getType(it) in setOf(Character.FORMAT.toInt(),Character.PRIVATE_USE.toInt())}
    .trim().take(limit)

private fun JSONObject.chartMetadata(key:String):Map<String,String> = optJSONObject(key)?.let { objectValue ->
    objectValue.keys().asSequence().associateWith {objectValue.optString(it)}
} ?: emptyMap()

/** 用户文件夹里的海图档案；priority 越小越优先，enabled 决定是否参与该文件夹图层。 */
data class ChartFile(
    val id: String, val uri: String, val name: String, val source: String,
    val minZoom: Int, val maxZoom: Int, val tileSize: Int, val scheme: String,
    val focus: GeoPoint, val bytes: Long, val attribution: String = "", val enabled: Boolean = true,
    val error: String? = null, val modified: Long = 0, val previewZoom:Double = minZoom.toDouble(),
    val priority: Int = 0, val filename: String = name, val label: String = "",
    val metadata: Map<String,String> = emptyMap(), val packageMetadata: Map<String,String> = emptyMap(),
    val metadataTruncated: Boolean = false, val packagePath:String = ""
) {
    val displayName get() = label.ifBlank { name.ifBlank { filename } }
    fun json() = JSONObject().put("id",id).put("uri",uri).put("name",name).put("source",source)
        .put("min",minZoom).put("max",maxZoom).put("size",tileSize).put("scheme",scheme)
        .put("focus",focus.json()).put("bytes",bytes).put("attribution",attribution)
        .put("enabled",enabled).put("error",error ?: "").put("modified",modified).put("previewZoom",previewZoom)
        .put("priority",priority).put("filename",filename).put("label",label)
        .put("metadata",JSONObject(metadata)).put("packageMetadata",JSONObject(packageMetadata)).put("metadataTruncated",metadataTruncated).put("packagePath",packagePath)
    companion object {
        fun from(j: JSONObject) = ChartFile(j.getString("id"),j.getString("uri"),j.getString("name"),j.optString("source"),
            j.getInt("min"),j.getInt("max"),j.getInt("size"),j.getString("scheme"),GeoPoint.from(j.getJSONObject("focus")),
            j.optLong("bytes"),j.optString("attribution"),j.optBoolean("enabled",true),j.optString("error").takeIf { it.isNotBlank() },j.optLong("modified"),j.optDouble("previewZoom",j.getInt("min").toDouble()),j.optInt("priority"),j.optString("filename",j.getString("name")),j.optString("label"),
            j.chartMetadata("metadata"),j.chartMetadata("packageMetadata"),j.optBoolean("metadataTruncated"),j.optString("packagePath"))
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
    private var cacheLease:File? = null
    private val db: SQLiteDatabase
    init {
        val cache=if(uri.scheme=="content") ChartCache.target(context,uri,true).also {cacheLease=it} else null
        db = try {
        val path = if (uri.scheme == "file") requireNotNull(uri.path) else if(cache?.isFile==true) cache.path else {
            descriptor = context.contentResolver.openFileDescriptor(uri,"r") ?: error("unreadable")
            "/proc/self/fd/${descriptor!!.fd}"
        }
        try { SQLiteDatabase.openDatabase(path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS) }
        catch (e: Exception) {
            descriptor?.close();descriptor=null
            if(uri.scheme!="content") throw e
            val local=ChartCache.copy(context,uri,requireNotNull(cache))
            SQLiteDatabase.openDatabase(local.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        }} catch(e:Exception) {descriptor?.close();cacheLease?.let(ChartCache::release);cacheLease=null;throw e}
    }
    @Synchronized fun inspect(uri: String, name: String, source: String, bytes: Long, modified: Long): ChartFile {
        val columns = db.rawQuery("PRAGMA table_info(tiles)",null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
        require(columns.containsAll(listOf("zoom_level","tile_column","tile_row","tile_data"))) { "schema" }
        var metadataTruncated=false
        // Read bounded values before they enter the cursor window. The original SQLite file
        // remains intact even when a large JSON field exceeds the catalog display budget.
        val metadata = runCatching { db.rawQuery("SELECT substr(name,1,81),substr(value,1,8193) FROM metadata ORDER BY CASE WHEN name IN ('format','scheme','name','center','bounds','attribution','minzoom','maxzoom') THEN 0 ELSE 1 END,name LIMIT 257",null).use { c ->
            val captured=linkedMapOf<String,String>()
            while(c.moveToNext()) {
                val key=c.getString(0).orEmpty();val value=c.getString(1).orEmpty()
                if(captured.containsKey(key) || runCatching {YokuliChartPackage.validateMetadata(captured+(key to value))}.isFailure) metadataTruncated=true
                else captured[key]=value
            }
            if(c.count>256)metadataTruncated=true
            captured.toMap()
        } }.getOrDefault(emptyMap())
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
            metadata["name"]?.let {chartDisplayText(it,120)}?.takeIf { it.isNotBlank() } ?: chartDisplayText(name,120),source,min,max,options.outWidth,scheme,focus,bytes,
            metadata["attribution"]?.let {chartDisplayText(it,700)} ?: "",modified=modified,previewZoom=previewZoom,filename=chartDisplayText(name,200),metadata=metadata,metadataTruncated=metadataTruncated)
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
    @Synchronized override fun close() { try {db.close();descriptor?.close()}finally {cacheLease?.let(ChartCache::release);cacheLease=null} }
}

/** SAF permission does not grant SQLite a path permission. A versioned read-only copy bridges that gap. */
private object ChartCache {
    private val locks=Array(16) {Any()}
    private val readers=mutableMapOf<File,Int>()
    private val retired=mutableSetOf<File>()
    private fun path(context:Context,uri:String,stamp:String):File {
        val id=java.util.UUID.nameUUIDFromBytes((uri+stamp).toByteArray()).toString()
        return File(File(context.filesDir,"chart-source-cache").apply {mkdirs()},"$id.mbtiles")
    }
    fun target(context:Context,uri:Uri,acquire:Boolean=false):File {
        // Querying the provider also verifies that access still exists before using a cached revision.
        val stamp=context.contentResolver.query(uri,arrayOf(Docs.Document.COLUMN_SIZE,Docs.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {
            require(it.moveToFirst()) {"unreadable"};"${it.getLong(0)}:${it.getLong(1)}"
        } ?: error("unreadable")
        return path(context,uri.toString(),stamp).also {file->if(acquire)synchronized(readers){readers[file]=(readers[file] ?: 0)+1}}
    }
    fun release(file:File)=synchronized(readers) {
        val remaining=(readers[file] ?: 1)-1
        if(remaining>0)readers[file]=remaining else {readers.remove(file);if(retired.remove(file))file.delete()}
    }
    /** 元数据已经落盘后才回收旧版本；渲染中的 SQLite reader 持有租约，关闭后再删除。 */
    fun prune(context:Context,files:List<ChartFile>) {
        val keep=files.filter {it.uri.startsWith("content:")}.map {path(context,it.uri,"${it.bytes}:${it.modified}")}.toSet()
        synchronized(readers) {
            retired.removeAll(keep)
            File(context.filesDir,"chart-source-cache").listFiles().orEmpty().filter {it.extension=="mbtiles" && it !in keep}.forEach {file->
                if((readers[file] ?: 0)>0)retired.add(file)else file.delete()
            }
        }
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

/** 文件夹是用户唯一的海图显示组；layerName 仅兼容旧索引，不再另建或删除图层。 */
data class ChartFolder(
    val id: String, val uri: String, val name: String,
    val layerName: String? = null, val enabled: Boolean = true,
    val packageId:String? = null, val provider:String = "", val license:String = "", val attribution:String = "",
    val metadata:Map<String,String> = emptyMap(), val metadataEdited:Boolean = false
) {
    val displayName get() = layerName?.takeIf {it.isNotBlank()} ?: name
    fun json() = JSONObject().put("id",id).put("uri",uri).put("name",name)
        .put("layerName",displayName).put("enabled",enabled).put("packageId",packageId)
        .put("provider",provider).put("license",license).put("attribution",attribution)
        .put("metadata",JSONObject(metadata)).put("metadataEdited",metadataEdited)
    companion object {
        fun from(j:JSONObject) = ChartFolder(j.getString("id"),j.getString("uri"),j.getString("name"),
            j.optString("layerName").takeIf {it.isNotBlank()},j.optBoolean("enabled",true),
            j.optString("packageId").takeIf {it.isNotBlank() && it!="null"},j.optString("provider"),j.optString("license"),j.optString("attribution"),j.chartMetadata("metadata"),j.optBoolean("metadataEdited"))
        fun linked(uri:String,name:String = Uri.decode(uri.substringAfterLast('/')).substringAfter(':')) =
            ChartFolder(java.util.UUID.nameUUIDFromBytes(uri.toByteArray()).toString(),uri,chartDisplayText(name,120).ifBlank {"charts"},chartDisplayText(name,120).ifBlank {"charts"})
    }
}

/** 当前渲染快照，files 已按优先级排序且只包含可读取、已启用的文件。 */
data class ChartLayer(val id:String,val name:String,val files:List<ChartFile>) {
    val rasterSize get() = files.maxOfOrNull {it.tileSize} ?: 256
    val minZoom get() = files.minOfOrNull {it.minZoom} ?: 0
    // A 512-pixel archive contributes one more level of native detail when served as
    // 256-pixel composite tiles. Keep that level instead of prematurely blurring it.
    val maxZoom get() = files.maxOfOrNull {(it.maxZoom+if(it.tileSize==512) 1 else 0).coerceAtMost(24)} ?: 24
}

class ChartLibrary(private val context: Context, private val scope: CoroutineScope) {
    private val index = AtomicFile(File(context.filesDir,"charts-v1.json"))
    private val initialRead = runCatching { JSONObject(index.openRead().bufferedReader().use { it.readText() }) }
    private val indexUnreadable = initialRead.isFailure && (index.baseFile.exists() || File(index.baseFile.path+".bak").exists())
    private val initial = initialRead.getOrDefault(JSONObject())
    private val packageRoot = File(context.filesDir,"chart-packages")
    private val writeMutex = Mutex()
    private var saveGeneration=0L
    var files by mutableStateOf(initial.optJSONArray("files")?.objects()?.mapIndexedNotNull { i,j ->
        runCatching {ChartFile.from(j).let {if(j.has("priority")) it else it.copy(priority=i)}}.getOrNull()
    } ?: emptyList())
    var folders by mutableStateOf(loadFolders())
    /** 从目录移除的文件仍记住身份，重新扫描不会偷偷加回来；用户可显式恢复。 */
    var excludedFiles by mutableStateOf(initial.optJSONArray("excluded")?.objects()?.mapNotNull {runCatching {ChartFile.from(it)}.getOrNull()} ?: emptyList())
        private set
    var busy by mutableStateOf(false)
    /** 只读格式预检不创建目录或导入任务；防止文件选择回调并发重复提交。 */
    private var checkingImport=false
    var progress by mutableStateOf("")
    var exporting by mutableStateOf(false)
        private set
    var exportComplete by mutableStateOf(false)
        private set
    private var exportJob:Job?=null
    var failure by mutableStateOf<String?>(if(indexUnreadable)"catalog-unreadable"else null)
    var rejected by mutableIntStateOf(0)
    var revision by mutableIntStateOf(0)
    init {
        // Capture only pre-existing paths. A newly started import cannot enter this cleanup list.
        val referenced=initial.optJSONArray("folders")?.objects()?.map {it.optString("uri")}?.toSet().orEmpty()
        val abandoned=if(indexUnreadable)emptyList()else packageRoot.listFiles().orEmpty().filter {
            (it.name.matches(Regex("[0-9a-f-]{36}")) || it.name.matches(Regex("\\.[0-9a-f-]{36}\\.partial"))) &&
                Uri.fromFile(it).toString() !in referenced
        }
        scope.launch(Dispatchers.IO) {abandoned.forEach {file->runCatching {
            if(file.canonicalFile.parentFile==packageRoot.canonicalFile)file.deleteRecursively()
        }}}
    }
    val layers get() = folders.map {folder ->
        ChartLayer(folder.id,folder.displayName,folderFiles(folder).filter {it.enabled && it.error==null})
    }
    fun folderFiles(folder:ChartFolder) = files.filter {it.source==folder.uri}.sortedWith(compareBy<ChartFile> {it.priority}.thenBy {it.filename.lowercase()})
    fun allFolderFiles(folder:ChartFolder) = (files+excludedFiles).filter {it.source==folder.uri}.distinctBy {it.id}
        .sortedWith(compareBy<ChartFile> {it.priority}.thenBy {it.filename.lowercase()})
    fun folderMetadata(folder:ChartFolder):Map<String,String> = folder.metadata.toMutableMap().apply {
        listOf("provider" to folder.provider,"license" to folder.license,"attribution" to folder.attribution).forEach {(key,value)->
            if(value.isNotBlank() && key !in this && runCatching {YokuliChartPackage.validateMetadata(this+(key to value))}.isSuccess)put(key,value)
        }
    }
    private fun loadFolders():List<ChartFolder> {
        val array=initial.optJSONArray("folders") ?: JSONArray()
        val linked=(0 until array.length()).mapNotNull {i -> runCatching {
            val item=array.get(i)
            if(item is JSONObject) ChartFolder.from(item) else ChartFolder.linked(item.toString()).let {it.copy(layerName=it.name)}
        }.getOrNull()}.toMutableList()
        // Existing imported copies and old flat selections remain available in a named local folder.
        if(files.any {it.source=="copy"} && linked.none {it.uri=="copy"}) linked.add(ChartFolder("local-copies","copy","imported charts","imported charts"))
        // 旧版独立图层名继续作为显示标签；未创建图层的文件夹也天然可选，ID 和文件顺序保持不变。
        return linked.map {it.copy(layerName=it.displayName)}
    }
    fun errorText(code: String?, zh: Boolean): String = when {
        code=="CHART_DISPLAY_EXTENSION_REQUIRED" -> if(zh) "这是航行数据，请到“数据”页导入 .yklgeodata 或原始数据文件。海图只接受 .yklchart 和 MBTiles。" else "Import navigation data in Data as .yklgeodata or original files. Charts accepts only .yklchart and MBTiles."
        code=="CHART_DISPLAY_FORMAT_UNSUPPORTED" -> if(zh) "请选择 .yklchart 或栅格 MBTiles 文件；不支持 .yklcharts 等其他后缀。" else "Choose a .yklchart or raster MBTiles file. Other suffixes, including .yklcharts, are not supported."
        code=="YKLCHART_KIND_MISMATCH" -> if(zh) "这是数据包，请在图册的“数据”页导入。" else "Import this data package in the library's Data tab."
        code=="YKLCHART_STORAGE_FAILED" -> legacyErrorText("space",zh)
        code=="YKLCHART_VERSION_UNSUPPORTED" -> if(zh) "此图包版本暂不支持，请更新应用或获取兼容的 .yklchart 图包。" else "This package version is unsupported. Update the app or obtain a compatible .yklchart package."
        code=="YKLCHART_CHART_FORMAT_UNSUPPORTED" -> if(zh) "海图包只支持 PNG/JPEG/WebP 栅格 MBTiles。" else "Chart packages support PNG/JPEG/WebP raster MBTiles only."
        code?.contains("SIZE_LIMIT")==true -> if(zh) "图包超出可处理范围，原集合保留。" else "This package exceeds the supported size. The previous collection is preserved."
        code?.startsWith("YKLCHART_")==true -> if(zh) "海图包不完整、格式无效或校验失败，未安装；原集合保留。请重新获取完整的 .yklchart 文件。" else "The chart package is incomplete, invalid or failed verification. The previous collection is preserved. Obtain a complete .yklchart file and retry."
        else -> legacyErrorText(code,zh)
    }
    private fun legacyErrorText(code: String?, zh: Boolean): String = when(code) {
        "data-package" -> if(zh) "这是航行数据包，请在图册的“数据”页导入。这里仅管理 MBTiles 海图。" else "Import this navigation dataset in the library's Data tab. Charts manages MBTiles files."
        "vector" -> if(zh) "这是矢量海图；请使用栅格 MBTiles" else "Vector chart. Use raster MBTiles."
        "empty" -> if(zh) "文件没有图块" else "No tiles in this file"
        "space" -> if(zh) "空间不足，未完成导入" else "Not enough storage to import"
        "schema","raster","tile","scheme","coordinate","zoom" -> if(zh) "不支持的海图格式，请使用 PNG/JPEG/WebP MBTiles" else "Use PNG/JPEG/WebP raster MBTiles"
        "permission" -> if(zh) "文件夹访问授权已失效，请重新选择" else "Folder access expired. Select it again."
        "limit" -> if(zh) "文件夹过大，请选择较小的子文件夹" else "Choose a smaller subfolder"
        "save" -> if(zh) "目录保存失败，请检查存储空间" else "Could not save the library. Check storage."
        "catalog-unreadable" -> if(zh) "原图册目录未能读取，已停止写入以保护离线副本。请重新启动后重试。" else "The existing catalog could not be read. Writes are blocked to preserve offline copies. Restart and retry."
        "export-empty" -> if(zh) "文件夹没有可导出的海图文件。" else "This folder has no chart files to export."
        "export-failed" -> if(zh) "导出未完成。请检查文件访问权限和保存位置的空间；原文件夹保留。" else "Export did not finish. Check access to every source and available storage at the destination. The original folder is preserved."
        "export-cancelled" -> if(zh) "已取消导出，原文件夹保留。" else "Export cancelled. The original folder is preserved."
        "export-cancelled-partial" -> if(zh) "已取消导出，但保存位置可能残留不完整文件，请删除后重试。原文件夹保留。" else "Export cancelled, but an incomplete file may remain at the destination. Delete it before retrying. The original folder is preserved."
        "export-failed-partial" -> if(zh) "导出未完成，保存位置可能残留不完整文件，请删除后重试。原文件夹保留。" else "Export did not finish and an incomplete file may remain at the destination. Delete it before retrying. The original folder is preserved."
        "metadata" -> if(zh) "资料字段无效或过大，未保存。" else "The metadata fields are invalid or too large. Nothing was saved."
        else -> if(zh) "无法读取文件；可尝试导入应用内副本" else "Cannot read this file. Try importing a local copy."
    }
    private fun persist() {
        if(indexUnreadable) {failure="catalog-unreadable";return}
        revision++
        val generation=++saveGeneration
        val referencedFiles=files.toList()
        val snapshot = JSONObject().put("version",4).put("files",JSONArray(files.map { it.json() })).put("folders",JSONArray(folders.map {it.json()})).put("excluded",JSONArray(excludedFiles.map {it.json()})).toString()
        scope.launch(Dispatchers.IO) { writeMutex.withLock {
            if(generation!=saveGeneration)return@withLock
            runCatching {
                val out = index.startWrite()
                try { out.write(snapshot.toByteArray()); index.finishWrite(out) }
                catch(e:Exception) { index.failWrite(out); throw e }
            }.onSuccess {ChartCache.prune(context,referencedFiles)}.onFailure { withContext(Dispatchers.Main) { failure = "save" } }
        } }
    }
    /** The new catalog becomes visible only after its AtomicFile write succeeds. */
    private suspend fun commitCatalog(nextFiles:List<ChartFile>,nextFolders:List<ChartFolder>,nextExcluded:List<ChartFile>) {
        check(!indexUnreadable) {"catalog-unreadable"}
        ++saveGeneration
        val snapshot=JSONObject().put("version",4).put("files",JSONArray(nextFiles.map {it.json()}))
            .put("folders",JSONArray(nextFolders.map {it.json()})).put("excluded",JSONArray(nextExcluded.map {it.json()})).toString()
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {writeMutex.withLock {
                val out=try {index.startWrite()} catch(e:Exception) {throw java.io.IOException("save",e)}
                try {out.write(snapshot.toByteArray());index.finishWrite(out)}
                catch(e:Exception) {index.failWrite(out);throw java.io.IOException("save",e)}
            }}
            files=nextFiles;folders=nextFolders;excludedFiles=nextExcluded;revision++
        }
    }
    fun toggle(file: ChartFile) { if(busy)return; files = files.map { if(it.id==file.id) it.copy(enabled=!it.enabled) else it }; persist() }
    /** 兼容历史调用；文件夹重命名只有一个标签与一个显示组。 */
    fun setLayer(folder:ChartFolder,name:String) = renameFolder(folder,name)
    fun moveFile(file:ChartFile,delta:Int) {
        if(busy)return
        val ordered=files.filter {it.source==file.source}.sortedBy {it.priority}.toMutableList()
        val from=ordered.indexOfFirst {it.id==file.id};if(from<0) return
        val to=(from+delta).coerceIn(0,ordered.lastIndex);if(from==to) return
        ordered.add(to,ordered.removeAt(from));val priorities=ordered.mapIndexed {i,f -> f.id to i}.toMap()
        files=files.map {f -> priorities[f.id]?.let {f.copy(priority=it)} ?: f};persist()
    }
    /** Make a file available. The map source selection belongs to MapSessionStore. */
    fun showOnly(file: ChartFile) {
        if(busy)return
        files=files.map {if(it.id==file.id) it.copy(enabled=true) else it}
        folders=folders.map {if(it.uri==file.source) it.copy(enabled=true,layerName=it.layerName ?: it.name) else it};persist()
    }
    fun renameFile(file:ChartFile,name:String) {
        if(busy)return
        val title=chartDisplayText(name,100);if(title.isBlank())return
        files=files.map {if(it.id==file.id)it.copy(label=title)else it};persist()
    }
    fun renameFolder(folder:ChartFolder,name:String) {
        if(busy)return
        val title=chartDisplayText(name,100);if(title.isBlank())return
        folders=folders.map {if(it.id==folder.id)it.copy(name=title,layerName=title)else it};persist()
    }
    fun updateFolderMetadata(folder:ChartFolder,metadata:Map<String,String>,onSaved:()->Unit={}) {
        if(busy)return
        val checked=runCatching {YokuliChartPackage.validateMetadata(metadata)}.getOrElse {failure="metadata";return}
        busy=true;failure=null;exportComplete=false
        scope.launch {
            try {
                val current=folders.firstOrNull {it.id==folder.id} ?: error("unreadable")
                val previousEditable=folderMetadata(current)
                fun field(key:String,previous:String,limit:Int)=chartDisplayText(checked[key] ?: if(key in previousEditable)""else previous,limit)
                val updated=current.copy(metadata=checked,metadataEdited=true,
                    provider=field("provider",current.provider,512),license=field("license",current.license,512),
                    attribution=field("attribution",current.attribution,8192))
                commitCatalog(files,folders.map {if(it.id==folder.id)updated else it},excludedFiles)
                onSaved()
            } catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {failure=error.message ?: "save"}
            finally {busy=false;progress=""}
        }
    }
    fun includeAll(folder:ChartFolder,included:Boolean) {
        if(busy)return
        files=files.map {if(it.source==folder.uri && it.error==null)it.copy(enabled=included)else it};persist()
    }
    fun forget(file: ChartFile) {
        if(busy)return
        excludedFiles=excludedFiles.filterNot {it.id==file.id}+file
        files=files.filterNot {it.id==file.id};persist()
    }
    fun restore(file:ChartFile) {
        if(busy)return
        excludedFiles=excludedFiles.filterNot {it.id==file.id}
        if(files.none {it.id==file.id})files=files+file.copy(enabled=true,priority=(files.filter {it.source==file.source}.maxOfOrNull {it.priority} ?: -1)+1)
        persist()
    }
    fun forgetFolder(uri:String,onRemoved:()->Unit={}) {
        if(busy)return
        val folder=folders.firstOrNull {it.uri==uri} ?: return
        busy=true;failure=null
        scope.launch {
            try {
                withContext(NonCancellable) {
                    commitCatalog(files.filterNot {it.source==uri},folders.filterNot {it.uri==uri},excludedFiles.filterNot {it.source==uri})
                    onRemoved()
                    if(folder.packageId!=null)withContext(Dispatchers.IO) {ownedPackageDirectory(folder)?.deleteRecursively()}
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(e:Exception) {failure=e.message ?: "save"}
            finally {busy=false}
        }
    }
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
                    val managed=folders.firstOrNull {it.uri==tree && it.packageId!=null}
                    if(managed!=null) {rescanPackage(managed);continue}
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
                                val id=c.getString(0); val originalName=c.getString(1); val name=chartDisplayText(originalName,200); val mime=c.getString(2)
                                if(mime==Docs.Document.MIME_TYPE_DIR) { queue.add(id to depth+1); continue }
                                if(!originalName.endsWith(".mbtiles",true)) continue
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
                    excludedFiles=excludedFiles.map {old->collected.firstOrNull {it.id==old.id}?.copy(label=old.label,enabled=old.enabled,priority=old.priority,packageMetadata=old.packageMetadata,packagePath=old.packagePath) ?: old}
                    val discovered=collected.filter {candidate->excludedFiles.none {it.id==candidate.id}}.sortedBy {it.filename.lowercase()}.map {f ->
                        f.copy(enabled=previous[f.id]?.enabled ?: true,priority=previous[f.id]?.priority ?: nextPriority++,label=previous[f.id]?.label.orEmpty(),packageMetadata=previous[f.id]?.packageMetadata.orEmpty(),packagePath=previous[f.id]?.packagePath.orEmpty())
                    }
                    files=files.filter {it.source!=tree}+discovered
                    persist()
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(e:Exception) { failure=if(e is SecurityException) "permission" else e.message ?: "unreadable" }
            finally { busy=false; progress="" }
        }
    }
    private fun ownedPackageDirectory(folder:ChartFolder):File? = runCatching {
        require(folder.packageId!=null)
        val uri=Uri.parse(folder.uri);require(uri.scheme=="file")
        val file=File(requireNotNull(uri.path)).canonicalFile
        require(file.parentFile==packageRoot.canonicalFile)
        file
    }.getOrNull()

    private suspend fun rescanPackage(folder:ChartFolder) {
        val directory=ownedPackageDirectory(folder) ?: error("unreadable")
        val inspected=withContext(Dispatchers.IO) {
            (files+excludedFiles).filter {it.source==folder.uri}.associate {old ->
                ensureActive()
                val file=runCatching {File(requireNotNull(Uri.parse(old.uri).path)).canonicalFile}.getOrNull()
                val updated=runCatching {
                    require(file!=null && file.isFile && file.path.startsWith(directory.path+File.separator)) {"unreadable"}
                    ChartReader(context,Uri.fromFile(file)).use {it.inspect(old.uri,old.filename,folder.uri,file.length(),file.lastModified())}
                        .copy(id=old.id,label=old.label,enabled=old.enabled,priority=old.priority,packageMetadata=old.packageMetadata,packagePath=old.packagePath)
                }.getOrElse {old.copy(error=it.message?.takeIf {code->code in setOf("vector","schema","raster","empty","zoom","scheme","tile")} ?: "unreadable")}
                old.id to updated
            }
        }
        commitCatalog(files.map {inspected[it.id] ?: it},folders,excludedFiles.map {inspected[it.id] ?: it})
        inspected.values.count {it.error!=null}.takeIf {it>0}?.let {rejected+=it;failure="unreadable"}
    }

    private suspend fun installPackage(uri:Uri) {
        val token=uid()
        val staging=File(packageRoot,".$token.partial")
        val target=File(packageRoot,token)
        var committed=false
        try {
            val installed=withContext(Dispatchers.IO) {
                check(packageRoot.isDirectory || packageRoot.mkdirs()) {"space"}
                val active=currentCoroutineContext()
                val input=if(uri.scheme=="file")File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri) ?: error("unreadable")
                val manifest=input.use {source ->
                    YokuliChartPackage.extract(source,staging,"charts") {
                        active.ensureActive()
                        require(packageRoot.usableSpace>128_000_000L) {"space"}
                    }
                }
                require(manifest.files.all {it.format=="mbtiles"}) {"YKLCHART_CHART_FORMAT_UNSUPPORTED"}
                val folderId="package-"+java.util.UUID.nameUUIDFromBytes(manifest.id.toByteArray(Charsets.UTF_8))
                val folder=ChartFolder(folderId,Uri.fromFile(target).toString(),chartDisplayText(manifest.name,120).ifBlank {"charts"},
                    packageId=manifest.id,provider=chartDisplayText(manifest.provider,512),license=chartDisplayText(manifest.license,512),attribution=chartDisplayText(manifest.attribution,8192),metadata=manifest.metadata)
                val charts=manifest.files.sortedBy {it.priority}.mapIndexed {order,entry ->
                    active.ensureActive()
                    val source=File(staging,entry.path)
                    withContext(Dispatchers.Main) {progress=chartDisplayText(source.name,200)}
                    val inspected=ChartReader(context,Uri.fromFile(source)).use {it.inspect(Uri.fromFile(source).toString(),source.name,folder.uri,source.length(),source.lastModified())}
                    java.io.FileOutputStream(source,true).use {it.fd.sync()}
                    inspected.copy(id=java.util.UUID.nameUUIDFromBytes("$folderId/${entry.path}".toByteArray(Charsets.UTF_8)).toString(),
                        uri=Uri.fromFile(File(target,entry.path)).toString(),priority=order,packageMetadata=entry.metadata,packagePath=entry.path)
                }
                active.ensureActive()
                check(staging.renameTo(target)) {"space"}
                folder to charts
            }
            val (candidate,charts)=installed
            val previous=folders.firstOrNull {it.packageId==candidate.packageId}
            val previousFiles=files.filter {it.source==previous?.uri}.associateBy {it.id}
            val removed=excludedFiles.filter {it.source==previous?.uri}.associateBy {it.id}
            val oldOrder=(previousFiles.values+removed.values).sortedBy {it.priority}.mapIndexed {order,chart->chart.id to order}.toMap()
            val refreshed=charts.sortedWith(compareBy<ChartFile> {oldOrder[it.id] ?: Int.MAX_VALUE}.thenBy {it.priority})
                .mapIndexed {order,chart ->(previousFiles[chart.id] ?: removed[chart.id])?.let {
                    chart.copy(label=it.label,enabled=it.enabled,priority=order)
                } ?: chart.copy(priority=order)}
            val folder=previous?.let {candidate.copy(name=it.name,layerName=it.layerName,enabled=it.enabled,
                metadata=if(it.metadataEdited)it.metadata else candidate.metadata,metadataEdited=it.metadataEdited,
                provider=if(it.metadataEdited)it.provider else candidate.provider,license=if(it.metadataEdited)it.license else candidate.license,
                attribution=if(it.metadataEdited)it.attribution else candidate.attribution)} ?: candidate
            val nextFolders=if(previous==null)folders+folder else folders.map {if(it.id==previous.id)folder else it}
            val absentRemoved=removed.values.filter {old->charts.none {it.id==old.id}}.mapNotNull {old ->
                val previousDirectory=previous?.let(::ownedPackageDirectory) ?: return@mapNotNull null
                val oldFile=File(Uri.parse(old.uri).path ?: return@mapNotNull null)
                if(!oldFile.path.startsWith(previousDirectory.path+File.separator))return@mapNotNull null
                old.copy(source=folder.uri,uri=Uri.fromFile(File(target,oldFile.relativeTo(previousDirectory).path)).toString(),error="unreadable")
            }
            withContext(NonCancellable) {
                commitCatalog(files.filterNot {it.source==previous?.uri}+refreshed.filterNot {it.id in removed},nextFolders,
                    excludedFiles.filterNot {it.source==previous?.uri}+refreshed.filter {it.id in removed}+absentRemoved)
                committed=true
                previous?.let(::ownedPackageDirectory)?.let {old->withContext(Dispatchers.IO) {old.deleteRecursively()}}
            }
        } finally {
            withContext(NonCancellable+Dispatchers.IO) {staging.deleteRecursively();if(!committed)target.deleteRecursively()}
        }
    }

    fun cancelExport() {exportJob?.cancel()}

    /** Snapshot every retained member before creating the package; the source folder is never rewritten. */
    fun exportFolder(folder:ChartFolder,destination:Uri) {
        if(busy)return
        val current=folders.firstOrNull {it.id==folder.id} ?: return
        val members=allFolderFiles(current).toList()
        if(members.isEmpty()) {failure="export-empty";return}
        busy=true;exporting=true;exportComplete=false;failure=null;rejected=0
        exportJob=scope.launch {
            val staging=File(context.cacheDir,"chart-export-${uid()}")
            var completed=false
            var cancelled=false
            try {
                withContext(Dispatchers.IO) {
                    check(staging.mkdirs()) {"space"}
                    require(members.size<=YokuliChartPackage.MAX_FILES) {"YKLCHART_FILE_COUNT_LIMIT"}
                    val active=currentCoroutineContext()
                    var total=0L
                    val sources=members.mapIndexed {order,file ->
                        active.ensureActive()
                        withContext(Dispatchers.Main) {progress="${order+1} / ${members.size} · ${file.filename}"}
                        val snapshot=File(staging,"$order.mbtiles")
                        val sourceUri=Uri.parse(file.uri)
                        val input=if(sourceUri.scheme=="file")File(requireNotNull(sourceUri.path)).inputStream()
                            else context.contentResolver.openInputStream(sourceUri) ?: error("unreadable")
                        input.use {source ->snapshot.outputStream().buffered().use {output ->
                            val buffer=ByteArray(64*1024);var bytes=0L
                            while(true) {
                                active.ensureActive()
                                val count=source.read(buffer);if(count<0)break
                                bytes+=count;total+=count
                                require(bytes<=YokuliChartPackage.MAX_FILE_BYTES && total<=YokuliChartPackage.MAX_TOTAL_BYTES) {"YKLCHART_SIZE_LIMIT"}
                                require(staging.usableSpace>count+128_000_000L) {"space"}
                                output.write(buffer,0,count)
                            }
                        }}
                        // Inspect the immutable copy so metadata and exported payload describe the same file.
                        ChartReader(context,Uri.fromFile(snapshot)).use {
                            it.inspect(Uri.fromFile(snapshot).toString(),file.filename,current.uri,snapshot.length(),snapshot.lastModified())
                        }
                        val filename=java.text.Normalizer.normalize(file.filename.substringBeforeLast('.').filter {it.isLetterOrDigit() || it in "-_ "}.trim().take(40),java.text.Normalizer.Form.NFC).ifBlank {"chart"}+".mbtiles"
                        val path=file.packagePath.ifBlank {"files/${java.util.UUID.nameUUIDFromBytes(file.id.toByteArray(Charsets.UTF_8))}/$filename"}
                        // Package annotations and embedded source metadata remain distinct. Existing
                        // annotations are preserved exactly; the immutable MBTiles carries every raw field.
                        val entryMetadata=file.packageMetadata
                        ChartPackageSource(path,"mbtiles",order,YokuliChartPackage.validateMetadata(entryMetadata)) {snapshot.inputStream()}
                    }
                    val archive=File(staging,"folder.yklchart")
                    withContext(Dispatchers.Main) {progress="${members.size} / ${members.size} · .yklchart"}
                    archive.outputStream().use {archiveOutput ->YokuliChartPackage.write(archiveOutput,
                        id=current.packageId ?: "chart-folder-${java.util.UUID.nameUUIDFromBytes(current.id.toByteArray(Charsets.UTF_8))}",
                        name=current.displayName,kind="charts",files=sources,metadata=current.metadata,
                        provider=current.provider,license=current.license,attribution=current.attribution,
                        check={active.ensureActive();require(staging.usableSpace>128_000_000L) {"space"}})}
                    active.ensureActive()
                    // Only a complete verified local package reaches the selected document provider.
                    val descriptor=context.contentResolver.openFileDescriptor(destination,"wt") ?: error("unreadable")
                    descriptor.use {
                        val canSync=descriptor.statSize>=0
                        coroutineScope {
                            // Closing the descriptor also releases a provider blocked inside write().
                            val closer=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {
                                try {awaitCancellation()}finally {runCatching {descriptor.close()}}
                            }
                            try {ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use {output ->archive.inputStream().use {input ->
                                val buffer=ByteArray(64*1024);var copied=0L;var lastPercent=-1
                                while(true) {
                                    active.ensureActive()
                                    val count=input.read(buffer);if(count<0)break
                                    output.write(buffer,0,count);copied+=count
                                    val percent=(copied*100/archive.length().coerceAtLeast(1)).toInt()
                                    if(percent!=lastPercent) {lastPercent=percent;withContext(Dispatchers.Main) {progress="$percent% · .yklchart"}}
                                }
                                output.flush();active.ensureActive()
                                if(canSync)output.fd.sync()
                            }}}finally {withContext(NonCancellable) {closer.cancelAndJoin()}}
                        }
                    }
                    completed=true
                }
                exportComplete=true
            } catch(cancel:CancellationException) {cancelled=true;failure="export-cancelled";throw cancel}
            catch(error:Exception) {
                cancelled=!currentCoroutineContext().isActive
                failure=if(cancelled)"export-cancelled"else if(error is SecurityException)"permission"else "export-failed"
            }
            finally {
                withContext(NonCancellable) {
                    val cleanupFailed=withContext(Dispatchers.IO) {
                        runCatching {staging.deleteRecursively()}
                        !completed && !runCatching {Docs.deleteDocument(context.contentResolver,destination)}.getOrDefault(false)
                    }
                    if(cleanupFailed)failure=if(cancelled)"export-cancelled-partial"else "export-failed-partial"
                    busy=false;exporting=false;progress="";exportJob=null
                }
            }
        }
    }

    fun importCopy(uri: Uri) {
        if(busy||checkingImport) return
        if(indexUnreadable) {failure="catalog-unreadable";return}
        checkingImport=true;failure=null
        scope.launch {
            var ownsBusy=false
            var temp:File?=null
            var copied:File?=null
            var committed=false
            try {
                // 按实际文档名称和包清单预检，不能先将错误资料复制数百 MB 才告诉用户选错入口。
                val name=withContext(Dispatchers.IO) {
                    val sourceName=if(uri.scheme=="file")uri.path?.let {File(it).name} else context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null}
                    val extension=sourceName?.substringAfterLast('.',"")?.lowercase(java.util.Locale.ROOT)
                    require(extension !in setOf("yklgeodata","gpkg","zip","tif","tiff","asc","ascii","nc","nc4","h5","hdf5")&&(extension?.let {it.length==3&&it.all(Char::isDigit)}!=true)) {"CHART_DISPLAY_EXTENSION_REQUIRED"}
                    require(extension in setOf("yklchart","mbtiles")) {"CHART_DISPLAY_FORMAT_UNSUPPORTED"}
                    val active=currentCoroutineContext()
                    if(extension=="yklchart") {
                        val input=if(uri.scheme=="file")File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri) ?: error("unreadable")
                        input.use {YokuliChartPackage.readManifest(it,"charts") {active.ensureActive()}}
                    }
                    requireNotNull(sourceName)
                }
                // 预读期间可能开始其他目录操作；不与其抢占目录提交，也不清掉其 busy 状态。
                if(busy)return@launch
                busy=true;ownsBusy=true;checkingImport=false
                progress=chartDisplayText(name,200)
                if(name.endsWith(".yklchart",true)) {installPackage(uri);return@launch}
                val directory=withContext(Dispatchers.IO) {File(context.filesDir,"chart-copies").apply {check(isDirectory||mkdirs()) {"space"}}}
                val staging=File(directory,"${uid()}.partial");temp=staging
                val chart=withContext(Dispatchers.IO) {
                    val input=if(uri.scheme=="file")File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri) ?: error("unreadable")
                    input.use { source -> staging.outputStream().buffered().use { output ->
                        val buffer=ByteArray(1024*1024); var total=0L
                        while(true) {
                            ensureActive(); val n=source.read(buffer); if(n<0) break
                            total+=n; require(directory.usableSpace > n+128_000_000 && total<=8_000_000_000L) { "space" }
                            output.write(buffer,0,n)
                        }
                    } }
                    val checked=ChartReader(context,Uri.fromFile(staging)).use { it.inspect(Uri.fromFile(staging).toString(),name,"copy",staging.length(),0) }
                    val target=File(directory,"${uid()}.mbtiles")
                    check(staging.renameTo(target)) { "space" }
                    copied=target
                    checked.copy(id=uid(),uri=Uri.fromFile(target).toString(),modified=target.lastModified())
                }
                withContext(NonCancellable) {
                    val nextFolders=if(folders.none {it.uri=="copy"})folders+ChartFolder("local-copies","copy","imported charts","imported charts")else folders
                    commitCatalog(files+chart.copy(priority=(files.filter {it.source=="copy"}.maxOfOrNull {it.priority} ?: -1)+1),nextFolders,excludedFiles)
                    committed=true
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(e:Exception) {failure=if(e is SecurityException)"permission"else e.message ?: "unreadable"}
            finally {
                withContext(NonCancellable+Dispatchers.IO) {temp?.delete();if(!committed)copied?.delete()}
                checkingImport=false
                if(ownsBusy){busy=false;progress=""}
            }
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
