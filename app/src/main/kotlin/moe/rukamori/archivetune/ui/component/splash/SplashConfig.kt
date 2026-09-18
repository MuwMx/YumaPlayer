package moe.rukamori.archivetune.ui.component.splash

object SplashConfig {
    var AUTO_BURST: Boolean = true
    var SLOTS_LOGO: Int = 28
    var SLOTS_BOLT: Int = 24
    var SLOTS_CROSS: Int = 24

    fun getSlotCount(shape: String): Int = when (shape) {
        SplashSlots.SHAPE_CROSS -> SLOTS_CROSS
        SplashSlots.SHAPE_BOLT -> SLOTS_BOLT
        else -> SLOTS_LOGO
    }

    object Timings {
        var DUST_DURATION_MS: Float = 0f
        var GATHER_BOLT_MS: Float = 850f
        var GATHER_LOGO_MS: Float = 800f
        var GATHER_CROSS_MS: Float = 450f
        var GATHER_SHORT_MS: Float = 400f
        var IGNITE_FULL_MS: Float = 200f
        var IGNITE_SHORT_MS: Float = 120f
        var BURST_FULL_MS: Float = 420f
        var BURST_SHORT_MS: Float = 280f
        var TRANSIT_MS: Float = 320f
    }

    object Spawn {
        var RING_INNER: Float = 140f
        var RING_WIDTH: Float = 180f
        var SPEED_BASE: Float = 0.25f
        var SPEED_VAR: Float = 0.35f
    }

    object Settle {
        var CONVERGE_DIST: Float = 2f
    }

    object Wave {
        var FRAMES_TO_CROSS: Float = 65f
        var STROKE_WIDTH_DP: Float = 2.0f
    }

    object Physics {
        var SPRING_K_NORMAL: Float = 0.42f
        var SPRING_K_TRANSIT: Float = 0.48f
        var DAMPING_FORMING: Float = 0.80f
        var DAMPING_BURST: Float = 0.985f
        var DAMPING_FREE: Float = 0.972f

        var SPEED_CLAMP_FORMING: Float = 20f
        var SPEED_CLAMP_BURST: Float = 45f
        var SPEED_CLAMP_TRANSIT: Float = 15f
        var SPEED_CLAMP_PROCESSING: Float = 10f
        var SPEED_CLAMP_FREE: Float = 9f

        var FORM_CROSS: Float = 0.85f
        var FORM_GATHER: Float = 0.15f
        var DAMP_ON_RESHAPE: Float = 0.65f
        var DAMP_ON_REGATHER: Float = 0.5f

        var wD: Float = 0.055f
        var gD: Float = 0.05f
        var vD: Float = 0.0022f
        var bD: Float = 0.020f
        var xD: Float = 0.7f
        var SD: Float = 0.55f
        var TD: Float = 3.2f
        var ED: Float = 14f
        var MD: Float = 15f
        var MAX_STEP: Float = 1.5f
    }

    object Burst {
        var EXPLODE_POWER: Float = 6.0f
        var MEMBER_BOOST: Float = 1.6f
        var FLOATER_BOOST: Float = 0.85f
        var POST_BURST_FRAMES: Int = 130

        var SHOCKWAVE_SPEED: Float = 20f
        var SHOCKWAVE_RADIUS_FACTOR: Float = 1.2f
        var SHOCKWAVE_ALPHA_BOLT: Float = 0.75f
        var SHOCKWAVE_ALPHA_CROSS: Float = 0.85f
        var SHOCKWAVE_ALPHA_BURST: Float = 1.0f

        var SCREEN_FLASH_BURST: Float = 0.18f
        var SCREEN_FLASH_IGNITE: Float = 0.08f
        var SCREEN_FLASH_DECAY: Float = 0.012f

        var SNAP_SCALE: Float = 0.85f
        var SNAP_MS: Float = 40f
    }

    object Effects {
        var PULSE_WAVE_SPEED: Float = 0.32f
        var STAR_STAGGER_MS: Float = 60f
        var STAR_STAGGER_SHORT_MS: Float = 34f
        var STAR_SIZE_DP: Float = 24f
        var PINCH_FACTOR: Float = 0.06f

        var SWING_ANGLE_DEG: Float = 3.6f
        var SWING_SPEED: Float = 1.6f
        var BREATH_SCALE: Float = 0.04f
        var BREATH_SPEED: Float = 1.8f
        var FLOAT_Y_DP: Float = 7f
        var FLOAT_Y_SPEED: Float = 1.3f

        var MAX_HALO_DP: Float = 28f
        var LINK_DISTANCE_DP: Float = 150f
        var LOGO_TARGET_SIZE_DP: Float = 150f
    }

    object Look {
        object Links {
            var LINE_WIDTH_DP: Float = 1.5f
            var APPEAR_MIN_FORM: Float = 0.45f
            var APPEAR_RANGE: Float = 0.55f
            var RAW_ALPHA_FACTOR: Float = 0.72f
            var WAVE_DIST_FACTOR: Float = 0.12f
            var WAVE_BOOST_BIN: Float = 0.4f
            var WAVE_BOOST_ALPHA: Float = 0.9f
            var WAVE_BOOST_WIDTH: Float = 1.6f
        }

        object Glow {
            var HEIGHT_FACTOR: Float = 0.9f
            var STOP_MID: Float = 0.35f
            var SPRITE_ALPHAS: FloatArray = floatArrayOf(1f, 0.72f, 0.3f, 0.09f, 0.025f, 0f)
            var SPRITE_STOPS: FloatArray = floatArrayOf(0f, 0.1f, 0.24f, 0.5f, 0.78f, 1f)
            var STRENGTH_ALPHA_BASE: Float = 0.15f
            var STRENGTH_ALPHA_MID: Float = 0.06f
        }

        object Halo {
            var STAR_BASE_HEIGHT_FACTOR: Float = 0.035f
            var STAR_HALO_FACTOR: Float = 1.8f
            var STAR_BODY_HALO_FACTOR: Float = 1.6f
            var STAR_FLARE_ALPHA: Float = 0.35f
            var STAR_SPRITE_ALPHA: Float = 0.45f
            var OUTER_SPRITE_ALPHA: Float = 0.55f
            var MEMBER_ALPHA_BASE: Float = 0.95f
            var FLOATER_ALPHA_BASE: Float = 0.4f
            var GLOW_MIX_BASE: Float = 0.45f
            var GLOW_MIX_FACTOR: Float = 0.55f
            var FAIL_GLOW_THRESHOLD: Float = 0.4f
            var CORE_WAVE_BOOST_THRESHOLD: Float = 0.12f
        }

        object Flash {
            var DURATION_MS: Float = 120f
            var MAX_ALPHA: Float = 0.25f
            var RADIUS_DP: Float = 200f
        }

        object Cutoffs {
            var FORM_GLOW: Float = 0.005f
            var FORM_LINKS: Float = 0.01f
            var RAW_ALPHA_LINKS: Float = 0.01f
            var PARTICLE_ALPHA: Float = 0.005f
            var STAR_ALPHA: Float = 0.005f
            var FLASH_ALPHA: Float = 0.005f
        }
    }

    object Reveal {
        var START_FRACTION: Float = 0.15f
        var DURATION_MS: Int = 450
        var RISE_DP: Float = 24f
        var FADE_MS: Int = 250
    }
}
