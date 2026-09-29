package moe.rukamori.archivetune.audiodsp

data class CrossfadeConfig(
    val durationMs: Long = 0L,
    val automixEnabled: Boolean = false,
    val preset: String = "auto",
    val gapless: Boolean = true,
    val enabled: Boolean = true,
) {
    val durationSeconds: Float
        get() = durationMs / 1000f

    val automix: Boolean
        get() = automixEnabled

    val automixTransitionPreset: String
        get() = preset
}
