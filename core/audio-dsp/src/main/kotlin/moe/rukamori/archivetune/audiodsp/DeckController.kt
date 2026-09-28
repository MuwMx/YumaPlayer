package moe.rukamori.archivetune.audiodsp

interface DeckController {
    val activeDeck: AudioDeck
    val transitionDeck: AudioDeck?
    fun prepareNext(target: CrossfadeTarget): Boolean
    fun primeIncoming(cueMs: Long)
    fun stopIncoming()
    fun startCrossfade(plan: AutomixPlan)
    fun completeHandoff()
    fun cancel()
}
