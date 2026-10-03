package com.yokuli.runtime.marine.planning

import com.google.gson.Gson
import com.yokuli.runtime.contract.planning.PASSAGE_RULES_VERSION
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.CRC32

/** Small, geometry-free projection of an existing nav-v3 header. Never navigation evidence alone. */
internal data class PassageRegionTopology(
    val schema: Int, val source: String, val policy: String, val rules: String,
    val region: PassageRegionId, val portals: List<PassagePortal>, val componentCount: Int,
) {
    val bytes: Long get() = 1024L + portals.size * 64L
}

/**
 * Reads ONLY the CRC-protected header of the existing .nav format. Gson skips evidence, raster and
 * constraint arrays not present in this projection; no WKB, JTS validation or mesh decode in the
 * coarse search. The selected path still loads and validates full products before being returned.
 * No new map format and no reinterpretation of water connectivity.
 */
internal class PassageTopologyStore(private val root: File) {
    private val gson = Gson()

    suspend fun read(key: String, source: String, policy: String, id: PassageRegionId): PassageRegionTopology? =
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()
            job.ensureActive(); VirtualHostServices.beforeRead()
            val file = File(root, "$key.nav")
            if(!file.isFile) return@withContext null
            val stamp = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
            synchronized(cache) { cache[stamp] }?.let { value ->
                if(value.source == source && value.policy == policy && value.region == id &&
                    value.rules == PASSAGE_RULES_VERSION) return@withContext value
            }
            try {
                require(file.length() in 20..64L * 1024 * 1024) { "NAVIGATION_PRODUCT_SIZE" }
                FileInputStream(file).buffered().use { stream ->
                    val input = DataInputStream(stream)
                    require(input.readInt() == 0x594b4e31 && input.readInt() == 3) { "NAVIGATION_PRODUCT_VERSION" }
                    val length = input.readInt()
                    require(length in 1..8 * 1024 * 1024) { "NAVIGATION_PRODUCT_HEADER_SIZE" }
                    val bytes = ByteArray(length)
                    var offset = 0
                    while(offset < length) {
                        job.ensureActive()
                        val count = input.read(bytes, offset, minOf(64 * 1024, length - offset))
                        require(count > 0) { "NAVIGATION_PRODUCT_HEADER_TRUNCATED" }
                        offset += count
                    }
                    require(input.readLong() == CRC32().apply { update(bytes) }.value) {
                        "NAVIGATION_PRODUCT_HEADER_CHECKSUM"
                    }
                    job.ensureActive()
                    val value = gson.fromJson(String(bytes, Charsets.UTF_8), PassageRegionTopology::class.java)
                    require(value.schema == 3 && value.source == source && value.policy == policy &&
                        value.rules == PASSAGE_RULES_VERSION && value.region == id) { "NAVIGATION_PRODUCT_IDENTITY" }
                    require(value.componentCount in 0..4096 && value.portals.size <= 16_384) {
                        "NAVIGATION_PRODUCT_TOPOLOGY"
                    }
                    require(value.portals.all { portal ->
                        val lower = if(portal.edge < 2) id.south else id.west
                        portal.component in 0 until value.componentCount && portal.edge in 0..3 &&
                            portal.lower.isFinite() && portal.upper.isFinite() && portal.lower <= portal.upper &&
                            portal.lower >= lower - 1e-7 && portal.upper <= lower + PassageRegionId.STEP + 1e-7
                    }) { "NAVIGATION_PRODUCT_TOPOLOGY" }
                    job.ensureActive()
                    synchronized(cache) {
                        cache.remove(stamp)
                        while(cache.isNotEmpty() && (cache.size >= 256 ||
                                    cache.values.sumOf { it.bytes } + value.bytes > MAX_CACHE_BYTES)) {
                            val iterator = cache.entries.iterator(); iterator.next(); iterator.remove()
                        }
                        if(value.bytes <= MAX_CACHE_BYTES) cache[stamp] = value
                    }
                    value
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: IOException) { throw error } // Permission/storage failures are not "no water".
            catch(_: RuntimeException) { null } // Full reader/preparer determines whether a product is repairable.
        }

    companion object {
        private const val MAX_CACHE_BYTES = 8L * 1024 * 1024
        private val cache = LinkedHashMap<String, PassageRegionTopology>(32, .75f, true)
    }
}
