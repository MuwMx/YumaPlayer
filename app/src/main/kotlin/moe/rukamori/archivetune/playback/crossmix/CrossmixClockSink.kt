/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.crossmix

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Tells the Crossmix renderer where in its song each buffer sits. Media3 1.7 gives audio
 * processors no timestamps, but every buffer passes through here with one, and the stream offset
 * that turns it into a position in the song arrives here too. The processor runs inside
 * [handleBuffer], so the timestamp it's given is always the one for the audio it's about to see.
 */
@UnstableApi
class CrossmixClockSink(
    delegate: AudioSink,
    private val processor: CrossmixTransitionAudioProcessor,
) : ForwardingAudioSink(delegate) {

    private var lastTimestampUs = Long.MIN_VALUE

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // a buffer the sink couldn't take yet comes back with the same timestamp, part-consumed;
        // stamping it again would pull the clock back by whatever was already processed
        if (presentationTimeUs != lastTimestampUs) {
            lastTimestampUs = presentationTimeUs
            processor.onInputTimestamp(presentationTimeUs)
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun flush() {
        lastTimestampUs = Long.MIN_VALUE
        super.flush()
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        processor.onStreamOffset(outputStreamOffsetUs)
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }
}
