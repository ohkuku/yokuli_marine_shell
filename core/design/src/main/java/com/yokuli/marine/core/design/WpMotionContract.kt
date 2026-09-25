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

enum class WpMotionFamily { NONE, TURNSTILE, SWIVEL, FADE, SLIDE, CONTINUUM }

enum class WpMotionEvidence {
    DERIVED_FROM_REVIEWED_SAMPLES,
    DERIVED_UNVERIFIED,
    REDUCED_MOTION,
    NOT_APPLICABLE,
    MDL2_ADAPTATION,
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
    val initialTranslationYDp: Float = 0f,
    val initialScale: Float = 1f,
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

/** W10M 控件配合用户选择保留的经典 Shell 翻转；内部子页仍使用紧凑转场。 */
data class WpMotionTimings(
    val pageSettleVisibleWindowMillis: Int = W10MobileMotion.PageMillis,
    val appOpenVisibleWindowMillis: Int = 520,
    val backReturnVisibleWindowMillis: Int = 440,
    val derivedModuleTransitionMillis: Int = W10MobileMotion.PageMillis,
    val derivedSearchTransitionMillis: Int = W10MobileMotion.ContentMillis,
    val derivedTransientMillis: Int = W10MobileMotion.OverlayMillis,
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
        maximumDegrees: Float = 0f,
    ): WpPressPlan {
        val progress = pressProgress.coerceIn(0f, 1f)
        if (progress == 0f) return WpPressPlan(0f, 0f, 1f)
        val horizontal = normalizedX.coerceIn(0f, 1f) - .5f
        val vertical = normalizedY.coerceIn(0f, 1f) - .5f
        // 默认是 MDL2 平面按压；旧磁贴显式要求的触点倾斜也不能恢复 3–5 度的大透视。
        val tilt = maximumDegrees.coerceIn(0f, 1.5f)
        return WpPressPlan(
            rotationXDegrees = -vertical * tilt * 2f * progress,
            rotationYDegrees = horizontal * tilt * 2f * progress,
            scale = 1f - .015f * progress,
        )
    }
}

object WpMotionPolicy {
    fun resolve(transition: WpSurfaceTransitionKind, reducedMotion: Boolean = false, timings: WpMotionTimings = WpMotionTimings()): WpMotionPlan {
        if (transition in setOf(WpSurfaceTransitionKind.SAFETY_CRITICAL, WpSurfaceTransitionKind.NONE,
                WpSurfaceTransitionKind.PAGER_FORWARD, WpSurfaceTransitionKind.PAGER_BACK)) return none()
        if (reducedMotion) return WpMotionPlan(WpMotionFamily.FADE,
            contentExitMillis = timings.reducedMotionMillis / 2, targetEntranceMillis = timings.reducedMotionMillis,
            evidence = WpMotionEvidence.REDUCED_MOTION)
        return when (transition) {
            WpSurfaceTransitionKind.DESKTOP_TO_MODULE -> turnstile(-22f, .12f, 0f, timings.appOpenVisibleWindowMillis)
            WpSurfaceTransitionKind.MODULE_LIST_TO_MODULE, WpSurfaceTransitionKind.SEARCH_TO_MODULE,
            WpSurfaceTransitionKind.TASK_ACTIVATE -> turnstile(-18f, .08f, 0f, 360)
            WpSurfaceTransitionKind.MODULE_ROUTE_FORWARD -> slide(1f, timings.derivedModuleTransitionMillis)
            WpSurfaceTransitionKind.MODULE_ROUTE_BACK -> slide(-1f, timings.derivedModuleTransitionMillis)
            WpSurfaceTransitionKind.MODULE_TO_DESKTOP -> turnstile(22f, -.12f, 1f, timings.backReturnVisibleWindowMillis)
            WpSurfaceTransitionKind.SEARCH_PRESENT, WpSurfaceTransitionKind.RECENTS_PRESENT -> WpMotionPlan(WpMotionFamily.SLIDE,
                contentExitMillis = 80, targetEntranceMillis = timings.derivedTransientMillis.coerceIn(100, W10MobileMotion.OverlayMillis),
                initialTranslationYDp = 16f, evidence = WpMotionEvidence.MDL2_ADAPTATION)
            WpSurfaceTransitionKind.SEARCH_DISMISS, WpSurfaceTransitionKind.RECENTS_DISMISS -> WpMotionPlan(WpMotionFamily.FADE,
                contentExitMillis = 100, targetEntranceMillis = 120, evidence = WpMotionEvidence.MDL2_ADAPTATION)
            WpSurfaceTransitionKind.NONE, WpSurfaceTransitionKind.PAGER_FORWARD, WpSurfaceTransitionKind.PAGER_BACK,
            WpSurfaceTransitionKind.SAFETY_CRITICAL -> none()
        }
    }
    private fun turnstile(rotation: Float, shift: Float, pivot: Float, duration: Int) = WpMotionPlan(
        WpMotionFamily.TURNSTILE, contentExitMillis = duration / 4,
        targetEntranceMillis = duration * 3 / 4, settleMillis = duration / 4,
        initialRotationYDegrees = rotation, initialTranslationXFraction = shift, transformOriginX = pivot,
        evidence = WpMotionEvidence.DERIVED_FROM_REVIEWED_SAMPLES)
    private fun slide(direction: Float, duration: Int) = WpMotionPlan(WpMotionFamily.SLIDE,
        contentExitMillis = 100, targetEntranceMillis = duration.coerceIn(120, W10MobileMotion.PageMillis),
        initialTranslationXFraction = .045f * direction, evidence = WpMotionEvidence.MDL2_ADAPTATION)
    private fun none() = WpMotionPlan(WpMotionFamily.NONE, evidence = WpMotionEvidence.NOT_APPLICABLE)
}
