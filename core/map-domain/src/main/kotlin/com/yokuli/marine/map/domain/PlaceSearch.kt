package com.yokuli.marine.map.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class PlaceSort { NAME, UPDATED_DESC, CREATED_DESC }

object PlaceSearch {
    fun filterAndSort(
        places: List<SavedPlace>,
        query: String,
        sort: PlaceSort = PlaceSort.NAME,
    ): List<SavedPlace> {
        val normalizedQuery = query.normalized()
        val coordinate = parseCoordinateQuery(query)
        val filtered = if (normalizedQuery.isEmpty()) {
            places
        } else {
            places.filter { place ->
                coordinate?.let { point ->
                    abs(place.point.latitude - point.latitude) <= COORDINATE_EPSILON &&
                        abs(place.point.longitude - point.longitude) <= COORDINATE_EPSILON
                } == true || place.searchTerms().any { normalizedQuery in it }
            }
        }
        return when (sort) {
            PlaceSort.NAME -> filtered.sortedWith(compareBy<SavedPlace> { it.name.normalized() }.thenBy { it.id })
            PlaceSort.UPDATED_DESC -> filtered.sortedWith(compareByDescending<SavedPlace> { it.updatedAtMillis }.thenBy { it.id })
            PlaceSort.CREATED_DESC -> filtered.sortedWith(compareByDescending<SavedPlace> { it.createdAtMillis }.thenBy { it.id })
        }
    }

    private fun SavedPlace.searchTerms(): List<String> = buildList {
        add(name.normalized())
        add(notes.normalized())
        add(category.wireValue.normalized())
        add(category.name.normalized())
        addAll(tags.map { it.normalized() })
        add(String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude).normalized())
    }

    private fun parseCoordinateQuery(query: String): GeoPoint? {
        val fields = query.split(',')
        if (fields.size != 2) return null
        return (CoordinateCodec.parse(fields[0], fields[1], CoordinateFormat.DECIMAL_DEGREES) as? CoordinateParseResult.Success)
            ?.point
    }

    private fun String.normalized(): String = Normalizer.normalize(trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)

    private const val COORDINATE_EPSILON = 0.000001
}
