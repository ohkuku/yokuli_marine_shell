package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.anchorwatch.data.database.TrackPointEntity
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlin.math.*

/**
 * 本次下锚的真实占用区域。分页读取数据库全部历史后增量加入，不受界面近期轨迹长度限制。
 * 每个色斑只代表实际观测到的位置；不连补 GPS 中断，不用凸包填充从未经过的海域。
 * 颜色浓度表示停留样本数量；最多 800 个格子，长时间漂移时合并网格而不丢掉旧区域。
 */
class AnchorSwingCoverage(private val origin: GeoPoint, radiusMeters: Double) {
    private data class Cell(var count: Int, var latest: Long)
    // 占用色斑来自观测网格，和用户设定的警戒半径无关；不会把单个位置扩成大圆。
    private var cellMeters = 3.0
    private val cells = linkedMapOf<Pair<Int, Int>, Cell>()
    var lastTimestamp = Long.MIN_VALUE
        private set
    var lastId = 0L
        private set
    var sampleCount = 0L
        private set

    @Synchronized
    fun add(samples: List<TrackPointEntity>) {
        samples.forEach { sample ->
            if (sample.timestamp < lastTimestamp || sample.timestamp == lastTimestamp && sample.id <= lastId) return@forEach
            lastTimestamp = sample.timestamp; lastId = sample.id
            val point = GeoPoint(sample.latitude, sample.longitude)
            if (!point.valid() || sample.wasQuarantined || sample.fixTrust in listOf("REJECTED", "QUARANTINED")) return@forEach
            val east = Math.toRadians(((point.lon - origin.lon + 540) % 360) - 180) * 6371008.8 * cos(Math.toRadians(origin.lat))
            val north = Math.toRadians(point.lat - origin.lat) * 6371008.8
            val key = floor(east / cellMeters).toInt() to floor(north / cellMeters).toInt()
            val cell = cells.getOrPut(key) { Cell(0, sample.timestamp) }
            cell.count++; cell.latest = sample.timestamp; sampleCount++
            if (cells.size > 800) merge()
        }
    }

    private fun merge() {
        val previous = cells.toMap(); cells.clear(); cellMeters *= 2
        previous.forEach { (key, cell) ->
            val merged = floor(key.first / 2.0).toInt() to floor(key.second / 2.0).toInt()
            val target = cells.getOrPut(merged) { Cell(0, cell.latest) }
            target.count += cell.count; target.latest = maxOf(target.latest, cell.latest)
        }
    }

    @Synchronized
    fun areas(): List<MapArea> = cells.map { (key, cell) ->
        val east = (key.first + .5) * cellMeters; val north = (key.second + .5) * cellMeters
        val center = destination(origin, hypot(east, north), Math.toDegrees(atan2(east, north)))
        val intensity = (ln(cell.count + 1.0) / ln(160.0)).coerceIn(0.0, 1.0)
        // 蓝色薄层到青绿色浓层；不会被误读为水深。
        val alpha = (28 + intensity * 70).toLong()
        val color = (alpha shl 24) or if (intensity > .55) 0x00109C8A else 0x002A80AC
        MapArea("swing-area:${cellMeters}:${key.first}:${key.second}",
            (0..12).map { destination(center, cellMeters * .76, it * 30.0) }, color)
    }
}
