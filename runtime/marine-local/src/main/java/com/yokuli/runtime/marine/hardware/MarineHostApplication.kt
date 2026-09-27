package com.yokuli.runtime.marine.hardware

import android.app.Application
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.yokuli.runtime.marine.ipc.MarineCoreProcess
import com.yokuli.runtime.marine.notification.NotificationProcessRole
import java.io.File

/**
 * 宿主存储适配器在唯一所有者进程隔离实验环境。Shell 仍保存同一份桌面与访问栈，
 * 不能通过它取得数据库对象。存储映射仅在所有者进程启动前决定，运行中绝不切换。
 */
open class MarineHostApplication : Application() {
    protected fun isMarineStorageOwner() = MarineCoreProcess.isCore(this) || NotificationProcessRole.isNotificationProcess()
    override fun getFilesDir(): File = super.getFilesDir().let { if (isMarineStorageOwner()) HardwareLabBoot.files(it) else it }
    override fun getNoBackupFilesDir(): File = super.getNoBackupFilesDir().let { if (isMarineStorageOwner()) HardwareLabBoot.files(it) else it }
    override fun getDatabasePath(name: String): File = super.getDatabasePath(name).let { if (isMarineStorageOwner()) HardwareLabBoot.database(it) else it }
    // SQLiteOpenHelper 使用 openOrCreateDatabase；仅改 getDatabasePath 不能拦住 ContextWrapper 的真实写入。
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        super.openOrCreateDatabase(if (isMarineStorageOwner()) getDatabasePath(name).absolutePath else name, mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        super.openOrCreateDatabase(if (isMarineStorageOwner()) getDatabasePath(name).absolutePath else name, mode, factory, errorHandler)
    override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(if (isMarineStorageOwner()) getDatabasePath(name).absolutePath else name)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(if (isMarineStorageOwner()) HardwareLabBoot.preferenceName(name) else name, mode)
}
