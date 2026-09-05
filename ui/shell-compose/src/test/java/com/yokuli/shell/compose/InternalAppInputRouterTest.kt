package com.yokuli.shell.compose

import com.yokuli.shell.contract.ShellInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalAppInputRouterTest {
    @Test
    fun `newest mounted feature gets first refusal and stale disposal cannot clear it`() {
        val router = InternalAppInputRouter()
        val first = router.register(Any()) { it == ShellInput.BACK }
        val second = router.register(Any()) { it == ShellInput.SEARCH }

        assertFalse(router.dispatch(ShellInput.BACK))
        assertTrue(router.dispatch(ShellInput.SEARCH))

        first.close()
        assertTrue(router.dispatch(ShellInput.SEARCH))

        second.close()
        assertFalse(router.dispatch(ShellInput.SEARCH))
    }
}
