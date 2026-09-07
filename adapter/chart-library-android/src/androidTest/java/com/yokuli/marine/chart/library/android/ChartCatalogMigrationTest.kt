package com.yokuli.marine.chart.library.android

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    companion object { private const val DB_NAME = "chart-catalog-migration" }
}
