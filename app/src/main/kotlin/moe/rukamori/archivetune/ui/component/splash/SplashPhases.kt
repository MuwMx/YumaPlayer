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

    fun next(): SplashPhase = when (this) {
        Dust -> Gather
        Gather -> Ignite
        Ignite -> Burst
        Burst -> Idle
        Idle -> Idle
        Transit -> Idle
        Success -> Idle
        Error -> Idle
    }

    companion object {
        const val VD380: Long = 0L
        const val BD900: Long = 120L
        const val qb340: Long = 130L
        const val UD420: Long = 420L
        const val kD460: Long = 100L
        const val Hb210: Long = 110L
        const val PD280: Long = 280L
        const val Om320: Long = 320L
        const val qD230: Long = 230L
        const val HD420: Long = 420L

        fun tickPhase(
            current: SplashPhase,
            elapsedMs: Float,
            short: Boolean = false
        ): SplashPhase {
            val s = if (short) kD460 else BD900
            val r = if (short) Hb210 else qb340
            val l = if (short) PD280 else UD420

            return when (current) {
                Dust -> if (elapsedMs >= VD380) current.next() else current
                Gather -> if (elapsedMs >= s) current.next() else current
                Ignite -> if (elapsedMs >= r) current.next() else current
                Burst -> if (elapsedMs >= l) current.next() else current
                Idle -> Idle
                Transit -> Transit
                Success -> Success
                Error -> Error
            }
        }
    }
}
