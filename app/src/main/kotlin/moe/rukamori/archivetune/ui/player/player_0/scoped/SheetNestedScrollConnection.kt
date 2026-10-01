package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal fun createNestedScrollConnection(
    handler: SheetVerticalDragGestureHandler,
    canDragProvider: () -> Boolean,
    targetSheet: ActiveDragSheet = ActiveDragSheet.LYRICS
): NestedScrollConnection {
    return object : NestedScrollConnection {
        private var isDraggingFromList = false
        private var accumulatedListDrag = 0f

        private fun finalizeListDrag(velocity: Float = 0f) {
            if (isDraggingFromList) {
                handler.onDragEnd(velocity)
                isDraggingFromList = false
                accumulatedListDrag = 0f
            }
        }

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val targetFractionAnimatable = if (targetSheet == ActiveDragSheet.LYRICS) handler.lyricsFraction else handler.queueFraction

            if (isDraggingFromList) {
                if (available.y < 0f && targetFractionAnimatable.value >= 0.99f && handler.currentSheetTranslationY.value <= handler.expandedYProvider()) {
                    finalizeListDrag()
                    return Offset.Zero
                }
                accumulatedListDrag += available.y
                handler.onVerticalDrag(
                    uptimeMillis = System.currentTimeMillis(),
                    dragAmount = available.y
                )
                return available
            }

            if (available.y > 0f && canDragProvider()) {
                if (!isDraggingFromList) {
                    isDraggingFromList = true
                    accumulatedListDrag = 0f
                    val screenWidth = handler.screenWidthPxProvider()
                    val startX = if (targetSheet == ActiveDragSheet.LYRICS) 0f else screenWidth
                    handler.onDragStart(position = Offset(startX, 0f))
                }
                accumulatedListDrag += available.y
                handler.onVerticalDrag(
                    uptimeMillis = System.currentTimeMillis(),
                    dragAmount = available.y
                )
                return Offset(0f, available.y)
            }

            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isDraggingFromList) {
                if (available.y < 0f) {
                    finalizeListDrag(available.y)
                    return Velocity.Zero
                }
                if (available.y > 0f) {
                    finalizeListDrag(available.y)
                    return available
                }
            }

            if (available.y > 0f && canDragProvider()) {
                if (!isDraggingFromList) {
                    isDraggingFromList = true
                    val screenWidth = handler.screenWidthPxProvider()
                    val startX = if (targetSheet == ActiveDragSheet.LYRICS) 0f else screenWidth
                    handler.onDragStart(position = Offset(startX, 0f))
                }
                finalizeListDrag(available.y)
                return available
            }

            return Velocity.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (isDraggingFromList && source == NestedScrollSource.UserInput && available.y != 0f) {
                accumulatedListDrag += available.y
                handler.onVerticalDrag(
                    uptimeMillis = System.currentTimeMillis(),
                    dragAmount = available.y
                )
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity
        ): Velocity {
            if (isDraggingFromList) {
                finalizeListDrag(available.y)
                return available
            }
            return Velocity.Zero
        }
    }
}
