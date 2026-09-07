package com.yokuli.marine.chart.library.android

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartCatalogMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChartCatalogDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun catalogV1MigratesToV2WithoutDestructiveReset() {
        helper.createDatabase(DB_NAME, 1).close()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, CHART_CATALOG_MIGRATION_1_2).close()
    }

    @Test fun catalogV2MigratesToV3WithManagedCopyRelations() {
        helper.createDatabase("$DB_NAME-v2", 2).close()
        helper.runMigrationsAndValidate("$DB_NAME-v2", 3, true, CHART_CATALOG_MIGRATION_2_3).close()
    }

    @Test fun catalogV1MigratesThroughV3WithoutDestructiveReset() {
        helper.createDatabase("$DB_NAME-v3", 1).close()
        helper.runMigrationsAndValidate(
            "$DB_NAME-v3", 3, true, CHART_CATALOG_MIGRATION_1_2, CHART_CATALOG_MIGRATION_2_3,
        ).close()
    }

    @Test fun catalogV3MigratesToV4WithIndependentAccessModeAndWarnings() {
        helper.createDatabase("$DB_NAME-v4", 3).close()
        helper.runMigrationsAndValidate("$DB_NAME-v4", 4, true, CHART_CATALOG_MIGRATION_3_4).close()
    }

    @Test fun catalogV1MigratesThroughV4WithoutDestructiveReset() {
        helper.createDatabase("$DB_NAME-v1-v4", 1).close()
        helper.runMigrationsAndValidate(
            "$DB_NAME-v1-v4",
            4,
            true,
            CHART_CATALOG_MIGRATION_1_2,
            CHART_CATALOG_MIGRATION_2_3,
            CHART_CATALOG_MIGRATION_3_4,
        ).close()
    }

    @Test fun catalogV4MigratesExistingSourceIntoDefaultLayerAndActiveView() {
        val name = "$DB_NAME-v5-content"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO chart_catalog_metadata(id,revision,lastTransactionId) VALUES (0,7,'legacy')")
            execSQL(
                """INSERT INTO chart_sources(
                    id,kind,locator,displayName,enabled,recursive,defaultRole,grantState,
                    scanGeneration,scanStatus,lastSuccessfulGeneration,discoveredCount,issueCount
                ) VALUES (
                    '11111111-1111-1111-1111-111111111111','TREE','content://provider/nz','NZ Hydro',
                    1,1,'BASE','GRANTED',3,'COMPLETE',3,2,0
                )""".trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 5, true, CHART_CATALOG_MIGRATION_4_5).use { database ->
            database.query("SELECT displayName FROM chart_layers").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("NZ Hydro", cursor.getString(0))
            }
            database.query("SELECT displayName,baseStyle FROM chart_views").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Sailing", cursor.getString(0))
                assertEquals("SATELLITE", cursor.getString(1))
            }
            database.query("SELECT activeViewId FROM chart_catalog_metadata WHERE id=0").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("default-view-v1", cursor.getString(0))
            }
        }
    }

    @Test fun catalogV1MigratesThroughV5WithoutDestructiveReset() {
        helper.createDatabase("$DB_NAME-v1-v5", 1).close()
        helper.runMigrationsAndValidate(
            "$DB_NAME-v1-v5",
            5,
            true,
            CHART_CATALOG_MIGRATION_1_2,
            CHART_CATALOG_MIGRATION_2_3,
            CHART_CATALOG_MIGRATION_3_4,
            CHART_CATALOG_MIGRATION_4_5,
        ).close()
    }

    companion object { private const val DB_NAME = "chart-catalog-migration" }
}
