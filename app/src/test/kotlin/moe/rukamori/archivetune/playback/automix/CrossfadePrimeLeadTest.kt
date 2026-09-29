package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.playback.PlaybackConstants

class CrossfadePrimeLeadTest {

    @Test
    fun primeLead_exceedsStandbyLoadControlMinBuffer() {
        assertTrue(
            "PRIME_LEAD_MS=${CrossfadeConstants.PRIME_LEAD_MS} must exceed " +
                "CROSSFADE_MIN_BUFFER_BEFORE_START_MS=${PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS}, " +
                "otherwise the incoming deck is still buffering when the fade opens",
            CrossfadeConstants.PRIME_LEAD_MS > PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS,
        )
    }

    @Test
    fun primeLead_fitsInsideThePrepareWindow() {
        assertTrue(
            "prepare window must cover the prime lead",
            PlaybackConstants.CROSSFADE_PREPARE_AHEAD_MS > CrossfadeConstants.PRIME_LEAD_MS,
        )
    }
}
