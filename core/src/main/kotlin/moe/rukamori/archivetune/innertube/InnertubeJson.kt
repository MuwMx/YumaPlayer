/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.GridRenderer
import moe.rukamori.archivetune.innertube.models.MusicCarouselShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.pages.LibraryPage

internal fun parseAccountChannel(renderer: JsonObject): AccountChannel? {
    val isDisabled = renderer.booleanValue("isDisabled") ?: false
    val hasChannel = renderer.booleanValue("hasChannel") ?: true
    if (isDisabled || !hasChannel) return null

    val dataSyncId = renderer.parseAccountChannelDataSyncId() ?: return null

    val name = renderer["accountName"].textValue() ?: return null
    val byline = renderer["accountByline"].textValue()
    val channelHandle = renderer["channelHandle"].textValue()
    val thumbnailUrl = renderer["accountPhoto"].thumbnailUrl()

    return AccountChannel(
        name = name,
        byline = byline,
        channelHandle = channelHandle,
        thumbnailUrl = thumbnailUrl,
        dataSyncId = dataSyncId,
        isSelected = renderer.booleanValue("isSelected") ?: false,
    )
}

internal fun JsonObject.parseAccountChannelDataSyncId(): String? =
    this["serviceEndpoint"]
        ?.findDelegationValue()
        ?.normalizeAccountChannelDataSyncId()

internal fun JsonElement.findMainAppWebDataSyncId(): String? =
    (this as? JsonObject)
        ?.get("responseContext")
        ?.jsonObjectOrNull()
        ?.get("mainAppWebResponseContext")
        ?.jsonObjectOrNull()
        ?.get("dataSyncId")
        ?.jsonPrimitiveOrNull()
        ?.contentOrNull
        ?.normalizeAccountChannelDataSyncId()

internal fun JsonElement.objectsNamed(name: String): Sequence<JsonObject> =
    sequence {
        when (val element = this@objectsNamed) {
            is JsonObject -> {
                element[name]?.jsonObjectOrNull()?.let { yield(it) }
                element.values.forEach { yieldAll(it.objectsNamed(name)) }
            }

            is JsonArray -> {
                element.forEach { yieldAll(it.objectsNamed(name)) }
            }

            else -> {
                Unit
            }
        }
    }

internal fun JsonElement?.textValue(): String? {
    val obj = this as? JsonObject ?: return null
    obj["simpleText"]?.jsonPrimitiveOrNull()?.contentOrNull?.let { return it.trim().takeIf(String::isNotBlank) }
    return obj["runs"]
        ?.jsonArrayOrNull()
        ?.joinToString(separator = "") { run ->
            run
                .jsonObjectOrNull()
                ?.get("text")
                ?.jsonPrimitiveOrNull()
                ?.contentOrNull
                .orEmpty()
        }?.trim()
        ?.takeIf(String::isNotBlank)
}

internal fun JsonElement?.thumbnailUrl(): String? {
    val thumbnails =
        (this as? JsonObject)
            ?.get("thumbnails")
            ?.jsonArrayOrNull()
            ?: return null
    return thumbnails
        .asSequence()
        .mapNotNull { thumbnail ->
            thumbnail
                .jsonObjectOrNull()
                ?.get("url")
                ?.jsonPrimitiveOrNull()
                ?.contentOrNull
        }.lastOrNull()
        ?.let { url -> if (url.startsWith("//")) "https:$url" else url }
}

internal fun JsonElement.findDelegationValue(): String? {
    val directKeys =
        setOf(
            "onBehalfOfUser",
            "obfuscatedSelectedGaiaId",
            "obfuscatedGaiaId",
            "accountId",
            "delegatedSessionId",
        )
    val fallbackKeys =
        setOf(
            "selectedSerializedDelegationContext",
            "serializedDelegationContext",
        )
    return findStringValue(directKeys) ?: findStringValue(fallbackKeys)
}

internal fun JsonElement.findStringValue(keys: Set<String>): String? =
    when (this) {
        is JsonObject -> {
            keys.firstNotNullOfOrNull { key ->
                this[key]
                    ?.jsonPrimitiveOrNull()
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            } ?: values.firstNotNullOfOrNull { it.findStringValue(keys) }
        }

        is JsonArray -> {
            firstNotNullOfOrNull { it.findStringValue(keys) }
        }

        else -> {
            null
        }
    }

internal fun JsonObject.booleanValue(key: String): Boolean? = this[key]?.jsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

internal fun String.normalizeAccountChannelDataSyncId(): String? {
    val normalized =
        trim()
            .takeIf(String::isNotBlank)
            ?.let { value ->
                value
                    .takeIf { !it.contains("||") }
                    ?: value.takeIf { it.endsWith("||") }?.substringBefore("||")
                    ?: value.substringAfter("||")
            }?.trim()
            ?.takeIf(String::isNotBlank)
    return normalized
}

internal fun SectionListRenderer.Content.libraryItems(): List<YTItem> =
    buildList {
        addAll(
            gridRenderer
                ?.items
                .orEmpty()
                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
        )
        addAll(
            musicCarouselShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
        )
        addAll(
            musicShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
        )
        addAll(
            musicPlaylistShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
        )
        itemSectionRenderer?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer
                ?.let { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                ?.let(::add)
            addAll(
                content.musicShelfRenderer
                    ?.contents
                    .orEmpty()
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
            )
            addAll(
                content.gridRenderer
                    ?.items
                    .orEmpty()
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
            )
        }
    }

internal fun SectionListRenderer.Content.libraryContinuation(): String? =
    gridRenderer?.continuations?.getContinuation()
        ?: musicShelfRenderer?.contents.orEmpty().getContinuation()
        ?: musicShelfRenderer?.continuations?.getContinuation()
        ?: musicPlaylistShelfRenderer?.contents.orEmpty().getContinuation()
        ?: musicPlaylistShelfRenderer?.continuations?.getContinuation()
        ?: itemSectionRenderer?.contents.orEmpty().firstNotNullOfOrNull { content ->
            content.musicShelfRenderer
                ?.contents
                .orEmpty()
                .getContinuation()
                ?: content.musicShelfRenderer?.continuations?.getContinuation()
                ?: content.gridRenderer?.continuations?.getContinuation()
        }

internal fun SectionListRenderer.Content.playlistSongContents(): List<MusicShelfRenderer.Content> =
    buildList {
        addAll(musicPlaylistShelfRenderer?.contents.orEmpty())
        addAll(musicShelfRenderer?.contents.orEmpty())
        itemSectionRenderer?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { renderer ->
                add(
                    MusicShelfRenderer.Content(
                        musicResponsiveListItemRenderer = renderer,
                        continuationItemRenderer = null,
                    ),
                )
            }
            addAll(content.musicShelfRenderer?.contents.orEmpty())
        }
    }

internal fun SectionListRenderer.Content.playlistSongContinuation(): String? =
    musicPlaylistShelfRenderer?.let { shelf ->
        shelf.contents.getContinuation() ?: shelf.continuations?.getContinuation()
    } ?: musicShelfRenderer?.let { shelf ->
        shelf.contents.orEmpty().getContinuation() ?: shelf.continuations?.getContinuation()
    } ?: itemSectionRenderer?.contents.orEmpty().firstNotNullOfOrNull { content ->
        content.musicShelfRenderer?.let { shelf ->
            shelf.contents.orEmpty().getContinuation() ?: shelf.continuations?.getContinuation()
        }
    }
