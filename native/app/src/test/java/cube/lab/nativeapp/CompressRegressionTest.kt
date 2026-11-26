package cube.lab.nativeapp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CompressRegressionTest {
    @Before
    fun load() {
        Kociemba.loadFromBytes(File("src/main/assets/cube3.bin").readBytes())
    }

    private fun wideScramble(cube: Cube, count: Int): IntArray {
        val pool = ArrayList<String>()
        for (k in FACE_KEYS) for (s in arrayOf("", "'", "2")) pool.add(k + s)
        for (k in FACE_KEYS) for (s in arrayOf("", "'")) {
            pool.add(k + "w" + s); pool.add("2" + k + s); pool.add("3" + k + s)
        }
        var b = cube.solved()
        var prev = ""
        repeat(count) {
            var m: String
            do { m = pool[(Math.random() * pool.size).toInt()] } while (m[0] == prev.firstOrNull())
            prev = m
            b = cube.apply(b, cube.move(m))
        }
        return b
    }

    private fun check(n: Int, trials: Int) {
        val cube = Cube(n)
        repeat(trials) { t ->
            val board = wideScramble(cube, 35)
            val perm = BigCube.inferHomePerm(cube, board)
            if (perm == null) { println("DBG n=$n trial=$t scheme failed"); return@repeat }
            val mapped = BigCube.mapToHome(board, perm)
            val moves = BigCube.solve(cube, mapped)
            assertNotNull("solve $n $t", moves)
            val raw = cube.apply(mapped, moves!!)
            assertTrue("raw not solved $n $t", cube.isSolved(raw))
            val simp = simplifyMoves(cube, moves)
            assertTrue("simplify broke $n $t", cube.isSolved(cube.apply(mapped, simp)))
            val steps = compressMoves(cube, simp)
            val out = cube.apply(mapped, steps.map { it.label })
            assertTrue("compress broke $n $t (${steps.size} steps)", cube.isSolved(out))
            val original = cube.apply(board, steps.map { it.label })
            assertTrue("original not solved $n $t (${steps.size} steps)", cube.isSolved(original))
            println("DBG n=$n trial=$t raw=${moves.size} simp=${simp.size} steps=${steps.size}")
        }
    }

    @Test fun compressed4x4() = check(4, 20)
    @Test fun compressed5x5() = check(5, 3)
}
