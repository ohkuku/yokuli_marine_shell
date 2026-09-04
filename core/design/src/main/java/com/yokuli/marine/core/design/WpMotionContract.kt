package com.yokuli.marine.core.design

enum class WpNavigationIntent {
    SIBLING_FORWARD,
    SIBLING_BACK,
    DEEPER_FORWARD,
    DEEPER_BACK,
    TRANSIENT,
    SAFETY_CRITICAL,
}

enum class WpMotionFamily { NONE, SLIDE, TURNSTILE, SWIVEL, FADE }

data class WpMotionPlan(
    val family: WpMotionFamily,
    val durationMillis: Int,
    val initialRotationXDegrees: Float = 0f,
    val initialRotationYDegrees: Float = 0f,
    val initialTranslationXFraction: Float = 0f,
    val transformOriginX: Float = .5f,
)

/** Timings copied from the human-reviewed Stage 2.5 emulator recording. */
data class WpMotionTimings(
    val pageSettleMillis: Int = 700,
    val appOpenMillis: Int = 1_000,
    val backReturnMillis: Int = 750,
    val transientMillis: Int = 220,
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
        intent: WpNavigationIntent,
        reducedMotion: Boolean = false,
        timings: WpMotionTimings = WpMotionTimings(),
    ): WpMotionPlan {
        if (intent == WpNavigationIntent.SAFETY_CRITICAL) {
            return WpMotionPlan(family = WpMotionFamily.NONE, durationMillis = 0)
        }
        if (reducedMotion) {
            return WpMotionPlan(family = WpMotionFamily.FADE, durationMillis = 120)
        }
        return when (intent) {
            WpNavigationIntent.SIBLING_FORWARD -> WpMotionPlan(
                family = WpMotionFamily.SLIDE,
                durationMillis = timings.pageSettleMillis,
                initialTranslationXFraction = 1f,
            )
            WpNavigationIntent.SIBLING_BACK -> WpMotionPlan(
                family = WpMotionFamily.SLIDE,
                durationMillis = timings.pageSettleMillis,
                initialTranslationXFraction = -1f,
            )
            WpNavigationIntent.DEEPER_FORWARD -> WpMotionPlan(
                family = WpMotionFamily.TURNSTILE,
                durationMillis = timings.appOpenMillis,
                initialRotationYDegrees = -22f,
                initialTranslationXFraction = .12f,
                transformOriginX = 0f,
            )
            WpNavigationIntent.DEEPER_BACK -> WpMotionPlan(
                family = WpMotionFamily.TURNSTILE,
                durationMillis = timings.backReturnMillis,
                initialRotationYDegrees = 22f,
                initialTranslationXFraction = -.12f,
                transformOriginX = 1f,
            )
            WpNavigationIntent.TRANSIENT -> WpMotionPlan(
                family = WpMotionFamily.SWIVEL,
                durationMillis = timings.transientMillis,
                initialRotationXDegrees = 14f,
            )
            WpNavigationIntent.SAFETY_CRITICAL -> error("Handled above")
        }
    }
}
