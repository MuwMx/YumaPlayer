package moe.rukamori.archivetune.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry

internal object NavigationTransitions {
    private const val StackingDurationMs = 350
    private const val OutgoingFadeMs = 150
    private const val PopExitFadeMs = 200
    private const val TopLevelFadeInDurationMs = 250
    private const val TopLevelFadeOutDurationMs = 200

    private val StackingEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    private val PopExitSlideEasing = Easing { f -> f * f * f }

    private val ImmediateFadeIn = fadeIn(tween(0))
    private val ImmediateFadeOut = fadeOut(tween(0))
    private val TopLevelFadeIn = fadeIn(tween(TopLevelFadeInDurationMs))
    private val TopLevelFadeOut = fadeOut(tween(TopLevelFadeOutDurationMs))

    private val StackEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(StackingDurationMs, easing = StackingEasing),
            initialOffsetX = { it / 3 },
        ) + fadeIn(
            animationSpec = tween(StackingDurationMs / 2),
        )

    private val StackExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = tween(StackingDurationMs, easing = StackingEasing),
        ) + scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(StackingDurationMs, easing = StackingEasing),
        ) + fadeOut(
            animationSpec = tween(OutgoingFadeMs),
            targetAlpha = 0f,
        )

    private val StackPopEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(StackingDurationMs, easing = StackingEasing),
            initialOffsetX = { -it / 3 },
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(StackingDurationMs, easing = StackingEasing),
        ) + fadeIn(
            animationSpec = tween(StackingDurationMs),
        )

    private val StackPopExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(StackingDurationMs, easing = PopExitSlideEasing),
        ) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(StackingDurationMs, easing = StackingEasing),
        ) + fadeOut(
            animationSpec = tween(PopExitFadeMs),
        )

    private fun isTopLevelTransition(
        initialRoute: String?,
        targetRoute: String?,
        topLevelScreens: List<String>,
    ): Boolean = initialRoute in topLevelScreens && targetRoute in topLevelScreens

    fun enter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): EnterTransition {
        if (disableAnimations) return ImmediateFadeIn
        val initialRoute = scope.initialState.destination.route
        val targetRoute = scope.targetState.destination.route
        return when {
            isTopLevelTransition(initialRoute, targetRoute, topLevelScreens) -> TopLevelFadeIn
            isArtworkDetailRoute(targetRoute) -> fadeIn(tween(300))
            else -> StackEnter
        }
    }

    fun exit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): ExitTransition {
        if (disableAnimations) return ImmediateFadeOut
        val initialRoute = scope.initialState.destination.route
        val targetRoute = scope.targetState.destination.route
        return when {
            isTopLevelTransition(initialRoute, targetRoute, topLevelScreens) -> TopLevelFadeOut
            isArtworkDetailRoute(targetRoute) ->
                scaleOut(
                    ContainerTransformTokens.RecedeSpring,
                    targetScale = ContainerTransformTokens.RecedeScale,
                ) + fadeOut(
                    ContainerTransformTokens.RecedeSpring,
                    targetAlpha = ContainerTransformTokens.RecedeAlpha,
                )
            else -> StackExit
        }
    }

    fun popEnter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): EnterTransition {
        if (disableAnimations) return ImmediateFadeIn
        val initialRoute = scope.initialState.destination.route
        val targetRoute = scope.targetState.destination.route
        return when {
            isTopLevelTransition(initialRoute, targetRoute, topLevelScreens) -> TopLevelFadeIn
            isArtworkDetailRoute(initialRoute) ->
                scaleIn(
                    ContainerTransformTokens.ReturnSpec,
                    initialScale = ContainerTransformTokens.RecedeScale,
                ) + fadeIn(
                    ContainerTransformTokens.ReturnSpec,
                    initialAlpha = ContainerTransformTokens.RecedeAlpha,
                )
            else -> StackPopEnter
        }
    }

    fun popExit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): ExitTransition {
        if (disableAnimations) return ImmediateFadeOut
        val initialRoute = scope.initialState.destination.route
        val targetRoute = scope.targetState.destination.route
        return when {
            isTopLevelTransition(initialRoute, targetRoute, topLevelScreens) -> TopLevelFadeOut
            isArtworkDetailRoute(initialRoute) ->
                scaleOut(
                    tween(260),
                    targetScale = 0.82f,
                    transformOrigin = TransformOrigin(0.5f, 0.22f),
                ) + fadeOut(tween(180))
            else -> StackPopExit
        }
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationEnterTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): EnterTransition = NavigationTransitions.enter(this, disableAnimations, topLevelScreens)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationExitTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): ExitTransition = NavigationTransitions.exit(this, disableAnimations, topLevelScreens)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationPopEnterTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): EnterTransition = NavigationTransitions.popEnter(this, disableAnimations, topLevelScreens)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationPopExitTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): ExitTransition = NavigationTransitions.popExit(this, disableAnimations, topLevelScreens)
