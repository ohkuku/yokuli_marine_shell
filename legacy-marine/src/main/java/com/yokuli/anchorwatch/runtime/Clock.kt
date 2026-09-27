package com.yokuli.anchorwatch.runtime

import com.yokuli.runtime.contract.time.MarineTime


interface MonotonicClock { fun elapsedRealtime():Long }
interface WallClock { fun currentTimeMillis():Long }

/** 兼容已有注入接口，真实与虚拟运行时都使用同一个 MarineTime 时间轴。
 * Android HAL 原始 BOOTTIME 由 fromHostElapsedMillis 转换；不得混入 uptime/nanoTime。
 */
object SystemMonotonicClock:MonotonicClock { override fun elapsedRealtime()=MarineTime.nowElapsedMillis() }
object SystemWallClock:WallClock { override fun currentTimeMillis()=MarineTime.nowUtcMillis() }
