package com.yokuli.anchorwatch.api

import android.net.Uri
import com.yokuli.anchorwatch.data.anchorage.AnchoragePlaceBundle
import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import com.yokuli.anchorwatch.data.database.TrackPointEntity
import com.yokuli.anchorwatch.data.database.entity.AnchorageCollectionEntity
import com.yokuli.anchorwatch.data.database.entity.AnchorageCollectionPlaceCrossRef
import com.yokuli.anchorwatch.data.database.entity.AnchoragePhotoEntity
import com.yokuli.anchorwatch.data.database.entity.AnchoragePlaceEntity
import com.yokuli.anchorwatch.data.database.entity.AnchorageSpotEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

/** 同一次 Room 事务读取的用户地点目录；沿用原记录 ID，不形成第二份持久化数据。 */
data class MarineLibrarySnapshot(
    val places: List<AnchoragePlaceEntity>,
    val spots: List<AnchorageSpotEntity>,
    val collections: List<AnchorageCollectionEntity>,
    val archivedPlaces: List<AnchoragePlaceEntity>,
    val memberships: List<AnchorageCollectionPlaceCrossRef>,
)

/** 照片导入仍由已有媒体仓库验证格式、尺寸和文件归属；调用者拿不到数据库或仓库。 */
interface MarinePhotoService {
    suspend fun import(placeId: Long, source: Uri, caption: String = ""): AnchoragePhotoEntity
    suspend fun delete(value: AnchoragePhotoEntity)
    fun file(value: AnchoragePhotoEntity, thumbnail: Boolean = false): File
}

/**
 * 用户内容与历史查询端口。UI 只保留显示投影，记录的增改、关系及事务边界归这里管理。
 * R0 继续复用现有实体作为数据载体；接口不导出 Room、DAO 或 repository 实例。
 * 警报事件是只读历史，删除通知不会通过此接口确认或清除实际警报。
 */
interface MarineContentService {
    val library: Flow<MarineLibrarySnapshot>
    val photos: MarinePhotoService
    fun observeRecentAlarmEvents(limit: Int = 200): Flow<List<AlarmEventEntity>>
    fun observeCollectionMembers(collectionId: Long): Flow<List<Long>>
    /** 按 timestamp、id 联合游标读取；相同时间的多个点不会因翻页遗漏。 */
    suspend fun anchorTrackPage(sessionId: Long, afterTimestamp: Long, afterId: Long, limit: Int = 1000): List<TrackPointEntity>
    suspend fun bundle(placeId: Long): AnchoragePlaceBundle?
    suspend fun updatePlace(value: AnchoragePlaceEntity): Long
    suspend fun updateSpot(value: AnchorageSpotEntity): Long
    suspend fun createSpot(placeId: Long, name: String, latitude: Double, longitude: Double): Long
    suspend fun archivePlace(placeId: Long)
    suspend fun restorePlace(placeId: Long)
    suspend fun createCollection(name: String): Long
    suspend fun toggleCollection(collectionId: Long, placeId: Long)
    /** 已收藏的 session 返回原 placeId；默认名称由 UI 按当前语言提供。 */
    suspend fun saveAnchorage(sessionId: Long, name: String, defaultSpotName: String): Long
}
