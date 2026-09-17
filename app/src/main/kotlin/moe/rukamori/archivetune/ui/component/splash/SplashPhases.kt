package moe.rukamori.archivetune.ui.component.splash

sealed interface SplashPhase {
    data object Dust : SplashPhase
    data object Gather : SplashPhase
    data object Ignite : SplashPhase
    data object Burst : SplashPhase
    data object Idle : SplashPhase
    data object Transit : SplashPhase
    data object Success : SplashPhase
    data object Error : SplashPhase
}
