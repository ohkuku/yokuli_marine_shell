package com.yokuli.marine.feature.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationWorkspaceStoryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `overview routes GPX and active are real Navigation owned workspaces`() {
        val actions = mutableListOf<NavigationUiAction>()
        compose.setContent {
            YokuliTheme(WpThemeSpec()) {
                NavigationWorkspace(state(), actions::add) { Box(Modifier.testTag("real-gpx-workflow")) }
            }
        }

        compose.onNodeWithTag("navigation-overview").assertIsDisplayed()
        compose.onNodeWithTag(NavigationTestTags.section(NavigationSection.ROUTES)).performClick()
        assertEquals(NavigationUiAction.Navigate(NavigationSection.ROUTES), actions.last())
        compose.onNodeWithTag(NavigationTestTags.section(NavigationSection.GPX)).performClick()
        assertEquals(NavigationUiAction.Navigate(NavigationSection.GPX), actions.last())
        compose.onNodeWithTag(NavigationTestTags.section(NavigationSection.ACTIVE)).performClick()
        assertEquals(NavigationUiAction.Navigate(NavigationSection.ACTIVE), actions.last())
    }

    @Test
    fun `route preview exposes geometry legs explicit start and content handoff`() {
        val route = route()
        val actions = mutableListOf<NavigationUiAction>()
        compose.setContent {
            YokuliTheme(WpThemeSpec()) {
                NavigationWorkspace(
                    state(section = NavigationSection.ROUTES, page = NavigationPage.RouteDetail(route.id)),
                    actions::add,
                ) {}
            }
        }

        compose.onNodeWithTag("navigation-route-sketch").assertIsDisplayed()
        compose.onNodeWithTag("navigation-route-start-${route.id}").performClick()
        compose.onNodeWithTag("navigation-route-chart-${route.id}").performClick()
        assertEquals(NavigationUiAction.StartRoute(route.id, route.revision), actions[0])
        assertEquals(NavigationUiAction.ShowRouteInChart(route.id), actions[1])
    }

    @Test
    fun `stale route deep link explains unavailable content without starting navigation`() {
        val actions = mutableListOf<NavigationUiAction>()
        compose.setContent {
            YokuliTheme(WpThemeSpec()) {
                NavigationWorkspace(state(page = NavigationPage.RouteDetail("deleted")), actions::add) {}
            }
        }

        compose.onNodeWithTag("navigation-route-missing").assertIsDisplayed()
        compose.onNodeWithTag("navigation-route-missing-back").performClick()
        assertEquals(NavigationUiAction.Navigate(NavigationSection.ROUTES), actions.single())
    }

    private fun state(
        section: NavigationSection = NavigationSection.OVERVIEW,
        page: NavigationPage = NavigationPage.Root,
    ) = NavigationUiState(section = section, page = page, library = NavigationLibrary(revision = 1, routePlans = listOf(route())), loading = false)

    private fun route() = RoutePlan(
        "passage", 2, "Harbour passage",
        listOf(
            RoutePoint("a", NavigationPosition(-36.84, 174.75)),
            RoutePoint("b", NavigationPosition(-36.79, 174.89)),
        ),
        plannedSpeedKnots = 6.0,
    )
}
