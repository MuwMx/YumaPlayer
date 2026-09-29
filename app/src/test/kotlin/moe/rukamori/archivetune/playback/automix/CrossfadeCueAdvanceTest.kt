/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.advanceCueForElapsed

class CrossfadeCueAdvanceTest {

    @Test
    fun cue_isUnchangedWhenFadeStartsOnTime() {
        assertEquals(8_000L, advanceCueForElapsed(8_000L, 0L, null))
        assertEquals(8_000L, advanceCueForElapsed(8_000L, -500L, null))
    }

    @Test
    fun cue_advancesByElapsedTimeOnLateStart() {
        assertEquals(10_000L, advanceCueForElapsed(8_000L, 2_000L, null))
    }

    @Test
    fun cue_staysAtZeroWhenNoCueWasResolved() {
        assertEquals(0L, advanceCueForElapsed(0L, 2_000L, null))
    }

    @Test
    fun cue_isClampedToIncomingDuration() {
        assertEquals(10_000L, advanceCueForElapsed(8_000L, 5_000L, 10_000L))
        assertEquals(0L, advanceCueForElapsed(8_000L, 5_000L, 0L))
    }
}
