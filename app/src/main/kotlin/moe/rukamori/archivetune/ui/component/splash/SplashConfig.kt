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
        var IGNITE_FULL_MS: Float = 140f
        var IGNITE_SHORT_MS: Float = 110f
        var BURST_FULL_MS: Float = 420f
        var BURST_SHORT_MS: Float = 280f
        var TRANSIT_MS: Float = 320f
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
    }

    object Burst {
        var EXPLODE_POWER: Float = 6.0f
        var MEMBER_BOOST: Float = 1.6f
        var FLOATER_BOOST: Float = 0.85f
        var POST_BURST_FRAMES: Int = 130

        var SHOCKWAVE_SPEED: Float = 20f
        var SHOCKWAVE_RADIUS_FACTOR: Float = 1.15f
        var SHOCKWAVE_ALPHA_BOLT: Float = 0.75f
        var SHOCKWAVE_ALPHA_CROSS: Float = 0.85f
        var SHOCKWAVE_ALPHA_BURST: Float = 1.0f

        var SCREEN_FLASH_BURST: Float = 0.18f
        var SCREEN_FLASH_IGNITE: Float = 0.08f
        var SCREEN_FLASH_DECAY: Float = 0.012f
    }

    object Effects {
        var PULSE_WAVE_SPEED: Float = 0.32f
        var STAR_STAGGER_MS: Float = 60f
        var STAR_STAGGER_SHORT_MS: Float = 34f
        var STAR_SIZE_DP: Float = 15f
        var PINCH_FACTOR: Float = 0.06f

        var SWING_ANGLE_DEG: Float = 3.6f
        var SWING_SPEED: Float = 1.6f
        var BREATH_SCALE: Float = 0.04f
        var BREATH_SPEED: Float = 1.8f
        var FLOAT_Y_DP: Float = 7f
        var FLOAT_Y_SPEED: Float = 1.3f

        var MAX_HALO_DP: Float = 28f
        var LINK_DISTANCE_DP: Float = 150f
        var LOGO_TARGET_SIZE_DP: Float = 180f
    }
}
