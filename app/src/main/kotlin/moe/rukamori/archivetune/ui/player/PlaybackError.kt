/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.login.buildLoginRoute
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun PlaybackErrorDialog(
    error: PlaybackException,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onLogin: (() -> Unit)? = null,
    onConfirm: ((String) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(SettingsDimensions.ScreenBottomPadding)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = minOf(420.dp, maxWidth)
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
                color = AlertDialogDefaults.containerColor,
                border = BorderStroke(
                    width = SettingsDimensions.GlassBorderThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                PlaybackError(
                    error = error,
                    onRetry = onRetry,
                    onDismiss = onDismiss,
                    onLogin = onLogin,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

@Composable
fun PlaybackError(
    error: PlaybackException,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onLogin: (() -> Unit)? = null,
    onConfirm: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val fallbackUnknown = stringResource(R.string.error_unknown)
    val fallbackNoInternet = stringResource(R.string.error_no_internet)
    val fallbackTimeout = stringResource(R.string.error_timeout)
    val fallbackNoStream = stringResource(R.string.error_no_stream)
    val fallbackMalformedStream = stringResource(R.string.error_malformed_stream)
    val retryText = stringResource(R.string.retry)
    val dismissText = stringResource(R.string.dismiss)
    val copyText = stringResource(R.string.copy)
    val copiedText = stringResource(R.string.copied)
    val loginText = stringResource(R.string.login)
    val detailsText = stringResource(R.string.details)
    val codeLabel = stringResource(R.string.playback_error_code)
    val httpLabel = stringResource(R.string.playback_error_http)
    val messageLabel = stringResource(R.string.playback_error_message)
    val causeLabel = stringResource(R.string.playback_error_cause)

    val errorInfo = remember(error) { error.toPlaybackErrorInfo() }
    val httpCode = errorInfo.httpCode
    val title =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> stringResource(R.string.playback_login_refresh_required)
            PlaybackErrorKind.ConfirmationRequired -> stringResource(R.string.playback_confirmation_required)
            PlaybackErrorKind.NoInternet -> fallbackNoInternet
            PlaybackErrorKind.Timeout -> fallbackTimeout
            PlaybackErrorKind.NoStream -> fallbackNoStream
            PlaybackErrorKind.MalformedStream -> fallbackMalformedStream
            else -> fallbackUnknown
        }
    val reason =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> {
                stringResource(R.string.playback_requires_youtube_music_login_refresh)
            }

            PlaybackErrorKind.ConfirmationRequired -> {
                stringResource(R.string.playback_requires_youtube_music_confirmation)
            }

            PlaybackErrorKind.NoInternet -> {
                fallbackNoInternet
            }

            PlaybackErrorKind.Timeout -> {
                fallbackTimeout
            }

            PlaybackErrorKind.NoStream -> {
                fallbackNoStream
            }

            PlaybackErrorKind.MalformedStream -> {
                fallbackMalformedStream
            }

            PlaybackErrorKind.Decoder -> {
                "$fallbackUnknown ($codeLabel ${error.errorCode})"
            }

            PlaybackErrorKind.Http -> {
                "$fallbackUnknown ($httpLabel $httpCode)"
            }

            PlaybackErrorKind.Unknown -> {
                error.cause?.message?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                    ?: fallbackUnknown
            }
        }

    val details =
        remember(error, reason, httpCode, codeLabel, httpLabel, messageLabel, causeLabel) {
            buildPlaybackErrorDetails(
                error = error,
                reason = reason,
                httpCode = httpCode,
                codeLabel = codeLabel,
                httpLabel = httpLabel,
                messageLabel = messageLabel,
                causeLabel = causeLabel,
            )
        }

    val recoveryUrl = errorInfo.loginRecoveryUrl

    val handleLogin: () -> Unit = remember(onLogin, onDismiss, context, recoveryUrl) {
        {
            onDismiss()
            if (onLogin != null) {
                onLogin()
            } else {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("navigate_to", buildLoginRoute(recoveryUrl))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
    }

    val onCopyClick =
        remember(clipboard, context, details, copiedText) {
            {
                clipboard.setText(AnnotatedString(details))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            }
        }

    val isAuthError = errorInfo.kind == PlaybackErrorKind.LoginRefreshRequired ||
        errorInfo.kind == PlaybackErrorKind.ConfirmationRequired

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsDimensions.ScreenBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(
                painter = painterResource(
                    if (isAuthError) R.drawable.login else R.drawable.ic_about
                ),
                contentDescription = null,
                tint = if (isAuthError) {
                    MaterialTheme.colorScheme.primary
                } else {
                    AlertDialogDefaults.iconContentColor
                },
                modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
            )
        }

        Spacer(Modifier.height(SettingsDimensions.ScreenHorizontalPadding))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(SettingsDimensions.ScreenHorizontalPadding))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PlaybackErrorDetails(
                label = detailsText,
                details = details,
                copyText = copyText,
                onCopyClick = onCopyClick,
            )
        }

        Spacer(Modifier.height(SettingsDimensions.ScreenBottomPadding))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.FloatingBarBottomSpacing, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.FloatingBarBottomSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = dismissText)
            }

            if (isAuthError) {
                TextButton(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = retryText)
                }
                Button(
                    onClick = handleLogin,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = loginText)
                }
            } else {
                Button(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = retryText)
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorDetails(
    label: String,
    details: String,
    copyText: String,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.FloatingBarBottomSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onCopyClick,
                    contentPadding = PaddingValues(
                        horizontal = SettingsDimensions.BadgePaddingH,
                        vertical = SettingsDimensions.BadgePaddingV,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.select_all),
                        contentDescription = null,
                        modifier = Modifier.size(SettingsDimensions.ScreenHorizontalPadding),
                    )
                    Spacer(Modifier.width(SettingsDimensions.BadgePaddingV))
                    Text(
                        text = copyText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun buildPlaybackErrorDetails(
    error: PlaybackException,
    reason: String,
    httpCode: Int?,
    codeLabel: String,
    httpLabel: String,
    messageLabel: String,
    causeLabel: String,
): String =
    buildString {
        appendLine(reason)
        appendLine("$codeLabel: ${error.errorCode}")
        if (httpCode != null) appendLine("$httpLabel: $httpCode")

        val rootMessage = error.message?.trim().orEmpty()
        if (rootMessage.isNotBlank() && rootMessage != reason) {
            appendLine()
            appendLine("$messageLabel: $rootMessage")
        }

        var throwable: Throwable? = error.cause
        var depth = 0
        while (throwable != null && depth < PlaybackErrorMaxCauseDepth) {
            val name = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
            val message = throwable.message?.trim().orEmpty()
            appendLine()
            appendLine("$causeLabel: $name${if (message.isNotBlank()) ": $message" else ""}")
            throwable = throwable.cause
            depth++
        }
    }.trim()

private const val PlaybackErrorMaxCauseDepth = 6
