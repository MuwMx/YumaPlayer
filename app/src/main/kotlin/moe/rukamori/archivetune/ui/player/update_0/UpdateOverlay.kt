package moe.rukamori.archivetune.ui.player.update_0

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.PlayerUiState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue

@Composable
fun CriticalUpdateOverlay(
    versionName: String,
    updateUrl: String,
    state: PlayerUiState
) {
    val uriHandler = LocalUriHandler.current
    val GoogleSans = FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_bold, FontWeight.Bold)
    )

    // Анимация сжатия кнопки
    val downloadInteraction = remember { MutableInteractionSource() }
    val isDownloadPressed by downloadInteraction.collectIsPressedAsState()
    val downloadScale by animateFloatAsState(if (isDownloadPressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))

    BackHandler { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F).copy(alpha = 0.95f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        val cardGradient = Brush.verticalGradient(
            colors = listOf(Color(state.darkMutedColor), Color(0xFF161616))
        )

        Box(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(28.dp))
                .background(cardGradient)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_monochromatic),
                    contentDescription = "Update Required",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Update Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSans
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version $versionName",
                    color = Color(state.vibrantColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GoogleSans
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "The current version is no longer supported due to server-side changes. Please install the latest fix to continue.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = GoogleSans,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Акцентная кнопка с пружинным сжатием
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = downloadScale; scaleY = downloadScale } // Масштаб
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(state.vibrantColor))
                        .clickable(
                            interactionSource = downloadInteraction, // Источник клика
                            indication = null
                        ) { uriHandler.openUri(updateUrl) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Download Update (Telegram)",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans
                    )
                }
            }
        }
    }
}
