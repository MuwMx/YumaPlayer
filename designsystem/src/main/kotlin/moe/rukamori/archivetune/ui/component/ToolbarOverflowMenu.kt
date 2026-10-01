package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.designsystem.R

@Composable
internal fun ToolbarOverflowMenu(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    onMusicTogetherClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (pureBlack) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = {
                    expanded = false
                    onMusicRecognitionClick?.invoke()
                },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.mic), contentDescription = musicRecognitionContentDescription)
                },
                enabled = onMusicRecognitionClick != null,
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together)) },
                onClick = {
                    expanded = false
                    onMusicTogetherClick?.invoke()
                },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.multi_user), contentDescription = null)
                },
                enabled = onMusicTogetherClick != null,
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = {
                        expanded = false
                        onShuffleClick()
                    },
                    leadingIcon = {
                        Icon(painter = painterResource(shuffleIconRes), contentDescription = shuffleContentDescription)
                    },
                )
            }
        }
    }
}
