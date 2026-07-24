/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.network.NetworkBannerUiState
import moe.rukamori.archivetune.network.ObserveNetworkBannerStateUseCase
import javax.inject.Inject

@HiltViewModel
class NetworkBannerViewModel
    @Inject
    constructor(
        observeNetworkBannerStateUseCase: ObserveNetworkBannerStateUseCase,
    ) : ViewModel() {
        val bannerState: StateFlow<NetworkBannerUiState> =
            observeNetworkBannerStateUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = NetworkBannerUiState.Hidden,
                )
    }
