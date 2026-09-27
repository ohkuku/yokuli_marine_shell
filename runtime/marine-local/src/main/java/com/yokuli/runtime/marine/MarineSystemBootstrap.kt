package com.yokuli.runtime.marine

import android.app.Application
import android.content.Context
import com.yokuli.anchorwatch.LegacyMarineRuntime

/**
 * 宿主只知道运行时入口，不直接初始化旧后端。这里只安装一次进程级故障记录器，
 * 只允许默认 Core 进程初始化。UI 在 :shell 通过 Binder 接入，不构造本地所有者。
 */
object MarineSystemBootstrap {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        check(com.yokuli.runtime.marine.ipc.MarineCoreProcess.isCore(context)) { "Only Marine Core may initialize local runtime" }
        if (initialized) return
        val application = (context.applicationContext as? Application) ?: (context as? Application)
            ?: error("Marine runtime bootstrap requires an application context")
        LegacyMarineRuntime.initialize(application)
        initialized = true
    }
}
