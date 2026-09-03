package moe.rukamori.archivetune.ui.player.player_0.buttons

sealed interface PlayerAction {
    // Воспроизведение
    object PlayPause : PlayerAction

    // Вперёд (закрывает и Next, и SkipNext)
    object Next : PlayerAction
    object SkipNext : PlayerAction

    // Назад (закрывает и Previous, и SkipPrevious)
    object Previous : PlayerAction
    object SkipPrevious : PlayerAction

    // Лайки
    object Like : PlayerAction
    object ToggleLike : PlayerAction

    // Лирика
    object Lyrics : PlayerAction
    object ToggleLyrics : PlayerAction
    object SearchLyrics : PlayerAction
    object ForceRefresh : PlayerAction
    object DeleteLyrics : PlayerAction
    object ToggleAutoDownload : PlayerAction

    // Таймер сна
    data class StartSleepTimer(val minutes: Int) : PlayerAction
    object StopSleepTimer : PlayerAction
    data class AdjustSleepTimer(val minutes: Int) : PlayerAction
    // Управление очередью и фичи
    object Shuffle : PlayerAction
    object Repeat : PlayerAction
    object Share : PlayerAction
    data class PlayQueueItem(val index: Int) : PlayerAction
    data class RemoveQueueItem(val index: Int) : PlayerAction
    data class MoveQueueItem(val from: Int, val to: Int) : PlayerAction
    // Перемотка трека
    data class SeekTo(val positionMs: Long) : PlayerAction

    // Дополнительные функции лирики (смещение, редактирование, перевод)
    object PrepareLyricsEdit : PlayerAction
    data class SetLyricsSyncOffset(val offsetMs: Int) : PlayerAction
    data class UpdateLyricsEditText(val text: String) : PlayerAction
    data class UpdateTranslateLanguage(val langCode: String) : PlayerAction
    data class SaveLyrics(val text: String) : PlayerAction
    data class TranslateLyrics(val langCode: String, val useAi: Boolean) : PlayerAction

    // Навигация по метаданным и функции плеера
    object OpenAlbum : PlayerAction
    object OpenArtist : PlayerAction
    object StartRadio : PlayerAction
    object AddToPlaylist : PlayerAction
    object TogglePin : PlayerAction

    // Дополнительные действия с треком
    object DownloadTrack : PlayerAction
    object ShowDetails : PlayerAction
    object OpenEqualizer : PlayerAction
    object OpenPlaybackSpeed : PlayerAction
    object ToggleCodecInfo : PlayerAction
    object ToggleAlbumCoverGlow : PlayerAction

    data class UpdateColors(val vibrant: Int, val darkMuted: Int, val gradient: Int) : PlayerAction

    object Dismiss : PlayerAction
}

enum class LyricsMenuScreen { MAIN, EDIT, TRANSLATE, SYNC_OFFSET }
