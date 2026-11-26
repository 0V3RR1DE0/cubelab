package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SolverTest {
    @Before
    fun load() {
        val bytes = File("src/main/assets/cube3.bin").readBytes()
        assertTrue(Kociemba.loadFromBytes(bytes))
    }

    @Test
    fun solvesRandom3x3() {
        val cube = Cube(3)
        repeat(40) {
            val (board, _) = randomScramble(cube, 12 + (Math.random() * 12).toInt())
            val state = decodeCubies(board, cube, HOME_COLORS)
            assertNotNull(state)
            val moves = Kociemba.solveCube(state!!)
            assertNotNull(moves)
            val out = cube.apply(board, moves!!)
            assertTrue("3x3 not solved", cube.isSolved(out))
        }
    }

    @Test
    fun solvesRandom2x2() {
        val cube = Cube(2)
        repeat(60) {
            val (board, _) = randomScramble(cube, 8 + (Math.random() * 6).toInt())
            val scheme = infer2x2Scheme(cube, board)
            assertNotNull(scheme)
            val state = decodeCubies(board, cube, scheme!!)
            assertNotNull(state)
            val moves = Kociemba.solveCorners(state!!.cp, state.co)
            assertNotNull(moves)
            val out = cube.apply(board, moves!!)
            assertTrue("2x2 not solved", cube.isSolved(out))
        }
    }

    @Test
    fun solvesIntoPattern() {
        val cube = Cube(3)
        repeat(15) {
            val (aBoard, _) = randomScramble(cube, 10)
            val (tBoard, _) = randomScramble(cube, 10)
            val moves = solveInto(cube, aBoard, tBoard, 3)
            assertNotNull(moves)
            val out = cube.apply(aBoard, moves!!)
            assertTrue("target not reached", out.contentEquals(tBoard))
        }
    }

    @Test
    fun pyraminxSolver() {
        Pyraminx.buildDistances()
        assertEquals(933120, Pyraminx.reachable)
        repeat(20) {
            var board = Pyraminx.solvedColors()
            repeat(14) {
                val key = Pyraminx.turnKeys[(Math.random() * Pyraminx.turnKeys.size).toInt()]
                board = Pyraminx.applyPerm(board, Pyraminx.moves[key]!!.perm)
            }
            val result = Pyraminx.solve(board)
            assertNotNull(result.error, result.moves)
            var out = board
            for (key in result.moves!!) out = Pyraminx.applyPerm(out, Pyraminx.moves[key]!!.perm)
            assertTrue("Pyraminx not solved", Pyraminx.isSolved(out))
        }
    }

    @Test
    fun rotatedSchemesSolve() {
        val cube = Cube(3)
        repeat(12) {
            val (b, _) = randomScramble(cube, 12)
            val rot = ROTATION_SCHEMES[(Math.random() * ROTATION_SCHEMES.size).toInt()]
            val rb = IntArray(b.size) { rot[b[it]] }
            assertEquals(null, diagnoseCube(cube, rb, 3))
            val scheme = centreScheme(rb)!!
            val state = decodeCubies(rb, cube, scheme)!!
            val moves = Kociemba.solveCube(state)!!
            assertTrue(cube.isSolved(cube.apply(rb, moves)))
        }
    }

    @Test
    fun diagnosesTwistedCorner() {
        val cube = Cube(3)
        val b = cube.solved()
        val fl = buildCubieMap(cube).corners[0].facelets
        val tmp = b[fl[0]]
        b[fl[0]] = b[fl[1]]
        b[fl[1]] = b[fl[2]]
        b[fl[2]] = tmp
        val issue = diagnoseCube(cube, b, 3)
        assertNotNull(issue)
        assertTrue(issue!!.message.contains("twist", ignoreCase = true))
        assertTrue(issue.cells.isNotEmpty())
    }

    @Test
    fun diagnosesValidCube() {
        val cube = Cube(3)
        val (b, _) = randomScramble(cube, 15)
        assertEquals(null, diagnoseCube(cube, b, 3))
    }

    @Test
    fun patternValidation() {
        for (i in PATTERN_NAMES.indices) {
            val board = patternBoard(3, i)
            assertTrue(PATTERN_NAMES[i], patternPossible(board, 3))
        }
    }
}
