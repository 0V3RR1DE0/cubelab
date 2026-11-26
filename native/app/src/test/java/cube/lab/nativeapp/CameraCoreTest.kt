package cube.lab.nativeapp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CameraCoreTest {

    @Test
    fun toLabWhiteIsNeutralAndBright() {
        val lab = ColourScience.toLab(Rgb(255, 255, 255))
        assertEquals(100.0, lab.l, 0.05)
        assertEquals(0.0, lab.a, 0.05)
        assertEquals(0.0, lab.b, 0.05)
    }

    @Test
    fun toLabBlackIsZero() {
        val lab = ColourScience.toLab(Rgb(0, 0, 0))
        assertEquals(0.0, lab.l, 1e-9)
        assertEquals(0.0, lab.a, 1e-9)
        assertEquals(0.0, lab.b, 1e-9)
    }

    @Test
    fun toLabPrimariesAreInExpectedQuadrants() {
        val red = ColourScience.toLab(Rgb(255, 0, 0))
        assertEquals(53.24, red.l, 1.0)
        assertTrue(red.a > 60.0)
        assertTrue(red.b > 50.0)

        val green = ColourScience.toLab(Rgb(0, 255, 0))
        assertEquals(87.73, green.l, 1.0)
        assertTrue(green.a < -60.0)
        assertTrue(green.b > 60.0)

        val blue = ColourScience.toLab(Rgb(0, 0, 255))
        assertEquals(32.30, blue.l, 1.0)
        assertTrue(blue.a > 60.0)
        assertTrue(blue.b < -90.0)

        val yellow = ColourScience.toLab(Rgb(255, 255, 0))
        assertTrue(yellow.a < 0.0)
        assertTrue(yellow.b > 80.0)
    }

    @Test
    fun whiteBalanceNeutralisesWarmWhite() {
        val gains = ColourScience.whiteBalanceGains(Rgb(255, 220, 180))
        assertEquals(3, gains.size)
        gains.forEach { assertTrue(it.isFinite()); assertTrue(it in 0.25..4.0) }

        val balanced = ColourScience.applyGains(Rgb(255, 220, 180), gains)
        assertTrue(abs(balanced.r - balanced.g) <= 1)
        assertTrue(abs(balanced.g - balanced.b) <= 1)
        assertTrue(abs(balanced.r - balanced.b) <= 1)
    }

    @Test
    fun whiteBalanceHandlesZeroChannels() {
        val gains = ColourScience.whiteBalanceGains(Rgb(0, 128, 0))
        gains.forEach { assertTrue(it.isFinite()); assertTrue(it > 0.0) }

        val balanced = ColourScience.applyGains(Rgb(0, 128, 0), gains)
        assertEquals(0, balanced.r)
        assertEquals(0, balanced.b)
        assertTrue(balanced.g in 0..255)
    }

    private val prototypes = listOf(
        Lab(100.0, 0.0, 0.0),      // white
        Lab(53.24, 80.09, 67.20),  // red
        Lab(87.73, -86.18, 83.18), // green
        Lab(32.30, 79.19, -107.86),// blue
        Lab(97.14, -21.55, 94.48), // yellow
        Lab(0.0, 0.0, 0.0),        // black
    )

    @Test
    fun classifyPicksNearbyPrototype() {
        val match = ColourModel(prototypes).classify(Lab(55.0, 81.0, 67.0))
        assertEquals(1, match.index)
        assertEquals(1.991, match.delta, 0.01)
        assertTrue(match.margin > 4.0)
    }

    @Test
    fun classifyRejectsSampleBetweenTwoPrototypes() {
        val between = Lab(50.0, 0.0, 0.0) // midpoint of white and black prototypes
        val match = ColourModel(prototypes).classify(between)
        assertNull(match.index)
        assertEquals(50.0, match.delta, 0.01)
        assertEquals(0.0, match.margin, 0.01)
    }

    @Test
    fun classifyEmptyModelStaysUncertain() {
        val match = ColourModel(emptyList()).classify(Lab(50.0, 0.0, 0.0))
        assertNull(match.index)
        assertTrue(match.delta.isInfinite())
    }

    @Test
    fun balanceRepairsTenEightToNineNine() {
        val indices = IntArray(54)
        var slot = 0
        repeat(10) { indices[slot++] = 0 }
        repeat(8) { indices[slot++] = 1 }
        for (colour in 2..5) repeat(9) { indices[slot++] = colour }
        assertEquals(54, slot)

        assertTrue(balanceToNine(indices))

        val counts = IntArray(6)
        indices.forEach {
            assertTrue(it in 0..5)
            counts[it]++
        }
        assertArrayEquals(intArrayOf(9, 9, 9, 9, 9, 9), counts)
    }

    @Test
    fun balanceLeavesBalancedArrayUnchanged() {
        val indices = IntArray(54) { it / 9 }
        val copy = indices.copyOf()

        assertTrue(balanceToNine(indices))
        assertArrayEquals(copy, indices)
    }

    @Test
    fun balanceRejectsInvalidInputWithoutCrashing() {
        val invalidValue = IntArray(54) { if (it == 0) 6 else it / 9 }
        val copy = invalidValue.copyOf()
        assertFalse(balanceToNine(invalidValue))
        assertArrayEquals(copy, invalidValue)

        assertFalse(balanceToNine(intArrayOf(0, 1, 2)))
        assertFalse(balanceToNine(IntArray(0)))
    }

    @Test
    fun deltaE76IsEuclideanAndSymmetric() {
        val a = Lab(50.0, 10.0, -5.0)
        val b = Lab(53.0, 14.0, -2.0)
        assertEquals(5.831, ColourScience.deltaE76(a, b), 0.001)
        assertEquals(ColourScience.deltaE76(a, b), ColourScience.deltaE76(b, a), 1e-9)
    }
}
