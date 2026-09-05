package com.yokuli.marine.map.domain

/** A single-place, human-readable export independent of Android storage APIs. */
object MapPlaceExport {
    const val MIME_TYPE: String = "application/json"

    fun suggestedFileName(place: SavedPlace): String {
        val safeId = place.id
            .map { character -> if (character.isLetterOrDigit() || character in "._-") character else '-' }
            .joinToString("")
            .trim('-', '.')
            .take(80)
            .ifBlank { "place" }
        return "yokuli-place-$safeId.json"
    }

    fun encode(place: SavedPlace): ByteArray = buildString {
        append("{\n")
        append("  \"format\": \"yokuli-place\",\n")
        append("  \"version\": 1,\n")
        append("  \"place\": {\"id\":${place.id.toJsonString()},\"revision\":${place.revision},")
        append("\"name\":${place.name.toJsonString()},\"notes\":${place.notes.toJsonString()},")
        append("\"category\":${place.category.wireValue.toJsonString()},")
        append("\"tags\":${place.tags.toJsonStrings()},")
        append("\"createdAtMillis\":${place.createdAtMillis},\"updatedAtMillis\":${place.updatedAtMillis},")
        append("\"point\":${place.point.toJsonPoint()}}\n")
        append("}\n")
    }.toByteArray(Charsets.UTF_8)
}
