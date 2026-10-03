package com.yokuli.marine.shell.rebuild.scene

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View

/** Shell 的图形资源租约，不保存定位、航线或 AIS 领域状态。所有方法在 UI / Filament 线程调用。 */
internal interface MaritimeSceneResource {
    val hostView: View
    val reusable: Boolean
    /** 暂停出帧并解除页面回调；TextureView 的 surface 随页面脱离，GPU 模型短期保留。 */
    fun park()
    fun close()
}

/**
 * 海图和交通各至多保留一个热宿主。短时间返回不重建 Engine / 网格；90 秒未使用或
 * 内存吃紧即释放。宿主只能使用 applicationContext，不保留已退出的 Activity / Compose。
 */
internal object MaritimeSceneResources {
    private val main = Handler(Looper.getMainLooper())
    private data class Entry(val resource: MaritimeSceneResource, var leased: Boolean, var expiry: Runnable? = null)
    private val entries = linkedMapOf<String, Entry>()
    private var registered = false

    class Lease<T : MaritimeSceneResource> internal constructor(
        val resource: T, private val releaseResource: () -> Unit,
    ) {
        private var released = false
        fun release() { if (!released) { released = true; releaseResource() } }
    }

    fun <T : MaritimeSceneResource> acquire(context: Context, key: String, create: (Context) -> T): Lease<T> {
        check(Looper.myLooper() == Looper.getMainLooper())
        require(key == "navigation" || key == "traffic")
        if (!registered) {
            registered = true
            context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit
                override fun onLowMemory() { main.post { discardIdle() } }
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) main.post { discardIdle() }
                }
            })
        }
        val old = entries[key]
        if (old != null && !old.leased) {
            old.expiry?.let(main::removeCallbacks)
            if (old.resource.reusable && old.resource.hostView.parent == null) {
                old.leased = true; old.expiry = null
                @Suppress("UNCHECKED_CAST")
                return Lease(old.resource as T) { release(key, old) }
            }
            entries.remove(key); old.resource.close()
        }
        val resource = create(context.applicationContext)
        // 进入/退出动画可短暂存在两个页面；临时宿主退出即销毁，不抢仍在使用的租约。
        if (old?.leased == true) return Lease(resource) { resource.park(); resource.close() }
        val entry = Entry(resource, true)
        entries[key] = entry
        return Lease(resource) { release(key, entry) }
    }

    private fun release(key: String, entry: Entry) {
        check(Looper.myLooper() == Looper.getMainLooper())
        entry.resource.park()
        entry.leased = false
        if (entries[key] !== entry || !entry.resource.reusable) {
            if (entries[key] === entry) entries.remove(key)
            entry.resource.close(); return
        }
        val expiry = Runnable {
            if (entries[key] === entry && !entry.leased) {
                entries.remove(key); entry.resource.close()
            }
        }
        entry.expiry = expiry
        main.postDelayed(expiry, 90_000L)
    }

    private fun discardIdle() {
        val idle = entries.filterValues { !it.leased }.toMap()
        for ((key, entry) in idle) {
            entries.remove(key); entry.expiry?.let(main::removeCallbacks); entry.resource.close()
        }
    }
}
