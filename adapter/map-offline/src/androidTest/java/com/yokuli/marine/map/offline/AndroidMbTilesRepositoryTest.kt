package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMbTilesRepositoryTest {
    @Test
    fun importIsValidatedAndAtomicAndDeleteKeepsUnrelatedUserFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-repository-test").also { it.deleteRecursively(); it.mkdirs() }
        val source = File(testRoot, "source.mbtiles")
        createRasterMbTiles(source)
        val packagesRoot = File(testRoot, "packages")
        val unrelated = File(testRoot, "user-route.txt").also { it.writeText("keep") }
        val repository = AndroidMbTilesRepository(context.contentResolver, packagesRoot)

        val candidate = repository.inspect(source.toURI().toString())
        val installed = repository.commit(
            ChartPackageImportRequest(
                candidate.stagedImportId, "Test chart", "Test source", "CC-BY", "Test attribution", "1",
            ),
        )

        assertEquals(listOf(installed), repository.listInstalled())
        assertTrue(File(android.net.Uri.parse(installed.localUri).path!!).isFile)
        repository.delete(installed.id)
        assertTrue(repository.listInstalled().isEmpty())
        assertTrue(unrelated.isFile)
        assertFalse(packagesRoot.listFiles().orEmpty().any { it.name.startsWith(".staging-") })
        testRoot.deleteRecursively()
    }

    private fun createRasterMbTiles(file: File) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            mapOf(
                "name" to "Fixture", "bounds" to "170,-47,179,-34", "minzoom" to "0",
                "maxzoom" to "2", "format" to "png",
            ).forEach { (name, value) ->
                database.execSQL("INSERT INTO metadata(name,value) VALUES(?,?)", arrayOf(name, value))
            }
            database.execSQL(
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(0,0,0,?)",
                arrayOf(byteArrayOf(0x01)),
            )
        }
    }
}
