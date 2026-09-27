package com.yokuli.runtime.contract.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlin.math.ceil

/** 同一 Marine Core 时间轴的锚点。IPC 两端以 Android BOOTTIME 推进，暂停时不推进。 */
data class ClockSnapshot(
    val virtual: Boolean = false,
    val paused: Boolean = false,
    val rate: Double = 1.0,
    val timeEpoch: Long = 0,
    val revision: Long = 0,
    val elapsedMillis: Long = 0,
    val utcMillis: Long = 0,
    val hostElapsedMillis: Long = 0,
)

/**
 * 系统时间与调度的唯一入口。业务超时、采样、老化使用此时间；Android 权限、Binder、
 * 文件 IO 超时仍使用宿主时间。切换后 elapsed 永不倒退，旧数据仍按其原始测量时间老化。
 * 状态流只广播控制变化，不按帧发送；需要周期工作的服务使用 [sleep]，没有轮询线程。
 */
object MarineTime {
    private val guard = Any()
    @Volatile private var elapsedProvider: () -> Long = { System.nanoTime() / 1_000_000L }
    @Volatile private var utcProvider: () -> Long = { System.currentTimeMillis() }
    private var hostInstalled = false
    private val initialHost = elapsedProvider()
    private val mutable = MutableStateFlow(ClockSnapshot(
        elapsedMillis = initialHost, utcMillis = utcProvider(), hostElapsedMillis = initialHost,
    ))
    val state: StateFlow<ClockSnapshot> = mutable.asStateFlow()

    /** Application 在每个进程最早期安装 Android elapsedRealtime，不能使用 uptimeMillis。 */
    fun installHost(elapsed: () -> Long, utc: () -> Long) = synchronized(guard) {
        if (hostInstalled) return@synchronized
        elapsedProvider = elapsed
        utcProvider = utc
        hostInstalled = true
        val host = elapsed()
        mutable.value = ClockSnapshot(elapsedMillis = host, utcMillis = utc(), hostElapsedMillis = host,
            revision = mutable.value.revision + 1)
    }

    fun hostElapsedMillis(): Long = elapsedProvider()
    fun hostUtcMillis(): Long = utcProvider()
    fun snapshot(): ClockSnapshot = synchronized(guard) { project(mutable.value, elapsedProvider()) }
    fun nowElapsedMillis(): Long = synchronized(guard) {
        val anchor = mutable.value
        safeAdd(anchor.elapsedMillis, elapsedDelta(anchor, elapsedProvider()))
    }
    fun nowUtcMillis(): Long = synchronized(guard) {
        val anchor = mutable.value
        if (anchor.virtual) safeAdd(anchor.utcMillis, elapsedDelta(anchor, elapsedProvider())) else utcProvider()
    }

    /** 将真实传感器 capture 时间转换到当前时间轴，保留采样延迟，不能把接收时间冒充采样时间。 */
    fun fromHostElapsedMillis(capturedHost: Long): Long = synchronized(guard) {
        val anchor = mutable.value
        // 暂停后到达的真实新帧没有业务时长；暂停前已采到但延迟送达的帧仍保留年龄。
        val delta = (capturedHost - anchor.hostElapsedMillis).let { if (anchor.paused) minOf(0L, it) else it }
        safeAdd(anchor.elapsedMillis, (delta * anchor.rate).toLong())
            .coerceAtLeast(0L)
    }

    fun enterVirtual(utcMillis: Long = nowUtcMillis(), rate: Double = 1.0, paused: Boolean = false): ClockSnapshot {
        require(utcMillis > 0L) { "Virtual UTC must be positive" }
        validateRate(rate)
        return update { it.copy(virtual = true, utcMillis = utcMillis, rate = rate, paused = paused,
            timeEpoch = it.timeEpoch + 1) }
    }

    fun setPaused(paused: Boolean): ClockSnapshot = update {
        check(it.virtual) { "Pause is only available on the virtual clock" }
        it.copy(paused = paused)
    }

