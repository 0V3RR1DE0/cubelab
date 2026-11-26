package cube.lab.nativeapp

/** Penalty applied to a solve. */
enum class Penalty { NONE, PLUS2, DNF }

/**
 * One measured solve.
 *
 * @property rawMs measured time in milliseconds. Negative values are invalid and treated as DNF.
 * @property penalty penalty applied on top of [rawMs].
 */
data class SolveTime(val rawMs: Long, val penalty: Penalty = Penalty.NONE) {
    /** Time in milliseconds after penalty, or `null` for a DNF (including negative [rawMs]). */
    val finalMs: Long?
        get() = when {
            rawMs < 0 || penalty == Penalty.DNF -> null
            penalty == Penalty.PLUS2 -> rawMs + 2000
            else -> rawMs
        }
}

/** Pure timing/average helpers. Holds no state. */
object TimerLogic {
    /** Inspection time from which a +2 is given. */
    const val INSPECTION_LIMIT_MS = 15_000L

    /** Inspection time from which the solve is a DNF. */
    const val INSPECTION_DNF_MS = 17_000L

    /** WCA rule: 15.00-16.99 s is +2, 17.00 s or later is DNF. */
    fun inspectionPenalty(inspectionMs: Long): Penalty = when {
        inspectionMs >= INSPECTION_DNF_MS -> Penalty.DNF
        inspectionMs >= INSPECTION_LIMIT_MS -> Penalty.PLUS2
        else -> Penalty.NONE
    }

    /**
     * Trimmed mean (drop one best and one worst) of the last [n] attempts,
     * rounded to the nearest 10 ms. One DNF counts as the worst and is dropped;
     * two or more DNFs make the average a DNF.
     *
     * @return the average in milliseconds, or `null` if fewer than [n] attempts
     *   (or fewer than 3) are available or 2+ are DNF.
     */
    fun aoN(times: List<SolveTime>, n: Int): Long? {
        if (n < 3 || times.size < n) return null
        return trimmedMean(times.subList(times.size - n, times.size))
    }

    /**
     * Best trimmed mean over every consecutive window of [n] attempts.
     *
     * @return the fastest average in milliseconds, or `null` if no complete
     *   window exists or every window contains 2+ DNFs.
     */
    fun bestAoN(times: List<SolveTime>, n: Int): Long? {
        if (n < 3 || times.size < n) return null
        var best: Long? = null
        for (start in 0..times.size - n) {
            val mean = trimmedMean(times.subList(start, start + n)) ?: continue
            if (best == null || mean < best) best = mean
        }
        return best
    }

    /** DNFs sort last via [Long.MAX_VALUE] so the worst-drop removes exactly one. */
    private fun trimmedMean(window: List<SolveTime>): Long? {
        val values = window.map { it.finalMs ?: Long.MAX_VALUE }.sorted()
        if (values.count { it == Long.MAX_VALUE } >= 2) return null
        val kept = values.subList(1, values.size - 1)
        val mean = kept.sumOf { it }.toDouble() / kept.size
        return Math.round(mean / 10.0) * 10
    }
}
