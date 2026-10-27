# Spotify Placeholders and Playlist Copies

How Spotify tracks are played without being matched first, and how playlists are copied.

## Mirror, copy and source

A playlist row can point at Spotify in two different ways:

| Field | Meaning | Owned by |
| --- | --- | --- |
| `spotifyId` | A read-only mirror of a Spotify playlist. | `SpotifyPlaylistSync` |
| `spotifySourceId` | An editable copy made from that Spotify playlist. | The user |

The sync only touches mirrors. It never changes the editability or type of a copy. A playlist's source badge is derived from its fields (`PlaylistEntity.playlistSource()`): `browseId` means YouTube, `spotifyId` means Spotify, and anything else is Personal.

`spotifySourceId` was added in Room version 38 (nullable, indexed). `UniversalMigration` reconciles older databases.

## Placeholders

A Spotify track is not matched to YouTube when it is added to a playlist or queue. It is stored and queued as a placeholder with the id `spotify:<trackId>` (`SpotifyPlaceholders`), together with its title, artists, album and duration. The YouTube song is looked up only when the track is about to play.

- **Queueing.** `spotifyAwareQueue` queues the whole list in order with the chosen song current. Stored matches are applied up front. The queue is never paged, so nothing is inserted above the current song later.
- **Playing.** `resolvePlaybackDataSpec` matches a placeholder when the player loads it and continues with the YouTube id. A track with no YouTube version fails with a not-found error, is remembered as unmatched for ten minutes, and `StreamErrorRouter` skips it without counting a playback failure.
- **Preparing.** `SpotifyPlaceholderLookahead` prepares the songs around the current one: the next four and the previous two are matched and get their stream URL resolved, the next one is downloaded by the stream prefetcher, and placeholders further away are matched in the background.
- **Priorities.** `MatchPriority` decides how soon a search may run. `PLAYING` searches are sent at once, up to four at a time, and do not wait for the other priorities. `UPCOMING` searches (songs being prepared, and copies to YouTube) run at most two at a time and wait while a `PLAYING` search is running. `BACKGROUND` searches run one at a time, spaced out, and only while nothing else is searching, so bulk matching cannot slow playback or get the IP rate-limited.
- **Failed searches.** A failed search (rate limit, no network) is not "no match". `resolveToMetadata(throwOnSearchFailure = true)` throws so the caller can retry, and nothing is remembered as unmatched while offline.

Radio and autoplay need a real video id, so they seed from `SpotifyPlaceholders.youtubeIdFor`.

## Matching

`SpotifyPlaybackResolver.resolveToMetadata` tries up to three searches, most precise first: songs by artist and title, the general search (uploads and videos), then songs by title alone. `SpotifySearchScorer.pickGeneric` is strict about version, duration and artist. When it rejects every candidate, `pickByTokenOverlap` accepts a candidate on word overlap, but only if the Spotify artist is on the candidate and it is the same version of the song. A stored match whose YouTube song carries none of the Spotify artists is ignored.

## Copying a playlist

`PlaylistCopier` copies a Spotify, YouTube or Personal playlist to a new or existing Personal or YouTube playlist. `PlaylistCopyManager` runs one copy at a time and publishes its state, and `PlaylistCopyDialog` is the UI for it.

- A Spotify playlist copied to a Personal playlist is written as placeholder rows in one batched write. It is complete immediately and each song is matched when played.
- Copying to YouTube matches every song first, with the `UPCOMING` limit of two searches at a time. This includes Spotify placeholders that are already in a Personal playlist. Songs with no match and songs whose search failed are reported separately, since only the second kind is worth retrying. **Retry missing** runs the copy again into the playlist that was written and adds what is still missing.
- A new YouTube playlist is created first and deleted again if adding the songs or writing the local rows fails.
- An existing playlist only gains songs it lacks. Repeats are skipped and counted.
- Local files are never uploaded to YouTube.

Spotify mirrors are not copy targets, and YouTube playlists are only offered while signed in.

## Editable copy of a Spotify playlist

**Make editable copy** (shown as **Update editable copy** once it exists) creates the Personal copy linked to a Spotify playlist, or adds the songs it lacks. A playlist has at most one linked copy; creating one is idempotent, so two requests at once cannot make two.

`SpotifyCopySync` does the same for every Spotify playlist in the background whenever the Spotify auto-sync is triggered (app start, opening the Spotify library). It does not wait for that sync's cooldown, which is cheap because a copy whose stored track count equals Spotify's is skipped without a request. Track lists are read in parallel and written as placeholders in one transaction. A change that keeps a playlist's track count the same is only noticed once the count changes.

Copies are additive. New songs from Spotify are appended, and songs the user added or kept are never removed. The reverse also holds: a song the user deletes from a copy comes back the next time Spotify's track count changes, and a copy the user deletes is created again by the next sync. Copies made through **Copy to playlist** are independent and not linked to the source.

## Known limitations

- A track that is not on YouTube Music cannot be played from a copy. It is skipped.
- A cancelled append to an existing YouTube playlist cannot be rolled back on YouTube.
- Spotify local files are not copied.
