package moe.rukamori.archivetune.playback

internal enum class PlayerRole {
    MASTER,
    STANDBY,
}

internal data class DualPlayerRoleHolder(
    var playerA: PlayerRole = PlayerRole.MASTER,
    var playerB: PlayerRole = PlayerRole.STANDBY,
)

internal fun shouldUseLegacyPath(durationMs: Long): Boolean = durationMs <= 0L
