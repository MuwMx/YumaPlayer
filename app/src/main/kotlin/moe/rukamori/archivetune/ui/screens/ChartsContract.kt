package moe.rukamori.archivetune.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val TOP_MUSIC_VIDEOS_SECTION_TITLE = "Top music videos"
internal const val TRENDING_SECTION_TITLE = "Trending"

internal const val CONTENT_TYPE_CHARTS_HEADER = "charts_header"
internal const val CONTENT_TYPE_CHARTS_GRID = "charts_grid"
internal const val CONTENT_TYPE_CHARTS_ROW = "charts_row"
internal const val CONTENT_TYPE_CHARTS_SONG = "charts_song"
internal const val CONTENT_TYPE_CHARTS_VIDEO = "charts_video"

internal fun chartsGridItemWidthFactor(maxWidth: Dp): Float =
    if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
