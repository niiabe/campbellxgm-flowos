package com.campbell.xgm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeardownPolicyTest {
    private val game = "com.game"
    private fun policy() = TeardownPolicy(game, ignored = setOf("com.android.systemui"), graceMs = 60_000, requiredMisses = 2)

    @Test fun slowColdStartDoesNotEndGameMode() {
        val p = policy()
        // Our own app / launcher still on screen while the game loads.
        assertFalse(p.onSample("com.campbell.xgm", 5_000))
        assertFalse(p.onSample("com.launcher", 30_000))
        assertFalse(p.onSample(game, 40_000))
    }

    @Test fun gameThatNeverOpensEndsAfterGrace() {
        val p = policy()
        assertFalse(p.onSample("com.launcher", 59_000))
        assertTrue(p.onSample("com.launcher", 60_000))
    }

    @Test fun leavingTheGameEndsAfterTwoPolls() {
        val p = policy()
        p.onSample(game, 5_000)
        assertFalse(p.onSample("com.launcher", 10_000))
        assertTrue(p.onSample("com.launcher", 15_000))
    }

    @Test fun briefExcursionResetsCounter() {
        val p = policy()
        p.onSample(game, 5_000)
        assertFalse(p.onSample("com.chat", 10_000))
        assertFalse(p.onSample(game, 15_000))
        assertFalse(p.onSample("com.chat", 20_000))
    }

    @Test fun overlaysAndUnknownNeverEndGameMode() {
        val p = policy()
        p.onSample(game, 5_000)
        repeat(10) { assertFalse(p.onSample("com.android.systemui", 10_000L + it)) }
        repeat(10) { assertFalse(p.onSample(null, 20_000L + it)) }
    }
}
