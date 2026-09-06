package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppPreferenceContractTest {
    @Test
    fun `registry accepts only installed owners and globally unique typed keys`() {
        val chart = LauncherAppId("chart")
        val key = AppPreferenceKey("chart.tile.mode")
        val contribution = AppPreferenceContribution(
            chart,
            listOf(AppPreferenceDefinition.Choice(key, listOf("auto", "static"), AppPreferenceValue.Choice("auto"))),
        )

        val registry = AppPreferenceRegistry.compose(setOf(chart), listOf(contribution))
        assertEquals(AppPreferenceValue.Choice("static"), registry.resolve(mapOf(key.value to "c:static"))[key])
        assertEquals(AppPreferenceValue.Choice("auto"), registry.resolve(mapOf(key.value to "c:unknown"))[key])
        assertThrows(IllegalArgumentException::class.java) { AppPreferenceRegistry.compose(emptySet(), listOf(contribution)) }
        assertThrows(IllegalArgumentException::class.java) {
            AppPreferenceRegistry.compose(setOf(chart), listOf(contribution, contribution))
        }
    }

    @Test
    fun `app and global preference registries have explicit hard bounds`() {
        val owner = LauncherAppId("chart")
        val tooManyForOneApp = (0..AppPreferenceContribution.MAX_APP_PREFERENCES).map { index ->
            AppPreferenceDefinition.Toggle(
                AppPreferenceKey("chart.tile.option-$index"),
                AppPreferenceValue.Toggle(false),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppPreferenceContribution(owner, tooManyForOneApp)
        }

        val owners = (0..AppPreferenceRegistry.MAX_REGISTERED_PREFERENCES).map { LauncherAppId("app-$it") }
        val contributions = owners.mapIndexed { index, appId ->
            AppPreferenceContribution(
                appId,
                listOf(
                    AppPreferenceDefinition.Toggle(
                        AppPreferenceKey("app-$index.tile.enabled"),
                        AppPreferenceValue.Toggle(false),
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppPreferenceRegistry.compose(owners.toSet(), contributions)
        }
    }
}
