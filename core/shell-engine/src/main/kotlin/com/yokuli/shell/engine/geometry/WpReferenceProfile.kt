package com.yokuli.shell.engine.geometry

@JvmInline
value class ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProfileId must not be blank" }
    }
}

enum class ReferenceEvidenceState { HUMAN_REVIEWED, NOT_OBSERVED, DERIVED_UNVERIFIED_HARDWARE }

data class OuterInsetPolicy(
    val referenceStartPx: Int,
    val referenceEndPx: Int,
    val referenceTileTopPx: Int,
)

data class StatusStripProfile(val referenceHeightPx: Int)

data class WpTypographyProfile(
    val measuredTitleBaselinePx: Int?,
    val evidenceState: ReferenceEvidenceState,
)

data class TileContentProfile(
    val measuredGlyphLeftPx: Int?,
    val measuredGlyphTopPx: Int?,
    val measuredGlyphWidthPx: Int?,
    val measuredGlyphHeightPx: Int?,
    val evidenceState: ReferenceEvidenceState,
)

data class WpMotionProfile(
    val measuredPageSettleMillis: Int?,
    val measuredAppOpenMillis: Int?,
    val measuredBackReturnMillis: Int?,
    val measuredLiveTileCycleMillis: Int?,
    val evidenceState: ReferenceEvidenceState,
)

data class WpInteractionProfile(
    val measuredLongPressMillis: Int?,
    val measuredPressScale: Float?,
    val measuredFastFlingThresholdPxPerSecond: Float?,
    val evidenceState: ReferenceEvidenceState,
)

data class WpLayoutPolicy(
    val allowIntentionalWhitespace: Boolean,
    val maximumPinnedInstancesPerEntry: Int,
)

data class WpReferenceProfile(
    val id: ProfileId,
    val referenceRevision: Int,
    val reviewedMeasurementHash: String,
    val evidenceState: ReferenceEvidenceState,
    val referenceWidthPx: Int,
    val referenceHeightPx: Int,
    val columnCount: Int,
    val outerInsetPolicy: OuterInsetPolicy,
    val statusStrip: StatusStripProfile,
    val typography: WpTypographyProfile,
    val tileContent: TileContentProfile,
    val motion: WpMotionProfile,
    val interaction: WpInteractionProfile,
    val layoutPolicy: WpLayoutPolicy,
    val outerInsetPx: Int,
    val seamPx: Int,
    val smallCellPx: Int,
    val mediumTilePx: Int,
    val wideTileWidthPx: Int,
) {
    init {
        require(referenceRevision > 0)
        require(reviewedMeasurementHash.matches(Regex("[0-9a-f]{64}")))
        require(referenceWidthPx > 0 && referenceHeightPx > 0)
        require(columnCount == 4)
        require(outerInsetPx > 0 && seamPx > 0 && smallCellPx > 0)
        require(mediumTilePx == smallCellPx * 2 + seamPx)
        require(wideTileWidthPx == smallCellPx * 4 + seamPx * 3)
    }
}

/**
 * 中文：数值只来自 Stage 2.5 人工批准的 revision 1 测量；未观察交互保持 null。
 * English: Values come only from the human-approved Stage 2.5 revision 1 measurements; unseen interactions stay null.
 */
object WpReferenceProfiles {
    const val REVIEWED_MEASUREMENT_HASH =
        "af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5"

    val PHONE_PORTRAIT_4COL = WpReferenceProfile(
        id = ProfileId("PHONE_PORTRAIT_4COL"),
        referenceRevision = 1,
        reviewedMeasurementHash = REVIEWED_MEASUREMENT_HASH,
        evidenceState = ReferenceEvidenceState.HUMAN_REVIEWED,
        referenceWidthPx = 480,
        referenceHeightPx = 800,
        columnCount = 4,
        outerInsetPolicy = OuterInsetPolicy(
            referenceStartPx = 24,
            referenceEndPx = 24,
            referenceTileTopPx = 57,
        ),
        statusStrip = StatusStripProfile(referenceHeightPx = 32),
        typography = WpTypographyProfile(
            measuredTitleBaselinePx = 158,
            evidenceState = ReferenceEvidenceState.HUMAN_REVIEWED,
        ),
        tileContent = TileContentProfile(
            measuredGlyphLeftPx = 104,
            measuredGlyphTopPx = 123,
            measuredGlyphWidthPx = 47,
            measuredGlyphHeightPx = 79,
            evidenceState = ReferenceEvidenceState.HUMAN_REVIEWED,
        ),
        motion = WpMotionProfile(
            measuredPageSettleMillis = 700,
            measuredAppOpenMillis = 1000,
            measuredBackReturnMillis = 750,
            measuredLiveTileCycleMillis = 1250,
            evidenceState = ReferenceEvidenceState.HUMAN_REVIEWED,
        ),
        interaction = WpInteractionProfile(
            measuredLongPressMillis = null,
            measuredPressScale = null,
            measuredFastFlingThresholdPxPerSecond = null,
            evidenceState = ReferenceEvidenceState.NOT_OBSERVED,
        ),
        layoutPolicy = WpLayoutPolicy(
            allowIntentionalWhitespace = true,
            maximumPinnedInstancesPerEntry = 1,
        ),
        outerInsetPx = 24,
        seamPx = 12,
        smallCellPx = 99,
        mediumTilePx = 210,
        wideTileWidthPx = 432,
    )

    val SQUARE_4COL = PHONE_PORTRAIT_4COL.copy(
        id = ProfileId("SQUARE_4COL"),
        evidenceState = ReferenceEvidenceState.DERIVED_UNVERIFIED_HARDWARE,
    )

    fun forViewport(viewport: StartViewport): WpReferenceProfile =
        if (viewport.widthPx == viewport.heightPx) SQUARE_4COL else PHONE_PORTRAIT_4COL

    fun require(id: ProfileId): WpReferenceProfile = when (id) {
        PHONE_PORTRAIT_4COL.id -> PHONE_PORTRAIT_4COL
        SQUARE_4COL.id -> SQUARE_4COL
        else -> error("Unknown WP reference profile: ${id.value}")
    }
}
