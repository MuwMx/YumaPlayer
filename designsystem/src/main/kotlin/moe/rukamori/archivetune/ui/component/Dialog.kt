/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.designsystem.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentScrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.heightIn(max = maxHeight),
                shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
                color = AlertDialogDefaults.containerColor,
                border = BorderStroke(
                    width = SettingsDimensions.GlassBorderThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    modifier = modifier.padding(24.dp),
                ) {
                    val bodyModifier =
                        if (contentScrollable) {
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }

                    Column(
                        horizontalAlignment = horizontalAlignment,
                        modifier = bodyModifier,
                    ) {
                        if (icon != null) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
                                Box(
                                    Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    icon()
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                        if (title != null) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                    Box(
                                        Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally),
                                    ) {
                                        title()
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                        }

                        content()
                    }

                    if (buttons != null) {
                        Spacer(Modifier.height(24.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) flowRowScope@{
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                                ProvideTextStyle(
                                    value = MaterialTheme.typography.labelLarge,
                                ) {
                                    this@flowRowScope.buttons()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.heightIn(max = maxHeight),
                shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
                color = AlertDialogDefaults.containerColor,
                border = BorderStroke(
                    width = SettingsDimensions.GlassBorderThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // title
                        if (titleBar != null) {
                            Row {
                                titleBar()
                            }
                        } else if (title != null) {
                            Text(
                                text = title,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        content() // body
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (onReset != null) {
                            Row(modifier = Modifier.weight(1f)) {
                                TextButton(
                                    onClick = { onReset() },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.yumaClickable(pressedScale = 0.96f, onClick = { onReset() }),
                                ) {
                                    Text(stringResource(R.string.reset))
                                }
                            }
                        }

                        if (onCancel != null) {
                            TextButton(
                                onClick = { onCancel() },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.yumaClickable(pressedScale = 0.96f, onClick = { onCancel() }),
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }

                        TextButton(
                            onClick = { onConfirm() },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.yumaClickable(pressedScale = 0.96f, onClick = { onConfirm() }),
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.heightIn(max = maxHeight),
                shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
                color = AlertDialogDefaults.containerColor,
                border = BorderStroke(
                    width = SettingsDimensions.GlassBorderThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = modifier.padding(vertical = 24.dp),
                ) {
                    LazyColumn(content = content)
                }
            }
        }
    }
}

@Composable
fun InfoLabel(text: String) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_about),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(4.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(), // legacy
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    isInputValid: (String) -> Boolean = { true },
    onDone: (String) -> Unit = {},
    // new multi-field support
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
    isMasked: Boolean = false,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }
    val focusRequester = remember { FocusRequester() }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        contentScrollable = true,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.yumaClickable(pressedScale = 0.96f, onClick = onDismiss),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid =
                textFields?.all { isInputValid(it.second.text) }
                    ?: isInputValid(legacyFieldState.value.text)

            TextButton(
                enabled = isValid,
                onClick = {
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(legacyFieldState.value.text)
                    }
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.yumaClickable(
                    enabled = isValid,
                    pressedScale = 0.96f,
                    onClick = {
                        if (textFields != null && onDoneMultiple != null) {
                            onDoneMultiple(textFields.map { it.second.text })
                        } else {
                            onDone(legacyFieldState.value.text)
                        }
                        onDismiss()
                    },
                ),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    ) {
        Column {
            val maskedTransformation = remember { PasswordVisualTransformation() }
            val visualTransformation = if (isMasked && !isPasswordVisible) {
                maskedTransformation
            } else {
                VisualTransformation.None
            }

            val trailingIcon: (@Composable () -> Unit)? = if (isMasked) {
                {
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPasswordVisible) R.drawable.visibility_off else R.drawable.visibility
                            ),
                            contentDescription = stringResource(
                                if (isPasswordVisible) R.string.spoiler_hide else R.string.spoiler_show
                            ),
                        )
                    }
                }
            } else {
                null
            }

            val fieldContent: @Composable () -> Unit = {
                if (textFields != null) {
                    textFields.forEachIndexed { index, (label, value) ->
                        TextField(
                            value = value,
                            onValueChange = { onTextFieldsChange?.invoke(index, it) },
                            placeholder = { Text(label) },
                            singleLine = singleLine,
                            maxLines = maxLines,
                            colors = OutlinedTextFieldDefaults.colors(),
                            keyboardOptions = keyboardOptions,
                            visualTransformation = visualTransformation,
                            trailingIcon = trailingIcon,
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (onDoneMultiple != null) {
                                            onDoneMultiple(textFields.map { it.second.text })
                                            onDismiss()
                                        }
                                    },
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                        )
                    }
                } else {
                    TextField(
                        value = legacyFieldState.value,
                        onValueChange = { legacyFieldState.value = it },
                        placeholder = placeholder,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions = keyboardOptions,
                        visualTransformation = visualTransformation,
                        trailingIcon = trailingIcon,
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    onDone(legacyFieldState.value.text)
                                    onDismiss()
                                },
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                }
            }

            fieldContent()

            extraContent?.invoke()
        }
    }
}

@Composable
fun EditPlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }

    val canSave by remember {
        derivedStateOf { nameField.text.isNotBlank() }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
        title = { Text(text = stringResource(R.string.edit_playlist)) },
        contentScrollable = true,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.yumaClickable(pressedScale = 0.96f, onClick = onDismiss),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                enabled = canSave,
                onClick = {
                    keyboardController?.hide()
                    onSave(nameField.text.trim())
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.yumaClickable(
                    enabled = canSave,
                    pressedScale = 0.96f,
                    onClick = {
                        keyboardController?.hide()
                        onSave(nameField.text.trim())
                        onDismiss()
                    },
                ),
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
    ) {
        TextField(
            value = nameField,
            onValueChange = { nameField = it },
            placeholder = { Text(text = stringResource(R.string.playlist_name)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (!canSave) return@KeyboardActions
                        keyboardController?.hide()
                        onSave(nameField.text.trim())
                        onDismiss()
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
