package com.yokuli.marine.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpMotionPolicyTest {
    @Test
    fun siblingSurfacesUseDirectionalHorizontalSlides() {
        val forward = WpMotionPolicy.resolve(WpNavigationIntent.SIBLING_FORWARD)
        val back = WpMotionPolicy.resolve(WpNavigationIntent.SIBLING_BACK)

        assertEquals(WpMotionFamily.SLIDE, forward.family)
        assertTrue(forward.initialTranslationXFraction > 0f)
        assertEquals(-forward.initialTranslationXFraction, back.initialTranslationXFraction, 0f)
    }

    @Test
    fun deeperNavigationUsesPerspectiveTurnstileWithInverseBackMotion() {
        val forward = WpMotionPolicy.resolve(WpNavigationIntent.DEEPER_FORWARD)
        val back = WpMotionPolicy.resolve(WpNavigationIntent.DEEPER_BACK)

        assertEquals(WpMotionFamily.TURNSTILE, forward.family)
        assertTrue(forward.initialRotationYDegrees < 0f)
        assertEquals(-forward.initialRotationYDegrees, back.initialRotationYDegrees, 0f)
        assertEquals(0f, forward.transformOriginX, 0f)
        assertEquals(1f, back.transformOriginX, 0f)
    }

    @Test
    fun transientUiUsesSwivel() {
        val plan = WpMotionPolicy.resolve(WpNavigationIntent.TRANSIENT)

        assertEquals(WpMotionFamily.SWIVEL, plan.family)
        assertTrue(plan.initialRotationXDegrees > 0f)
    }

    @Test
    fun reducedMotionPreservesContextWithoutPerspective() {
        val plan = WpMotionPolicy.resolve(WpNavigationIntent.DEEPER_FORWARD, reducedMotion = true)

        assertEquals(WpMotionFamily.FADE, plan.family)
        assertEquals(0f, plan.initialRotationXDegrees, 0f)
        assertEquals(0f, plan.initialRotationYDegrees, 0f)
        assertTrue(plan.durationMillis in 1..150)
    }

    @Test
    fun safetyCriticalPresentationIsImmediate() {
        val plan = WpMotionPolicy.resolve(WpNavigationIntent.SAFETY_CRITICAL)

        assertEquals(WpMotionFamily.NONE, plan.family)
        assertEquals(0, plan.durationMillis)
    }

    @Test
    fun centeredPressDepressesWithoutTilting() {
        val plan = WpPressPolicy.resolve(.5f, .5f, pressProgress = 1f)

        assertEquals(0f, plan.rotationXDegrees, 0f)
        assertEquals(0f, plan.rotationYDegrees, 0f)
        assertEquals(.975f, plan.scale, 0f)
    }

    @Test
    fun cornerPressTiltsTowardTheFingerWithinFiveDegrees() {
        val plan = WpPressPolicy.resolve(0f, 0f, pressProgress = 1f)

        assertEquals(5f, plan.rotationXDegrees, 0f)
        assertEquals(-5f, plan.rotationYDegrees, 0f)
    }

    @Test
    fun pressCoordinatesAndProgressAreClamped() {
        val plan = WpPressPolicy.resolve(-4f, 7f, pressProgress = 3f)

        assertTrue(plan.rotationXDegrees in -5f..5f)
        assertTrue(plan.rotationYDegrees in -5f..5f)
        assertTrue(plan.scale >= .975f)
    }

    @Test
    fun releasedPlaneReturnsExactlyToRest() {
        val plan = WpPressPolicy.resolve(0f, 1f, pressProgress = 0f)

        assertEquals(WpPressPlan(0f, 0f, 1f), plan)
    }
}
