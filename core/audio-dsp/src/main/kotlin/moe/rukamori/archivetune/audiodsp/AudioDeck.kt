package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.Player

interface AudioDeck {
    val player: Player
    val djFilter: DjFilterAudioProcessor
    fun applyAutomation(progress: Float, plan: AutomixPlan)
    fun clearAutomation()
}
