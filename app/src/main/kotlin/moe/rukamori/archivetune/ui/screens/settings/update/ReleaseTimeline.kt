/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Stable
internal class TimelineExpansion {
    private val releases = mutableStateMapOf<String, Boolean>()
    private val sections = mutableStateMapOf<String, Boolean>()
    private val sectionPreviews = mutableStateMapOf<String, Boolean>()

    fun seedReleases(keys: List<String>) {
        keys.forEachIndexed { index, key ->
            if (key !in releases) releases[key] = index == 0
        }
    }

    fun isReleaseExpanded(key: String, default: Boolean): Boolean = releases[key] ?: default

    fun toggleRelease(key: String, default: Boolean) {
        releases[key] = !isReleaseExpanded(key, default)
    }

    fun isSectionExpanded(releaseKey: String, sectionId: String): Boolean =
        sections[sectionKey(releaseKey, sectionId)] == true

    fun toggleSection(releaseKey: String, sectionId: String) {
        val key = sectionKey(releaseKey, sectionId)
        sections[key] = sections[key] != true
    }

    fun isSectionShowingAll(releaseKey: String, sectionId: String): Boolean =
        sectionPreviews[sectionKey(releaseKey, sectionId)] == true

    fun toggleSectionPreview(releaseKey: String, sectionId: String) {
        val key = sectionKey(releaseKey, sectionId)
        sectionPreviews[key] = sectionPreviews[key] != true
    }

    private fun sectionKey(releaseKey: String, sectionId: String): String = "$releaseKey#$sectionId"
}

@Composable
internal fun ReleaseTimeline(
    state: ReleaseTimelineState,
    actions: ReleaseTimelineActions,
    listState: LazyListState,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    topPadding: Dp = SettingsDimensions.SectionSpacing,
) {
    val expansion = remember { TimelineExpansion() }
    val releaseKeys = state.entries.map { it.releaseKey }

    LaunchedEffect(releaseKeys) {
        expansion.seedReleases(releaseKeys)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        contentPadding =
            PaddingValues(
                top = topPadding,
                bottom = bottomPadding,
            ),
    ) {
        itemsIndexed(
            items = state.entries,
            key = { _, entry -> "release:${entry.releaseKey}" },
            contentType = { _, _ -> "release_timeline_entry" },
        ) { index, entry ->
            val isLast = index == state.entries.lastIndex
            val isFirst = index == 0
            val versionBaseline = remember(entry.releaseKey) { mutableStateOf<Float?>(null) }
            val isReleaseExpanded = expansion.isReleaseExpanded(entry.releaseKey, entry.isLatest)
            val sections =
                entry.sections.map { section ->
                    section.copy(
                        isExpanded = expansion.isSectionExpanded(entry.releaseKey, section.id),
                        isShowingAll = expansion.isSectionShowingAll(entry.releaseKey, section.id),
                    )
                }
            val adaptedEntry =
                entry.copy(
                    isExpanded = isReleaseExpanded,
                    sections = sections,
                )

            Box(modifier = Modifier.fillMaxWidth()) {
                ReleaseTimelineCard(
                    entry = adaptedEntry,
                    onToggleRelease = { key -> expansion.toggleRelease(key, entry.isLatest) },
                    onToggleSection = expansion::toggleSection,
                    onToggleSectionPreview = expansion::toggleSectionPreview,
                    onOpenRelease = actions.onOpenRelease,
                    onVersionBaseline = { versionBaseline.value = it },
                    modifier =
                        Modifier
                            .padding(
                                start = TimelineRailWidth,
                                bottom = if (isLast) 0.dp else SettingsDimensions.SectionSpacing,
                            ).fillMaxWidth(),
                )
                ReleaseTimelineRail(
                    isFirst = isFirst,
                    isLast = isLast,
                    isLatest = entry.isLatest,
                    isInstalled = entry.isInstalled,
                    nodeBaseline = versionBaseline,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Composable
private fun ReleaseTimelineRail(
    isFirst: Boolean,
    isLast: Boolean,
    isLatest: Boolean,
    isInstalled: Boolean,
    nodeBaseline: State<Float?>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val installedColor = Color(0xFF81C784)
    val nodeColor =
        when {
            isInstalled -> installedColor
            isLatest -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
    val pulse = rememberInstalledPulse(isInstalled && !LocalAnimationsDisabled.current)

    Canvas(modifier = modifier) {
        val nodeY = nodeBaseline.value ?: return@Canvas
        val centerX = TimelineRailWidth.toPx() / 2f
        val nodeRadius = TimelineNodeRadius.toPx()
        val nodeGap = TimelineNodeGap.toPx()
        val strokeWidth = TimelineLineWidth.toPx()

        if (!isFirst) {
            drawLine(
                color = lineColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, nodeY - nodeRadius - nodeGap),
                strokeWidth = strokeWidth,
            )
        }
        if (!isLast) {
            drawLine(
                color = lineColor,
                start = Offset(centerX, nodeY + nodeRadius + nodeGap),
                end = Offset(centerX, size.height),
                strokeWidth = strokeWidth,
            )
        }

        if (isInstalled) {
            val pulseAlpha = pulse?.alpha?.value ?: InstalledPulseRestingAlpha
            val pulseRadius = pulse?.radius?.value ?: InstalledPulseRestingRadius
            drawCircle(
                color = installedColor.copy(alpha = pulseAlpha),
                radius = nodeRadius + pulseRadius.dp.toPx(),
                center = Offset(centerX, nodeY),
            )
        }

        drawCircle(
            color = nodeColor,
            radius = nodeRadius,
            center = Offset(centerX, nodeY),
        )
    }
}

@Composable
private fun rememberInstalledPulse(enabled: Boolean): InstalledPulse? {
    if (!enabled) return null

    val transition = rememberInfiniteTransition(label = "installed_node_pulse")
    return InstalledPulse(
        alpha =
            transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.45f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1400),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "installed_node_alpha",
            ),
        radius =
            transition.animateFloat(
                initialValue = 2f,
                targetValue = 5.5f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1400),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "installed_node_radius",
            ),
    )
}

private data class InstalledPulse(
    val alpha: State<Float>,
    val radius: State<Float>,
)

private val TimelineRailWidth = 20.dp
private val TimelineNodeRadius = 3.5.dp
private val TimelineNodeGap = 4.dp
private val TimelineLineWidth = 1.5.dp
private const val InstalledPulseRestingAlpha = 0.3f
private const val InstalledPulseRestingRadius = 2f
