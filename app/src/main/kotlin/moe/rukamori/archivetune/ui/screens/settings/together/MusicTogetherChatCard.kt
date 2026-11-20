/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.together.MusicTogetherChatMessageUiModel
import moe.rukamori.archivetune.together.MusicTogetherChatTrackUiModel
import moe.rukamori.archivetune.together.MusicTogetherChatUiModel
import moe.rukamori.archivetune.together.MusicTogetherViewModel
import moe.rukamori.archivetune.ui.component.ItemThumbnail

/**
 * Chat card of a Together session.
 *
 * Messages travel through the same WebSocket that carries the playback state, so a shared track is
 * just another chat message with a playable payload attached: read it, play it next, or queue it.
 */
@Composable
internal fun TogetherChatCard(
    chat: MusicTogetherChatUiModel,
    viewModel: MusicTogetherViewModel,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    if (!chat.visible) return

    val listState = rememberLazyListState()
    val messageCount = chat.messages.size

    LaunchedEffect(messageCount) {
        if (messageCount > 0) listState.animateScrollToItem(messageCount - 1)
    }

    SectionCard(
        iconResId = R.drawable.queue_music,
        titleResId = R.string.together_chat_title,
        subtitle = pluralStringResource(R.plurals.together_chat_subtitle, messageCount, messageCount),
        accent = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    ) {
        if (!chat.messages.isEmpty) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillAvailableHeight) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.heightIn(max = 380.dp)
                            },
                        ),
                verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
            ) {
                items(
                    count = messageCount,
                    key = { index -> chat.messages[index].id },
                    contentType = { "chat_message" },
                ) { index ->
                    TogetherChatBubble(
                        message = chat.messages[index],
                        onPlaySong = viewModel::playSongFromChat,
                        onQueueSong = viewModel::addSongFromChatToQueue,
                    )
                }
            }
        } else {
            EmptyPanel(
                iconResId = R.drawable.queue_music,
                titleResId = R.string.together_chat_empty_title,
                bodyResId = R.string.together_chat_empty_body,
            )
        }

        TogetherChatTypingLine(chat)

        TogetherChatComposer(
            draft = chat.draft,
            enabled = chat.canSend,
            onDraftChange = viewModel::onChatDraftChange,
            onSend = viewModel::sendChatMessage,
            onShareSong = viewModel::shareCurrentlyPlayingSong,
        )

        if (!chat.enabled) {
            Text(
                text = stringResource(R.string.together_chat_disabled),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TogetherChatBubble(
    message: MusicTogetherChatMessageUiModel,
    onPlaySong: (String) -> Unit,
    onQueueSong: (String) -> Unit,
) {
    if (message.system) {
        Text(
            text = togetherChatMessageText(message),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val containerColor =
        if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor =
        if (message.mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape =
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (message.mine) 20.dp else 6.dp,
                    bottomEnd = if (message.mine) 6.dp else 20.dp,
                ),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = MusicTogetherSpacing.sm, vertical = MusicTogetherSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xxs),
            ) {
                if (message.showAuthor) {
                    Text(
                        text = message.authorName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!message.text.isNullOrBlank()) {
                    Text(
                        text = togetherChatMessageText(message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                message.track?.let { track ->
                    TogetherChatTrackChip(
                        track = track,
                        mine = message.mine,
                        onPlaySong = { onPlaySong(message.id) },
                        onQueueSong = { onQueueSong(message.id) },
                    )
                }
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/** Shared song card inside a chat bubble: artwork, metadata and the two playback actions. */
@Composable
private fun TogetherChatTrackChip(
    track: MusicTogetherChatTrackUiModel,
    mine: Boolean,
    onPlaySong: () -> Unit,
    onQueueSong: () -> Unit,
) {
    val accentColor =
        if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (mine) 0.35f else 0.5f),
    ) {
        Column(modifier = Modifier.padding(top = MusicTogetherSpacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
            ) {
                ItemThumbnail(
                    thumbnailUrl = track.thumbnailUrl,
                    isActive = false,
                    isPlaying = false,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(44.dp),
                    placeholderIconRes = R.drawable.music_note,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle =
                        when {
                            track.artists.isNotBlank() && track.durationLabel != null ->
                                "${track.artists} • ${track.durationLabel}"

                            track.artists.isNotBlank() -> track.artists
                            else -> track.durationLabel.orEmpty()
                        }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onQueueSong, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.library_add),
                        contentDescription = stringResource(R.string.together_chat_add_queue),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(MusicTogetherSpacing.xxs))
                IconButton(onClick = onPlaySong, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.together_chat_play_next),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TogetherChatTypingLine(chat: MusicTogetherChatUiModel) {
    val typingResId = chat.typingResId ?: return
    Text(
        text =
            when (chat.typingArgs.size) {
                1 -> stringResource(typingResId, chat.typingArgs[0])
                2 -> stringResource(typingResId, chat.typingArgs[0], chat.typingArgs[1])
                else -> stringResource(typingResId)
            },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TogetherChatComposer(
    draft: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onShareSong: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSubmit = enabled && draft.isNotBlank()

    fun submit() {
        if (!canSubmit) return
        onSend()
        keyboardController?.hide()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xxs),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier =
                Modifier
                    .weight(1f),
            enabled = enabled,
            placeholder = { Text(text = stringResource(R.string.together_chat_hint)) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        IconButton(onClick = onShareSong, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = stringResource(R.string.together_chat_share_song),
            )
        }
        FilledIconButton(
            onClick = { submit() },
            enabled = canSubmit,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = stringResource(R.string.together_chat_send),
            )
        }
    }
}

@Composable
private fun togetherChatMessageText(message: MusicTogetherChatMessageUiModel): String =
    when {
        message.textResId != null && message.args.size == 1 -> stringResource(message.textResId, message.args[0])
        message.textResId != null -> stringResource(message.textResId)
        else -> message.text.orEmpty()
    }

