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
        append("  \"version\": 3,\n")
        append("  \"libraryRevision\": ${snapshot.revision},\n")
        append("  \"places\": [")
        snapshot.places.forEachIndexed { index, place ->
            if (index > 0) append(',')
            append("\n    {\"id\":${place.id.toJsonString()},\"revision\":${place.revision},")
            append("\"name\":${place.name.toJsonString()},\"notes\":${place.notes.toJsonString()},")
            append("\"category\":${place.category.wireValue.toJsonString()},")
            append("\"tags\":${place.tags.sorted().toJsonStrings()},")
            append("\"createdAtMillis\":${place.createdAtMillis},\"updatedAtMillis\":${place.updatedAtMillis},")
            append("\"point\":${place.point.toJsonPoint()}}")
        }
        if (snapshot.places.isNotEmpty()) append('\n').append("  ")
        append("],\n")
        append("  \"routeDrafts\": [")
        snapshot.routeDrafts.forEachIndexed { index, draft ->
            if (index > 0) append(',')
            append("\n    {\"id\":${draft.id.toJsonString()},\"revision\":${draft.revision},")
            append("\"name\":${draft.name.toJsonString()},\"notes\":${draft.notes.toJsonString()},")
            append("\"plannedSpeedKnots\":${draft.plannedSpeedKnots ?: "null"},")
            append("\"basePlanId\":${draft.basePlanId.toJsonStringOrNull()},")
            append("\"basePlanRevision\":${draft.basePlanRevision ?: "null"},")
            append("\"waypointIds\":${draft.waypointIds.toJsonStrings()},")
            append("\"waypointPlaceReferences\":${draft.waypointPlaceReferences.jsonReferences()},")
            append("\"points\":${draft.waypoints.toJsonPoints()}}")
        }
        if (snapshot.routeDrafts.isNotEmpty()) append('\n').append("  ")
        append("],\n")
        append("  \"savedRoutes\": [")
        snapshot.savedRoutes.forEachIndexed { index, route ->
            if (index > 0) append(',')
            append("\n    {\"id\":${route.id.toJsonString()},\"revision\":${route.revision},")
            append("\"name\":${route.name.toJsonString()},\"notes\":${route.notes.toJsonString()},")
            append("\"plannedSpeedKnots\":${route.plannedSpeedKnots ?: "null"},")
            append("\"sourceDraftId\":${route.sourceDraftId.toJsonStringOrNull()},")
            append("\"sourceDraftRevision\":${route.sourceDraftRevision ?: "null"},")
            append("\"waypointPlaceReferences\":${route.waypointPlaceReferences.jsonReferences()},")
            append("\"waypointIds\":${route.waypointIds.toJsonStrings()},")
            append("\"points\":${route.waypoints.toJsonPoints()}}")
        }
        if (snapshot.savedRoutes.isNotEmpty()) append('\n').append("  ")
        append("]\n")
        append("}\n")
    }.toByteArray(Charsets.UTF_8)

    private fun Map<Int, PlaceRevisionReference>.jsonReferences(): String = entries.sortedBy { it.key }
        .joinToString(prefix = "[", postfix = "]") { (index, reference) ->
            "{\"index\":$index,\"placeId\":${reference.placeId.toJsonString()},\"revision\":${reference.revision}}"
        }
}
