package com.yokuli.marine.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpMotionPolicyTest {
    @Test
    fun pagerTransitionsAreOwnedByThePagerNotTheOuterSurfaceHost() {
        val forward = WpMotionPolicy.resolve(WpSurfaceTransitionKind.PAGER_FORWARD)
        val back = WpMotionPolicy.resolve(WpSurfaceTransitionKind.PAGER_BACK)

        assertEquals(WpMotionFamily.NONE, forward.family)
        assertEquals(WpMotionFamily.NONE, back.family)
        assertEquals(0, forward.durationMillis)
    }

    @Test
    fun desktopModuleMotionDecomposesOnlyItsApplicableReviewedVisualWindows() {
        val forward = WpMotionPolicy.resolve(WpSurfaceTransitionKind.DESKTOP_TO_MODULE)
        val back = WpMotionPolicy.resolve(WpSurfaceTransitionKind.MODULE_TO_DESKTOP)

        assertEquals(WpMotionFamily.TURNSTILE, forward.family)
        assertTrue(forward.initialRotationYDegrees < 0f)
        assertEquals(-forward.initialRotationYDegrees, back.initialRotationYDegrees, 0f)
        assertTrue(forward.contentExitMillis < forward.durationMillis)
        assertTrue(forward.targetEntranceMillis < forward.durationMillis)
        assertTrue(forward.settleMillis < forward.durationMillis)
        assertEquals(1_000, forward.durationMillis)
        assertEquals(750, back.durationMillis)
        assertEquals(WpMotionEvidence.DERIVED_FROM_REVIEWED_SAMPLES, forward.evidence)
    }

    @Test
    fun unobservedSearchToModuleUsesASeparateDerivedPlan() {
        val plan = WpMotionPolicy.resolve(WpSurfaceTransitionKind.SEARCH_TO_MODULE)

        assertEquals(WpMotionFamily.TURNSTILE, plan.family)
        assertTrue(plan.durationMillis < 1_000)
        assertEquals(WpMotionEvidence.DERIVED_UNVERIFIED, plan.evidence)
    }

    @Test
    fun reducedMotionPreservesContextWithoutPerspective() {
        val plan = WpMotionPolicy.resolve(WpSurfaceTransitionKind.DESKTOP_TO_MODULE, reducedMotion = true)

        assertEquals(WpMotionFamily.FADE, plan.family)
        assertEquals(0f, plan.initialRotationXDegrees, 0f)
        assertEquals(0f, plan.initialRotationYDegrees, 0f)
        assertTrue(plan.durationMillis in 1..150)
    }

    @Test
    fun safetyCriticalPresentationIsImmediate() {
        val plan = WpMotionPolicy.resolve(WpSurfaceTransitionKind.SAFETY_CRITICAL)

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
