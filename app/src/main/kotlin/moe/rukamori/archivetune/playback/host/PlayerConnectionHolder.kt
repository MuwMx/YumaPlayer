package moe.rukamori.archivetune.playback.host

import moe.rukamori.archivetune.playback.PlayerConnection

import moe.rukamori.archivetune.playback.MusicService

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerConnectionHolder @Inject constructor() {
    val connection = MutableStateFlow<PlayerConnection?>(null)
}
