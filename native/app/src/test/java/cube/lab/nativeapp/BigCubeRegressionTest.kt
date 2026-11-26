package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BigCubeRegressionTest {
    @Before
    fun load() {
        val bytes = File("src/main/assets/cube3.bin").readBytes()
        assertTrue(Kociemba.loadFromBytes(bytes))
    }

    // Classic vertical stripes on 5-7 was rejected because the reduced 3x3 is a
    // pure 2-2 corner swap, which needs 16 phase-2 moves; the phase-2 node
    // budget used to run out before that depth.
    @Test
    fun solvesVerticalStripes5To7() {
        val alg = "F U F R L2 B D' R D2 L D' B R2 L F U F".split(" ")
        for (n in 5..7) {
            val cube = Cube(n)
            val board = cube.apply(cube.solved(), alg)
            val moves = BigCube.solve(cube, board)
            assertNotNull("no solution for ${n}x$n vertical stripes", moves)
            val out = cube.apply(board, moves!!)
            for (f in 0 until 6) {
                val base = f * n * n
                val colour = out[base]
                for (k in 1 until n * n) {
                    assertEquals("${n}x$n face $f sticker $k", colour, out[base + k])
                }
            }
            val faceColours = (0 until 6).map { out[it * n * n] }
            assertEquals("${n}x$n did not end solved", 6, faceColours.toSet().size)
        }
    }

    // Root cause: a legal pure 2-2 corner swap with the edges solved is 16
    // phase-2 moves, which the old 40M node budget never reached.
    @Test
    fun kociembaSolvesPureCornerSwap() {
        val state = CubieState(
            intArrayOf(3, 2, 1, 0, 7, 6, 5, 4),
            IntArray(8),
            IntArray(12) { it },
            IntArray(12),
        )
        assertTrue(Kociemba.isValid3(state))
        assertNotNull("legal 3x3 state reported unsolvable", Kociemba.solveCube(state))
    }
}
