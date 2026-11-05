# 🧠 Injecting External History into YumaPlayer

This document outlines how to bypass standard restore limits and natively inject thousands of tracks from ListenBrainz, Spotify, or YouTube Music directly into the SQLite database.

## The Problem
When importing a .backup file with thousands of tracks, the YumaPlayer UI often fails silently, dropping metadata for unique songs and only importing the play events.

## The Solution
We built a Python ETL pipeline (available at https://github.com/Apersonwithtoenail/Listenermerge) that pulls your entire listening history directly from the ListenBrainz API, opens the SQLite database, and manually constructs the required relations (song, artist, album, event, playlist, playCount).

## The blockedAt Bug (Stats Screen Fix)
During development, we found that the StatsViewModel calculates Unique Songs using a query that ignores artists where blockedAt IS NOT NULL.
If you inject synthetic artists with blockedAt = 0, Yuma treats them as blocked and completely hides them from the stats!
The Fix: When injecting data, you must set blockedAt = NULL for the artist to ensure the Stats screen counts the unique songs properly.

## Results
After injecting the database and setting blockedAt = NULL:
- Total Plays: 7468
- Unique Songs: 2993
- Unique Artists: 1745
- Top Artist: XXXTENTACION (407 plays)

*Hacked together by Kavish (https://github.com/Apersonwithtoenail)*
