package com.yokuli.marine.map.domain

internal fun GeoPoint.toJsonPoint(): String = "{\"latitude\":$latitude,\"longitude\":$longitude}"

internal fun List<GeoPoint>.toJsonPoints(): String =
    joinToString(prefix = "[", postfix = "]") { it.toJsonPoint() }

internal fun Iterable<String>.toJsonStrings(): String =
    joinToString(prefix = "[", postfix = "]") { it.toJsonString() }

internal fun String?.toJsonStringOrNull(): String = this?.toJsonString() ?: "null"

internal fun String.toJsonString(): String = buildString(length + 2) {
    append('"')
    this@toJsonString.forEach { character ->
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
