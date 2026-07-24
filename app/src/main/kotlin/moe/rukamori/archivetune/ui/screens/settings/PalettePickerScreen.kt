/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

// Оставляем только базовые сочные акценты для фолбэка приложения
val TargetAccentColors = listOf(
    Color(0xFFED5564), // Дефолтный красный Yuma
    Color(0xFF4A90D9), // Синий
    Color(0xFF1DB954), // Зелёный
    Color(0xFF9B59B6), // Пурпурный
    Color(0xFFE67E22), // Оранжевый
    Color(0xFF1ABC9C), // Бирюзовый
    Color(0xFF708090), // Слейт
    Color(0xFFFF69B4)  // Розовый
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePickerScreen(navController: NavController) {
    val (customThemeColor, onCustomThemeColorChange) = rememberPreference(
        CustomThemeColorKey,
        defaultValue = "#ED5564"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.color_palette)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Выберите базовый цвет приложения (используется, когда нет обложки трека):",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(TargetAccentColors) { color ->
                    val hexString = String.format("#%06X", 0xFFFFFF and color.toArgb())
                    val isSelected = customThemeColor == hexString || (customThemeColor == "default" && hexString == "#ED5564")

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onCustomThemeColorChange(hexString)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = if (color.red * 0.2126 + color.green * 0.7152 + color.blue * 0.0722 > 0.5) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Заглушка экрана создания темы, чтобы не ломать граф навигации в MainActivity
@Composable
fun ThemeCreatorScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        Toast.makeText(context, "Используется автоматический Monet-движок Юмы", Toast.LENGTH_SHORT).show()
        navController.navigateUp()
    }
    Box(modifier = Modifier.fillMaxSize())
}