package com.yokuli.marine.map.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPlaceExportTest {
    @Test
    fun `single-place document preserves the complete stable place record`() {
        val place = SavedPlace(
            id = "place:west/harbour",
            name = "西港 \"Marina\"",
            point = GeoPoint(-36.8485, 174.7633),
            revision = 7L,
            notes = "Gate B\nnight access",
            category = PlaceCategory.MARINA,
            tags = listOf("fuel", "补水"),
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
        )

        val document = MapPlaceExport.encode(place).toString(Charsets.UTF_8)

        assertTrue(document.contains("\"format\": \"yokuli-place\""))
        assertTrue(document.contains("\"id\":\"place:west/harbour\""))
        assertTrue(document.contains("西港 \\\"Marina\\\""))
        assertTrue(document.contains("Gate B\\nnight access"))
        assertTrue(document.contains("\"category\":\"marina\""))
        assertTrue(document.contains("\"tags\":[\"fuel\", \"补水\"]"))
        assertTrue(document.contains("\"createdAtMillis\":100"))
        assertTrue(document.contains("\"updatedAtMillis\":200"))
        assertTrue(document.contains("\"latitude\":-36.8485,\"longitude\":174.7633"))
        assertFalse(document.contains("route"))
        assertTrue(MapPlaceExport.suggestedFileName(place).matches(Regex("yokuli-place-[A-Za-z0-9._-]+\\.json")))
    }
}
