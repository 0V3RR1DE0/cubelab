package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun reached(cube: Cube, input: IntArray, moves: List<String>, target: IntArray, n: Int): Boolean {
        val expected = targetInInputScheme(cube, input, target, n) ?: return false
        return cube.apply(input, moves).contentEquals(expected)
    }

    @Test
    fun everyOfferedPatternIsLegalDistinctAndReachable() {
        for (n in 2..7) {
            val cube = Cube(n)
            val names = patternNames(n)
            val seen = HashSet<String>()
            var offered = 0
            val scramble = randomScramble(cube, 10 + n * 2).first
            val scramblePerm = if (n >= 4) BigCube.inferHomePerm(cube, scramble) else null
            val scrambleToSolved = if (n >= 4) {
                assertNotNull("$n x $n scramble scheme", scramblePerm)
                BigCube.solve(cube, BigCube.mapToHome(scramble, scramblePerm!!))
            } else null

            for (i in names.indices) {
                val target = patternBoard(n, i)
                val label = "${names[i]} ${n}x$n"
                val homeMoves: List<String>? = if (n >= 4) BigCube.solve(cube, target) else null
                val legal = if (n >= 4) homeMoves != null else patternPossible(target, n)
                if (n <= 3) assertTrue("$label is not a legal cube state", legal)
                if (i > 0) {
                    assertFalse("$label is a uniform recolour of solved", isUniformColourBoard(target, n))
                }
                if (i > 0 && (cube.isSolved(target) || isUniformColourBoard(target, n))) continue
                // The picker silently drops a pattern its solver cannot handle.
                if (!legal) continue
                assertTrue("$label duplicates an earlier board", seen.add(target.joinToString(",")))
                offered++

                val solvedInput = cube.solved()
                val fromSolved = if (n >= 4) invSeq(homeMoves!!) else solveInto(cube, solvedInput, target, n)
                assertNotNull("$label is not solvable from solved", fromSolved)
                if (i > 0) assertFalse("$label has no moves from solved", fromSolved!!.isEmpty())
                assertTrue("$label not reached from solved", reached(cube, solvedInput, fromSolved!!, target, n))

                val fromScramble = if (n >= 4) {
                    val targetPerm = BigCube.inferHomePerm(cube, target)
                    assertNotNull("$label scheme", targetPerm)
                    val targetToSolved = BigCube.solve(cube, BigCube.mapToHome(target, targetPerm!!))
                    assertNotNull("$label is not solvable in the home scheme", targetToSolved)
                    assertNotNull("$n x $n scramble is not solvable", scrambleToSolved)
                    scrambleToSolved!! + invSeq(targetToSolved!!)
                } else solveInto(cube, scramble, target, n)
                assertNotNull("$label is not solvable from a scramble", fromScramble)
                assertTrue("$label not reached from a scramble", reached(cube, scramble, fromScramble!!, target, n))
            }
            assertTrue("${n}x$n offers no patterns", offered > 0)
        }
    }

    @Test
    fun patternCheckAcceptsRotatedScheme() {
        val cube = Cube(3)
        val home = cube.solved()
        val rotated = recolourToScheme(home, ROTATION_SCHEMES[5])
        assertFalse("the chosen rotation must not be the home scheme", rotated.contentEquals(home))
        for (i in patternNames(3).indices) {
            val target = patternBoard(3, i)
            val label = "${PATTERN_NAMES[i]} 3x3"
            if (i > 0 && (cube.isSolved(target) || isUniformColourBoard(target, 3))) continue
            val moves = solveInto(cube, rotated, target, 3)
            assertNotNull("$label is not solvable from a rotated scheme", moves)
            val expected = targetInInputScheme(cube, rotated, target, 3)
            assertNotNull("$label rotated-scheme target mapping", expected)
            val raw = cube.apply(rotated, moves!!)
            assertFalse(
                "$label: the old raw comparison must fail here to cover the reported bug",
                raw.contentEquals(target),
            )
            assertTrue("$label not reached under a rotated scheme", raw.contentEquals(expected!!))
        }
    }
}
