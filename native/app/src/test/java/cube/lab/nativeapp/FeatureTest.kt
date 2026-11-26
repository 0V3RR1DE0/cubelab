package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FeatureTest {
    @Before
    fun load() {
        val bytes = File("src/main/assets/cube3.bin").readBytes()
        assertTrue(Kociemba.loadFromBytes(bytes))
    }

    private fun paintedPreserved(partial: IntArray, full: IntArray) {
        for (i in partial.indices) {
            if (partial[i] >= 0) assertEquals("cell $i changed", partial[i], full[i])
        }
    }

    @Test
    fun fillEmpty3x3IsSolved() {
        val cube = Cube(3)
        val empty = IntArray(54) { -1 }
        val out = InputFill.complete(3, empty)
        assertNotNull(out)
        assertTrue(cube.isSolved(out!!))
    }

    @Test
    fun fillCentresKeepsOpposites() {
        val cube = Cube(3)
        val scheme = ROTATION_SCHEMES[7]
        val partial = IntArray(54) { -1 }
        for (f in 0 until 6) partial[f * 9 + 4] = scheme[f]
        val out = InputFill.complete(3, partial)
        assertNotNull("no completion for a valid centre scheme", out)
        paintedPreserved(partial, out!!)
        assertTrue("completed cube is not valid", patternPossible(out, 3))
        val opposite = intArrayOf(1, 0, 3, 2, 5, 4)
        for (f in 0 until 6) {
            val pair = setOf(out[f * 9 + 4], out[opposite[f] * 9 + 4])
            assertTrue(
                "centres ${out[f * 9 + 4]}/${out[opposite[f] * 9 + 4]} are not an opposite pair",
                pair == setOf(4, 0) || pair == setOf(1, 3) || pair == setOf(2, 5),
            )
        }
    }

    @Test
    fun fillKeepsYellowOppositeWhite() {
        val partial = IntArray(54) { -1 }
        partial[4] = 0      // U centre yellow
        partial[9 + 4] = 4  // D centre white
        val out = InputFill.complete(3, partial)
        assertNotNull("yellow/white opposite centres must complete", out)
        assertEquals(0, out!![4])
        assertEquals(4, out[9 + 4])
        assertTrue("completed cube is not valid", patternPossible(out, 3))
    }

    @Test
    fun fillRejectsNonOppositeCentres() {
        val partial = IntArray(54) { -1 }
        // U = yellow (0), D = green (1) are adjacent colours in the home scheme.
        partial[4] = 0
        partial[9 + 4] = 1
        assertNull("adjacent colours must not be forced onto opposite faces", InputFill.complete(3, partial))
    }

    @Test
    fun fillCompletesRandomPartial3x3() {
        val cube = Cube(3)
        repeat(12) { t ->
            val (board, _) = randomScramble(cube, 12 + (Math.random() * 10).toInt())
            val partial = board.copyOf()
            for (i in partial.indices) if (Math.random() < 0.45) partial[i] = -1
            val out = InputFill.complete(3, partial)
            assertNotNull("no completion on trial $t", out)
            paintedPreserved(partial, out!!)
            assertTrue("completion $t is not valid", patternPossible(out, 3))
        }
    }

    @Test
    fun fillCompletesRandomPartial2x2() {
        val cube = Cube(2)
        repeat(12) { t ->
            val (board, _) = randomScramble(cube, 8 + (Math.random() * 8).toInt())
            val partial = board.copyOf()
            for (i in partial.indices) if (Math.random() < 0.45) partial[i] = -1
            val out = InputFill.complete(2, partial)
            assertNotNull("no completion on 2x2 trial $t", out)
            paintedPreserved(partial, out!!)
            assertTrue("2x2 completion $t is not valid", patternPossible(out, 2))
        }
    }

    @Test
    fun fillEmptyBigCubes() {
        for (n in 4..7) {
            val cube = Cube(n)
            val out = InputFill.complete(n, IntArray(6 * n * n) { -1 })
            assertNotNull("no fill for $n", out)
            assertTrue("$n fill is not solved", cube.isSolved(out!!))
        }
    }

    @Test
    fun fillPyraminxEmptyAndConflict() {
        val out = InputFill.completePyraminx(IntArray(36) { -1 })
        assertNotNull(out)
        for (f in 0 until 4) {
            val c = out!![f * 9]
            for (k in 0 until 9) assertEquals(c, out[f * 9 + k])
        }
        val conflict = IntArray(36) { -1 }
        conflict[0] = 0
        conflict[1] = 1
        assertNull("a face with two colours cannot be a solved Pyraminx", InputFill.completePyraminx(conflict))
    }

    @Test
    fun allPatternsSolve() {
        for (n in 2..7) {
            val cube = Cube(n)
            var validCount = 0
            val names = patternNames(n)
            for (i in names.indices) {
                val target = patternBoard(n, i)
                if (i > 0 && cube.isSolved(target)) continue
                val valid = if (n <= 3) {
                    patternPossible(target, n)
                } else {
                    runCatching { BigCube.solve(cube, target) != null }.getOrDefault(false)
                }
                if (!valid) continue
                validCount++
                val (input, _) = randomScramble(cube, 10 + n * 2)
                if (n <= 3) {
                    val moves = solveInto(cube, input, target, n)
                    assertNotNull("${names[i]} ${n}x$n target not solvable", moves)
                    val out = cube.apply(input, moves!!)
                    assertTrue("${names[i]} ${n}x$n target not reached", out.contentEquals(target))
                } else {
                    val inputPerm = BigCube.inferHomePerm(cube, input)
                    val targetPerm = BigCube.inferHomePerm(cube, target)
                    assertNotNull("${n}x$n input scheme", inputPerm)
                    assertNotNull("${n}x$n target scheme", targetPerm)
                    val mappedInput = BigCube.mapToHome(input, inputPerm!!)
                    val mappedTarget = BigCube.mapToHome(target, targetPerm!!)
                    val a = BigCube.solve(cube, mappedInput)
                    val t = BigCube.solve(cube, mappedTarget)
                    assertNotNull("${n}x$n input not solvable", a)
                    assertNotNull("${n}x$n target not solvable", t)
                    val out = cube.apply(mappedInput, a!! + invSeq(t!!))
                    assertTrue("${names[i]} ${n}x$n target not reached", out.contentEquals(mappedTarget))
                }
            }
            assertTrue("$n×$n has no valid patterns", validCount > 0)
        }
    }
}
