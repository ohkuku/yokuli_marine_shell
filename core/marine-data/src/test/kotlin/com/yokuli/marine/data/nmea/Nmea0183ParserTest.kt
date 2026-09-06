package com.yokuli.marine.data.nmea

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ChecksumTrust
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.model.UdpOriginIdentityPolicy
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.model.WindSpeedReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class Nmea0183ParserTest {
    private val parser = Nmea0183Parser()
    private val sender = SenderIdentity("192.0.2.40", 10_110)
    private val context = NmeaParseContext(
        source = SourceIdentity.forUdp(
            ConnectionId("boat-gateway"),
            sender,
            UdpOriginIdentityPolicy.HOST_ADDRESS,
        ),
        sessionGeneration = SessionGeneration(4),
        receivedAtMonotonicMillis = 42_000,
        observationGroupId = ObservationGroupId(17),
        sender = sender,
    )

    @Test
    fun allRequiredSentenceTypesProduceTypedResults() {
        val fixtures = mapOf(
            "RMC" to "GPRMC,123519,A,4807.038,N,01131.000,E,22.4,84.4,230394,3.1,W",
            "GGA" to "GNGGA,123520,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,",
            "GLL" to "GPGLL,4916.45,N,12311.12,W,225444,A",
            "VTG" to "GPVTG,84.4,T,,M,12.3,N,22.8,K",
            "ZDA" to "GPZDA,201530.00,04,07,2002,00,00",
            "HDG" to "IIHDG,100.0,,,10.0,E",
            "HDM" to "IIHDM,120.0,M",
            "HDT" to "IIHDT,123.4,T",
            "DPT" to "IIDPT,5.2,-1.2",
            "DBT" to "IIDBT,16.4,f,5.0,M,2.7,F",
            "MWD" to "IIMWD,214.8,T,201.3,M,12.4,N,6.4,M",
            "MWV" to "IIMWV,32.0,R,6.0,M,A",
        )

        fixtures.forEach { (formatter, body) ->
            val result = parser.parse(NmeaChecksum.append(body), context)
            assertTrue("$formatter should parse, got $result", result is NmeaParseResult.Parsed)
            result as NmeaParseResult.Parsed
            assertEquals(formatter, result.sentence.formatter)
            assertEquals(SessionGeneration(4), result.sentence.origin.sessionGeneration)
            assertEquals(42_000, result.sentence.receivedAtMonotonicMillis)
            assertEquals(ObservationGroupId(17), result.sentence.groupId)
            assertEquals(ChecksumTrust.VERIFIED, result.sentence.checksumTrust)
            assertTrue(result.observations.all { it.groupId == ObservationGroupId(17) })
            assertTrue(result.observations.all { it.checksumTrust == ChecksumTrust.VERIFIED })
            assertTrue("$formatter should expose at least one typed observation", result.observations.isNotEmpty())
        }
    }

    @Test
    fun parserPreservesPositionUnitsTimesAndProvenance() {
        val result = parsed("GPRMC,123519.250,A,4807.038,N,01131.000,E,22.4,84.4,230394,3.1,W,A")
        val position = result.observation(DataKey.Position)

        val positionValue = position.value as MarineValue.Position
        assertEquals(48.1173, positionValue.latitudeDegrees, 0.000_001)
        assertEquals(11.516666666666667, positionValue.longitudeDegrees, 0.000_001)
        assertEquals(ObservationValidity.VALID, position.validity)
        assertEquals("GP", position.origin.talker)
        assertEquals("RMC", position.origin.formatter)
        assertEquals(764426119250L, position.sourceTimeEpochMillis)
        assertDecimal(result, DataKey.SpeedOverGround, 22.4, MarineUnit.KNOTS)
        assertDecimal(result, DataKey.CourseOverGround, 84.4, MarineUnit.DEGREES)
        assertDecimal(result, DataKey.MagneticVariation, -3.1, MarineUnit.DEGREES)

        val southWest = parsed("GPGLL,4916.45,S,12311.12,W,225444,A")
            .observation(DataKey.Position).value as MarineValue.Position
        assertEquals(-49.2741666667, southWest.latitudeDegrees, 0.000_001)
        assertEquals(-123.1853333333, southWest.longitudeDegrees, 0.000_001)
    }

    @Test
    fun blankFieldsDoNotRefreshObservations() {
        val blank = parsed("GPRMC,123520,A,,,,,,,,,,")

        assertTrue(blank.observations.none { it.key == DataKey.Position })
        assertTrue(blank.observations.none { it.key == DataKey.SpeedOverGround })
        assertTrue(blank.observations.none { it.key == DataKey.CourseOverGround })
        assertTrue(blank.observations.none { it.value is MarineValue.Decimal && it.value.value == 0.0 })
    }

    @Test
    fun explicitInvalidFixesAndWindInvalidateImmediatelyWithoutValues() {
        val rmc = invalid("GPRMC,123519,V,4807.038,N,01131.000,E,5.0,80.0,230394,,,")
        assertEquals(
            setOf(DataKey.Position, DataKey.SpeedOverGround, DataKey.CourseOverGround),
            rmc.observations.map { it.key }.toSet(),
        )
        assertTrue(rmc.observations.all { it.validity == ObservationValidity.EXPLICIT_INVALID })
        assertTrue(rmc.observations.all { it.value == null })

        val gga = invalid("GNGGA,123519,4807.038,N,01131.000,E,0,08,0.9,545.4,M,46.9,M,,")
        assertTrue(gga.observations.any { it.key == DataKey.Position && it.value == null })
        assertTrue(gga.observations.any { it.key == DataKey.Altitude && it.value == null })
        assertTrue(gga.observations.none { it.key == DataKey.Altitude && it.value != null })

        val wind = invalid("IIMWV,30.0,T,12.0,N,V")
        assertEquals(
            setOf(
                DataKey.WindAngle(WindReference.TRUE_RELATIVE),
                DataKey.WindSpeed(WindSpeedReference.TRUE),
            ),
            wind.observations.map { it.key }.toSet(),
        )
    }

    @Test
    fun referenceSpecificKeysNeverCollapseDistinctMarineMeanings() {
        val hdg = parsed("IIHDG,100.0,,,10.0,E")
        assertDecimal(hdg, DataKey.Heading(HeadingReference.MAGNETIC), 100.0, MarineUnit.DEGREES)
        assertDecimal(hdg, DataKey.MagneticVariation, 10.0, MarineUnit.DEGREES)
        assertFalse(hdg.observations.any { it.key == DataKey.Heading(HeadingReference.TRUE) })

        val dpt = parsed("IIDPT,5.2,-1.2")
        assertDecimal(dpt, DataKey.Depth(DepthReference.BELOW_TRANSDUCER), 5.2, MarineUnit.METERS)
        assertDecimal(dpt, DataKey.Depth(DepthReference.BELOW_KEEL), 4.0, MarineUnit.METERS)

        val surface = parsed("IIDPT,5.2,1.2")
        assertDecimal(surface, DataKey.Depth(DepthReference.BELOW_SURFACE), 6.4, MarineUnit.METERS)

        val zeroOffset = parsed("IIDPT,5.2,0.0")
        assertEquals(
            setOf(DataKey.Depth(DepthReference.BELOW_TRANSDUCER)),
            zeroOffset.observations.map { it.key }.toSet(),
        )

        val dbt = parsed("IIDBT,16.4,f,5.0,M,2.7,F")
        assertDecimal(dbt, DataKey.Depth(DepthReference.BELOW_TRANSDUCER), 5.0, MarineUnit.METERS)

        val mwd = parsed("IIMWD,214.8,T,201.3,M,12.4,N,6.4,M")
        assertDecimal(mwd, DataKey.WindAngle(WindReference.TRUE_NORTH), 214.8, MarineUnit.DEGREES)
        assertDecimal(mwd, DataKey.WindAngle(WindReference.MAGNETIC_NORTH), 201.3, MarineUnit.DEGREES)
        assertDecimal(mwd, DataKey.WindSpeed(WindSpeedReference.TRUE), 12.4, MarineUnit.KNOTS)

        val mwv = parsed("IIMWV,32.0,R,6.0,M,A")
        assertDecimal(mwv, DataKey.WindAngle(WindReference.APPARENT), 32.0, MarineUnit.DEGREES)
        assertDecimal(mwv, DataKey.WindSpeed(WindSpeedReference.APPARENT), 11.663064, MarineUnit.KNOTS)
    }

    @Test
    fun standardAlternativeUnitsConvertWithoutChangingSemanticKeys() {
        val vtg = parsed("GPVTG,84.4,T,,M,,N,18.52,K")
        assertDecimal(vtg, DataKey.SpeedOverGround, 10.0, MarineUnit.KNOTS)

        val dbtFeetOnly = parsed("IIDBT,16.4,f,,M,,F")
        assertDecimal(
            dbtFeetOnly,
            DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
            4.99872,
            MarineUnit.METERS,
        )

        val dbtFathomsOnly = parsed("IIDBT,,f,,M,2.7,F")
        assertDecimal(
            dbtFathomsOnly,
            DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
            4.93776,
            MarineUnit.METERS,
        )

        val mwdMetresPerSecond = parsed("IIMWD,214.8,T,201.3,M,,N,6.4,M")
        assertDecimal(
            mwdMetresPerSecond,
            DataKey.WindSpeed(WindSpeedReference.TRUE),
            12.4406016,
            MarineUnit.KNOTS,
        )

        val mwvKilometresPerHour = parsed("IIMWV,32.0,R,18.52,K,A")
        assertDecimal(
            mwvKilometresPerHour,
            DataKey.WindSpeed(WindSpeedReference.APPARENT),
            10.0,
            MarineUnit.KNOTS,
        )
    }

    @Test
    fun invalidNumbersRangesHemisphereAndUnitsNeverBecomeObservations() {
        val badPosition = parsed("GPRMC,123519,A,-4807.038,N,18100.000,E,NaN,361.0,230394,,,")
        assertTrue(badPosition.observations.none { it.key == DataKey.Position })
        assertTrue(badPosition.observations.none { it.key == DataKey.SpeedOverGround })
        assertTrue(badPosition.observations.none { it.key == DataKey.CourseOverGround })
        assertTrue(badPosition.fieldErrors.isNotEmpty())

        val badWindUnit = parsed("IIMWV,32.0,R,6.0,X,A")
        assertTrue(badWindUnit.observations.none { it.key is DataKey.WindSpeed })
        assertTrue(badWindUnit.fieldErrors.any { it.fieldName == "speedUnit" })
    }

    @Test
    fun numericFieldsRejectNonNmeaDecimalLexemesAndWrongCoordinateWidths() {
        val decimalDegrees = parsed(
            "GPRMC,123519,A,48.1173,N,11.5167,E,1.0,2.0,230394,,,A",
        )
        assertTrue(decimalDegrees.observations.none { it.key == DataKey.Position })
        assertTrue(decimalDegrees.fieldErrors.any { it.fieldName == "latitude" })

        val exponentAndPlus = parsed(
            "GPRMC,123519,A,4807.038,N,01131.000,E,1e1,+2.0,230394,,,A",
        )
        assertTrue(exponentAndPlus.observations.none { it.key == DataKey.SpeedOverGround })
        assertTrue(exponentAndPlus.observations.none { it.key == DataKey.CourseOverGround })
        assertTrue(exponentAndPlus.fieldErrors.any { it.fieldName == "speedOverGround" })
        assertTrue(exponentAndPlus.fieldErrors.any { it.fieldName == "courseOverGround" })

        val plusFix = parsed(
            "GNGGA,123519,4807.038,N,01131.000,E,+1,1e1,0.9,545.4,M,46.9,M,,",
        )
        assertTrue(plusFix.observations.none { it.key == DataKey.FixQuality })
        assertTrue(plusFix.observations.none { it.key == DataKey.Satellites })
        assertTrue(plusFix.observations.none { it.key == DataKey.Position })

        val exponentClock = parsed("GPZDA,123519e0,04,07,2002,00,00")
        assertTrue(exponentClock.observations.none { it.key == DataKey.SourceTime })
        assertTrue(exponentClock.fieldErrors.any { it.fieldName == "sourceDateTime" })

        val plusDepth = parsed("IIDPT,+5.2,-1.2")
        assertTrue(plusDepth.observations.none { it.key is DataKey.Depth })
        assertTrue(plusDepth.fieldErrors.any { it.fieldName == "depth" })
    }

    @Test
    fun explicitlySignedAltitudeAndTransducerOffsetRemainSupported() {
        val gga = parsed(
            "GNGGA,123520,4807.038,N,01131.000,E,1,08,0.9,-2.5,M,46.9,M,,",
        )
        assertDecimal(gga, DataKey.Altitude, -2.5, MarineUnit.METERS)

        val dpt = parsed("IIDPT,5.2,-1.2")
        assertDecimal(dpt, DataKey.Depth(DepthReference.BELOW_KEEL), 4.0, MarineUnit.METERS)
    }

    @Test
    fun invalidRmcGllAndVtgModesPublishNoValues() {
        val badMode = parsed("GPVTG,84.4,T,,M,12.3,N,22.8,K,X")
        assertTrue(badMode.observations.isEmpty())
        assertTrue(badMode.fieldErrors.any { it.fieldName == "mode" })

        val badRmcMode = parsed("GPRMC,123519,A,4807.038,N,01131.000,E,1.0,2.0,230394,,,X")
        assertTrue(badRmcMode.observations.isEmpty())
        assertTrue(badRmcMode.fieldErrors.any { it.fieldName == "mode" })

        val badGllMode = parsed("GPGLL,4916.45,N,12311.12,W,225444,A,X")
        assertTrue(badGllMode.observations.isEmpty())
        assertTrue(badGllMode.fieldErrors.any { it.fieldName == "mode" })

        listOf("A", "D", "E", "F", "M", "P", "R", "S").forEach { legalMode ->
            assertTrue(
                parsed("GPRMC,123519,A,4807.038,N,01131.000,E,1.0,2.0,230394,,,$legalMode")
                    .observations.any { it.key == DataKey.Position },
            )
        }

        val wrongHeadingReference = parsed("IIHDT,123.4,M")
        assertTrue(wrongHeadingReference.observations.isEmpty())
        assertTrue(wrongHeadingReference.fieldErrors.any { it.fieldName == "headingReference" })

        val wrongMagneticReference = parsed("IIHDM,123.4,T")
        assertTrue(wrongMagneticReference.observations.isEmpty())
        assertTrue(wrongMagneticReference.fieldErrors.any { it.fieldName == "headingReference" })

        val incompleteVariation = parsed("GPRMC,123519,A,4807.038,N,01131.000,E,1.0,2.0,230394,3.1,,A")
        assertTrue(incompleteVariation.observations.none { it.key == DataKey.MagneticVariation })
        assertTrue(incompleteVariation.fieldErrors.any { it.fieldName == "magneticVariation" })
    }

    @Test
    fun ggaQualityZeroInvalidatesFixDependentValuesAndUnknownQualityIsRejected() {
        val noFix = invalid("GNGGA,123519,4807.038,N,01131.000,E,0,08,0.9,545.4,M,46.9,M,,")
        assertEquals(
            setOf(DataKey.Position, DataKey.Altitude),
            noFix.observations.filter { it.validity == ObservationValidity.EXPLICIT_INVALID }.map { it.key }.toSet(),
        )
        assertTrue(noFix.observations.any { it.key == DataKey.FixQuality })
        assertTrue(noFix.observations.any { it.key == DataKey.Satellites })
        assertTrue(noFix.observations.any { it.key == DataKey.HorizontalDilution })

        val impossible = parsed("GNGGA,123519,4807.038,N,01131.000,E,99,08,0.9,545.4,M,46.9,M,,")
        assertTrue(impossible.observations.none { it.key == DataKey.Position || it.key == DataKey.Altitude })
        assertTrue(impossible.fieldErrors.any { it.fieldName == "fixQuality" })
    }

    @Test
    fun sentenceSemanticInstanceSeparatesMwvRelativeAndTrue() {
        val r = parsed("IIMWV,32.0,R,6.0,N,A")
        val t = parsed("IIMWV,32.0,T,6.0,N,A")
        assertEquals(SentenceSemanticDiscriminator("MWV:R"), r.sentence.semanticDiscriminator)
        assertEquals(SentenceSemanticDiscriminator("MWV:T"), t.sentence.semanticDiscriminator)
        assertEquals(context.sender, r.sentence.origin.sender)
    }

    @Test
    fun oneSentenceSharesOneObservationGroupButSeparateFramesDoNot() {
        val r = parsed("IIMWV,32.0,R,6.0,N,A")
        assertTrue(r.observations.all { it.groupId == context.observationGroupId })
        assertEquals(1, r.observations.map { it.groupId }.toSet().size)

        val nextContext = context.copy(observationGroupId = ObservationGroupId(18))
        val next = parser.parse(NmeaChecksum.append("IIMWV,32.0,R,6.0,N,A"), nextContext)
            as NmeaParseResult.Parsed
        assertTrue(r.observations.map { it.groupId }.toSet() != next.observations.map { it.groupId }.toSet())
    }

    @Test
    fun checksumTrustSurvivesIntoEveryObservation() {
        val verified = parsed("IIMWV,32.0,R,6.0,N,A")
        assertTrue(verified.observations.all { it.checksumTrust == ChecksumTrust.VERIFIED })
        val permissive = Nmea0183Parser(ChecksumPolicy.ALLOW_MISSING)
        val unverified = permissive.parse("\$IIMWV,32.0,R,6.0,N,A", context) as NmeaParseResult.Parsed
        assertEquals(ChecksumTrust.UNVERIFIED_ALLOWED, unverified.sentence.checksumTrust)
        assertTrue(unverified.observations.all { it.checksumTrust == ChecksumTrust.UNVERIFIED_ALLOWED })

        val unverifiedInvalid = permissive.parse("\$IIMWV,32.0,R,6.0,N,V", context)
            as NmeaParseResult.ExplicitInvalid
        assertTrue(
            unverifiedInvalid.observations.all {
                it.checksumTrust == ChecksumTrust.UNVERIFIED_ALLOWED
            },
        )
    }

    @Test
    fun dptPositiveNegativeAndZeroOffsetsKeepTruthfulReferences() {
        val positive = parsed("IIDPT,5.2,1.2")
        assertDecimal(positive, DataKey.Depth(DepthReference.BELOW_SURFACE), 6.4, MarineUnit.METERS)

        val negative = parsed("IIDPT,5.2,-1.2")
        assertDecimal(negative, DataKey.Depth(DepthReference.BELOW_KEEL), 4.0, MarineUnit.METERS)

        val zero = parsed("IIDPT,5.2,0.0")
        assertEquals(
            setOf(DataKey.Depth(DepthReference.BELOW_TRANSDUCER)),
            zero.observations.map { it.key }.toSet(),
        )
    }

    @Test
    fun zdaAndSignedVariationMappingsAreExact() {
        val zda = parsed("GPZDA,201530.00,04,07,2002,00,00")
        val sourceTime = zda.observation(DataKey.SourceTime).value as MarineValue.UtcEpochMillis
        assertEquals(1_025_813_730_000L, sourceTime.value)

        val invalidZda = parsed("GPZDA,246060.00,31,02,2002,00,00")
        assertTrue(invalidZda.observations.none { it.key == DataKey.SourceTime })
        assertTrue(invalidZda.fieldErrors.any { it.fieldName == "sourceDateTime" })

        val hdgWest = parsed("IIHDG,100.0,,,10.0,W")
        assertDecimal(hdgWest, DataKey.MagneticVariation, -10.0, MarineUnit.DEGREES)
        val rmcEast = parsed("GPRMC,123519,A,4807.038,N,01131.000,E,1.0,2.0,230394,3.1,E,A")
        assertDecimal(rmcEast, DataKey.MagneticVariation, 3.1, MarineUnit.DEGREES)
    }

    @Test
    fun zdaRejectsNonFourDigitYearsWithoutThrowing() {
        listOf("02", "999999999").forEach { malformedYear ->
            val result = parsed("GPZDA,201530.00,04,07,$malformedYear,00,00")

            assertTrue(result.observations.none { it.key == DataKey.SourceTime })
            assertTrue(
                "year $malformedYear must be a typed field error",
                result.fieldErrors.any {
                    it.fieldName == "sourceDateTime" &&
                        it.reason == NmeaFieldErrorReason.INVALID_DATE_TIME
                },
            )
        }
    }

    @Test
    fun zdaRejectsSignedOrWrongWidthDayAndMonthFields() {
        listOf(
            "+4,07",
            "04,+7",
            "4,07",
            "04,7",
        ).forEach { dayAndMonth ->
            val result = parsed("GPZDA,201530.00,$dayAndMonth,2002,00,00")

            assertTrue(result.observations.none { it.key == DataKey.SourceTime })
            assertTrue(
                result.fieldErrors.any {
                    it.fieldName == "sourceDateTime" &&
                        it.reason == NmeaFieldErrorReason.INVALID_DATE_TIME
                },
            )
        }
    }

    @Test
    fun overflowingUnitConversionsBecomeTypedErrorsWithoutThrowing() {
        val maximumFinite = "1" + "0".repeat(308)

        val dbt = parsed("IIDBT,,f,,M,$maximumFinite,F")
        assertTrue(dbt.observations.none { it.key is DataKey.Depth })
        assertTrue(dbt.fieldErrors.any { it.fieldName == "depthFathoms" })

        val mwd = parsed("IIMWD,,T,,M,,N,$maximumFinite,M")
        assertTrue(mwd.observations.none { it.key is DataKey.WindSpeed })
        assertTrue(mwd.fieldErrors.any { it.fieldName == "trueWindSpeedMetresPerSecond" })

        val mwv = parsed("IIMWV,32.0,R,$maximumFinite,M,A")
        assertTrue(mwv.observations.none { it.key is DataKey.WindSpeed })
        assertTrue(mwv.fieldErrors.any { it.fieldName == "windSpeed" })
        assertTrue(mwv.observations.any { it.key is DataKey.WindAngle })
    }

    @Test
    fun unknownLegalSentenceRemainsVisible() {
        val body = "IIVHW,123.4,T,120.0,M,5.6,N,10.4,K"
        val result = parser.parse(NmeaChecksum.append(body), context)

        assertTrue(result is NmeaParseResult.Unsupported)
        result as NmeaParseResult.Unsupported
        assertEquals("II", result.sentence.origin.talker)
        assertEquals("VHW", result.sentence.formatter)
        assertEquals(body, result.sentence.body)
    }

    @Test
    fun checksumAndEnvelopeFailuresAreTypedAndNeverExposeObservations() {
        val missing = parser.parse("\$GPRMC,123519,A", context)
        assertTrue(missing is NmeaParseResult.ChecksumFailure)

        val bad = parser.parse("\$GPRMC,123519,A*00", context)
        assertTrue(bad is NmeaParseResult.ChecksumFailure)

        val malformed = parser.parse(NmeaChecksum.append("TOO-SHORT,1,2"), context)
        assertTrue(malformed is NmeaParseResult.Malformed)
    }

    @Test
    fun boundedPrintableFieldFuzzNeverEscapesTheTypedResultContract() {
        val random = Random(0x59_4f_4b_55_4c_49L)
        val formatters = listOf(
            "RMC", "GGA", "GLL", "VTG", "ZDA", "HDG",
            "HDM", "HDT", "DPT", "DBT", "MWD", "MWV",
        )
        val edgeTokens = listOf(
            "", "+1", "-1", ".", "1.", ".1", "1e2", "1E309",
            "NaN", "Infinity", "999999999", "0000", "360", "-0.1",
        )

        formatters.forEachIndexed { formatterIndex, formatter ->
            repeat(250) { caseIndex ->
                val fieldCount = random.nextInt(18)
                val fields = List(fieldCount) {
                    if (random.nextInt(4) == 0) {
                        edgeTokens[random.nextInt(edgeTokens.size)]
                    } else {
                        buildString {
                            repeat(random.nextInt(13)) {
                                var character: Char
                                do {
                                    character = (0x20 + random.nextInt(0x7f - 0x20)).toChar()
                                } while (character == '*' || character == ',')
                                append(character)
                            }
                        }
                    }
                }
                val body = buildString {
                    append("GP")
                    append(formatter)
                    fields.forEach {
                        append(',')
                        append(it)
                    }
                }
                val fuzzContext = context.copy(
                    observationGroupId = ObservationGroupId(
                        formatterIndex.toLong() * 250L + caseIndex.toLong(),
                    ),
                )

                assertNotNull(parser.parse(NmeaChecksum.append(body), fuzzContext))
            }
        }
    }

    private fun parsed(body: String): NmeaParseResult.Parsed {
        val result = parser.parse(NmeaChecksum.append(body), context)
        assertTrue("Expected Parsed but got $result", result is NmeaParseResult.Parsed)
        return result as NmeaParseResult.Parsed
    }

    private fun invalid(body: String): NmeaParseResult.ExplicitInvalid {
        val result = parser.parse(NmeaChecksum.append(body), context)
        assertTrue("Expected ExplicitInvalid but got $result", result is NmeaParseResult.ExplicitInvalid)
        return result as NmeaParseResult.ExplicitInvalid
    }

    private fun NmeaParseResult.Parsed.observation(key: DataKey): MarineObservation =
        observations.single { it.key == key }

    private fun assertDecimal(
        result: NmeaParseResult.Parsed,
        key: DataKey,
        expected: Double,
        unit: MarineUnit,
    ) {
        val value = result.observation(key).value as MarineValue.Decimal
        assertEquals(expected, value.value, 0.000_001)
        assertEquals(unit, value.unit)
    }
}
