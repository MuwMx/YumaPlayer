package moe.rukamori.archivetune.viewmodels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.about.AboutDependencyLicense
import moe.rukamori.archivetune.about.AboutDependencyLicenseCollection
import moe.rukamori.archivetune.about.FetchAboutDependencyLicensesUseCase
import moe.rukamori.archivetune.currentBuildHash
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AboutScreenState {
    data object Loading : AboutScreenState
    data class Success(val model: AboutUiModel) : AboutScreenState
    data object Empty : AboutScreenState
    data class Error(@StringRes val messageResId: Int) : AboutScreenState
}

@Immutable
data class AboutUiModel(
    @StringRes val appNameResId: Int,
    val versionName: String,
    val buildHash: String?,
    val buildVariant: String,
    val primaryLinks: AboutLinkCollection,
    val leadDeveloper: TeamMember,
    val dependencyLicensesState: AboutDependencyLicensesUiState,
    val isOverflowMenuExpanded: Boolean,
    val activeDialog: AboutDialog,
)

@Immutable
data class TeamMember(
    val avatarUrl: String,
    val name: String,
    @StringRes val positionResId: Int,
    val profileUrl: String?,
    val links: AboutLinkCollection,
)

@Immutable
data class AboutLinkUiModel(
    val id: String,
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int,
    val url: String,
)

@Immutable
data class AboutLinkCollection private constructor(
    private val values: List<AboutLinkUiModel>,
) {
    val size: Int get() = values.size
    operator fun get(index: Int): AboutLinkUiModel = values[index]
    companion object {
        val Empty = AboutLinkCollection(emptyList())
        fun of(vararg values: AboutLinkUiModel): AboutLinkCollection = AboutLinkCollection(values.toList())
    }
}

sealed interface AboutDependencyLicensesUiState {
    data object Loading : AboutDependencyLicensesUiState
    data class Success(val licenses: AboutDependencyLicenseUiCollection) : AboutDependencyLicensesUiState
    data object Empty : AboutDependencyLicensesUiState
    data class Error(@StringRes val messageResId: Int) : AboutDependencyLicensesUiState
}

@Immutable
data class AboutDependencyLicenseUiModel(
    val name: String,
    val version: String?,
    val licenses: String?,
)

@Immutable
data class AboutDependencyLicenseUiCollection private constructor(
    private val values: List<AboutDependencyLicenseUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()
    operator fun get(index: Int): AboutDependencyLicenseUiModel = values[index]
    companion object {
        fun from(values: List<AboutDependencyLicenseUiModel>): AboutDependencyLicenseUiCollection =
            AboutDependencyLicenseUiCollection(values.toList())
    }
}

enum class AboutDialog {
    NONE,
    DEPENDENCY_LICENSES,
}

sealed interface AboutScreenEffect {
    data class OpenUri(val uri: String) : AboutScreenEffect
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val fetchDependencyLicenses: FetchAboutDependencyLicensesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AboutScreenState>(AboutScreenState.Loading)
    val state: StateFlow<AboutScreenState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AboutScreenEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var dependencyLicensesJob: Job? = null
    private var dependencyLicensesState: AboutDependencyLicensesUiState = AboutDependencyLicensesUiState.Loading
    private var isOverflowMenuExpanded = false
    private var activeDialog = AboutDialog.NONE

    init {
        updateState()
    }

    fun showOverflowMenu() { isOverflowMenuExpanded = true; updateState() }
    fun dismissOverflowMenu() { isOverflowMenuExpanded = false; updateState() }

    fun openDependencyLicenses() {
        isOverflowMenuExpanded = false
        activeDialog = AboutDialog.DEPENDENCY_LICENSES
        updateState()
        loadDependencyLicenses()
    }

    fun dismissDialog() { activeDialog = AboutDialog.NONE; updateState() }
    fun retryDependencyLicenses() { loadDependencyLicenses(force = true) }

    fun openUri(uri: String) {
        if (uri.isBlank()) return
        _effects.tryEmit(AboutScreenEffect.OpenUri(uri))
    }

    private fun loadDependencyLicenses(force: Boolean = false) {
        if (!force && dependencyLicensesJob?.isActive == true) return
        if (!force && dependencyLicensesState is AboutDependencyLicensesUiState.Success) return
        dependencyLicensesJob?.cancel()
        dependencyLicensesState = AboutDependencyLicensesUiState.Loading
        updateState()
        dependencyLicensesJob = viewModelScope.launch(Dispatchers.IO) {
            dependencyLicensesState = try {
                fetchDependencyLicenses().fold(
                    onSuccess = { licenses ->
                        val licenseUiModels = licenses.toUiCollection()
                        if (licenseUiModels.isEmpty) AboutDependencyLicensesUiState.Empty
                        else AboutDependencyLicensesUiState.Success(licenseUiModels)
                    },
                    onFailure = { AboutDependencyLicensesUiState.Error(R.string.error_unknown) }
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                AboutDependencyLicensesUiState.Error(R.string.error_unknown)
            }
            updateState()
        }
    }

    private fun updateState() {
        _state.value = AboutScreenState.Success(buildUiModel())
    }

    private fun buildUiModel(): AboutUiModel = AboutUiModel(
        appNameResId = R.string.app_name,
        versionName = BuildConfig.VERSION_NAME,
        buildHash = currentBuildHash,
        buildVariant = if (BuildConfig.DEBUG) "DEBUG" else BuildConfig.ARCHITECTURE.uppercase(),
        primaryLinks = AboutLinkCollection.of(
            AboutLinkUiModel(
                id = "telegram",
                iconResId = R.drawable.ic_telegram,
                labelResId = R.string.about_content_desc_telegram,
                url = "https://t.me/YOUR_TELEGRAM", // 👈 ЗАМЕНИ НА СВОЙ ТЕЛЕГРАМ
            ),
            AboutLinkUiModel(
                id = "github",
                iconResId = R.drawable.ic_github,
                labelResId = R.string.about_content_desc_github,
                url = "https://github.com/MuwMx/YumaPlayer", // 👈 ЗАМЕНИ НА СВОЙ РЕПОЗИТОРИЙ
            ),
            AboutLinkUiModel(
                id = "donate",
                iconResId = R.drawable.ic_coffe,
                labelResId = R.string.about_content_desc_donate,
                url = "https://ko-fi.com/muwmix", // 👈 ЗАМЕНИ НА СВОЙ ДОНАТ
            ),
        ),
        leadDeveloper = TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/138480557?v=4", // 👈 ЗАМЕНИ НА СВОЙ ID
            name = "MuwMix", // 👈 ТВОЙ НИКНЕЙМ
            positionResId = R.string.about_position_lead_dev,
            profileUrl = "https://github.com/MuwMx",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/MuwMx",
                ),
            ),
        ),
        dependencyLicensesState = dependencyLicensesState,
        isOverflowMenuExpanded = isOverflowMenuExpanded,
        activeDialog = activeDialog,
    )

    private fun AboutDependencyLicenseCollection.toUiCollection(): AboutDependencyLicenseUiCollection {
        val licenses = ArrayList<AboutDependencyLicenseUiModel>(size)
        for (index in 0 until size) {
            licenses.add(this[index].toUiModel())
        }
        return AboutDependencyLicenseUiCollection.from(licenses)
    }

    private fun AboutDependencyLicense.toUiModel(): AboutDependencyLicenseUiModel =
        AboutDependencyLicenseUiModel(name = name, version = version, licenses = licenses)
}