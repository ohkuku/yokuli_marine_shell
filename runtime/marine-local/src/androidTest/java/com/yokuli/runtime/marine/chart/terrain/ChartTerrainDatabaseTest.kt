package com.yokuli.runtime.marine.chart.terrain

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses Android SQLite, not a mocked schema. No renderer or source chart is modified by these tests. */
@RunWith(AndroidJUnit4::class)
class ChartTerrainDatabaseTest {
    private lateinit var directory:File
    private val key="a".repeat(64)
    private val source="b".repeat(64)
    // Storage-layer fixture only: this test exercises schema and BLOB checksums, not the GLB codec.
    private val payload=ByteArray(64){it.toByte()}

    @Before fun setUp() {
        directory=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"terrain-schema-${UUID.randomUUID()}")
        check(directory.mkdirs())
    }
    @After fun tearDown() { directory.deleteRecursively() }

    private fun insert(db:SQLiteDatabase) {
        db.execSQL("INSERT INTO products VALUES (?,?,?,?,?,?)",arrayOf<Any>(key,source,1,ChartTerrainBlockCodec.hash(payload),payload,1L))
    }
    private fun hasMetadata(db:SQLiteDatabase)=db.rawQuery("SELECT 1 FROM sqlite_master WHERE name='android_metadata'",null).use{it.moveToFirst()}
    private fun assertSchemaRejected(block:()->Unit) {
        try {block();fail("Unexpected schema was accepted")}
        catch(error:IllegalArgumentException){assertEquals("CHART_TERRAIN_PRODUCT_SCHEMA",error.message)}
    }

    @Test fun cleanPreparedDatabaseCanBeOpenedAndReopenedWithoutMetadata()=runBlocking {
        val file=File(directory,"clean.sqlite")
        openTerrainProductDatabase(file).use{db->createTerrainProducts(db);insert(db)}
        repeat(3) {
            openTerrainProductDatabase(file).use{db->
                validateTerrainSchema(db,allowAndroidMetadata=true)
                createTerrainJobs(db)
                assertFalse(hasMetadata(db))
                assertArrayEquals(payload,readTerrainProduct(db,key).bytes)
            }
        }
    }

    @Test fun oldAndroidOpenReproducesRejectionAndCompatibilityPreservesTheBlock()=runBlocking {
        val file=File(directory,"legacy.sqlite")
        // This is the old production open call. Android inserts its own table before validation.
        SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
            createTerrainProducts(db);insert(db)
            assertTrue(hasMetadata(db))
            assertSchemaRejected {validateTerrainSchema(db)}
            validateTerrainSchema(db,allowAndroidMetadata=true)
            assertArrayEquals(payload,readTerrainProduct(db,key).bytes)
        }
        repeat(3) {
            openTerrainProductDatabase(file).use{db->
                validateTerrainSchema(db,allowAndroidMetadata=true)
                createTerrainJobs(db)
                val stored=readTerrainProduct(db,key)
                assertEquals(source,stored.sourceKey)
                assertEquals(ChartTerrainBlockCodec.hash(payload),stored.sha256)
                assertArrayEquals(payload,stored.bytes)
            }
        }
    }

    @Test fun importedPackagesDoNotInheritPrivateRuntimeCompatibility()=runBlocking {
        val file=File(directory,"external.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file,null).use{db->createTerrainProducts(db);insert(db)}
        var rejected=false
        try {validateChartTerrainProducts(file)}
        catch(error:IllegalArgumentException){assertEquals("CHART_TERRAIN_PRODUCT_SCHEMA",error.message);rejected=true}
        assertTrue(rejected)
    }

    @Test fun metadataCompatibilityDoesNotAdmitOtherTablesViewsOrTriggers() {
        val extensions=listOf(
            "CREATE TABLE unexpected(value TEXT)",
            "CREATE VIEW unexpected AS SELECT key FROM products",
            "CREATE TRIGGER unexpected AFTER INSERT ON products BEGIN SELECT 1; END",
            "CREATE VIEW android_metadata AS SELECT 'en_NZ' AS locale",
            "CREATE TABLE android_metadata(locale TEXT, extra TEXT)",
            "CREATE TABLE android_metadata(locale BLOB)",
            "CREATE TABLE android_metadata(locale TEXT NOT NULL)",
        )
        extensions.forEachIndexed{index,sql->
            openTerrainProductDatabase(File(directory,"invalid-$index.sqlite")).use{db->
                createTerrainProducts(db);db.execSQL(sql)
                assertSchemaRejected {validateTerrainSchema(db,allowAndroidMetadata=true)}
            }
        }
    }

    @Test fun aTriggerOnTheLegacyMetadataTableIsStillRejected() {
        openTerrainProductDatabase(File(directory,"trigger.sqlite")).use{db->
            createTerrainProducts(db)
            db.execSQL("CREATE TABLE android_metadata(locale TEXT)")
            db.execSQL("CREATE TRIGGER metadata_changed AFTER INSERT ON android_metadata BEGIN SELECT 1; END")
            assertSchemaRejected {validateTerrainSchema(db,allowAndroidMetadata=true)}
        }
    }

    @Test fun compatibilityDoesNotDisableProductChecksumValidation()=runBlocking {
        SQLiteDatabase.openOrCreateDatabase(File(directory,"corrupt.sqlite"),null).use{db->
            createTerrainProducts(db);insert(db)
            db.execSQL("UPDATE products SET sha256=?",arrayOf("0".repeat(64)))
            validateTerrainSchema(db,allowAndroidMetadata=true)
            var rejected=false
            try {readTerrainProduct(db,key)}
            catch(error:IllegalArgumentException){assertEquals("CHART_TERRAIN_PRODUCT_CORRUPT",error.message);rejected=true}
            assertTrue(rejected)
        }
    }

    @Test fun exportStyleCopyDoesNotLeakLocalMetadataOrJobs()=runBlocking {
        val local=File(directory,"local.sqlite");val exported=File(directory,"exported.sqlite")
        SQLiteDatabase.openOrCreateDatabase(local,null).use{db->createTerrainProducts(db);createTerrainJobs(db);insert(db)}
        openTerrainProductDatabase(local,readOnly=true).use{input->
            validateTerrainSchema(input,allowAndroidMetadata=true)
            openTerrainProductDatabase(exported).use{output->
                createTerrainProducts(output)
                output.execSQL("ATTACH DATABASE ? AS prepared",arrayOf(local.path))
                output.beginTransactionNonExclusive()
                try {output.execSQL("INSERT INTO products SELECT * FROM prepared.products");output.setTransactionSuccessful()}
                finally {output.endTransaction();output.execSQL("DETACH DATABASE prepared")}
            }
        }
        openTerrainProductDatabase(exported,readOnly=true).use{db->
            validateTerrainSchema(db,allowJobs=false)
            assertFalse(hasMetadata(db))
            assertArrayEquals(payload,readTerrainProduct(db,key).bytes)
        }
    }
}
