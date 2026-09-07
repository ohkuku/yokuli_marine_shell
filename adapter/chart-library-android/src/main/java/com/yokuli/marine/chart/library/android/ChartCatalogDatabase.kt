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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "chart_catalog_metadata")
internal data class ChartCatalogMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val revision: Long,
    val lastTransactionId: String?,
    val activeViewId: String?,
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
    val rasterMimeType: String?,
    val attribution: String?,
    val attributionProvenance: String,
    val role: String,
    val priority: Int,
    val enabled: Boolean,
    val accessState: String,
    val validationState: String,
    val accessMode: String?,
    val compatibilityWarnings: String,
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

@Entity(
    tableName = "chart_managed_copy_relations",
    primaryKeys = ["originalAssetId", "managedAssetId"],
    foreignKeys = [
        ForeignKey(
            entity = ChartAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["originalAssetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChartAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["managedAssetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("originalAssetId"), Index("managedAssetId")],
)
internal data class ChartManagedCopyRelationEntity(
    val originalAssetId: String,
    val managedAssetId: String,
)

@Entity(tableName = "chart_layers")
internal data class ChartLayerEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val visible: Boolean,
    val opacity: Float,
    val stackOrder: Int,
    val role: String,
)

@Entity(
    tableName = "chart_layer_sources",
    primaryKeys = ["layerId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = ChartLayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["layerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChartSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("layerId"), Index("sourceId")],
)
internal data class ChartLayerSourceEntity(val layerId: String, val sourceId: String)

@Entity(tableName = "chart_views", indices = [Index(value = ["displayName"])])
internal data class ChartViewEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val baseStyle: String,
)

