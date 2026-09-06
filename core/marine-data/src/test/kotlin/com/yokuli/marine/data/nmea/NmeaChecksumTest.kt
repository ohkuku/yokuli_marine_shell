package com.yokuli.marine.data.nmea

import com.yokuli.marine.data.model.ChecksumTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaChecksumTest {
    @Test
    fun strictChecksumRejectsMissingAndBadChecksums() {
        val body = "GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W"
        val valid = NmeaChecksum.append(body)

        assertEquals(
            ChecksumTrust.VERIFIED,
            (NmeaChecksum.validate(valid) as ChecksumValidation.Valid).trust,
        )
        assertEquals(
            ChecksumFailureReason.MISSING,
            (NmeaChecksum.validate("\$$body") as ChecksumValidation.Failure).reason,
        )
        assertEquals(
            ChecksumFailureReason.MISMATCH,
            (NmeaChecksum.validate(valid.dropLast(2) + "00") as ChecksumValidation.Failure).reason,
        )
        assertEquals(
            ChecksumTrust.UNVERIFIED_ALLOWED,
            (NmeaChecksum.validate("\$$body", ChecksumPolicy.ALLOW_MISSING) as ChecksumValidation.Valid).trust,
        )
    }

    @Test
    fun checksumValidationRejectsMalformedOrNonAsciiFrames() {
        assertFalse(NmeaChecksum.validate("not-nmea") is ChecksumValidation.Valid)
        assertFalse(NmeaChecksum.validate("\$GPRMC*QZ") is ChecksumValidation.Valid)
        assertFalse(NmeaChecksum.validate("\$GPRMC,é*00") is ChecksumValidation.Valid)

        val bangFrame = NmeaChecksum.append("AIVDM,1,1,,A,13aG?P001oP,0", prefix = '!')
        assertTrue(NmeaChecksum.validate(bangFrame) is ChecksumValidation.Valid)
    }

    @Test
    fun checksumRequiresExactlyTwoAsciiHexDigits() {
        val valid = NmeaChecksum.append("GPXYZ")
        val supplied = valid.takeLast(2)

        assertTrue(NmeaChecksum.validate(valid) is ChecksumValidation.Valid)
        // 'A' xor '@' is exactly 0x01; the old numeric parser accepted "+1" as that value.
        assertEquals(
            ChecksumFailureReason.MALFORMED,
            (NmeaChecksum.validate("\$A@*+1") as ChecksumValidation.Failure).reason,
        )
        assertEquals(
            ChecksumFailureReason.MALFORMED,
            (NmeaChecksum.validate(valid.dropLast(2) + "-${supplied.last()}") as ChecksumValidation.Failure).reason,
        )
        assertEquals(
            ChecksumFailureReason.MALFORMED,
            (NmeaChecksum.validate(valid.dropLast(2) + "G${supplied.last()}") as ChecksumValidation.Failure).reason,
        )
    }
}
