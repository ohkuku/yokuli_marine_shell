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

object WpMotionPolicy {
    fun resolve(intent: WpNavigationIntent, reducedMotion: Boolean = false): WpMotionPlan {
        if (intent == WpNavigationIntent.SAFETY_CRITICAL) {
            return WpMotionPlan(family = WpMotionFamily.NONE, durationMillis = 0)
        }
        if (reducedMotion) {
            return WpMotionPlan(family = WpMotionFamily.FADE, durationMillis = 120)
        }
        return when (intent) {
            WpNavigationIntent.SIBLING_FORWARD -> WpMotionPlan(
                family = WpMotionFamily.SLIDE,
                durationMillis = 245,
                initialTranslationXFraction = 1f,
            )
            WpNavigationIntent.SIBLING_BACK -> WpMotionPlan(
                family = WpMotionFamily.SLIDE,
                durationMillis = 245,
                initialTranslationXFraction = -1f,
            )
            WpNavigationIntent.DEEPER_FORWARD -> WpMotionPlan(
                family = WpMotionFamily.TURNSTILE,
                durationMillis = 260,
                initialRotationYDegrees = -22f,
                initialTranslationXFraction = .12f,
                transformOriginX = 0f,
            )
            WpNavigationIntent.DEEPER_BACK -> WpMotionPlan(
                family = WpMotionFamily.TURNSTILE,
                durationMillis = 235,
                initialRotationYDegrees = 22f,
                initialTranslationXFraction = -.12f,
                transformOriginX = 1f,
            )
            WpNavigationIntent.TRANSIENT -> WpMotionPlan(
                family = WpMotionFamily.SWIVEL,
                durationMillis = 220,
                initialRotationXDegrees = 14f,
            )
            WpNavigationIntent.SAFETY_CRITICAL -> error("Handled above")
        }
    }
}
