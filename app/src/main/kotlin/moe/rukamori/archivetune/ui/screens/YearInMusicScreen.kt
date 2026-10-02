/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.viewmodels.YearInMusicViewModel

internal val RecapBlack = Color(0xFF070707)
internal val RecapSurfaceHigh = Color(0xFF1D1D1D)
internal val RecapRed = Color(0xFFFF0033)
internal val RecapRedDeep = Color(0xFFB60024)
internal val RecapCream = Color(0xFFFFF7EF)
internal val RecapYellow = Color(0xFFFFD447)
internal val RecapGreen = Color(0xFF1ED760)
internal val RecapPurple = Color(0xFF8A2CFF)
internal val RecapBlue = Color(0xFF7CB7FF)
internal val RecapSurface = Color(0xFF121212)
internal val RecapPink = Color(0xFFFF8BDE)
internal val RecapLime = Color(0xFFDFFF3E)
internal val RecapInk = Color(0xFF151515)
internal object RecapTokens {
    val SectionRadius = 24.dp
    val ItemRadius = 18.dp
    val ShareVerticalPadding = 14.dp
}
@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    YearInMusicRoute(
        navController = navController,
        viewModel = viewModel,
        initialYear = initialYear,
    )
}
@Composable
private fun YearInMusicRoute(
    navController: NavController,
    viewModel: YearInMusicViewModel,
    initialYear: Int?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialYear) {
        if (initialYear != null) {
            viewModel.selectYear(initialYear)
        }
    }

    YearInMusicRecapScreen(
        navController = navController,
        uiState = uiState,
    )
}
