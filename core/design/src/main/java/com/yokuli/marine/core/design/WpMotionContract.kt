package com.yokuli.marine.core.design

import kotlin.math.max

/** Exact shell transition role. The renderer must not collapse these back into depth-only intents. */
enum class WpSurfaceTransitionKind {
    NONE,
    PAGER_FORWARD,
    PAGER_BACK,
    DESKTOP_TO_MODULE,
    MODULE_LIST_TO_MODULE,
    SEARCH_TO_MODULE,
    MODULE_ROUTE_FORWARD,
    MODULE_ROUTE_BACK,
    MODULE_TO_DESKTOP,
    SEARCH_PRESENT,
    SEARCH_DISMISS,
    RECENTS_PRESENT,
    RECENTS_DISMISS,
    TASK_ACTIVATE,
    SAFETY_CRITICAL,
}

enum class WpMotionFamily { NONE, TURNSTILE, SWIVEL, FADE }

enum class WpMotionEvidence {
    DERIVED_FROM_REVIEWED_SAMPLES,
    DERIVED_UNVERIFIED,
    REDUCED_MOTION,
    NOT_APPLICABLE,
}

data class WpMotionPlan(
    val family: WpMotionFamily,
    val contentExitMillis: Int = 0,
    val targetEntranceDelayMillis: Int = 0,
    val targetEntranceMillis: Int = 0,
    val settleMillis: Int = 0,
    val initialRotationXDegrees: Float = 0f,
    val initialRotationYDegrees: Float = 0f,
    val initialTranslationXFraction: Float = 0f,
    val transformOriginX: Float = .5f,
    val evidence: WpMotionEvidence,
) {
    val durationMillis: Int = max(
        contentExitMillis,
        targetEntranceDelayMillis + targetEntranceMillis + settleMillis,
    )

    init {
        require(contentExitMillis >= 0)
        require(targetEntranceDelayMillis >= 0)
        require(targetEntranceMillis >= 0)
        require(settleMillis >= 0)
    }
}

/**
 * Approved values are visible recording windows, not input latency or universal animation constants.
 * The derived values cover product-only transitions that the Stage 2.5 video did not observe.
 */
data class WpMotionTimings(
    val pageSettleVisibleWindowMillis: Int = 700,
    val appOpenVisibleWindowMillis: Int = 1_000,
    val backReturnVisibleWindowMillis: Int = 750,
    val derivedModuleTransitionMillis: Int = 480,
    val derivedSearchTransitionMillis: Int = 360,
    val derivedTransientMillis: Int = 220,
    val reducedMotionMillis: Int = 120,
)

data class WpPressPlan(
    val rotationXDegrees: Float,
    val rotationYDegrees: Float,
    val scale: Float,
)

object WpPressPolicy {
    fun resolve(
        normalizedX: Float,
        normalizedY: Float,
        pressProgress: Float,
        maximumDegrees: Float = 5f,
    ): WpPressPlan {
        val progress = pressProgress.coerceIn(0f, 1f)
        if (progress == 0f) return WpPressPlan(0f, 0f, 1f)
        val horizontal = normalizedX.coerceIn(0f, 1f) - .5f
        val vertical = normalizedY.coerceIn(0f, 1f) - .5f
        return WpPressPlan(
            rotationXDegrees = -vertical * maximumDegrees * 2f * progress,
            rotationYDegrees = horizontal * maximumDegrees * 2f * progress,
            scale = 1f - .025f * progress,
        )
    }
}