@Entity(
    tableName = "chart_view_layers",
    primaryKeys = ["viewId", "layerId"],
    foreignKeys = [
        ForeignKey(
            entity = ChartViewEntity::class,
            parentColumns = ["id"],
            childColumns = ["viewId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChartLayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["layerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("viewId"), Index("layerId")],
)
internal data class ChartViewLayerEntity(
    val viewId: String,
    val layerId: String,
    val visible: Boolean,
    val opacity: Float,
    val stackOrder: Int,
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

    @Query("SELECT COUNT(*) FROM chart_layers")
    suspend fun layerCount(): Int

    @Query("SELECT COUNT(*) FROM chart_views")
    suspend fun viewCount(): Int

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

    @Query("SELECT * FROM chart_layers ORDER BY stackOrder, displayName COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun layers(limit: Int, offset: Int): List<ChartLayerEntity>

    @Query("SELECT * FROM chart_layers WHERE id = :id")
    suspend fun layer(id: String): ChartLayerEntity?

    @Query("SELECT id FROM chart_layers ORDER BY stackOrder, id")
    suspend fun allLayerIds(): List<String>

    @Query("SELECT layerId FROM chart_layer_sources WHERE sourceId = :sourceId ORDER BY layerId")
    suspend fun layerIdsForSource(sourceId: String): List<String>

    @Query("SELECT sourceId FROM chart_layer_sources WHERE layerId = :layerId ORDER BY sourceId")
    suspend fun sourceIdsForLayer(layerId: String): List<String>

    @Upsert
    suspend fun putLayer(value: ChartLayerEntity)

    @Query("DELETE FROM chart_layers WHERE id = :id")
    suspend fun deleteLayer(id: String)

    @Query("DELETE FROM chart_layers WHERE id NOT IN (SELECT DISTINCT layerId FROM chart_layer_sources)")
    suspend fun deleteOrphanLayers()

    @Query("DELETE FROM chart_layer_sources WHERE layerId = :layerId")
    suspend fun deleteLayerSources(layerId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putLayerSources(values: List<ChartLayerSourceEntity>)

    @Query("SELECT * FROM chart_views ORDER BY displayName COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun views(limit: Int, offset: Int): List<ChartViewEntity>

    @Query("SELECT * FROM chart_views WHERE id = :id")
    suspend fun view(id: String): ChartViewEntity?

    @Query("SELECT id FROM chart_views ORDER BY displayName COLLATE NOCASE, id")
    suspend fun allViewIds(): List<String>

    @Upsert
    suspend fun putView(value: ChartViewEntity)

    @Query("DELETE FROM chart_views WHERE id = :id")
    suspend fun deleteView(id: String)

    @Query("SELECT * FROM chart_view_layers WHERE viewId = :viewId ORDER BY stackOrder, layerId")
    suspend fun viewLayers(viewId: String): List<ChartViewLayerEntity>

    @Query("DELETE FROM chart_view_layers WHERE viewId = :viewId")
    suspend fun deleteViewLayers(viewId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putViewLayers(values: List<ChartViewLayerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLegacyMapping(value: LegacyChartMappingEntity)

    @Query("SELECT assetId FROM legacy_chart_mappings WHERE legacyLogicalId = :logicalId AND legacyVersionKey = :versionKey")
    suspend fun legacyAssetId(logicalId: String, versionKey: String): String?

    @Upsert
    suspend fun putManagedCopyRelation(value: ChartManagedCopyRelationEntity)

    @Query("SELECT managedAssetId FROM chart_managed_copy_relations WHERE originalAssetId = :assetId ORDER BY managedAssetId DESC LIMIT 1")
    suspend fun managedCopyFor(assetId: String): String?

    @Query("SELECT originalAssetId FROM chart_managed_copy_relations WHERE managedAssetId = :assetId ORDER BY originalAssetId LIMIT 1")
    suspend fun originalForManagedCopy(assetId: String): String?

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
        ChartManagedCopyRelationEntity::class,
        ChartLayerEntity::class,
        ChartLayerSourceEntity::class,
        ChartViewEntity::class,
        ChartViewLayerEntity::class,
        ChartCatalogTransactionEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
internal abstract class ChartCatalogDatabase : RoomDatabase() {
    abstract fun dao(): ChartCatalogDao
}

internal val CHART_CATALOG_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chart_assets ADD COLUMN rasterMimeType TEXT")
    }
}

internal val CHART_CATALOG_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `chart_managed_copy_relations` (
                `originalAssetId` TEXT NOT NULL,
                `managedAssetId` TEXT NOT NULL,
                PRIMARY KEY(`originalAssetId`, `managedAssetId`),
                FOREIGN KEY(`originalAssetId`) REFERENCES `chart_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`managedAssetId`) REFERENCES `chart_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_managed_copy_relations_originalAssetId` ON `chart_managed_copy_relations` (`originalAssetId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_managed_copy_relations_managedAssetId` ON `chart_managed_copy_relations` (`managedAssetId`)")
    }
}

internal val CHART_CATALOG_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chart_assets ADD COLUMN accessMode TEXT")
        database.execSQL("ALTER TABLE chart_assets ADD COLUMN compatibilityWarnings TEXT NOT NULL DEFAULT ''")
    }
}

internal val CHART_CATALOG_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chart_catalog_metadata ADD COLUMN activeViewId TEXT")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `chart_layers` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `visible` INTEGER NOT NULL,
                `opacity` REAL NOT NULL,
                `stackOrder` INTEGER NOT NULL,
                `role` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `chart_layer_sources` (
                `layerId` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`layerId`, `sourceId`),
                FOREIGN KEY(`layerId`) REFERENCES `chart_layers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`sourceId`) REFERENCES `chart_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_layer_sources_layerId` ON `chart_layer_sources` (`layerId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_layer_sources_sourceId` ON `chart_layer_sources` (`sourceId`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `chart_views` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `baseStyle` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_views_displayName` ON `chart_views` (`displayName`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `chart_view_layers` (
                `viewId` TEXT NOT NULL,
                `layerId` TEXT NOT NULL,
                `visible` INTEGER NOT NULL,
                `opacity` REAL NOT NULL,
                `stackOrder` INTEGER NOT NULL,
                PRIMARY KEY(`viewId`, `layerId`),
                FOREIGN KEY(`viewId`) REFERENCES `chart_views`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`layerId`) REFERENCES `chart_layers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_view_layers_viewId` ON `chart_view_layers` (`viewId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chart_view_layers_layerId` ON `chart_view_layers` (`layerId`)")
        database.execSQL(
            """INSERT OR IGNORE INTO chart_layers(id,displayName,visible,opacity,stackOrder,role)
               SELECT 'source-' || id, displayName, enabled, 1.0, 0, defaultRole
               FROM chart_sources WHERE kind != 'MANAGED'""".trimIndent(),
        )
        database.execSQL(
            """INSERT OR IGNORE INTO chart_layer_sources(layerId,sourceId)
               SELECT 'source-' || id, id FROM chart_sources WHERE kind != 'MANAGED'""".trimIndent(),
        )
        database.execSQL(
            """INSERT OR IGNORE INTO chart_views(id,displayName,baseStyle)
               SELECT 'default-view-v1','Sailing','SATELLITE'
               WHERE EXISTS(SELECT 1 FROM chart_layers)""".trimIndent(),
        )
        database.execSQL(
            """INSERT OR IGNORE INTO chart_view_layers(viewId,layerId,visible,opacity,stackOrder)
               SELECT 'default-view-v1',id,visible,opacity,stackOrder FROM chart_layers""".trimIndent(),
        )
        database.execSQL(
            "UPDATE chart_catalog_metadata SET activeViewId='default-view-v1' WHERE EXISTS(SELECT 1 FROM chart_views)",
        )
    }
}
