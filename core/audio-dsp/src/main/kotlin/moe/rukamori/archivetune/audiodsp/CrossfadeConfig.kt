package moe.rukamori.archivetune.audiodsp

data class CrossfadeConfig(
    val durationMs: Long = 0L,
    val automixEnabled: Boolean = false,
    val aggressiveness: String = "standard",
    val preset: String = "auto",
    val gapless: Boolean = true,
    val enabled: Boolean = true,
) {
    val durationSeconds: Float
        get() = durationMs / 1000f

    val automix: Boolean
        get() = automixEnabled

    val automixAggressiveness: String
        get() = aggressiveness

    val automixTransitionPreset: String
        get() = preset
}
