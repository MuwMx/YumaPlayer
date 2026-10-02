/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

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
    val collaborators: TeamMemberCollection,
    val respecters: TeamMemberCollection,
    val contributorsState: AboutContributorsUiState,
    val contributorsReadMoreUrl: String,
    val dependencyLicensesState: AboutDependencyLicensesUiState,
    val translationContributorsState: AboutTranslationContributorsUiState,
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
data class TeamMemberCollection private constructor(
    private val values: List<TeamMember>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): TeamMember = values[index]

    companion object {
        val Empty = TeamMemberCollection(emptyList())

        fun of(vararg values: TeamMember): TeamMemberCollection = TeamMemberCollection(values.toList())
        fun from(values: List<TeamMember>): TeamMemberCollection = TeamMemberCollection(values.toList())
    }
}

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

sealed interface AboutContributorsUiState {
    data object Loading : AboutContributorsUiState
    data class Success(val contributors: AboutContributorUiCollection) : AboutContributorsUiState
    data object Empty : AboutContributorsUiState
    data class Error(@StringRes val messageResId: Int) : AboutContributorsUiState
}

@Immutable
data class AboutContributorUiModel(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

@Immutable
data class AboutContributorUiCollection private constructor(
    private val values: List<AboutContributorUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutContributorUiModel = values[index]

    fun forEach(action: (AboutContributorUiModel) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutContributorUiCollection(emptyList())

        fun from(values: List<AboutContributorUiModel>): AboutContributorUiCollection =
            AboutContributorUiCollection(values.toList())
    }
}

sealed interface AboutTranslationContributorsUiState {
    data object Loading : AboutTranslationContributorsUiState
    data class Success(val contributors: AboutTranslationContributorUiCollection) : AboutTranslationContributorsUiState
    data object Empty : AboutTranslationContributorsUiState
    data class Error(@StringRes val messageResId: Int) : AboutTranslationContributorsUiState
}

@Immutable
data class AboutTranslationContributorUiModel(
    val language: String,
    val contributors: String?,
)

@Immutable
data class AboutTranslationContributorUiCollection private constructor(
    private val values: List<AboutTranslationContributorUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutTranslationContributorUiModel = values[index]

    companion object {
        val Empty = AboutTranslationContributorUiCollection(emptyList())

        fun from(values: List<AboutTranslationContributorUiModel>): AboutTranslationContributorUiCollection =
            AboutTranslationContributorUiCollection(values.toList())
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
    TRANSLATION_CONTRIBUTORS,
    DEPENDENCY_LICENSES,
}

sealed interface AboutScreenEffect {
    data class OpenUri(val uri: String) : AboutScreenEffect
}
