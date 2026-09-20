package moe.rukamori.archivetune.core.model.packed

@JvmInline
value class Lyrics(
    val text: String,
) {
    val sentences
        get() =
            runCatching {
                buildMap {
                    put(0L, "")
                    text.trim().lines().filter { it.length >= 10 }.forEach {
                        put(
                            it[8].digitToInt() * 10L +
                                it[7].digitToInt() * 100 +
                                it[5].digitToInt() * 1000 +
                                it[4].digitToInt() * 10000 +
                                it[2].digitToInt() * 60 * 1000 +
                                it[1].digitToInt() * 600 * 1000,
                            it.substring(10),
                        )
                    }
                }
            }.getOrNull()
}
