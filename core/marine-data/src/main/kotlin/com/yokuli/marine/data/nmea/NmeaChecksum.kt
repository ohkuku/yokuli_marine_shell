package com.yokuli.marine.data.nmea

import com.yokuli.marine.data.model.ChecksumTrust

enum class ChecksumPolicy {
    STRICT,
    ALLOW_MISSING,
}

enum class ChecksumFailureReason {
    INVALID_ENVELOPE,
    NON_ASCII,
    MISSING,
    MALFORMED,
    MISMATCH,
}

sealed interface ChecksumValidation {
    data class Valid(
        val body: String,
        val prefix: Char,
        val trust: ChecksumTrust,
    ) : ChecksumValidation

    data class Failure(val reason: ChecksumFailureReason) : ChecksumValidation
}

/** NMEA 0183 XOR checksum validation. Strict verification is the safe default. */
object NmeaChecksum {
    fun validate(
        rawFrame: String,
        policy: ChecksumPolicy = ChecksumPolicy.STRICT,
    ): ChecksumValidation {
        val frame = rawFrame.trimEnd('\r', '\n')
        if (frame.isEmpty() || frame.first() !in setOf('$', '!')) {
            return ChecksumValidation.Failure(ChecksumFailureReason.INVALID_ENVELOPE)
        }
        if (frame.any { it.code !in ASCII_PRINTABLE_RANGE }) {
            return ChecksumValidation.Failure(ChecksumFailureReason.NON_ASCII)
        }

        val checksumSeparator = frame.indexOf('*')
        if (checksumSeparator < 0) {
            return if (policy == ChecksumPolicy.ALLOW_MISSING && frame.length > 1) {
                ChecksumValidation.Valid(
                    body = frame.substring(1),
                    prefix = frame.first(),
                    trust = ChecksumTrust.UNVERIFIED_ALLOWED,
                )
            } else {
                ChecksumValidation.Failure(ChecksumFailureReason.MISSING)
            }
        }
        if (
            checksumSeparator <= 1 ||
            checksumSeparator != frame.lastIndex - 2 ||
            frame.indexOf('*', checksumSeparator + 1) >= 0
        ) {
            return ChecksumValidation.Failure(ChecksumFailureReason.MALFORMED)
        }

        val checksumText = frame.substring(checksumSeparator + 1)
        if (!checksumText.all(::isAsciiHexDigit)) {
            return ChecksumValidation.Failure(ChecksumFailureReason.MALFORMED)
        }
        val supplied = checksumText.toIntOrNull(radix = 16)
            ?: return ChecksumValidation.Failure(ChecksumFailureReason.MALFORMED)
        val body = frame.substring(1, checksumSeparator)
        val calculated = body.fold(0) { checksum, character -> checksum xor character.code }
        return if (calculated == supplied) {
            ChecksumValidation.Valid(
                body = body,
                prefix = frame.first(),
                trust = ChecksumTrust.VERIFIED,
            )
        } else {
            ChecksumValidation.Failure(ChecksumFailureReason.MISMATCH)
        }
    }

    fun append(body: String, prefix: Char = '$'): String {
        require(prefix == '$' || prefix == '!') { "NMEA frames start with $ or !" }
        require(body.isNotEmpty() && body.none { it == '*' || it == '\r' || it == '\n' })
        require(body.all { it.code in ASCII_PRINTABLE_RANGE }) { "NMEA body must be printable ASCII" }
        val checksum = body.fold(0) { value, character -> value xor character.code }
        val hexadecimal = checksum.toString(radix = 16).uppercase().padStart(2, '0')
        return "$prefix$body*$hexadecimal"
    }

    private val ASCII_PRINTABLE_RANGE = 0x20..0x7e

    private fun isAsciiHexDigit(character: Char): Boolean =
        character in '0'..'9' || character in 'A'..'F' || character in 'a'..'f'
}
