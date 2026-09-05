package com.yokuli.marine.map.domain

/**
 * A deliberately small, human-readable escape hatch for edits that could not be persisted.
 * This is not the GPX interchange contract; C08 owns that workflow. The recovery document
 * preserves stable IDs, revisions and coordinates so support tooling can inspect it without
 * pretending that a failed database write succeeded.
 */
object MapRecoveryExport {
    const val MIME_TYPE: String = "application/json"
    const val SUGGESTED_FILE_NAME: String = "yokuli-map-recovery.json"

    fun encode(snapshot: MapLibrarySnapshot): ByteArray = buildString {
        append("{\n")
        append("  \"format\": \"yokuli-map-recovery\",\n")
        append("  \"version\": 2,\n")
        append("  \"libraryRevision\": ${snapshot.revision},\n")
        append("  \"places\": [")
        snapshot.places.forEachIndexed { index, place ->
            if (index > 0) append(',')
            append("\n    {\"id\":${place.id.json()},\"revision\":${place.revision},")
            append("\"name\":${place.name.json()},\"notes\":${place.notes.json()},")
            append("\"category\":${place.category.wireValue.json()},")
            append("\"tags\":${place.tags.sorted().jsonStrings()},")
            append("\"createdAtMillis\":${place.createdAtMillis},\"updatedAtMillis\":${place.updatedAtMillis},")
            append("\"point\":${place.point.json()}}")
        }
        if (snapshot.places.isNotEmpty()) append('\n').append("  ")
        append("],\n")
        append("  \"routeDrafts\": [")
        snapshot.routeDrafts.forEachIndexed { index, draft ->
            if (index > 0) append(',')
            append("\n    {\"id\":${draft.id.json()},\"revision\":${draft.revision},")
            append("\"name\":${draft.name.json()},\"plannedSpeedKnots\":${draft.plannedSpeedKnots},")
            append("\"points\":${draft.waypoints.json()}}")
        }
        if (snapshot.routeDrafts.isNotEmpty()) append('\n').append("  ")
        append("],\n")
        append("  \"savedRoutes\": [")
        snapshot.savedRoutes.forEachIndexed { index, route ->
            if (index > 0) append(',')
            append("\n    {\"id\":${route.id.json()},\"revision\":${route.revision},")
            append("\"name\":${route.name.json()},\"plannedSpeedKnots\":${route.plannedSpeedKnots},")
            append("\"sourceDraftId\":${route.sourceDraftId.jsonOrNull()},")
            append("\"sourceDraftRevision\":${route.sourceDraftRevision ?: "null"},")
            append("\"waypointPlaceReferences\":${route.waypointPlaceReferences.jsonReferences()},")
            append("\"points\":${route.waypoints.json()}}")
        }
        if (snapshot.savedRoutes.isNotEmpty()) append('\n').append("  ")
        append("]\n")
        append("}\n")
    }.toByteArray(Charsets.UTF_8)

    private fun GeoPoint.json(): String = "{\"latitude\":$latitude,\"longitude\":$longitude}"

    private fun List<GeoPoint>.json(): String = joinToString(prefix = "[", postfix = "]") { it.json() }

    private fun List<String>.jsonStrings(): String = joinToString(prefix = "[", postfix = "]") { it.json() }

    private fun Map<Int, PlaceRevisionReference>.jsonReferences(): String = entries.sortedBy { it.key }
        .joinToString(prefix = "[", postfix = "]") { (index, reference) ->
            "{\"index\":$index,\"placeId\":${reference.placeId.json()},\"revision\":${reference.revision}}"
        }

    private fun String?.jsonOrNull(): String = this?.json() ?: "null"

    private fun String.json(): String = buildString(length + 2) {
        append('"')
        this@json.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
