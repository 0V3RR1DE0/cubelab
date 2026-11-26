package cube.lab.nativeapp

import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

private const val SAMPLES_PER_AXIS = 3

/**
 * Averages the inner 50% of every cell of an n x n grid laid over an RGBA_8888 plane.
 *
 * [rotationDegrees] is the clockwise turn that makes the buffer upright
 * (`ImageInfo.rotationDegrees`). The returned list is already upright, row-major,
 * top-left first, so it lines up with the on-screen grid.
 */
fun sampleCells(
    rgba: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    n: Int,
    rotationDegrees: Int = 0,
    cropWidth: Int = width,
    cropHeight: Int = height,
    gridFraction: Float = 1f,
): List<Rgb> {
    if (n <= 0 || width <= 0 || height <= 0 || rowStride <= 0 || pixelStride < 3) return emptyList()

    var rotation = ((rotationDegrees % 360) + 360) % 360
    if (rotation != 90 && rotation != 180 && rotation != 270) rotation = 0

    // LifecycleCameraController applies a centred ViewPort crop to ImageAnalysis.
    // The preview shows exactly that crop, and the square grid covers [gridFraction]
    // of the crop's smaller upright side, centred. Crop edges are assumed centred.
    val swapped = rotation == 90 || rotation == 270
    val uprightWidth = if (swapped) height else width
    val uprightHeight = if (swapped) width else height
    val bufferCropWidth = cropWidth.coerceIn(1, width)
    val bufferCropHeight = cropHeight.coerceIn(1, height)
    val cropUprightWidth = if (swapped) bufferCropHeight else bufferCropWidth
    val cropUprightHeight = if (swapped) bufferCropWidth else bufferCropHeight
    val cropLeft = (uprightWidth - cropUprightWidth) / 2.0
    val cropTop = (uprightHeight - cropUprightHeight) / 2.0
    val fraction = gridFraction.toDouble().coerceIn(0.05, 1.0)
    val side = min(cropUprightWidth, cropUprightHeight) * fraction
    val left = cropLeft + (cropUprightWidth - side) / 2.0
    val top = cropTop + (cropUprightHeight - side) / 2.0
    val start = rgba.position()
    val limit = rgba.limit()

    val cells = ArrayList<Rgb>(n * n)
    for (row in 0 until n) {
        for (col in 0 until n) {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (sy in 0 until SAMPLES_PER_AXIS) {
                for (sx in 0 until SAMPLES_PER_AXIS) {
                    val u = (col + 0.25 + 0.5 * (sx + 0.5) / SAMPLES_PER_AXIS) / n
                    val v = (row + 0.25 + 0.5 * (sy + 0.5) / SAMPLES_PER_AXIS) / n
                    val ux = (left + u * side) / uprightWidth
                    val uy = (top + v * side) / uprightHeight

                    // Inverse of the clockwise turn that makes the buffer upright.
                    val bx: Double
                    val by: Double
                    when (rotation) {
                        90 -> { bx = uy; by = 1.0 - ux }
                        180 -> { bx = 1.0 - ux; by = 1.0 - uy }
                        270 -> { bx = 1.0 - uy; by = ux }
                        else -> { bx = ux; by = uy }
                    }
                    val x = (bx * width).toInt().coerceIn(0, width - 1)
                    val y = (by * height).toInt().coerceIn(0, height - 1)
                    val index = start + y * rowStride + x * pixelStride
                    if (index + 2 < limit) {
                        red += rgba.get(index).toInt() and 0xFF
                        green += rgba.get(index + 1).toInt() and 0xFF
                        blue += rgba.get(index + 2).toInt() and 0xFF
                        count++
                    }
                }
            }
            cells.add(
                if (count == 0) Rgb(0, 0, 0)
                else Rgb(red / count, green / count, blue / count),
            )
        }
    }
    return cells
}

/** Rotates a row-major n x n grid 90 degrees clockwise. Invalid sizes are copied unchanged. */
fun rotateGrid(values: IntArray, n: Int): IntArray {
    if (n <= 0 || values.size != n * n) return values.copyOf()
    return IntArray(n * n) { i ->
        val row = i / n
        val col = i % n
        values[(n - 1 - col) * n + row]
    }
}

/** New index of the old cell [index] after [rotateGrid] rotates clockwise. */
fun rotateIndex(index: Int, n: Int): Int {
    if (n <= 0) return index
    val row = index / n
    val col = index % n
    return (index % n) * n + (n - 1 - row)
}

/** Median per channel. An empty list yields black so callers never get a null reading. */
fun medianRgb(values: List<Rgb>): Rgb {
    if (values.isEmpty()) return Rgb(0, 0, 0)

    fun median(pick: (Rgb) -> Int): Int {
        val sorted = values.map(pick).sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else ((sorted[middle - 1] + sorted[middle]) / 2.0).roundToInt()
    }
    return Rgb(median { it.r }, median { it.g }, median { it.b })
}
