package moe.rukamori.archivetune.ui.player.lyrics_0

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.LyricsMenuScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsSwitchRow
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsMenuRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.lyrics.LyricsTranslator
import kotlin.math.roundToInt

@Composable
fun LyricsOptionsMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    // Локальное состояние текущего экрана
    var currentScreen by remember { mutableStateOf(LyricsMenuScreen.MAIN) }

    LaunchedEffect(isVisible) {
        Log.d("SpotLyrics", "LyricsOptionsMenu: isVisible = $isVisible")
        if (!isVisible) {
            currentScreen = LyricsMenuScreen.MAIN
        }
    }

    if (isVisible) {
        BackHandler {
            if (currentScreen == LyricsMenuScreen.MAIN) {
                onDismiss()
            } else {
                currentScreen = LyricsMenuScreen.MAIN
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "LyricsMenuAlpha"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 300f,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = Spring.StiffnessLow
        ),
        label = "LyricsMenuSlide"
    )

    // Автоматический возврат на главный экран при успешном окончании перевода
    val isTranslating = state.isAiTranslating || state.isStandardTranslating
    LaunchedEffect(isTranslating) {
        if (!isTranslating && state.aiTranslationError == null && currentScreen == LyricsMenuScreen.TRANSLATE) {
            currentScreen = LyricsMenuScreen.MAIN
        }
    }

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha * 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (currentScreen == LyricsMenuScreen.MAIN) {
                            onDismiss()
                        } else {
                            currentScreen = LyricsMenuScreen.MAIN
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .graphicsLayer {
                        this.translationY = translateY
                        this.alpha = alpha
                    }
                    .yumaGlassCard(
                        shape = RoundedCornerShape(28.dp),
                        backgroundColor = Color(state.darkMutedColor).copy(alpha = 1f),
                        borderColor = LocalYumaColors.current.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(20.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                val darkScheme = darkColorScheme()
                MaterialTheme(colorScheme = darkScheme) {
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White,
                        LocalYumaColors provides darkYumaColorScheme(darkScheme),
                    ) {
                        AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (targetState != LyricsMenuScreen.MAIN) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "LyricsMenuTransition"
                ) { screen ->
                    when (screen) {
                        LyricsMenuScreen.MAIN -> LyricsMenuMain(
                            state = state,
                            onAction = onAction,
                            onDismiss = onDismiss,
                            onNavigateTo = { currentScreen = it }
                        )
                        LyricsMenuScreen.EDIT -> LyricsMenuEdit(
                            state = state,
                            onAction = onAction,
                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
                        )
                        LyricsMenuScreen.TRANSLATE -> LyricsMenuTranslate(
                            state = state,
                            onAction = onAction,
                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
                        )
                        LyricsMenuScreen.SYNC_OFFSET -> LyricsMenuSyncOffset(
                            state = state,
                            onAction = onAction,
                            onBack = { currentScreen = LyricsMenuScreen.MAIN }
                        )
                    }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsMenuMain(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onDismiss: () -> Unit,
    onNavigateTo: (LyricsMenuScreen) -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    val count = 5

    Column {
        Text(
            text = "Lyrics Options",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        SettingsSwitchRow(
            title = "Auto-Download",
            subtitle = "Automatically search and cache lyrics when a new track starts.",
            checked = state.isAutoDownloadEnabled,
            onCheckedChange = { onAction(PlayerAction.ToggleAutoDownload) },
            vibrantColor = Color(state.vibrantColor),
            index = 0,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = "Edit Lyrics",
            subtitle = "Modify the lyric lines of this track manually",
            iconResId = R.drawable.edit,
            onClick = {
                onAction(PlayerAction.PrepareLyricsEdit)
                onNavigateTo(LyricsMenuScreen.EDIT)
            },
            index = 1,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = "Translate Lyrics",
            subtitle = "Translate cached lyrics using AI or Translator",
            iconResId = R.drawable.translate,
            onClick = {
                onNavigateTo(LyricsMenuScreen.TRANSLATE)
            },
            index = 2,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = "Sync Offset",
            subtitle = "Shift lyrics timeline backward or forward",
            iconResId = R.drawable.speed,
            onClick = {
                onNavigateTo(LyricsMenuScreen.SYNC_OFFSET)
            },
            index = 3,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = "Search / Refresh Lyrics",
            subtitle = "Find lyrics online and force update cache",
            iconResId = R.drawable.ic_search,
            onClick = {
                onAction(PlayerAction.SearchLyrics)
                onDismiss()
            },
            index = 4,
            count = count,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Close",
            color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .yumaClickable { onDismiss() }
                .padding(6.dp)
        )
    }
}

@Composable
private fun LyricsMenuEdit(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column {
        Text(
            text = "Edit Lyrics",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        OutlinedTextField(
            value = state.lyricsEditText,
            onValueChange = { onAction(PlayerAction.UpdateLyricsEditText(it)) },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = font),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(state.vibrantColor),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBack) {
                Text("CANCEL", color = Color.White, fontFamily = font)
            }
            TextButton(onClick = {
                onAction(PlayerAction.SaveLyrics(state.lyricsEditText))
                onBack()
            }) {
                Text("SAVE", color = Color(state.vibrantColor), fontWeight = FontWeight.Bold, fontFamily = font)
            }
        }
    }
}

@Composable
private fun LyricsMenuTranslate(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    val context = LocalContext.current
    val languages = remember(context) { moe.rukamori.archivetune.utils.TranslatorLanguages.load(context) }

    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)

    val isAiProviderConfigured = aiProvider != AiProvider.NONE
    val isAiTranslationEnabled = isAiProviderConfigured &&
            aiApiKey.isNotBlank() &&
            (aiProvider != moe.rukamori.archivetune.constants.AiProvider.CUSTOM || aiCustomEndpoint.isNotBlank()) &&
            aiValidationStatus != moe.rukamori.archivetune.constants.AiApiValidationStatus.FAILED

    var useAi by remember { mutableStateOf(isAiTranslationEnabled) }
    var langExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.lyricsTranslateLanguage.isEmpty()) {
            val defaultLangCode = context.resources.configuration.locales.get(0)
                .getDisplayLanguage(java.util.Locale.ENGLISH)
                .uppercase(java.util.Locale.US)
                .replace(' ', '_')
            onAction(PlayerAction.UpdateTranslateLanguage(defaultLangCode))
        }
    }

    val selectedLang = languages.find { it.code == state.lyricsTranslateLanguage } ?: languages.firstOrNull()

    Column {
        Text(
            text = "Translate Lyrics",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        if (state.isAiTranslating || state.isStandardTranslating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(state.vibrantColor))
            }
        } else {
            if (state.aiTranslationError != null) {
                Text(
                    text = "Error: ${state.aiTranslationError}",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isAiTranslationEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "Use AI translation",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = useAi,
                        onCheckedChange = { useAi = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(state.vibrantColor),
                            checkedTrackColor = Color(state.vibrantColor).copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Text(
                text = "Target Language:",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedLang?.name ?: "Select Language",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { langExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = font),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { langExpanded = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(state.vibrantColor),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    )
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { langExpanded = true }
                )

                DropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .heightIn(max = 280.dp)
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.name) },
                            onClick = {
                                onAction(PlayerAction.UpdateTranslateLanguage(lang.code))
                                langExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onBack) {
                    Text("CANCEL", color = Color.White, fontFamily = font)
                }
                TextButton(
                    onClick = {
                        onAction(PlayerAction.TranslateLyrics(state.lyricsTranslateLanguage, useAi))
                    }
                ) {
                    Text("TRANSLATE", color = Color(state.vibrantColor), fontWeight = FontWeight.Bold, fontFamily = font)
                }
            }
        }
    }
}

@Composable
private fun LyricsMenuSyncOffset(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    // Временное локальное смещение, инициализируемое значением из стейта
    var tempOffset by remember(state.lyricsSyncOffset) { mutableStateOf(state.lyricsSyncOffset) }

    Column {
        Text(
            text = "Sync Offset",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = LyricsTranslator.formatLyricsSyncOffset(tempOffset),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Slider(
                value = tempOffset.toFloat(),
                onValueChange = { tempOffset = it.roundToInt() },
                valueRange = -1000f..1000f,
                steps = 79,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(state.vibrantColor),
                    activeTrackColor = Color(state.vibrantColor),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-1s", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = font)
                Text("+1s", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = font)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                tempOffset = 0
            }) {
                Text("RESET", color = Color.White, fontFamily = font)
            }
            Row {
                TextButton(onClick = onBack) {
                    Text("CANCEL", color = Color.White, fontFamily = font)
                }
                TextButton(onClick = {
                    onAction(PlayerAction.SetLyricsSyncOffset(tempOffset))
                    onBack()
                }) {
                    Text("APPLY", color = Color(state.vibrantColor), fontWeight = FontWeight.Bold, fontFamily = font)
                }
            }
        }
    }
}
