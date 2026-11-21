/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

data class ParametricEQBand(
    val frequency: Double,
    val gain: Double,
    val q: Double = 1.41,
    val filterType: FilterType = FilterType.PK,
    val enabled: Boolean = true
)

data class ParametricEQ(
    val preamp: Double,
    val bands: List<ParametricEQBand>,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        const val MAX_BANDS = 20
    }
}
