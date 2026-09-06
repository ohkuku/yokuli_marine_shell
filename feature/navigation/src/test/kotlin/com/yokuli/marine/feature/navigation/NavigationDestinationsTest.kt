package com.yokuli.marine.feature.navigation

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.MarineTileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationDestinationsTest {
    @Test
    fun `route destination round trips UTF-8 without exposing the route id in a token`() {
        val destination = NavigationDestinations.route("航线 / harbour")

        assertEquals(NavigationDestination.Route("航线 / harbour"), NavigationDestinations.parse(destination))
        assertEquals(false, destination.value.contains("harbour"))
    }

    @Test
    fun `malformed and non canonical route destinations stay unresolved`() {
        assertNull(NavigationDestinations.parse(LaunchToken("navigation.route.+1")))
        assertNull(NavigationDestinations.parse(LaunchToken("navigation.route.ff")))
        assertNull(NavigationDestinations.parse(LaunchToken("navigation.unknown")))
    }

    @Test
    fun `installed Navigation entry owns the three WP tile sizes`() {
        assertEquals(MarineTileSize.entries.toSet(), NavigationShellContribution.entries.single().supportedSizes.toSet())
    }
}
