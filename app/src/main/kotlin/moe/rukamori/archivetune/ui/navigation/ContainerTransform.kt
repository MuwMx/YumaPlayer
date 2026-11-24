/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalSharedTransitionApi::class)

package moe.rukamori.archivetune.ui.navigation

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import moe.rukamori.archivetune.LocalAnimationsDisabled

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedContentScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

internal object ContainerTransformTokens {
    val DefaultCardCornerRadius = 16.dp
    const val OpenDamping = 0.88f
    const val OpenStiffness = 150f
    const val CloseMillis = 560
    val CloseEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)
    val RecedeSpring =
        spring<Float>(
            dampingRatio = OpenDamping,
            stiffness = OpenStiffness,
            visibilityThreshold = 0.001f,
        )
    val ReturnSpec = tween<Float>(CloseMillis, easing = CloseEasing)
    const val RecedeScale = 0.92f
    const val RecedeAlpha = 0.45f
}

internal val ContainerBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    if (targetBounds.width < initialBounds.width) {
        tween(
            durationMillis = ContainerTransformTokens.CloseMillis,
            easing = ContainerTransformTokens.CloseEasing,
        )
    } else {
        spring(
            dampingRatio = ContainerTransformTokens.OpenDamping,
            stiffness = ContainerTransformTokens.OpenStiffness,
            visibilityThreshold = Rect.VisibilityThreshold,
        )
    }
}

internal object ContainerTransformRegistry {
    val sourceWidths = mutableMapOf<String, Float>()
    val sourceRadii = mutableMapOf<String, Float>()
    val sourceCovers = mutableMapOf<String, String>()
    val hosts = mutableMapOf<String, MutableList<String>>()
    val scopedOwners = mutableMapOf<String, MutableList<TransformOwner>>()
    val claimed = mutableStateMapOf<String, TransformOwner>()
    var targetWidth: Float = 0f
}

@Composable
private fun currentScreenId(): String =
    (LocalViewModelStoreOwner.current as? NavBackStackEntry)?.id.orEmpty()

private fun screenKey(
    key: String,
    screenId: String?,
): String = "$key@$screenId"

internal class TransformOwner(
    private val scoped: String,
) : RememberObserver {
    private val owners: MutableList<TransformOwner>
        get() = ContainerTransformRegistry.scopedOwners.getOrPut(scoped) { mutableListOf() }

    init {
        owners += this
    }

    val isPrimary: Boolean
        get() {
            val list = ContainerTransformRegistry.scopedOwners[scoped] ?: return false
            val picked = ContainerTransformRegistry.claimed[scoped]
            return if (picked != null && picked in list) picked === this else list.firstOrNull() === this
        }

    fun claim() {
        val list = ContainerTransformRegistry.scopedOwners[scoped] ?: return
        if (list.size > 1 && ContainerTransformRegistry.claimed[scoped] !== this) {
            ContainerTransformRegistry.claimed[scoped] = this
        }
    }

    override fun onRemembered() = Unit

    override fun onForgotten() {
        release()
    }

    override fun onAbandoned() {
        release()
    }

    private fun release() {
        val list = ContainerTransformRegistry.scopedOwners[scoped] ?: return
        val wasPrimary = ContainerTransformRegistry.claimed[scoped] === this
        list.remove(this)
        if (list.isEmpty()) {
            ContainerTransformRegistry.scopedOwners.remove(scoped)
            ContainerTransformRegistry.claimed.remove(scoped)
        } else if (wasPrimary) {
            ContainerTransformRegistry.claimed[scoped] = list.first()
        }
    }
}

internal class MorphingCornerClip(
    private val key: String,
    private val sourceCornerRadiusPx: Float,
    private val targetCornerRadiusPx: Float,
) : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val sourceWidth = ContainerTransformRegistry.sourceWidths[key] ?: bounds.width
        val targetWidth = ContainerTransformRegistry.targetWidth.takeIf { it > sourceWidth } ?: bounds.width
        val fraction =
            if (targetWidth > sourceWidth) {
                ((bounds.width - sourceWidth) / (targetWidth - sourceWidth)).coerceIn(0f, 1f)
            } else {
                0f
            }
        val radiusPx = lerp(sourceCornerRadiusPx, targetCornerRadiusPx, fraction)
        return Path().apply {
            addRoundRect(RoundRect(bounds, CornerRadius(radiusPx, radiusPx)))
        }
    }
}

fun containerTransformCover(key: String?): String? =
    if (key == null) null else ContainerTransformRegistry.sourceCovers[key]

@Composable
private fun rememberSystemCornerRadiusPx(): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    val view = LocalView.current
    return remember(view) {
        view.rootWindowInsets
            ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            ?.radius
            ?.toFloat()
            ?: 0f
    }
}

