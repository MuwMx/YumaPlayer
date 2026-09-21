package moe.rukamori.archivetune.flaccore.qbdlx

/**
 * The ONE place the token pool's origin is defined. Today: decrypt the bundled
 * BuildConfig blob. A future runtime/Worker broker swaps only the @Provides in
 * QbdlxModule — failover, picker, and the source never change.
 *
 * ponytail: rawPool() is synchronous. A broker fetch is async, but the embedded
 * path is sync and adding suspend now speculates on an API that doesn't exist.
 */
fun interface QbdlxPoolProvider {
    suspend fun rawPool(): String
}
