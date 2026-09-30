package com.yokuli.chartpackage

import com.google.gson.Gson
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

/** 包元数据只声明来源，不授予分析或导航资格。 */
data class ChartPackageManifest(
    /** 固定格式标识 yokuli.chart-package，与安装应用 .ykl 分离。 */
    val format: String,
    /** 当前清单协议版本。 */
    val version: Int,
    /** 制作者声明的稳定资料包标识，不替代本机图册 ID。 */
    val id: String,
    /** 制作者提供的集合显示名称。 */
    val name: String,
    /** data 为航行数据；charts 为显示用背景海图，两类禁止混装。 */
    val kind: String,
    /** UTC ISO 8601 制包时间，不冒充原资料的测量或修订日期。 */
    val createdAt: String,
    /** 原资料提供方说明。 */
    val provider: String,
    /** 许可声明，仍需使用者确认适用范围。 */
    val license: String,
    /** 必须保留的来源署名。 */
    val attribution: String,
    /** 严格按唯一非负优先级递增排列的有效载荷清单。 */
    val files: List<ChartPackageFile>,
    /** 本层说明：集合与文件独立保存，不能作为数据精度或能力的声明。 */
    val metadata: Map<String, String> = emptyMap(),
)

data class ChartPackageFile(
    /** 包内 files/ 开始的安全相对路径，解包时原样保留。 */
    val path: String,
    /** 未压缩有效载荷的精确字节数。 */
    val bytes: Long,
    /** 原始有效载荷的 SHA-256 十六进制摘要。 */
    val sha256: String,
    /** gpkg / s57 / gebco / mbtiles，只选择原有真实格式解析器。 */
    val format: String,
    /** 数值越小越优先；更新时已有手动顺序仍由图册所有者保留。 */
    val priority: Int,
    val metadata: Map<String, String> = emptyMap(),
    val rasterProduct: String? = null,
)

/** 每次打开同一不可变原文件；写出时再次核对字节数与摘要。 */
data class ChartPackageSource(val path: String, val format: String, val priority: Int,
    val metadata: Map<String, String> = emptyMap(), val rasterProduct: String? = null, val open: () -> InputStream)

/** 可供调用方展示或翻译的稳定错误码，不包含原文件内容。 */
class ChartPackageException(val code: String, cause: Throwable? = null) : IOException(code, cause)

/** 航行数据与背景海图共用的有界流式传输校验。 */
object YokuliChartPackage {
    const val FORMAT = "yokuli.chart-package"
    const val VERSION = 2
    const val MAX_FILE_BYTES = 32_000_000_000L
    const val MAX_TOTAL_BYTES = 64_000_000_000L
    const val MAX_FILES = 2_000
    const val MAX_MANIFEST_BYTES = 1_048_576
    private val manifestFields = setOf("format", "version", "id", "name", "kind", "createdAt", "provider", "license", "attribution", "files")
    private val fileFields = setOf("path", "bytes", "sha256", "format", "priority")
    private val integer = Regex("0|[1-9][0-9]*")
    private val digestPattern = Regex("[0-9a-fA-F]{64}")
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")

    /** 只读取元数据，成功不表示已验证有效载荷；正式导入必须调用 [extract]。 */
    fun readManifest(input: InputStream, expectedKind: String, check: () -> Unit = {}): ChartPackageManifest =
        ZipInputStream(input.buffered()).use { zip -> firstManifest(zip, expectedKind, check, false) }

    internal fun readAtlasManifest(input: InputStream, check: () -> Unit): ChartPackageManifest =
        ZipInputStream(input.buffered()).use { zip -> firstManifest(zip, "atlas", check, true) }

    /**
     * 仅把声明的有效载荷解到空暂存目录，保留 files/... 原路径，并同步到磁盘后返回。
     * 暂存目录的取消/失败清理及原子发布由调用方负责。
     * 每次读取数据块前调用 [check]，供调用方检查取消和剩余磁盘空间。
     */
    fun extract(input: InputStream, directory: File, expectedKind: String, check: () -> Unit = {}): ChartPackageManifest =
        extractContainer(input,directory,expectedKind,check,false)

    internal fun extractAtlas(input:InputStream,directory:File,check:()->Unit):ChartPackageManifest =
        extractContainer(input,directory,"atlas",check,true)

