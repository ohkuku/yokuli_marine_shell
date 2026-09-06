package com.yokuli.marine.chart.library.android

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "chart_catalog_metadata")
internal data class ChartCatalogMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val revision: Long,
    val lastTransactionId: String?,
)

@Entity(tableName = "chart_sources")
internal data class ChartSourceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val locator: String,
    val displayName: String,
    val enabled: Boolean,
    val recursive: Boolean,
    val defaultRole: String,
    val grantState: String,
    val scanGeneration: Long,
    val scanStatus: String,
    val lastSuccessfulGeneration: Long?,
    val discoveredCount: Long,
    val issueCount: Int,
)

@Entity(
    tableName = "chart_assets",
    indices = [Index(value = ["authority", "documentId"], unique = true), Index("displayPath")],
)
internal data class ChartAssetEntity(
    @PrimaryKey val id: String,
    val authority: String,
    val documentId: String,
    val locator: String,
    val displayPath: String,
    val revisionIdentity: String,
    val revisionSizeBytes: Long?,
    val revisionModifiedAtMillis: Long?,
    val revisionProviderHint: String?,
    val revisionSha256: String?,
    val format: String,
    val sizeBytes: Long?,
    val west: Double?,
    val south: Double?,
    val east: Double?,
    val north: Double?,
    val minZoom: Int?,
    val maxZoom: Int?,
    val tileCount: Long?,
    val tileSize: Int?,
    val tileScheme: String?,
    val attribution: String?,
    val attributionProvenance: String,
    val role: String,
    val priority: Int,
    val enabled: Boolean,
    val accessState: String,
    val validationState: String,
)

@Entity(
    tableName = "chart_memberships",
    primaryKeys = ["assetId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = ChartAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChartSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assetId"), Index("sourceId")],
)
internal data class ChartMembershipEntity(val assetId: String, val sourceId: String)

@Entity(
    tableName = "legacy_chart_mappings",
    primaryKeys = ["legacyLogicalId", "legacyVersionKey"],
    foreignKeys = [
        ForeignKey(
            entity = ChartAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assetId")],
)
internal data class LegacyChartMappingEntity(
    val legacyLogicalId: String,
    val legacyVersionKey: String,
    val assetId: String,
)

@Entity(tableName = "chart_catalog_transactions")
internal data class ChartCatalogTransactionEntity(
    @PrimaryKey val transactionId: String,
    val revision: Long,
)

@Dao
internal interface ChartCatalogDao {
    @Query("SELECT * FROM chart_catalog_metadata WHERE id = 0")
    suspend fun metadata(): ChartCatalogMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(value: ChartCatalogMetadataEntity)

    @Query("SELECT COUNT(*) FROM chart_sources")
    suspend fun sourceCount(): Int

    @Query("SELECT COUNT(*) FROM chart_assets")
    suspend fun assetCount(): Int

    @Query("SELECT COALESCE(SUM(issueCount), 0) FROM chart_sources")
    suspend fun issueCount(): Int

    @Query("SELECT * FROM chart_sources ORDER BY displayName COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun sources(limit: Int, offset: Int): List<ChartSourceEntity>

    @Query("SELECT * FROM chart_sources WHERE id = :id")
    suspend fun source(id: String): ChartSourceEntity?

    @Upsert
    suspend fun putSource(value: ChartSourceEntity)

    @Query("DELETE FROM chart_sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    @Query("SELECT * FROM chart_assets WHERE id = :id")
    suspend fun asset(id: String): ChartAssetEntity?

    @Query("SELECT * FROM chart_assets WHERE authority = :authority AND documentId = :documentId LIMIT 1")
    suspend fun assetByIdentity(authority: String, documentId: String): ChartAssetEntity?

    @Query(
        """SELECT DISTINCT a.* FROM chart_assets a
           LEFT JOIN chart_memberships m ON m.assetId = a.id
           WHERE (:sourceId IS NULL OR m.sourceId = :sourceId)
             AND (:enabledOnly = 0 OR a.enabled = 1)
             AND a.displayPath LIKE :text ESCAPE '\'
             AND (:filterAccess = 0 OR a.accessState IN (:accessStates))
             AND (:filterValidation = 0 OR a.validationState IN (:validationStates))
           ORDER BY a.priority DESC, a.displayPath COLLATE NOCASE, a.id
           LIMIT :limit OFFSET :offset""",
    )
    suspend fun assets(
        sourceId: String?,
        text: String,
        enabledOnly: Boolean,
        filterAccess: Boolean,
        accessStates: List<String>,
        filterValidation: Boolean,
        validationStates: List<String>,
        limit: Int,
        offset: Int,
    ): List<ChartAssetEntity>

    @Query(
        """SELECT COUNT(DISTINCT a.id) FROM chart_assets a
           LEFT JOIN chart_memberships m ON m.assetId = a.id
           WHERE (:sourceId IS NULL OR m.sourceId = :sourceId)
             AND (:enabledOnly = 0 OR a.enabled = 1)
             AND a.displayPath LIKE :text ESCAPE '\'
             AND (:filterAccess = 0 OR a.accessState IN (:accessStates))
             AND (:filterValidation = 0 OR a.validationState IN (:validationStates))""",
    )
    suspend fun filteredAssetCount(
        sourceId: String?,
        text: String,
        enabledOnly: Boolean,
        filterAccess: Boolean,
        accessStates: List<String>,
        filterValidation: Boolean,
        validationStates: List<String>,
    ): Int

    @Upsert
    suspend fun putAsset(value: ChartAssetEntity)

    @Query("DELETE FROM chart_assets WHERE id = :id")
    suspend fun deleteAsset(id: String)

    @Query("SELECT sourceId FROM chart_memberships WHERE assetId = :assetId ORDER BY sourceId")
    suspend fun memberships(assetId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putMemberships(values: List<ChartMembershipEntity>)

    @Query("DELETE FROM chart_memberships WHERE assetId = :assetId AND sourceId = :sourceId")
    suspend fun deleteMembership(assetId: String, sourceId: String)

    @Query("DELETE FROM chart_assets WHERE id NOT IN (SELECT DISTINCT assetId FROM chart_memberships)")
    suspend fun deleteOrphanAssets()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLegacyMapping(value: LegacyChartMappingEntity)

    @Query("SELECT assetId FROM legacy_chart_mappings WHERE legacyLogicalId = :logicalId AND legacyVersionKey = :versionKey")
    suspend fun legacyAssetId(logicalId: String, versionKey: String): String?

    @Query("SELECT revision FROM chart_catalog_transactions WHERE transactionId = :transactionId")
    suspend fun appliedTransactionRevision(transactionId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordTransaction(value: ChartCatalogTransactionEntity)

    @Query(
        """DELETE FROM chart_catalog_transactions
           WHERE transactionId NOT IN (
             SELECT transactionId FROM chart_catalog_transactions ORDER BY revision DESC LIMIT :keep
           )""",
    )
    suspend fun pruneTransactions(keep: Int)
}

@Database(
    entities = [
        ChartCatalogMetadataEntity::class,
        ChartSourceEntity::class,
        ChartAssetEntity::class,
        ChartMembershipEntity::class,
        LegacyChartMappingEntity::class,
        ChartCatalogTransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class ChartCatalogDatabase : RoomDatabase() {
    abstract fun dao(): ChartCatalogDao
}
