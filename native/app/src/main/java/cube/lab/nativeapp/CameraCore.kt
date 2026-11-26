package cube.lab.nativeapp

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Rgb(val r: Int, val g: Int, val b: Int)

data class Lab(val l: Double, val a: Double, val b: Double)

data class ColourMatch(val index: Int?, val delta: Double, val margin: Double)

object ColourScience {
    private const val XN = 95.047
    private const val YN = 100.0
    private const val ZN = 108.883
    private const val EPSILON = 216.0 / 24389.0
    private const val DELTA = 6.0 / 29.0

    fun toLab(rgb: Rgb): Lab {
        val r = linear(rgb.r / 255.0)
        val g = linear(rgb.g / 255.0)
        val b = linear(rgb.b / 255.0)

        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / (XN / 100.0)
        val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / (YN / 100.0)
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / (ZN / 100.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Lab(
            l = 116.0 * fy - 16.0,
            a = 500.0 * (fx - fy),
            b = 200.0 * (fy - fz),
        )
    }

    /** White-patch gains that make [white] neutral, clamped to 0.25..4.0. */
    fun whiteBalanceGains(white: Rgb): DoubleArray {
        val r = white.r.toDouble()
        val g = white.g.toDouble()
        val b = white.b.toDouble()
        val target = (r + g + b) / 3.0
        fun gain(channel: Double) = if (channel <= 0.0) 1.0 else (target / channel).coerceIn(0.25, 4.0)
        return doubleArrayOf(gain(r), gain(g), gain(b))
    }

    fun applyGains(rgb: Rgb, gains: DoubleArray): Rgb = Rgb(
        r = (rgb.r * gains.getOrElse(0) { 1.0 }).roundToInt().coerceIn(0, 255),
        g = (rgb.g * gains.getOrElse(1) { 1.0 }).roundToInt().coerceIn(0, 255),
        b = (rgb.b * gains.getOrElse(2) { 1.0 }).roundToInt().coerceIn(0, 255),
    )

    fun deltaE76(a: Lab, b: Lab): Double {
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return sqrt(dl * dl + da * da + db * db)
    }

    private fun linear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun f(t: Double): Double =
        if (t > EPSILON) t.pow(1.0 / 3.0) else t / (3.0 * DELTA * DELTA) + 4.0 / 29.0
}

fun labChroma(lab: Lab): Double = hypot(lab.a, lab.b)

fun labHue(lab: Lab): Double = (Math.toDegrees(atan2(lab.b, lab.a)) + 360.0) % 360.0

fun hueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return min(d, 360.0 - d)
}

/**
 * Scanner classifier built for real sticker ink rather than the decorative UI
 * palette. Lightness is ignored for chromatic colours (exposure moves L* far
 * more than hue), and low-chroma bright samples become white.
 *
 * Palette order: 0 yellow, 1 green, 2 red, 3 blue, 4 white, 5 orange.
 */
class InkColourModel(
    val prototypes: List<Lab>,
    val whiteIndex: Int = 4,
    val whiteMaxChroma: Double = 25.0,
    val minWhiteL: Double = 18.0,
    val minLightness: Double = 4.0,
) {
    /**
     * Hue-based sticker ink classifier. Hue survives exposure changes; lightness
     * does not, so chroma alone decides white, and the remaining colours are
     * chosen by nearest hue. It always returns a colour unless the sample is
     * nearly black; the scanner lets the user tap a cell to correct a guess.
     */
    fun classify(sample: Lab): ColourMatch {
        if (prototypes.isEmpty()) return ColourMatch(null, Double.POSITIVE_INFINITY, 0.0)
        if (sample.l < minLightness) return ColourMatch(null, sample.l, 0.0)

        val chroma = labChroma(sample)
        // A blue cast on a white sticker raises chroma to ~20, so the neutral
        // band must be wider than a perfectly balanced white.
        if (chroma <= whiteMaxChroma && sample.l >= minWhiteL) {
            return ColourMatch(whiteIndex, chroma, whiteMaxChroma - chroma)
        }

        val hue = labHue(sample)
        var best = whiteIndex
        var bestD = Double.POSITIVE_INFINITY
        var secondD = Double.POSITIVE_INFINITY
        prototypes.forEachIndexed { i, p ->
            if (i == whiteIndex) return@forEachIndexed
            val d = hueDistance(hue, labHue(p))
            if (d < bestD) {
                secondD = bestD
                bestD = d
                best = i
            } else if (d < secondD) {
                secondD = d
            }
        }
        val margin = if (prototypes.size <= 1) Double.POSITIVE_INFINITY else secondD - bestD
        return ColourMatch(best, bestD, margin)
    }
}

class ColourModel(val prototypes: List<Lab>, val minMargin: Double = 4.0) {
    /** Nearest prototype; index == null when it is not confidently closer than the runner-up. */
    fun classify(sample: Lab): ColourMatch {
        if (prototypes.isEmpty()) return ColourMatch(null, Double.POSITIVE_INFINITY, 0.0)

        var best = 0
        var bestDelta = Double.POSITIVE_INFINITY
        var secondDelta = Double.POSITIVE_INFINITY
        prototypes.forEachIndexed { i, prototype ->
            val d = ColourScience.deltaE76(sample, prototype)
            if (d < bestDelta) {
                secondDelta = bestDelta
                bestDelta = d
                best = i
            } else if (d < secondDelta) {
                secondDelta = d
            }
        }

        val margin = if (prototypes.size == 1) Double.POSITIVE_INFINITY else secondDelta - bestDelta
        return ColourMatch(if (margin >= minMargin) best else null, bestDelta, margin)
    }
}

/**
 * Repairs a 54-slot reading so that each of the 6 colours appears exactly 9 times, when possible.
 * Mutates [indices] in place. Returns false (leaving it untouched) for invalid input.
 */
fun balanceToNine(indices: IntArray): Boolean {
    if (indices.size != 54) return false
    if (indices.any { it !in 0..5 }) return false

    val count = IntArray(6)
    val positions = Array(6) { ArrayList<Int>() }
    indices.forEachIndexed { i, colour ->
        count[colour]++
        positions[colour].add(i)
    }
    if (count.all { it == 9 }) return true

    // shortcut: no confidences are available, so the highest-index occurrences of overfull
    // colours are moved; upgrade when callers can pass per-slot confidence.
    val deficits = IntArray(6)
    for (colour in 0..5) deficits[colour] = 9 - count[colour]
    var target = 0
    for (colour in 0..5) {
        while (count[colour] > 9) {
            while (deficits[target] <= 0) target++
            val position = positions[colour].removeAt(positions[colour].lastIndex)
            indices[position] = target
            count[colour]--
            count[target]++
            deficits[target]--
        }
    }
    return count.all { it == 9 }
}
