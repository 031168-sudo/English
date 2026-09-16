package com.english.pronunciation

/**
 * Scores a spoken attempt by comparing what the speech recognizer heard
 * against the target word using normalized Levenshtein distance. The
 * recognizer returns several candidate transcriptions, so the closest
 * one wins.
 */
object PronunciationScorer {

    fun score(target: String, recognizedCandidates: List<String>): Int {
        val cleanTarget = normalize(target)
        if (recognizedCandidates.isEmpty() || cleanTarget.isEmpty()) return 0
        return recognizedCandidates.maxOf { similarity(cleanTarget, normalize(it)) }
    }

    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetter() }

    private fun similarity(a: String, b: String): Int {
        if (a.isEmpty() && b.isEmpty()) return 100
        val distance = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length, 1)
        val percent = ((1.0 - distance.toDouble() / maxLen) * 100).toInt()
        return percent.coerceIn(0, 100)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
