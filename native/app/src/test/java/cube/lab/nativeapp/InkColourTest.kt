package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkColourTest {
    private val refs = intArrayOf(
        0xFFFFD500.toInt(), 0xFF009E60.toInt(), 0xFFC41E3A.toInt(),
        0xFF0051BA.toInt(), 0xFFF2F5FA.toInt(), 0xFFFF5800.toInt(),
    )
    private val model = InkColourModel(refs.map {
        ColourScience.toLab(Rgb((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF))
    })

    private fun classify(r: Int, g: Int, b: Int) = model.classify(ColourScience.toLab(Rgb(r, g, b))).index

    @Test fun dimOrangeStaysOrange() = assertEquals(5, classify(153, 53, 0))
    @Test fun neonOrangeStaysOrange() = assertEquals(5, classify(153, 64, 0))
    @Test fun dimYellowStaysYellow() = assertEquals(0, classify(153, 128, 0))
    @Test fun dimGreenStaysGreen() = assertEquals(1, classify(0, 70, 42))
    @Test fun dimBlueStaysBlue() = assertEquals(3, classify(0, 49, 112))
    @Test fun neutralDimWhiteIsWhite() = assertEquals(4, classify(98, 98, 96))
    @Test fun blueCastWhiteStaysWhite() {
        val raw = Rgb(115, 125, 160)
        assertEquals(4, model.classify(ColourScience.toLab(raw)).index)
        val gains = ColourScience.whiteBalanceGains(raw)
        val balanced = ColourScience.applyGains(raw, gains)
        assertEquals(4, model.classify(ColourScience.toLab(balanced)).index)
    }
    @Test fun darkCellStillGetsAColour() = assertTrue(classify(30, 30, 32) != null)
    @Test fun pureBlackIsUncertain() = assertNull(classify(0, 0, 0))
    @Test fun midChromaStillGetsAColour() {
        // Greyish samples are ambiguous, but the scanner must still pick one;
        // the user can tap the cell to correct it.
        assertTrue(model.classify(Lab(50.0, 8.0, 10.0)).index != null)
    }
}
