package moe.rukamori.archivetune.core.common.math

fun levenshteinDistance(
    s1: CharSequence,
    s2: CharSequence,
): Int {
    val len1 = s1.length
    val len2 = s2.length
    val matrix = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) matrix[i][0] = i
    for (j in 0..len2) matrix[0][j] = j

    for (i in 1..len1) {
        for (j in 1..len2) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            matrix[i][j] =
                minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost,
                )
        }
    }

    return matrix[len1][len2]
}
