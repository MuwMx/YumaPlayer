/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models.body

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.Context

@Serializable
data class AccountsListBody(
    val context: Context,
    val requestType: String = "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER",
    val callCircumstance: String = "SWITCHING_USERS_FULL",
)
