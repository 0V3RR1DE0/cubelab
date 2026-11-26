package cube.lab.nativeapp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FaceScannerMathTest {

    private fun buffer(
        width: Int,
        height: Int,
        rowStride: Int = width * 4,
        color: (x: Int, y: Int) -> Int,
    ): ByteBuffer {
        val rgba = ByteBuffer.allocate(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = color(x, y)
                val index = y * rowStride + x * 4
                rgba.put(index, ((rgb shr 16) and 0xFF).toByte())
                rgba.put(index + 1, ((rgb shr 8) and 0xFF).toByte())
                rgba.put(index + 2, (rgb and 0xFF).toByte())
                rgba.put(index + 3, 0xFF.toByte())
            }
        }
        return rgba
    }

    private fun quadrants(x: Int, y: Int, size: Int): Int = when {
        x < size / 2 && y < size / 2 -> 0xFF0000 // red
        x >= size / 2 && y < size / 2 -> 0x00FF00 // green
        x < size / 2 -> 0x0000FF // blue
        else -> 0xFFFF00 // yellow
    }

    // ------------------------------------------------------------ grid bounds

    @Test
    fun sampleReadsCellsRowMajorTopLeftFirst() {
        val cells = sampleCells(buffer(8, 8) { x, y -> quadrants(x, y, 8) }, 8, 8, 32, 4, 2)

        assertEquals(4, cells.size)
        assertEquals(Rgb(255, 0, 0), cells[0])
        assertEquals(Rgb(0, 255, 0), cells[1])
        assertEquals(Rgb(0, 0, 255), cells[2])
        assertEquals(Rgb(255, 255, 0), cells[3])
    }

    @Test
    fun sampleRespectsRowPadding() {
        val rowStride = 4 * 4 + 12
        val rgba = buffer(4, 4, rowStride) { x, y -> quadrants(x, y, 4) }
        // Poison the padding: a sampler using width*4 instead of rowStride reads this.
        for (y in 0 until 4) {
            for (x in 16 until rowStride) rgba.put(y * rowStride + x, 0xFF.toByte())
        }

        val cells = sampleCells(rgba, 4, 4, rowStride, 4, 2)

        assertEquals(listOf(Rgb(255, 0, 0), Rgb(0, 255, 0), Rgb(0, 0, 255), Rgb(255, 255, 0)), cells)
    }

    @Test
    fun sampleUsesOnlyInnerHalfOfSingleCell() {
        val rgba = buffer(10, 10) { x, y ->
            if (x < 2 || y < 2 || x > 7 || y > 7) 0xFF0000 else 0xFFFFFF
        }

        assertEquals(listOf(Rgb(255, 255, 255)), sampleCells(rgba, 10, 10, 40, 4, 1))
    }

    @Test
    fun sampleFollowsCentredCropRect() {
        // Only a 3x3 white block sits where a 10px centred crop's top-left cell
        // samples. Ignoring the crop would read mostly red instead.
        val rgba = buffer(20, 20) { x, y ->
            if (x in 6..8 && y in 6..8) 0xFFFFFF else 0xFF0000
        }

        val cells = sampleCells(
            rgba = rgba,
            width = 20,
            height = 20,
            rowStride = 80,
            pixelStride = 4,
            n = 2,
            cropWidth = 10,
            cropHeight = 10,
        )

        assertEquals(Rgb(255, 255, 255), cells[0])
    }

    @Test
    fun sampleAlwaysReturnsOneRgbPerCellForEveryRotation() {
        val rgba = buffer(7, 5) { x, y -> (x * 20 + y * 5) and 0xFFFFFF }
        for (rotation in intArrayOf(0, 90, 180, 270, 360, -90)) {
            val cells = sampleCells(rgba, 7, 5, 28, 4, 3, rotation)
            assertEquals("rotation $rotation", 9, cells.size)
            cells.forEach {
                assertTrue(it.r in 0..255 && it.g in 0..255 && it.b in 0..255)
            }
        }
    }

    @Test
    fun sampleRejectsInvalidInputWithoutThrowing() {
        val rgba = buffer(2, 2) { _, _ -> 0xFFFFFF }
        assertTrue(sampleCells(rgba, 2, 2, 8, 4, 0).isEmpty())
        assertTrue(sampleCells(rgba, 0, 2, 8, 4, 2).isEmpty())
        assertTrue(sampleCells(rgba, 2, 2, 8, 0, 2).isEmpty())
    }

    @Test
    fun sampleAppliesSensorRotation() {
        val size = 12
        val rgba = buffer(size, size) { x, y ->
            val col = x * 3 / size
            val row = y * 3 / size
            ((row * 3 + col + 1) * 16) shl 16
        }
        val upright = sampleCells(rgba, size, size, size * 4, 4, 3)

        val turned = sampleCells(rgba, size, size, size * 4, 4, 3, rotationDegrees = 90)

        assertArrayEquals(upright.map { it.r }.toIntArray(), intArrayOf(16, 32, 48, 64, 80, 96, 112, 128, 144))
        assertArrayEquals(rotateGrid(upright.map { it.r }.toIntArray(), 3), turned.map { it.r }.toIntArray())
    }

    // ------------------------------------------------------------ rotation

    @Test
    fun rotateTurnsTwoByTwoClockwise() {
        assertArrayEquals(
            intArrayOf(2, 0, 3, 1),
            rotateGrid(intArrayOf(0, 1, 2, 3), 2),
        )
    }

    @Test
    fun rotateTurnsThreeByThreeClockwise() {
        assertArrayEquals(
            intArrayOf(7, 4, 1, 8, 5, 2, 9, 6, 3),
            rotateGrid(intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9), 3),
        )
    }

    @Test
    fun rotateFourTimesIsIdentity() {
        val original = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        var grid = original.copyOf()
        repeat(4) { grid = rotateGrid(grid, 4) }
        assertArrayEquals(original, grid)
    }

    @Test
    fun rotateHandlesDegenerateGrids() {
        assertArrayEquals(intArrayOf(9), rotateGrid(intArrayOf(9), 1))
        assertArrayEquals(intArrayOf(1, 2, 3), rotateGrid(intArrayOf(1, 2, 3), 2))
        assertTrue(rotateGrid(IntArray(0), 0).isEmpty())
    }

    // ------------------------------------------------------------ median

    @Test
    fun medianTakesMiddleValuePerChannel() {
        val values = listOf(
            Rgb(9, 0, 200),
            Rgb(1, 100, 100),
            Rgb(5, 50, 0),
            Rgb(7, 200, 255),
        )

        assertEquals(Rgb(6, 75, 150), medianRgb(values))
    }

    @Test
    fun medianAveragesTheTwoMiddleValues() {
        assertEquals(Rgb(5, 7, 9), medianRgb(listOf(Rgb(0, 2, 4), Rgb(10, 12, 14))))
        assertEquals(Rgb(1, 2, 3), medianRgb(listOf(Rgb(1, 2, 3))))
    }

    @Test
    fun medianIgnoresChannelCorrelation() {
        val values = listOf(Rgb(255, 0, 0), Rgb(0, 255, 0), Rgb(0, 0, 255))
        assertEquals(Rgb(0, 0, 0), medianRgb(values))
    }

    @Test
    fun medianOfEmptyListIsBlack() {
        assertEquals(Rgb(0, 0, 0), medianRgb(emptyList()))
    }
}
