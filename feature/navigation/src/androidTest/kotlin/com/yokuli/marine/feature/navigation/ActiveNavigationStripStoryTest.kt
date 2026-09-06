package com.yokuli.marine.feature.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationSession
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationAdvancePolicy
import com.yokuli.marine.navigation.domain.NavigationInputStatus
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.marine.navigation.domain.NavigationSolution
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActiveNavigationStripStoryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun realSessionShowsLiveSolutionAndDispatchesTypedControls() {
        var command: ActiveNavigationCommand? = null
        compose.setContent {
            YokuliTheme(WpThemeSpec()) { ActiveNavigationStrip(snapshot(), { command = it }) }
        }

        compose.onNodeWithTag("active-navigation-strip").assertIsDisplayed()
        compose.onNodeWithTag("active-navigation-primary").assertIsDisplayed()
        compose.onNodeWithTag("active-navigation-stop").performClick()
        assertEquals(ActiveNavigationCommand.Stop, command)
    }

    private fun snapshot(): ActiveNavigationSnapshot {
        val route = RoutePlan(
            "route", 1, "Passage",
            listOf(
                RoutePoint("A", NavigationPosition(-36.85, 174.76)),
                RoutePoint("B", NavigationPosition(-36.80, 174.86)),
            ),
        )
        val session = ActiveNavigationSession(
            route.id, route.revision, 1_000, 0, 50.0, NavigationAdvancePolicy.MANUAL, NavigationSessionState.ACTIVE,
        )
        return ActiveNavigationSnapshot(
            revision = 2,
            session = session,
            route = route,
            solution = NavigationSolution(
                route.id, route.revision, 0, "A", "B", NavigationInputStatus.LIVE,
                1.2, 42.0, -0.03, 600_000, 0.4, 0.2, "source", "speed", 123,
            ),
        )
    }
}