    private fun extractContainer(input: InputStream, directory: File, expectedKind: String, check: () -> Unit, atlas:Boolean): ChartPackageManifest {
        check()
        if ((!directory.isDirectory && !directory.mkdirs()) || directory.listFiles()?.isEmpty() != true) fail("YKLCHART_STAGE_NOT_EMPTY")
        val root = directory.canonicalFile
        return ZipInputStream(input.buffered()).use { zip ->
            val manifest = firstManifest(zip, expectedKind, check, atlas)
            val declared = manifest.files.associateBy { it.path }
            val found = hashSetOf("manifest.json")
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                check()
                val entry = zipCall { zip.nextEntry } ?: break
                if (entry.isDirectory || !safePath(entry.name)) fail("YKLCHART_PATH_INVALID")
                if (!found.add(entry.name.lowercase(Locale.ROOT))) fail("YKLCHART_DUPLICATE_PATH")
                val file = declared[entry.name] ?: fail("YKLCHART_UNLISTED_FILE")
                if (entry.size >= 0 && entry.size != file.bytes) fail("YKLCHART_BYTE_COUNT_MISMATCH")
                val target = File(root, file.path)
                if (!target.canonicalPath.startsWith(root.path + File.separator)) fail("YKLCHART_PATH_INVALID")
                val parent = target.parentFile ?: fail("YKLCHART_PATH_INVALID")
                if (!parent.isDirectory && !parent.mkdirs()) fail("YKLCHART_STORAGE_FAILED")
                if (target.exists()) fail("YKLCHART_DUPLICATE_PATH")
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                // CREATE_NEW 禁止覆盖现有文件，也拒绝已有的悬空符号链接。
                val channel = try {
                    FileChannel.open(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                } catch (error: IOException) { throw ChartPackageException("YKLCHART_STORAGE_FAILED", error) }
                try {
                    channel.use { fileChannel ->
                        Channels.newOutputStream(fileChannel).buffered().use { out ->
                            while (true) {
                                check()
                                val count = zipCall { zip.read(buffer) }
                                if (count < 0) break
                                bytes += count; total += count
                                if (bytes > file.bytes || bytes > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) fail("YKLCHART_BYTE_COUNT_MISMATCH")
                                digest.update(buffer, 0, count)
                                out.write(buffer, 0, count)
                            }
                            check()
                            out.flush()
                            fileChannel.force(true)
                        }
                    }
                } catch (error: ChartPackageException) { throw error }
                  catch (error: IOException) { throw ChartPackageException("YKLCHART_STORAGE_FAILED", error) }
                if (bytes != file.bytes) fail("YKLCHART_BYTE_COUNT_MISMATCH")
                val actualDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!actualDigest.equals(file.sha256, ignoreCase = true)) fail("YKLCHART_SHA256_MISMATCH")
                zipCall { zip.closeEntry() }
            }
            if (found.size != declared.size + 1) fail("YKLCHART_MISSING_FILE")
            check()
            manifest
        }
    }

    private fun firstManifest(zip: ZipInputStream, expectedKind: String, check: () -> Unit, atlas:Boolean): ChartPackageManifest {
        if (expectedKind !in (if(atlas)setOf("atlas")else setOf("data", "charts"))) fail("YKLCHART_KIND_INVALID")
        check()
        val first = zipCall { zip.nextEntry } ?: fail("YKLCHART_MANIFEST_MISSING")
        if (first.name != "manifest.json" || first.isDirectory) fail("YKLCHART_MANIFEST_FIRST_REQUIRED")
        if (first.size > MAX_MANIFEST_BYTES) fail("YKLCHART_MANIFEST_SIZE_LIMIT")
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            check()
            val count = zipCall { zip.read(buffer) }
            if (count < 0) break
            if (bytes.size() + count > MAX_MANIFEST_BYTES) fail("YKLCHART_MANIFEST_SIZE_LIMIT")
            bytes.write(buffer, 0, count)
        }
        zipCall { zip.closeEntry() }
        val manifest = parseManifest(bytes.toByteArray(),atlas)
        if (manifest.kind != expectedKind) fail("YKLCHART_KIND_MISMATCH")
        return manifest
    }

    private fun parseManifest(bytes: ByteArray,atlas:Boolean=false): ChartPackageManifest = try {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val strings = mutableMapOf<String, String>()
            var version: Long? = null
            var files: List<ChartPackageFile>? = null
            var metadata: Map<String,String> = emptyMap()
            var metadataPresent = false
            var fileMetadataPresent = false
            fields(reader, manifestFields, setOf("metadata")) { key ->
                when (key) {
                    "version" -> version = number(reader)
                    "metadata" -> { metadataPresent = true; metadata = readMetadata(reader) }
                    "files" -> {
                        val entries = ArrayList<ChartPackageFile>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (entries.size >= MAX_FILES) fail("YKLCHART_FILE_COUNT_LIMIT")
                            entries += readFile(reader,atlas) { fileMetadataPresent = true }
                        }
                        reader.endArray()
                        files = entries
                    }
                    else -> strings[key] = string(reader)
                }
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) fail("YKLCHART_MANIFEST_INVALID")
            if (strings.getValue("format") != (if(atlas)YokuliAtlasPackage.FORMAT else FORMAT) || version?.let { it in 1L..(if(atlas)1L else VERSION.toLong()) } != true) fail("YKLCHART_VERSION_UNSUPPORTED")
            if (strings.getValue("kind") !in (if(atlas)setOf("atlas")else setOf("data", "charts"))) fail("YKLCHART_KIND_INVALID")
            if (!idPattern.matches(strings.getValue("id"))) fail("YKLCHART_MANIFEST_INVALID")
            validateText(strings.getValue("name"), 512)
            listOf("provider", "license").forEach { validateText(strings.getValue(it), 512, allowEmpty = atlas || version == 2L) }
            validateText(strings.getValue("attribution"), 8192, allowEmpty = atlas || version == 2L)
            if (!atlas && version == 1L && (metadataPresent || fileMetadataPresent)) fail("YKLCHART_VERSION_UNSUPPORTED")
            val createdAt = strings.getValue("createdAt")
            validateText(createdAt, 64)
            if (!createdAt.endsWith('Z')) fail("YKLCHART_MANIFEST_INVALID")
            Instant.parse(createdAt)
            val entries = files ?: fail("YKLCHART_MANIFEST_INVALID")
            if (entries.isEmpty() || (atlas && (entries.size !in 1..2 || entries.map {it.format}.distinct().size!=entries.size))) fail("YKLCHART_FILE_COUNT_LIMIT")
            val paths = hashSetOf<String>()
            var total = 0L
            var previousPriority = -1
            for (file in entries) {
                if (!paths.add(file.path.lowercase(Locale.ROOT))) fail("YKLCHART_DUPLICATE_PATH")
                if (file.priority <= previousPriority) fail("YKLCHART_PRIORITY_INVALID")
                previousPriority = file.priority
                total += file.bytes
                if (total > MAX_TOTAL_BYTES) fail("YKLCHART_SIZE_LIMIT")
                if (!atlas && (strings.getValue("kind") == "charts") != (file.format == "mbtiles")) fail("YKLCHART_KIND_MISMATCH")
            }
            // 写盘前拒绝文件与目录路径别名，包括大小写不敏感卷上的冲突。
            for (path in paths) {
                var parent = path.substringBeforeLast('/', "")
                while (parent.isNotEmpty()) {
                    if (parent in paths) fail("YKLCHART_DUPLICATE_PATH")
                    parent = parent.substringBeforeLast('/', "")
                }
            }
            ChartPackageManifest(strings.getValue("format"), requireNotNull(version).toInt(), strings.getValue("id"), strings.getValue("name"), strings.getValue("kind"),
                strings.getValue("createdAt"), strings.getValue("provider"), strings.getValue("license"), strings.getValue("attribution"), entries, metadata)
        }
    } catch (error: ChartPackageException) { throw error }
      catch (error: Exception) { throw ChartPackageException("YKLCHART_MANIFEST_INVALID", error) }

    private fun readFile(reader: JsonReader, atlas:Boolean=false, metadataFound: () -> Unit = {}): ChartPackageFile {
        val strings = mutableMapOf<String, String>()
        var bytes: Long? = null
        var priority: Long? = null
        var metadata: Map<String,String> = emptyMap()
        fields(reader, fileFields, setOf("metadata", "rasterProduct")) { key ->
            when (key) {
                "bytes" -> bytes = number(reader)
                "priority" -> priority = number(reader)
                "metadata" -> { metadataFound(); metadata = readMetadata(reader) }
                "rasterProduct" -> { metadataFound(); strings[key] = string(reader) }
                else -> strings[key] = string(reader)
            }
        }
        val size = bytes ?: fail("YKLCHART_MANIFEST_INVALID")
        if (size !in 1..MAX_FILE_BYTES) fail("YKLCHART_SIZE_LIMIT")
        val order = priority ?: fail("YKLCHART_MANIFEST_INVALID")
        if (order !in 0..Int.MAX_VALUE.toLong()) fail("YKLCHART_PRIORITY_INVALID")
        val path = strings.getValue("path")
        if (!safePath(path)) fail("YKLCHART_PATH_INVALID")
        if (!digestPattern.matches(strings.getValue("sha256"))) fail("YKLCHART_SHA256_INVALID")
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val validFormat = if(atlas)when(strings.getValue("format")) {
            "charts" -> extension == "yklcharts"
            "geodata" -> extension == "yklgeodata"
            else -> false
        } else when (strings.getValue("format")) {
            "gpkg" -> extension == "gpkg"
            "s57" -> extension.matches(Regex("[0-9]{3}")) && !path.substringAfterLast('/').equals("CATALOG.031", true)
            "gebco" -> extension in setOf("tif", "tiff", "asc", "ascii")
            "mbtiles" -> extension == "mbtiles"
            else -> false
        }
        if (!validFormat) fail("YKLCHART_FORMAT_INVALID")
        val rasterProduct = strings["rasterProduct"]
        if (rasterProduct != null && (strings.getValue("format") != "gebco" || !Regex("GEBCO_20[0-9]{2}_Grid").matches(rasterProduct))) fail("YKLCHART_RASTER_PRODUCT_INVALID")
        return ChartPackageFile(path, size, strings.getValue("sha256"), strings.getValue("format"), order.toInt(), metadata, rasterProduct)
    }

    private fun fields(reader: JsonReader, expected: Set<String>, optional: Set<String> = emptySet(), read: (String) -> Unit) {
        val seen = hashSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if ((key !in expected && key !in optional) || !seen.add(key)) fail("YKLCHART_MANIFEST_INVALID")
            read(key)
        }
        reader.endObject()
        if (!seen.containsAll(expected)) fail("YKLCHART_MANIFEST_INVALID")
    }

    private fun string(reader: JsonReader): String {
        if (reader.peek() != JsonToken.STRING) fail("YKLCHART_MANIFEST_INVALID")
        return reader.nextString()
    }

    private fun number(reader: JsonReader): Long {
        if (reader.peek() != JsonToken.NUMBER) fail("YKLCHART_MANIFEST_INVALID")
        val number = reader.nextString()
        if (!integer.matches(number)) fail("YKLCHART_MANIFEST_INVALID")
        return number.toLongOrNull() ?: fail("YKLCHART_MANIFEST_INVALID")
    }

    private fun safePath(path: String): Boolean = path.startsWith("files/") &&
        path.toByteArray(Charsets.UTF_8).size <= 240 &&
        Normalizer.isNormalized(path, Normalizer.Form.NFC) && !hasControlCharacters(path) &&
        path.none { it == '\\' || it == ':' } &&
        path.split('/').all { it.isNotEmpty() && !it.startsWith('.') && it != ".." }

    private fun validateText(text: String, maxBytes: Int, allowEmpty: Boolean = false) {
        if (allowEmpty && text.isEmpty()) return
        if (text.isBlank() || text != text.trim() || text.toByteArray(Charsets.UTF_8).size > maxBytes || hasControlCharacters(text)) {
            fail("YKLCHART_MANIFEST_INVALID")
        }
    }

    /** 元数据不是许可开关；限制与 UI/领域保存一致，避免无界 Binder 或清单负载。 */
    fun validateMetadata(metadata: Map<String,String>): Map<String,String> {
        if (metadata.size > 64) fail("YKLCHART_METADATA_FIELD_LIMIT")
        var bytes = 0
        metadata.forEach { (key,value) ->
            if (key.isBlank() || key != key.trim() || key.length > 80 || hasControlCharacters(key) ||
                value.length > 8192 || value.any { it == '\u0000' || (it.isISOControl() && it !in "\n\r\t") }) fail("YKLCHART_METADATA_INVALID")
            bytes += key.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
            if (bytes > 131072) fail("YKLCHART_METADATA_SIZE_LIMIT")
        }
        return metadata.toMap()
    }

    private fun readMetadata(reader: JsonReader): Map<String,String> {
        val result = linkedMapOf<String,String>()
        reader.beginObject()
        while (reader.hasNext()) {
            if (result.size >= 64) fail("YKLCHART_METADATA_FIELD_LIMIT")
            val key = reader.nextName()
            if (result.put(key,string(reader)) != null) fail("YKLCHART_METADATA_INVALID")
        }
        reader.endObject()
        return validateMetadata(result)
    }

    /** 完整输出原始资料，先计算清单再写真实文件；不会把索引或仅清单伪装为导出包。 */
    fun write(output: OutputStream, id: String, name: String, kind: String, files: List<ChartPackageSource>,
        metadata: Map<String,String> = emptyMap(), provider: String = "", license: String = "", attribution: String = "",
        check: () -> Unit = {}, progress: (Long,Long) -> Unit = { _,_ -> }): ChartPackageManifest =
        writeContainer(output,id,name,kind,files,metadata,provider,license,attribution,check,progress,false)

    internal fun writeAtlas(output:OutputStream,id:String,name:String,files:List<ChartPackageSource>,
        metadata:Map<String,String>,provider:String,license:String,attribution:String,check:()->Unit,
        progress:(Long,Long)->Unit):ChartPackageManifest =
        writeContainer(output,id,name,"atlas",files,metadata,provider,license,attribution,check,progress,true)

    private fun writeContainer(output: OutputStream, id: String, name: String, kind: String, files: List<ChartPackageSource>,
        metadata:Map<String,String>,provider:String,license:String,attribution:String,check:()->Unit,
        progress:(Long,Long)->Unit,atlas:Boolean):ChartPackageManifest {
        if (files.size !in 1..MAX_FILES) fail("YKLCHART_FILE_COUNT_LIMIT")
        val buffer = ByteArray(64 * 1024)
        var scanned = 0L
        val entries = files.sortedBy { it.priority }.map { source ->
            check()
            var bytes = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            source.open().buffered().use { input ->
                while (true) {
                    check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes += count; scanned += count
                    if (bytes > MAX_FILE_BYTES || scanned > MAX_TOTAL_BYTES) fail("YKLCHART_SIZE_LIMIT")
                    digest.update(buffer,0,count)
                    progress(scanned,0)
                }
            }
            ChartPackageFile(source.path,bytes,hex(digest.digest()),source.format,source.priority,validateMetadata(source.metadata),source.rasterProduct)
        }
        val manifest = ChartPackageManifest(if(atlas)YokuliAtlasPackage.FORMAT else FORMAT,if(atlas)1 else VERSION,id,name,kind,Instant.now().toString(),provider,license,attribution,entries,validateMetadata(metadata))
        val encoded = Gson().toJson(manifest).toByteArray(Charsets.UTF_8)
        if (encoded.size > MAX_MANIFEST_BYTES) fail("YKLCHART_MANIFEST_SIZE_LIMIT")
        parseManifest(encoded,atlas) // The writer obeys exactly the same path, identity, format and size contract.
        val sourceByPath = files.associateBy { it.path }
        var written = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            check()
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(encoded);zip.closeEntry()
            for (entry in entries) {
                check()
                zip.putNextEntry(ZipEntry(entry.path))
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                sourceByPath.getValue(entry.path).open().buffered().use { input ->
                    while (true) {
                        check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count; written += count
                        if (bytes > entry.bytes) fail("YKLCHART_SOURCE_CHANGED")
                        digest.update(buffer,0,count);zip.write(buffer,0,count)
                        progress(scanned + written,scanned * 2)
                    }
                }
                if (bytes != entry.bytes || hex(digest.digest()) != entry.sha256) fail("YKLCHART_SOURCE_CHANGED")
                zip.closeEntry()
            }
            check();zip.finish()
        }
        return manifest
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** 与制包工具的 Unicode C 类规则一致；按码点检查，允许合法的辅助平面字符。 */
    private fun hasControlCharacters(text: String): Boolean = text.codePoints().anyMatch { code ->
        Character.getType(code) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt(),
            Character.PRIVATE_USE.toInt(), Character.UNASSIGNED.toInt())
    }

    private inline fun <T> zipCall(block: () -> T): T = try { block() }
        catch (error: IOException) { throw ChartPackageException("YKLCHART_ARCHIVE_INVALID", error) }
        catch (error: IllegalArgumentException) { throw ChartPackageException("YKLCHART_ARCHIVE_INVALID", error) }

    private fun fail(code: String): Nothing = throw ChartPackageException(code)
}