@Composable
fun Modifier.containerTransformSource(
    key: String?,
    cornerRadius: Dp = ContainerTransformTokens.DefaultCardCornerRadius,
    coverUrl: String? = null,
): Modifier {
    if (key == null || LocalAnimationsDisabled.current) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedContentScope = LocalNavAnimatedContentScope.current ?: return this

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val systemCornerRadiusPx = rememberSystemCornerRadiusPx()
    val screenId = currentScreenId()
    val scoped = screenKey(key, screenId)
    val owner = remember(scoped) { TransformOwner(scoped) }

    DisposableEffect(key, screenId) {
        val list = ContainerTransformRegistry.hosts.getOrPut(key) { mutableListOf() }
        list += screenId
        onDispose {
            list.remove(screenId)
            if (list.isEmpty()) {
                ContainerTransformRegistry.hosts.remove(key)
            }
        }
    }

    SideEffect {
        ContainerTransformRegistry.sourceRadii[scoped] = cornerRadiusPx
        if (coverUrl != null) {
            ContainerTransformRegistry.sourceCovers[key] = coverUrl
        }
    }

    val claimModifier =
        Modifier
            .pointerInput(owner) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    owner.claim()
                }
            }
            .onFocusChanged {
                if (it.hasFocus) owner.claim()
            }
            .onSizeChanged { size ->
                ContainerTransformRegistry.sourceWidths[scoped] = size.width.toFloat()
            }

    if (!owner.isPrimary) {
        return this.then(claimModifier)
    }

    val clip =
        remember(scoped, cornerRadiusPx, systemCornerRadiusPx) {
            MorphingCornerClip(
                key = scoped,
                sourceCornerRadiusPx = cornerRadiusPx,
                targetCornerRadiusPx = systemCornerRadiusPx,
            )
        }

    return with(sharedTransitionScope) {
        val sharedState = rememberSharedContentState(key = scoped)
        this@containerTransformSource
            .then(claimModifier)
            .sharedBounds(
                sharedContentState = sharedState,
                animatedVisibilityScope = animatedContentScope,
                enter = fadeIn(tween(220, delayMillis = 100)),
                exit = fadeOut(tween(220, delayMillis = 40)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode =
                    SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.FillWidth,
                        Alignment.TopCenter,
                    ),
                clipInOverlayDuringTransition = clip,
            )
    }
}

@Composable
fun Modifier.containerTransformTarget(
    key: String?,
    sourceCornerRadius: Dp = ContainerTransformTokens.DefaultCardCornerRadius,
): Modifier {
    if (key == null || LocalAnimationsDisabled.current) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedContentScope = LocalNavAnimatedContentScope.current ?: return this

    val own = currentScreenId()
    val scoped = rememberSaveable(key) { screenKey(key, ContainerTransformRegistry.hosts[key]?.lastOrNull { it != own }) }

    val density = LocalDensity.current
    val fallbackRadiusPx = with(density) { sourceCornerRadius.toPx() }
    val sourceRadiusPx = ContainerTransformRegistry.sourceRadii[scoped] ?: fallbackRadiusPx
    val systemCornerRadiusPx = rememberSystemCornerRadiusPx()

    val clip =
        remember(scoped, sourceRadiusPx, systemCornerRadiusPx) {
            MorphingCornerClip(
                key = scoped,
                sourceCornerRadiusPx = sourceRadiusPx,
                targetCornerRadiusPx = systemCornerRadiusPx,
            )
        }

    return with(sharedTransitionScope) {
        val sharedState = rememberSharedContentState(key = scoped)
        this@containerTransformTarget
            .onSizeChanged { size ->
                ContainerTransformRegistry.targetWidth = size.width.toFloat()
            }
            .sharedBounds(
                sharedContentState = sharedState,
                animatedVisibilityScope = animatedContentScope,
                enter = fadeIn(tween(280)),
                exit = fadeOut(tween(200, delayMillis = 120)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode =
                    SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.FillWidth,
                        Alignment.TopCenter,
                    ),
                zIndexInOverlay = 1f,
                clipInOverlayDuringTransition = clip,
            )
    }
}

internal fun containerTransformKeyForEntry(entry: NavBackStackEntry): String? {
    val route = entry.destination.route?.substringBefore('?') ?: return null
    val args = entry.arguments ?: return null
    return when (route) {
        "album/{albumId}",
        "spotify_album/{albumId}",
        -> args.getString("albumId")?.let { "album-$it" }

        "online_playlist/{playlistId}",
        "local_playlist/{playlistId}",
        "spotify_playlist/{playlistId}",
        -> args.getString("playlistId")?.let { "playlist-$it" }

        "auto_playlist/{playlist}",
        "cache_playlist/{playlist}",
        -> args.getString("playlist")?.let { "playlist-$it" }

        "top_playlist/{top}" -> args.getString("top")?.let { "playlist-$it" }

        else -> null
    }
}

internal fun NavGraphBuilder.yumaComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    sizeTransform: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform?)? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        sizeTransform = sizeTransform,
    ) { backStackEntry ->
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            val key = containerTransformKeyForEntry(backStackEntry)
            if (key != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .containerTransformTarget(key),
                ) {
                    content(backStackEntry)
                }
            } else {
                content(backStackEntry)
            }
        }
    }
}
