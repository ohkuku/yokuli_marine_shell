package com.yokuli.anchorwatch.runtime

import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File

/**
 * Marine Core 单写文件：先 fsync 和原子替换，再向订阅者公布状态。
 * 损坏/读写失败必须阻断相关命令，不能用空账本覆盖旧文件后再次执行安全操作。
 * 调用方持有自身串行锁；Shell 进程只通过 IPC 读取投影。
 */
internal class DurableRuntimeFile<T>(directory: File, name: String, private val type: Class<T>) {
    private val file = AtomicFile(File(directory, name))
    private val gson = Gson()

    fun read(empty: () -> T): T {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return empty()
        require(file.baseFile.length() <= 8L * 1024 * 1024) { "RUNTIME_DOCUMENT_TOO_LARGE" }
        return file.openRead().bufferedReader().use { gson.fromJson(it, type) }
            ?: error("RUNTIME_DOCUMENT_EMPTY")
    }

    fun write(value: T) {
        val directory = requireNotNull(file.baseFile.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "RUNTIME_DIRECTORY_UNAVAILABLE" }
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 8 * 1024 * 1024) { "RUNTIME_DOCUMENT_TOO_LARGE" }
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
        check(file.openRead().use { it.readBytes() }.contentEquals(bytes)) { "RUNTIME_WRITE_NOT_CONFIRMED" }
    }
}
