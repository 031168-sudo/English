package com.english.pronunciation

import kotlin.math.roundToInt

/**
 * Scores a spoken attempt against the target word.
 *
 * The recognizer only returns text, so closeness is judged two ways: raw
 * spelling distance, and distance between rough pronunciation keys. The keys
 * normalise English spelling quirks while keeping vowel quality, so homophones
 * ("sea"/"see") collapse together while words that only differ in a vowel
 * ("ship"/"sheep") stay apart — which is exactly the distinction a Russian
 * speaker needs feedback on. Hypotheses the recognizer ranked lower or scored
 * with low confidence are trusted slightly less.
 */
object PronunciationScorer {

    fun score(target: String, hypotheses: List<String>, confidences: FloatArray? = null): Int {
        val cleanTarget = letters(target)
        if (cleanTarget.isEmpty() || hypotheses.isEmpty()) return 0

        var best = 0
        hypotheses.forEachIndexed { index, hypothesis ->
            val quality = matchQuality(cleanTarget, hypothesis)
            val rankFactor = (1f - index * 0.03f).coerceAtLeast(0.85f)
            val confidence = confidences?.getOrNull(index) ?: -1f
            val confidenceFactor =
                if (confidence > 0f) 0.88f + 0.12f * confidence.coerceAtMost(1f) else 1f
            val adjusted = (quality * rankFactor * confidenceFactor).roundToInt()
            if (adjusted > best) best = adjusted
        }
        return best.coerceIn(0, 100)
    }

    private fun matchQuality(cleanTarget: String, hypothesis: String): Float {
        val spoken = hypothesis.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .map { spellOut(it) }
            .filter { it.isNotEmpty() }
        if (spoken.isEmpty()) return 0f

        val variants = spoken + listOf(spoken.joinToString(""))
        val targetKey = pronunciationKey(cleanTarget)
        var best = 0f
        for (variant in variants) {
            if (variant == cleanTarget || isHomophone(cleanTarget, variant)) return 100f
            val variantKey = pronunciationKey(variant)
            val spelling = similarity(cleanTarget, variant)
            val sound = similarity(targetKey, variantKey)
            var combined = 100f * (0.45f * spelling + 0.55f * sound)
            // The opening sound is where accent errors are most audible
            // (th→t/s, w→v, r→l), so weigh a mismatch there more heavily.
            if (targetKey.firstOrNull() != variantKey.firstOrNull()) combined *= 0.9f
            if (targetKey == variantKey) combined = maxOf(combined, 92f)
            if (combined > best) best = combined
        }
        return best
    }

    /**
     * The recognizer writes what it heard, not what was meant: a spoken "tea"
     * comes back as "T" and a spoken "two" as "2". Both are perfect
     * pronunciations, so the token is turned back into the word it stands for
     * before anything is compared.
     */
    private fun spellOut(token: String): String {
        NUMBER_WORDS[token]?.let { return it }
        if (token.length == 1 && token[0].isLetter()) {
            LETTER_NAMES[token]?.let { return it }
        }
        return token.filter { it.isLetter() }
    }

    private val NUMBER_WORDS = mapOf(
        "0" to "zero", "1" to "one", "2" to "two", "3" to "three", "4" to "four",
        "5" to "five", "6" to "six", "7" to "seven", "8" to "eight", "9" to "nine",
        "10" to "ten", "11" to "eleven", "12" to "twelve", "13" to "thirteen",
        "14" to "fourteen", "15" to "fifteen", "16" to "sixteen", "17" to "seventeen",
        "18" to "eighteen", "19" to "nineteen", "20" to "twenty", "30" to "thirty",
        "40" to "forty", "50" to "fifty", "60" to "sixty", "70" to "seventy",
        "80" to "eighty", "90" to "ninety", "100" to "hundred", "1000" to "thousand"
    )

    private val LETTER_NAMES = mapOf(
        "a" to "ay", "b" to "bee", "c" to "see", "d" to "dee", "e" to "ee",
        "f" to "ef", "g" to "jee", "h" to "aitch", "i" to "eye", "j" to "jay",
        "k" to "kay", "l" to "el", "m" to "em", "n" to "en", "o" to "oh",
        "p" to "pee", "q" to "cue", "r" to "ar", "s" to "ess", "t" to "tea",
        "u" to "you", "v" to "vee", "w" to "doubleyou", "x" to "ex",
        "y" to "why", "z" to "zee"
    )

