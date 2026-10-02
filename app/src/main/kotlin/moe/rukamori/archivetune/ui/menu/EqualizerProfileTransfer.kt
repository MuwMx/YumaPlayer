/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.EqProfile
import moe.rukamori.archivetune.playback.EqProfilesPayload
import moe.rukamori.archivetune.playback.EqualizerJson
import java.util.UUID

internal suspend fun exportEqProfileToUri(
    context: Context,
    uri: Uri,
    rawJson: String,
) {
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(rawJson.toByteArray(Charsets.UTF_8))
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.backup_create_success), Toast.LENGTH_SHORT).show()
        }
    }
}

internal data class EqImportResult(
    val updatedPayload: EqProfilesPayload,
    val firstImported: EqProfile,
    val importedCount: Int,
)

internal suspend fun importEqProfilesFromUri(
    context: Context,
    uri: Uri,
    currentProfiles: List<EqProfile>,
): EqImportResult? =
    withContext(Dispatchers.IO) {
        val raw =
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()

        if (raw.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.eq_import_failed), Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        val payload =
            decodeProfilesPayload(raw).takeIf { it.profiles.isNotEmpty() }
                ?: runCatching {
                    EqProfilesPayload(EqualizerJson.json.decodeFromString<List<EqProfile>>(raw))
                }.getOrNull()
                ?: EqProfilesPayload()

        if (payload.profiles.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.eq_import_failed), Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        val existingIds = currentProfiles.map { it.id }.toMutableSet()
        val normalizedImported =
            payload.profiles.map { p ->
                val baseName = p.name.trim().ifBlank { context.getString(R.string.eq_imported_profile) }
                val incomingId = p.id.trim()
                val finalId =
                    if (incomingId.isBlank() || !existingIds.add(incomingId)) {
                        generateSequence { UUID.randomUUID().toString() }.first { existingIds.add(it) }
                    } else {
                        incomingId
                    }
                p.copy(id = finalId, name = baseName)
            }

        val updatedPayload =
            EqProfilesPayload(
                profiles =
                    (currentProfiles + normalizedImported)
                        .distinctBy { it.id }
                        .sortedBy { it.name.lowercase() },
            )

        val firstImported = normalizedImported.firstOrNull() ?: return@withContext null
        EqImportResult(
            updatedPayload = updatedPayload,
            firstImported = firstImported,
            importedCount = normalizedImported.size,
        )
    }

@Composable
internal fun rememberEqualizerExportLauncher(
    pendingJsonProvider: () -> String?,
    onClearPendingJson: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: Context,
) = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
) { uri ->
    val raw = pendingJsonProvider() ?: return@rememberLauncherForActivityResult
    onClearPendingJson()
    if (uri == null) return@rememberLauncherForActivityResult
    coroutineScope.launch {
        exportEqProfileToUri(context, uri, raw)
    }
}

@Composable
internal fun rememberEqualizerImportLauncher(
    profiles: List<EqProfile>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    onImportApplied: (EqProfile, EqProfilesPayload, Int) -> Unit,
) = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    coroutineScope.launch {
        val result = importEqProfilesFromUri(context, uri, profiles) ?: return@launch
        withContext(Dispatchers.Main) {
            onImportApplied(result.firstImported, result.updatedPayload, result.importedCount)
        }
    }
}