    fun setRate(rate: Double): ClockSnapshot {
        validateRate(rate)
        return update {
            check(it.virtual) { "Rate is only available on the virtual clock" }
            it.copy(rate = rate)
        }
    }

    /** 单步只允许暂停时调用。所有已到期业务任务会被唤醒，不生成不存在的历史样本。 */
    fun step(millis: Long): ClockSnapshot = update {
        check(it.virtual && it.paused) { "Pause the virtual clock before stepping" }
        require(millis in 1..604_800_000L) { "Step must be between 1 ms and seven days" }
        it.copy(elapsedMillis = safeAdd(it.elapsedMillis, millis), utcMillis = safeAdd(it.utcMillis, millis))
    }

    fun returnToReal(): ClockSnapshot = update {
        it.copy(virtual = false, paused = false, rate = 1.0, utcMillis = utcProvider(), timeEpoch = it.timeEpoch + 1)
    }

    /** 仅供私有 Core IPC 镜像；公共应用通过 Core 的系统端口发命令，不能调用此入口。 */
    fun applyRemote(snapshot: ClockSnapshot) = synchronized(guard) {
        validateRate(snapshot.rate)
        require(snapshot.elapsedMillis >= 0L && snapshot.utcMillis > 0L && snapshot.hostElapsedMillis >= 0L)
        require(snapshot.timeEpoch >= 0L && snapshot.revision >= 0L && (snapshot.virtual || !snapshot.paused && snapshot.rate == 1.0))
        mutable.value = snapshot
    }

    suspend fun sleep(millis: Long) {
        require(millis >= 0L)
        sleepUntil(safeAdd(nowElapsedMillis(), millis))
    }

    suspend fun sleepUntil(deadlineElapsed: Long) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val anchor = mutable.value
            val current = synchronized(guard) { project(anchor, elapsedProvider()) }
            val remaining = deadlineElapsed - current.elapsedMillis
            if (remaining <= 0L) return
            if (current.paused) {
                state.first { it != anchor }
            } else {
                val wait = ceil(remaining.toDouble() / current.rate).toLong().coerceIn(1L, Long.MAX_VALUE / 2)
                kotlinx.coroutines.withTimeoutOrNull(wait) { state.first { it != anchor } }
            }
        }
    }

    /** 业务等待窗口随虚拟时间推进；硬件授权 / Binder / 文件 IO 仍用 coroutines 的宿主超时。 */
    suspend fun <T> withTimeoutOrNull(millis: Long, block: suspend () -> T): T? {
        if (millis <= 0L) return null
        return coroutineScope {
            val operation = async { block() }
            val deadline = async { sleep(millis) }
            try {
                select<T?> {
                    operation.onAwait { it }
                    deadline.onAwait { null }
                }
            } finally {
                deadline.cancel()
                operation.cancel()
            }
        }
    }

    private fun update(change: (ClockSnapshot) -> ClockSnapshot): ClockSnapshot = synchronized(guard) {
        val current = project(mutable.value, elapsedProvider())
        change(current).copy(revision = current.revision + 1).also { mutable.value = it }
    }

    private fun project(anchor: ClockSnapshot, host: Long): ClockSnapshot {
        val delta = elapsedDelta(anchor, host)
        return anchor.copy(elapsedMillis = safeAdd(anchor.elapsedMillis, delta),
            utcMillis = if (anchor.virtual) safeAdd(anchor.utcMillis, delta) else utcProvider(), hostElapsedMillis = host)
    }

    private fun elapsedDelta(anchor: ClockSnapshot, host: Long): Long =
        if (anchor.paused) 0L else ((host - anchor.hostElapsedMillis).coerceAtLeast(0L) * anchor.rate).toLong()

    private fun validateRate(rate: Double) = require(rate.isFinite() && rate in 0.01..600.0) {
        "Clock rate must be between 0.01 and 600"
    }

    private fun safeAdd(value: Long, delta: Long): Long = when {
        delta > 0 && value > Long.MAX_VALUE - delta -> Long.MAX_VALUE
        delta < 0 && value < Long.MIN_VALUE - delta -> Long.MIN_VALUE
        else -> value + delta
    }
}
