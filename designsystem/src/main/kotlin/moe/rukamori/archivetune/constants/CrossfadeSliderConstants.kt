package moe.rukamori.archivetune.constants

/**
 * User-facing range of the manual crossfade duration slider (seconds).
 *
 * Mirrors `CrossfadeConstants` in `:core:audio-dsp`, which the playback service clamps the
 * persisted preference against. `:designsystem` cannot depend on `:core:audio-dsp`
 * (MODULES.md dependency table), so the duplicate lives here and must move in lockstep with the
 * DSP ceiling. 0 is the "crossfade off" value routed through `shouldUseLegacyPath`, so the lower
 * bound must stay reachable.
 */
const val CROSSFADE_SLIDER_MIN_SECONDS = 0f
const val CROSSFADE_SLIDER_MAX_SECONDS = 20f
const val CROSSFADE_SLIDER_DEFAULT_SECONDS = 5f
val CROSSFADE_SLIDER_RANGE = CROSSFADE_SLIDER_MIN_SECONDS..CROSSFADE_SLIDER_MAX_SECONDS

/**
 * Snap resolution of the crossfade slider: half-second steps across the range. Material3
 * `steps` counts the discrete values between the range endpoints, hence the minus one.
 */
const val CROSSFADE_SLIDER_STEP_SECONDS = 0.5f
const val CROSSFADE_SLIDER_STEPS = 39
