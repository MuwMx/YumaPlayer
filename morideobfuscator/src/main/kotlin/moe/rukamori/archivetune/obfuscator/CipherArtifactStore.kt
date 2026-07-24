/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.obfuscator

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class CipherArtifactStore(
    private val directory: File,
) {
    private val json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        }
    private val artifactFile = File(directory, ARTIFACT_FILE)

    fun read(): CachedTransformArtifact? =
        runCatching {
            if (!artifactFile.isFile || artifactFile.length() > MAX_CACHE_BYTES) return null
            json
                .decodeFromString<CachedTransformArtifact>(artifactFile.readText())
                .takeIf { it.plan.sourceSha256 == it.playerJavaScript.sha256() }
        }.getOrNull()

    fun write(artifact: CachedTransformArtifact) {
        directory.mkdirs()
        val encoded = json.encodeToString(CachedTransformArtifact.serializer(), artifact)
        if (encoded.toByteArray().size > MAX_CACHE_BYTES) {
            throw MoriCipherException("Compiled player cache exceeded the size limit")
        }
        val temporary = File(directory, "$ARTIFACT_FILE.tmp")
        temporary.writeText(encoded)
        runCatching {
            Files.move(
                temporary.toPath(),
                artifactFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(
                temporary.toPath(),
                artifactFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val ARTIFACT_FILE = "active_player.json"
        const val MAX_CACHE_BYTES = 8_000_000
    }
}
