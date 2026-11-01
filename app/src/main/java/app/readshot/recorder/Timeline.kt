package app.readshot.recorder

/**
 * Maps subtitle time to scroll position and back: a cue scrolls past the reading
 * line during its own time, and the text waits through the gaps between cues.
 */
class Timeline(cues: List<Cue>, tops: List<Float>, height: Float) {
    private val times = DoubleArray(cues.size * 2)
    private val ys = FloatArray(cues.size * 2)

    init {
        var last = 0.0
        cues.forEachIndexed { i, c ->
            val next = cues.getOrNull(i + 1)
            val start = maxOf(c.start, last)
            val end = maxOf(start + 0.01, if (next != null) minOf(c.end, next.start) else c.end)
            times[i * 2] = start
            ys[i * 2] = tops.getOrElse(i) { 0f }
            times[i * 2 + 1] = end
            ys[i * 2 + 1] = tops.getOrElse(i + 1) { height }
            last = end
        }
    }

    val startTime: Double get() = times.firstOrNull() ?: 0.0
    val endTime: Double get() = times.lastOrNull() ?: 0.0

    fun posAt(t: Double): Float {
        if (times.isEmpty() || t <= times.first()) return ys.firstOrNull() ?: 0f
        if (t >= times.last()) return ys.last()
        var lo = 0
        var hi = times.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val f = (t - times[lo]) / (times[hi] - times[lo])
        return ys[lo] + ((ys[hi] - ys[lo]) * f).toFloat()
    }

    fun timeAt(y: Float): Double {
        if (times.isEmpty()) return 0.0
        if (y <= ys.first()) return times.first()
        for (i in 1..ys.lastIndex) {
            if (y <= ys[i] && ys[i] > ys[i - 1]) {
                return times[i - 1] + (times[i] - times[i - 1]) * (y - ys[i - 1]) / (ys[i] - ys[i - 1])
            }
        }
        return times.last()
    }
}
