package com.yokuli.anchorwatch.api

/**
 * 同一显示能力的进程级持有者计数。只有 0→1 和 1→0 才更新后台需求；
 * 句柄可在任意线程重复关闭。后台申请失败时不产生一个未生效的租约。
 */
internal class DisplayLeaseRegistry(private val onActiveChanged: (Boolean) -> Unit) {
    private val guard = Any()
    private var holders = 0

    fun acquire(): DisplayLease = synchronized(guard) {
        if (holders == 0) onActiveChanged(true)
        holders++
        object : DisplayLease {
            private var closed = false

            override fun close() = synchronized(guard) {
                if (closed) return@synchronized
                if (holders == 1) onActiveChanged(false)
                holders--
                closed = true
            }
        }
    }
}
