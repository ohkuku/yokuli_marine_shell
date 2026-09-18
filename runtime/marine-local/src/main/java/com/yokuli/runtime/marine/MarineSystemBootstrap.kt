package com.yokuli.runtime.marine

import android.app.Application
import android.content.Context
import com.yokuli.anchorwatch.LegacyMarineRuntime

/**
 * 宿主只知道运行时入口，不直接初始化旧后端。这里只安装一次进程级故障记录器，
 * 不启动定位、连接、航行、值守或发布，也不表示已有独立进程/IPC。
 */
object MarineSystemBootstrap {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val application = (context.applicationContext as? Application) ?: (context as? Application)
            ?: error("Marine runtime bootstrap requires an application context")
        LegacyMarineRuntime.initialize(application)
        initialized = true
    }
}