object WpMotionPolicy {
    fun resolve(
        transition: WpSurfaceTransitionKind,
        reducedMotion: Boolean = false,
        timings: WpMotionTimings = WpMotionTimings(),
    ): WpMotionPlan {
        if (
            transition == WpSurfaceTransitionKind.SAFETY_CRITICAL ||
            transition == WpSurfaceTransitionKind.NONE ||
            transition == WpSurfaceTransitionKind.PAGER_FORWARD ||
            transition == WpSurfaceTransitionKind.PAGER_BACK
        ) return none()
        if (reducedMotion) {
            return WpMotionPlan(
                family = WpMotionFamily.FADE,
                contentExitMillis = timings.reducedMotionMillis / 2,
                targetEntranceMillis = timings.reducedMotionMillis,
                evidence = WpMotionEvidence.REDUCED_MOTION,
            )
        }
        return when (transition) {
            WpSurfaceTransitionKind.DESKTOP_TO_MODULE -> reviewedAppOpen(timings.appOpenVisibleWindowMillis)
            WpSurfaceTransitionKind.MODULE_TO_DESKTOP -> reviewedBackReturn(timings.backReturnVisibleWindowMillis)
            WpSurfaceTransitionKind.MODULE_LIST_TO_MODULE -> forward(
                timings.derivedModuleTransitionMillis,
                WpMotionEvidence.DERIVED_UNVERIFIED,
            )
            WpSurfaceTransitionKind.SEARCH_TO_MODULE -> forward(
                timings.derivedSearchTransitionMillis,
                WpMotionEvidence.DERIVED_UNVERIFIED,
                rotation = -10f,
            )
            WpSurfaceTransitionKind.MODULE_ROUTE_FORWARD -> forward(
                timings.derivedSearchTransitionMillis,
                WpMotionEvidence.DERIVED_UNVERIFIED,
            )
            WpSurfaceTransitionKind.MODULE_ROUTE_BACK -> backward(
                timings.derivedSearchTransitionMillis,
                WpMotionEvidence.DERIVED_UNVERIFIED,
            )
            WpSurfaceTransitionKind.TASK_ACTIVATE -> forward(
                timings.derivedModuleTransitionMillis,
                WpMotionEvidence.DERIVED_UNVERIFIED,
            )
            WpSurfaceTransitionKind.SEARCH_PRESENT,
            WpSurfaceTransitionKind.SEARCH_DISMISS,
            WpSurfaceTransitionKind.RECENTS_PRESENT,
            WpSurfaceTransitionKind.RECENTS_DISMISS -> WpMotionPlan(
                family = WpMotionFamily.SWIVEL,
                contentExitMillis = timings.derivedTransientMillis / 2,
                targetEntranceMillis = timings.derivedTransientMillis,
                initialRotationXDegrees = 14f,
                evidence = WpMotionEvidence.DERIVED_UNVERIFIED,
            )
            WpSurfaceTransitionKind.NONE,
            WpSurfaceTransitionKind.PAGER_FORWARD,
            WpSurfaceTransitionKind.PAGER_BACK,
            WpSurfaceTransitionKind.SAFETY_CRITICAL -> none()
        }
    }

    /** Reviewed samples: departure at 0, focused plane at 20%, target plane at 80%, stable at 100%. */
    private fun reviewedAppOpen(windowMillis: Int): WpMotionPlan = WpMotionPlan(
        family = WpMotionFamily.TURNSTILE,
        contentExitMillis = windowMillis / 5,
        targetEntranceDelayMillis = windowMillis / 5,
        targetEntranceMillis = windowMillis * 3 / 5,
        settleMillis = windowMillis / 5,
        initialRotationYDegrees = -22f,
        initialTranslationXFraction = .12f,
        transformOriginX = 0f,
        evidence = WpMotionEvidence.DERIVED_FROM_REVIEWED_SAMPLES,
    )

    /** Reviewed samples: leaving app at 0, incoming Start plane at 2/3, stable at 100%. */
    private fun reviewedBackReturn(windowMillis: Int): WpMotionPlan = WpMotionPlan(
        family = WpMotionFamily.TURNSTILE,
        contentExitMillis = windowMillis / 3,
        targetEntranceMillis = windowMillis * 2 / 3,
        settleMillis = windowMillis / 3,
        initialRotationYDegrees = 22f,
        initialTranslationXFraction = -.12f,
        transformOriginX = 1f,
        evidence = WpMotionEvidence.DERIVED_FROM_REVIEWED_SAMPLES,
    )

    private fun forward(totalMillis: Int, evidence: WpMotionEvidence, rotation: Float = -18f) = WpMotionPlan(
        family = WpMotionFamily.TURNSTILE,
        contentExitMillis = totalMillis / 3,
        targetEntranceMillis = totalMillis * 3 / 4,
        settleMillis = totalMillis / 4,
        initialRotationYDegrees = rotation,
        initialTranslationXFraction = .08f,
        transformOriginX = 0f,
        evidence = evidence,
    )

    private fun backward(totalMillis: Int, evidence: WpMotionEvidence) = WpMotionPlan(
        family = WpMotionFamily.TURNSTILE,
        contentExitMillis = totalMillis / 3,
        targetEntranceMillis = totalMillis * 3 / 4,
        settleMillis = totalMillis / 4,
        initialRotationYDegrees = 18f,
        initialTranslationXFraction = -.08f,
        transformOriginX = 1f,
        evidence = evidence,
    )

    private fun none() = WpMotionPlan(
        family = WpMotionFamily.NONE,
        evidence = WpMotionEvidence.NOT_APPLICABLE,
    )
}
