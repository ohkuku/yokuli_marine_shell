package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {
    private val places = listOf(
        SavedPlace(
            "p2", "同名", GeoPoint(-36.8485, 174.7633), notes = "Night fuel pontoon",
            category = PlaceCategory.MARINA, tags = listOf("补水"), createdAtMillis = 20, updatedAtMillis = 40,
        ),
        SavedPlace(
            "p1", "同名", GeoPoint(-41.2866, 174.7756), notes = "避风良好",
            category = PlaceCategory.ANCHORAGE, tags = listOf("quiet"), createdAtMillis = 10, updatedAtMillis = 30,
        ),
        SavedPlace(
            "p3", "Landing", GeoPoint(-36.9000, 174.8000), notes = "dinghy only",
            category = PlaceCategory.LANDING, tags = listOf("shore"), createdAtMillis = 30, updatedAtMillis = 50,
        ),
    )

    @Test
    fun `local search covers Chinese English notes categories tags and explicit coordinates`() {
        assertEquals(listOf("p2"), PlaceSearch.filterAndSort(places, "补水").map { it.id })
        assertEquals(listOf("p2"), PlaceSearch.filterAndSort(places, "FUEL").map { it.id })
        assertEquals(listOf("p1"), PlaceSearch.filterAndSort(places, "anchorage").map { it.id })
        assertEquals(listOf("p2"), PlaceSearch.filterAndSort(places, "码头").map { it.id })
        assertEquals(listOf("p3"), PlaceSearch.filterAndSort(places, "shore").map { it.id })
        assertEquals(listOf("p2"), PlaceSearch.filterAndSort(places, "-36.8485, 174.7633").map { it.id })
        assertTrue(PlaceSearch.filterAndSort(places, "Auckland online geocoder result").isEmpty())
    }

    @Test
    fun `duplicate names have deterministic sort order and update sort is explicit`() {
        assertEquals(listOf("p3", "p1", "p2"), PlaceSearch.filterAndSort(places, "", PlaceSort.NAME).map { it.id })
        assertEquals(listOf("p3", "p2", "p1"), PlaceSearch.filterAndSort(places, "", PlaceSort.UPDATED_DESC).map { it.id })
    }
}
