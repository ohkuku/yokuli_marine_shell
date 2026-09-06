package com.yokuli.marine.map.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileSnapshotTest {
    @Test fun `late and oversized renderer snapshots cannot replace the bounded latest image`() {
        val runtime = BoundedMapTileSnapshotRuntime(maximumEncodedBytes = 8, maximumDimension = 32)
        val a = runtime.begin(MapCamera(GeoPoint(-36.8, 174.7), 9.0), "a")
        val b = runtime.begin(MapCamera(GeoPoint(-36.9, 174.8), 10.0), "b")

        assertFalse(runtime.complete(a, 2, 2, MapTileSnapshotFormat.JPEG, byteArrayOf(1)))
        assertFalse(runtime.complete(b, 2, 2, MapTileSnapshotFormat.JPEG, ByteArray(9)))
        assertNull(runtime.snapshots.value)
        val bytes = byteArrayOf(2, 3, 4)
        assertTrue(runtime.complete(b, 2, 2, MapTileSnapshotFormat.JPEG, bytes))
        bytes[0] = 9

        assertEquals("b", runtime.snapshots.value!!.request.contentRevision)
        assertArrayEquals(byteArrayOf(2, 3, 4), runtime.snapshots.value!!.encodedBytes())
    }
}