    private fun isHomophone(a: String, b: String) =
        HOMOPHONES.any { group -> a in group && b in group }

    /**
     * Words the recognizer may legitimately spell the other way round even when
     * the speaker was perfect, so they must not cost points.
     */
    private val HOMOPHONES = listOf(
        setOf("son", "sun"), setOf("sea", "see", "c"), setOf("two", "to", "too"),
        setOf("tea", "tee", "t"), setOf("eye", "aye", "i"), setOf("bee", "be", "b"),
        setOf("you", "ewe", "u"), setOf("queue", "cue", "q"), setOf("why", "y"),
        setOf("oh", "owe", "o"), setOf("pea", "pee", "p"), setOf("are", "ar", "r"),
        setOf("one", "won"), setOf("eight", "ate"), setOf("hour", "our"),
        setOf("week", "weak"), setOf("meat", "meet"), setOf("night", "knight"),
        setOf("red", "read"), setOf("new", "knew"), setOf("know", "no"),
        setOf("blue", "blew"), setOf("flower", "flour"), setOf("plane", "plain"),
        setOf("bear", "bare"), setOf("deer", "dear"), setOf("pear", "pair", "pare"),
        setOf("wait", "weight"), setOf("hear", "here"), setOf("write", "right", "rite"),
        setOf("sale", "sail"), setOf("piece", "peace"), setOf("buy", "by", "bye"),
        setOf("cell", "sell"), setOf("fair", "fare"), setOf("flu", "flew"),
        setOf("great", "grate"), setOf("hole", "whole"), setOf("mail", "male"),
        setOf("nose", "knows"), setOf("role", "roll"), setOf("sum", "some"),
        setOf("tail", "tale"), setOf("wear", "where"), setOf("wood", "would"),
        setOf("steal", "steel"), setOf("break", "brake"), setOf("die", "dye"),
        setOf("board", "bored"), setOf("cereal", "serial"), setOf("waist", "waste")
    )

    private fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val longest = maxOf(a.length, b.length, 1)
        return (1f - levenshtein(a, b).toFloat() / longest).coerceIn(0f, 1f)
    }

    private fun letters(text: String) = text.lowercase().filter { it.isLetter() }

    /**
     * Markers keep multi-letter sounds as a single symbol: C=ch, S=sh, Z=zh,
     * T=th, N=ng, and I/U/W/A/O/Q/E/Y for the common vowel digraphs.
     */
    private fun pronunciationKey(word: String): String {
        if (word.isEmpty()) return ""
        var s = word

        s = s.replace(Regex("^(kn|gn|pn)"), "n")
        s = s.replace(Regex("^wr"), "r")
        s = s.replace(Regex("^ps"), "s")
        s = s.replace(Regex("^x"), "z")
        s = s.replace(Regex("^gh"), "g")

        s = s.replace("eigh", "A")
        s = s.replace("igh", "Y")
        s = s.replace("tion", "Sun")
        s = s.replace("sion", "Zun")
        s = s.replace("tch", "C")
        s = s.replace("dge", "j")
        s = s.replace("ch", "C")
        s = s.replace("sh", "S")
        s = s.replace("th", "T")
        s = s.replace("ph", "f")
        s = s.replace("wh", "w")
        s = s.replace("ck", "k")
        s = s.replace("qu", "kw")
        s = s.replace("ng", "N")
        s = s.replace("gh", "")

        s = s.replace(Regex("c(?=[eiy])"), "s")
        s = s.replace("c", "k")
        s = s.replace(Regex("g(?=[eiy])"), "j")
        s = s.replace("x", "ks")

        s = s.replace(Regex("ee|ea|ie|ei"), "I")
        s = s.replace("oo", "U")
        s = s.replace(Regex("ou|ow"), "W")
        s = s.replace(Regex("ai|ay|ey"), "A")
        s = s.replace(Regex("oa|oe"), "O")
        s = s.replace(Regex("au|aw"), "Q")
        s = s.replace(Regex("oi|oy"), "E")

        s = s.replace(Regex("mb$"), "m")
        s = s.replace(Regex("gn$"), "n")
        if (s.length > 3) s = s.replace(Regex("e$"), "")
        s = s.replace(Regex("y$"), "I")
        s = s.replace(Regex("(.)\\1+"), "$1")

        return s
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
