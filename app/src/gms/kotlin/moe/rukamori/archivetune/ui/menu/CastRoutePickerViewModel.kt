/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import timber.log.Timber

internal enum class CastEmptyReason {
    NO_DEVICES,
    NO_PLAY_SERVICES,
    NO_NETWORK,
}

internal sealed interface CastRoutePickerScreenState {
    data object Loading : CastRoutePickerScreenState

    data class Success(
        val routes: List<CastRouteUiModel>,
    ) : CastRoutePickerScreenState

    data class Empty(
        val reason: CastEmptyReason = CastEmptyReason.NO_DEVICES,
    ) : CastRoutePickerScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : CastRoutePickerScreenState
}

@Immutable
internal data class CastRouteUiModel(
    val id: String,
    val name: String,
    val description: String?,
    val selected: Boolean,
    val enabled: Boolean,
    val connecting: Boolean,
)

internal class CastRoutePickerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val router = MediaRouter.getInstance(application)
    private val _screenState = MutableStateFlow<CastRoutePickerScreenState>(CastRoutePickerScreenState.Loading)
    private val _castState = MutableStateFlow(CastState.NO_DEVICES_AVAILABLE)
    val castState: StateFlow<Int> = _castState.asStateFlow()

    private var castContext: CastContext? = null
    private var selector: MediaRouteSelector? = null
    private var callback: MediaRouter.Callback? = null
    private var emptyStateJob: Job? = null

    val screenState: StateFlow<CastRoutePickerScreenState> = _screenState.asStateFlow()

    private val castStateListener = CastStateListener { state ->
        _castState.value = state
        Timber.tag("Cast").d("Cast state changed: $state")
        refreshRoutes()
    }

    private fun isNetworkConnected(): Boolean =
        runCatching {
            val cm = getApplication<Application>().getSystemService(Application.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return@runCatching false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)

    fun startDiscovery() {
        if (callback != null) {
            refreshRoutes()
            return
        }

        if (!isNetworkConnected()) {
            _screenState.value = CastRoutePickerScreenState.Empty(CastEmptyReason.NO_NETWORK)
            return
        }

        val context =
            runCatching { CastContext.getSharedInstance(getApplication<Application>()) }
                .onFailure { Timber.tag("Cast").w(it, "Unable to start Cast route discovery") }
                .getOrNull()

        if (context == null) {
            _screenState.value = CastRoutePickerScreenState.Empty(CastEmptyReason.NO_PLAY_SERVICES)
            return
        }

        castContext = context
        context.addCastStateListener(castStateListener)
        _castState.value = context.castState

        val castSelector =
            runCatching { context.mergedSelector }
                .onFailure { Timber.tag("Cast").w(it, "Unable to get Cast mergedSelector") }
                .getOrNull()

        if (castSelector == null) {
            _screenState.value = CastRoutePickerScreenState.Empty(CastEmptyReason.NO_PLAY_SERVICES)
            return
        }

        selector = castSelector
        val routeCallback =
            object : MediaRouter.Callback() {
                override fun onRouteAdded(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteRemoved(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteChanged(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteSelected(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                    reason: Int,
                ) = refreshRoutes()

                override fun onRouteUnselected(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                    reason: Int,
                ) = refreshRoutes()
            }

        callback = routeCallback
        router.addCallback(
            castSelector,
            routeCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        refreshRoutes()
    }

    fun stopDiscovery() {
        castContext?.removeCastStateListener(castStateListener)
        castContext = null
        emptyStateJob?.cancel()
        emptyStateJob = null
        callback?.let(router::removeCallback)
        callback = null
        selector = null
        _screenState.value = CastRoutePickerScreenState.Loading
    }

    fun selectRoute(routeId: String): Boolean {
        val castSelector = selector ?: return false
        val route =
            router.routes.firstOrNull {
                it.id == routeId && it.isSelectableCastRoute(castSelector)
            } ?: return false
        if (route == router.selectedRoute || route.isSelected) return false
        router.selectRoute(route)
        refreshRoutes()
        return true
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }

    private fun refreshRoutes() {
        val castSelector = selector ?: return
        val routes =
            router.routes
                .asSequence()
                .filter { it.isSelectableCastRoute(castSelector) }
                .map { it.toUiModel(router.selectedRoute == it || it.isSelected) }
                .sortedWith(compareByDescending<CastRouteUiModel> { it.selected }.thenBy { it.name.lowercase() })
                .toList()

        if (routes.isEmpty()) {
            if (!isNetworkConnected()) {
                emptyStateJob?.cancel()
                emptyStateJob = null
                _screenState.value = CastRoutePickerScreenState.Empty(CastEmptyReason.NO_NETWORK)
            } else {
                if (_screenState.value !is CastRoutePickerScreenState.Empty) {
                    _screenState.value = CastRoutePickerScreenState.Loading
                    scheduleEmptyState()
                }
            }
        } else {
            emptyStateJob?.cancel()
            emptyStateJob = null
            _screenState.value = CastRoutePickerScreenState.Success(routes)
        }
    }

    private fun scheduleEmptyState() {
        if (emptyStateJob?.isActive == true) return
        emptyStateJob =
            viewModelScope.launch {
                delay(3_500)
                val castSelector = selector ?: return@launch
                if (router.routes.none { it.isSelectableCastRoute(castSelector) }) {
                    val reason =
                        when {
                            !isNetworkConnected() -> CastEmptyReason.NO_NETWORK
                            castContext == null -> CastEmptyReason.NO_PLAY_SERVICES
                            else -> CastEmptyReason.NO_DEVICES
                        }
                    _screenState.value = CastRoutePickerScreenState.Empty(reason)
                }
            }
    }

    private fun MediaRouter.RouteInfo.isSelectableCastRoute(selector: MediaRouteSelector): Boolean {
        val defaultRoute = router.defaultRoute
        return isEnabled &&
            this != defaultRoute &&
            id != defaultRoute.id &&
            !isDefault &&
            !isBluetooth &&
            !isSystemRoute &&
            matchesSelector(selector)
    }

    private fun MediaRouter.RouteInfo.toUiModel(selected: Boolean) =
        CastRouteUiModel(
            id = id,
            name = name.toString(),
            description = description?.toString()?.takeIf(String::isNotBlank),
            selected = selected,
            enabled = isEnabled,
            connecting = connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
        )
}
