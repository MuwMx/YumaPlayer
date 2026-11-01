/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats.route

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.screens.stats.StatsScreen as StatsScreenImpl
import moe.rukamori.archivetune.ui.screens.stats.StatsViewModel

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    StatsScreenImpl(
        navController = navController,
        viewModel = viewModel,
    )
}
