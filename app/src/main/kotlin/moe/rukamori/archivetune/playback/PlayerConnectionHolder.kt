package moe.rukamori.archivetune.playback

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerConnectionHolder @Inject constructor() {
    val connection = MutableStateFlow<PlayerConnection?>(null)
}