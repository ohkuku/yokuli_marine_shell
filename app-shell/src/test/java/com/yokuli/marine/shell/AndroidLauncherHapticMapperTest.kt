package com.yokuli.marine.shell

import com.yokuli.shell.engine.LauncherHaptic
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AndroidLauncherHapticMapperTest {
    @Test
    fun engineHapticKindsMapToDistinctPlatformFeedback() {
        val mapped = LauncherHaptic.entries.associateWith(AndroidLauncherHapticMapper::constantFor)

        assertNotEquals(mapped.getValue(LauncherHaptic.SELECTION), mapped.getValue(LauncherHaptic.LONG_PRESS))
        assertNotEquals(mapped.getValue(LauncherHaptic.LONG_PRESS), mapped.getValue(LauncherHaptic.DROP))
        assertNotEquals(mapped.getValue(LauncherHaptic.SELECTION), mapped.getValue(LauncherHaptic.DROP))
    }
}
