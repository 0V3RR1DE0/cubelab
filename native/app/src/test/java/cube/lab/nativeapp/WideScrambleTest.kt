package cube.lab.nativeapp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Real 4x4+ scrambles use wide/slice turns, which can leave wing orbits with
 * odd parity. The solver must fix that parity first instead of reporting the
 * cube as unsolvable.
 */
class WideScrambleTest {
    @Before
    fun load() {
        Kociemba.loadFromBytes(File("src/main/assets/cube3.bin").readBytes())
    }

    private fun wideScramble(cube: Cube, count: Int, rotate: Boolean): IntArray {
        val pool = ArrayList<String>()
        for (k in FACE_KEYS) for (s in arrayOf("", "'", "2")) pool.add(k + s)
        for (k in FACE_KEYS) for (s in arrayOf("", "'")) {
            pool.add(k + "w" + s); pool.add("2" + k + s); pool.add("3" + k + s)
        }
        var board = cube.solved()
        var prev = ""
        repeat(count) {
            var m: String
            do { m = pool[(Math.random() * pool.size).toInt()] } while (m[0] == prev.firstOrNull())
            prev = m
            board = cube.apply(board, cube.move(m))
        }
        if (!rotate) return board
        val perm = IntArray(6) { it }.also { p ->
            for (i in p.indices.reversed()) {
                val j = (Math.random() * (i + 1)).toInt()
                val t = p[i]; p[i] = p[j]; p[j] = t
            }
        }
        return IntArray(board.size) { perm[board[it]] }
    }

    private fun check(n: Int, trials: Int) {
        val cube = Cube(n)
        repeat(trials) { t ->
            val board = wideScramble(cube, 40, rotate = t % 2 == 1)
            val perm = BigCube.inferHomePerm(cube, board)
            assertNotNull("scheme inference failed $n x $n trial $t", perm)
            val mapped = BigCube.mapToHome(board, perm!!)
            val moves = BigCube.solve(cube, mapped)
            assertNotNull("no solution $n x $n trial $t", moves)
            val out = cube.apply(board, moves!!)
            assertTrue("not solved $n x $n trial $t", cube.isSolved(out))
        }
    }

    @Test
    fun solvesWideScrambled4x4() = check(4, 60)

    @Test
    fun solvesWideScrambled5x5() = check(5, 15)

    @Test
    fun solvesWideScrambled6x6() = check(6, 10)

    @Test
    fun solvesWideScrambled7x7() = check(7, 6)
}
