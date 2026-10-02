/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.about.AboutContributor
import moe.rukamori.archivetune.about.AboutContributorCollection
import moe.rukamori.archivetune.about.AboutDependencyLicense
import moe.rukamori.archivetune.about.AboutDependencyLicenseCollection
import moe.rukamori.archivetune.about.AboutTranslationContributor
import moe.rukamori.archivetune.about.AboutTranslationContributorCollection
import moe.rukamori.archivetune.currentBuildHash

internal const val MaxDisplayedContributors = 20
internal const val ContributorsReadMoreUrl = "https://github.com/MuwMx/YumaPlayer/graphs/contributors"

internal fun buildAboutUiModel(
    contributorsState: AboutContributorsUiState,
    dependencyLicensesState: AboutDependencyLicensesUiState,
    translationContributorsState: AboutTranslationContributorsUiState,
    isOverflowMenuExpanded: Boolean,
    activeDialog: AboutDialog,
): AboutUiModel = AboutUiModel(
    appNameResId = R.string.app_name,
    versionName = BuildConfig.VERSION_NAME,
    buildHash = currentBuildHash,
    buildVariant = if (BuildConfig.DEBUG) "DEBUG" else BuildConfig.ARCHITECTURE.uppercase(),
    primaryLinks = AboutLinkCollection.of(
        AboutLinkUiModel(
            id = "telegram",
            iconResId = R.drawable.ic_telegram,
            labelResId = R.string.about_content_desc_telegram,
            url = "https://t.me/yumaplayer",
        ),
        AboutLinkUiModel(
            id = "github",
            iconResId = R.drawable.ic_github,
            labelResId = R.string.about_content_desc_github,
            url = "https://github.com/MuwMx/YumaPlayer",
        ),
        AboutLinkUiModel(
            id = "donate",
            iconResId = R.drawable.ic_coffe,
            labelResId = R.string.about_content_desc_donate,
            url = "https://ko-fi.com/muwmix",
        ),
    ),
    leadDeveloper = TeamMember(
        avatarUrl = "https://avatars.githubusercontent.com/u/138480557?v=4",
        name = "MuwMix",
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
    collaborators = TeamMemberCollection.of(
        TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/89002922?v=4",
            name = "Miko",
            positionResId = R.string.about_position_developers,
            profileUrl = "https://github.com/mikooochi",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/mikooochi",
                ),
            ),
        ),
        TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/195509093?v=4",
            name = "Shino",
            positionResId = R.string.about_position_developers,
            profileUrl = "https://github.com/shinonatsukii",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/shinonatsukii",
                ),
                AboutLinkUiModel(
                    id = "telegram",
                    iconResId = R.drawable.ic_telegram,
                    labelResId = R.string.about_content_desc_telegram,
                    url = "https://t.me/shinonatsukii",
                ),
            ),
        ),
        TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/93458424?v=4",
            name = "WTTexe",
            positionResId = R.string.about_position_developers,
            profileUrl = "https://github.com/Windowstechtips",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/Windowstechtips",
                ),
                AboutLinkUiModel(
                    id = "discord",
                    iconResId = R.drawable.alternate_email,
                    labelResId = R.string.about_content_desc_discord,
                    url = "https://discord.com/users/840839409640800258",
                ),
            ),
        ),
        TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/203143605?v=4",
            name = "Yuki/Reze",
            positionResId = R.string.about_position_developers,
            profileUrl = "https://github.com/4nx3b",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/4nx3b",
                ),
            ),
        ),
    ),
    respecters = TeamMemberCollection.of(
        TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/80542861?v=4",
            name = "MO AGAMY",
            positionResId = R.string.about_position_mo_agamy,
            profileUrl = "https://github.com/mostafaalagamy",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/mostafaalagamy",
                ),
            ),
        ),
        TeamMember(
            avatarUrl = "https://avatars.githubusercontent.com/u/110614797?v=4",
            name = "Zion Huang",
            positionResId = R.string.about_position_zion_huang,
            profileUrl = "https://github.com/z-huang",
            links = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/z-huang",
                ),
            ),
        ),
    ),
    contributorsState = contributorsState,
    contributorsReadMoreUrl = ContributorsReadMoreUrl,
    dependencyLicensesState = dependencyLicensesState,
    translationContributorsState = translationContributorsState,
    isOverflowMenuExpanded = isOverflowMenuExpanded,
    activeDialog = activeDialog,
)

internal fun AboutContributorCollection.toUiCollection(): AboutContributorUiCollection {
    val contributors = ArrayList<AboutContributorUiModel>(MaxDisplayedContributors)
    forEach { contributor ->
        contributors.add(contributor.toUiModel())
    }
    return AboutContributorUiCollection.from(contributors)
}

internal fun AboutContributor.toUiModel(): AboutContributorUiModel =
    AboutContributorUiModel(
        login = login,
        avatarUrl = avatarUrl,
        profileUrl = profileUrl,
    )

internal fun AboutTranslationContributorCollection.toUiCollection(): AboutTranslationContributorUiCollection {
    val contributors = ArrayList<AboutTranslationContributorUiModel>(size)
    for (index in 0 until size) {
        contributors.add(this[index].toUiModel())
    }
    return AboutTranslationContributorUiCollection.from(contributors)
}

internal fun AboutTranslationContributor.toUiModel(): AboutTranslationContributorUiModel =
    AboutTranslationContributorUiModel(
        language = language,
        contributors = contributors.joinToString().takeIf(String::isNotBlank),
    )

internal fun AboutDependencyLicenseCollection.toUiCollection(): AboutDependencyLicenseUiCollection {
    val licenses = ArrayList<AboutDependencyLicenseUiModel>(size)
    for (index in 0 until size) {
        licenses.add(this[index].toUiModel())
    }
    return AboutDependencyLicenseUiCollection.from(licenses)
}

internal fun AboutDependencyLicense.toUiModel(): AboutDependencyLicenseUiModel =
    AboutDependencyLicenseUiModel(name = name, version = version, licenses = licenses)
