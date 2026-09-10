# Issues & Root Cause Analysis — Spotify Likes Sync & Readers

## 1. Player Sheet Heart Reader (PlayerViewModel)
- **Причина**: `PlayerViewModel` проверял только `spotifyTrackId` и точные классы очередей Spotify (`SpotifyLikedSongsQueue`, `SpotifyPlaylistQueue`, `SpotifyTracksQueue`). При воспроизведении трека из библиотеки, истории, поиска или обычных очередей эти условия были `false`, и ридер возвращал `likedYtm` вместо `likedSpotify`, из-за чего лайкнутые в Spotify треки отображались с пустым сердцем.
- **Исправление**: Расширена детекция Spotify-контекста: проверка Spotify ID (`spotify:` или 22 символа Base62) и флагов `song.likedSpotify && !song.likedYtm`.

## 2. Notification & Media Session Path (MusicService)
- **Причина**: В `updateNotification()` и `toggleLike()` иконка уведомления и вычисление источника (`LikeSource`) не учитывали воспроизведение треков со Spotify ID или с `likedSpotify=true` вне Spotify-очередей.
- **Исправление**: `updateNotification()` и `toggleLike()` синхронизированы с логикой `PlayerViewModel` — учитывают Spotify ID, очереди и активный флаг `likedSpotify`.

## 3. SongMenu Heart Reader & Actions (SongMenu)
- **Причина**: Условие `(song.song.likedSpotify && !song.song.likedYtm && !song.song.liked)` содержало `!song.song.liked`, которое всегда вычислялось в `false` из-за инварианта `liked = likedYtm || likedSpotify`. Кроме того, при клике в `syncUtils.likeSong()` передавался YouTube ID как `explicitSpotifyId`.
- **Исправление**: Убрано ложное отрицание `!song.song.liked`, а в `syncUtils.likeSong()` передается только валидный Spotify ID (`spotify:` или 22 символа).

## 4. UI Item Badges (SongItems)
- **Причина**: `YouTubeListItem` и `YouTubeGridItem` проверяли только `song?.song?.likedYtm == true` для отображения бейджа избранного, игнорируя лайки из Spotify (`likedSpotify`).
- **Исправление**: Заменено на union `song?.song?.liked == true`.

## 5. Sync Write-Back (SyncLikes & SpotifySync)
- **Причина**: В `SyncLikes.likeSong()` для треков с YouTube ID и пустым `explicitSpotifyId` вызов Spotify sync пропускался и отправлялся только в `YouTube.likeVideo()`. В `SpotifySync.syncLikeForSongs()` для батча отправлялся `song.liked` вместо `song.likedSpotify`, а в `resolveSpotifyId` невалидный `explicitSpotifyId` (YouTube ID) мог записываться в `SpotifyMatchEntity`.
- **Исправление**: 
  - `SyncLikes.likeSong()` и `likeSongs()` проверяют наличие Spotify ID / совпадений в базе и корректно триггерят `SpotifySync.syncLikeForSong(..., s.likedSpotify)`.
  - В `SpotifySync.syncLikeForSongs()` используется `song.likedSpotify`.
  - В `resolveSpotifyId()` добавлена валидация Spotify ID перед сохранением маппинга.

## 6. Like-Chain SRP Refactor & Source Isolation
- **Причина**: 
  1. `SongEntity.toggleLike()` содержал побочные эффекты (сетевой запрос `YouTube.likeVideo` внутри неконтролируемого `CoroutineScope(Dispatchers.IO)`), нарушая чистоту Room-сущностей.
  2. Логика детекции источника (`LikeSource`) была продублирована в 4 местах, причем использовалась проверка флагов БД (`likedSpotify && !likedYtm`), что приводило к взаимному влиянию источников.
  3. `SyncLikes.likeSong()` делал перекрестные проверки (`getSpotifyMatchesByYouTubeIds`) и отправлял запись в Spotify при YTM-лайках (отсылая ложные unlike-запросы в Spotify).
  4. `SpotifySync.resolveSpotifyId()` производил мутацию БД (`database.insert`) прямо внутри функции разрешения ID до подтверждения успешности сетевого запроса.
- **Исправление**:
  1. `SongEntity`: Удален `toggleLike()` и сетевые/корутинные импорты. Оставлена чистая функция `localToggleLike(source: LikeSource)` с сохранением инварианта `liked = likedYtm || likedSpotify`, обновлением `likedDate` и `inLibrary`.
  2. `LikeSourceResolver`: Создан единый централизованный объект контекстной детекции источника (`mediaId`, `spotifyTrackId`, `Queue`, `isLocal`). Полностью исключены сигналы на основе флагов БД.
  3. `SyncLikes` & `SyncUtils`: Сетевая синхронизация изолирована строго по переданному `LikeSource`: `SPOTIFY` отправляется только в `SpotifySync`, `YTM` — только в `YouTube.likeVideo`. Устранены перекрестные сайд-эффекты и ложные анлайки.
  4. `SpotifySync`: `resolveSpotifyId` сделан чистым запросом без побочных эффектов. Сохранение `SpotifyMatchEntity` перенесено в точку после подтвержденного успеха API (`executeSetSaved`).
  5. Все вызывающие компоненты (`MusicService`, `PlayerViewModel`, `SongMenu`, `SelectionSongsMenu`, `AddToPlaylistDialogOnline`, `YouTubeSongMenu`) переведены на `localToggleLike()` и `LikeSourceResolver`.
- AlbumScreen:1074 - Not a Spotify playlist, passed null (default).
- CachePlaylistScreen:825 - Not a Spotify playlist, passed null (default).
- TopPlaylistScreen:934 - Not a Spotify playlist, passed null (default).
- AutoPlaylistScreen:1202,1234 - Not a Spotify playlist, passed null (default).
- LocalPlaylistScreen:1615 - Checked `playlist?.playlist?.browseId?.startsWith("spotify:") == true`, passed `LikeSource.SPOTIFY` if true, else null.
- HistoryScreen:625 - Not a Spotify playlist, passed null (default).
- OnlinePlaylistScreen:1326 - Checked `playlist?.id?.startsWith("spotify:") == true`, passed `LikeSource.SPOTIFY` if true, else null.
