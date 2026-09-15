package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder

@Composable
fun ChartsShimmer(
    modifier: Modifier = Modifier,
) {
    ShimmerHost(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            TextPlaceholder(
                height = 36.dp,
                modifier =
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(0.5f),
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val horizontalLazyGridItemWidthFactor = chartsGridItemWidthFactor(maxWidth)
                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 4.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4),
                ) {
                    items(4) {
                        Row(
                            modifier =
                                Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(ListItemHeight - 16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.onSurface),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .height(16.dp)
                                            .width(120.dp)
                                            .background(MaterialTheme.colorScheme.onSurface),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier =
                                        Modifier
                                            .height(12.dp)
                                            .width(80.dp)
                                            .background(MaterialTheme.colorScheme.onSurface),
                                )
                            }
                        }
                    }
                }
            }
            TextPlaceholder(
                height = 36.dp,
                modifier =
                    Modifier
                        .padding(vertical = 12.dp, horizontal = 12.dp)
                        .width(250.dp),
            )
            Row {
                repeat(2) {
                    GridItemPlaceHolder()
                }
            }
        }
    }
}
