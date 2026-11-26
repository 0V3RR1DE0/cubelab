package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Human3Test {
    private val cube = Cube(3)
    private val solved = IntArray(54) { it / 9 }

    private fun randomMoves(count: Int): List<String> {
        val out = ArrayList<String>()
        var prev = ""
        repeat(count) {
            var face: String
            do {
                face = arrayOf("U", "D", "F", "B", "R", "L")[(Math.random() * 6).toInt()]
            } while (face == prev)
            prev = face
            out.add(face + arrayOf("", "'", "2")[(Math.random() * 3).toInt()])
        }
        return out
    }

    @Test
    fun rotationsAreRealCubeRotations() {
        assertFalse(cube.apply(solved, listOf("y")).contentEquals(solved))
        for (axis in arrayOf("x", "y", "z")) {
            assertTrue("$axis x4", cube.apply(solved, List(4) { axis }).contentEquals(solved))
            assertTrue("$axis $axis'", cube.apply(solved, listOf(axis, "$axis'")).contentEquals(solved))
            assertTrue("$axis 2 2", cube.apply(solved, listOf("${axis}2", "${axis}2")).contentEquals(solved))
        }
    }

    @Test
    fun solvedCubeNeedsNoMoves() {
        val phases = Human3.solve(solved)
        assertNotNull(phases)
        assertEquals(0, phases!!.size)
    }

    @Test
    fun solvesSingleTurns() {
        for (move in listOf("R", "U", "F", "D", "L", "B", "R2", "U'")) {
            val board = cube.apply(solved, listOf(move))
            val phases = Human3.solve(board)
            assertNotNull("no solution for $move", phases)
            val flat = phases!!.flatMap { it.moves }
            assertTrue("$move not solved by ${flat.joinToString(" ")}", cube.apply(board, flat).contentEquals(solved))
        }
    }

    @Test
    fun solvesRandomScrambles() {
        var worst = 0
        var total = 0
        repeat(300) { t ->
            val scramble = randomMoves(25)
            val board = cube.apply(solved, scramble)
            val phases = Human3.solve(board)
            assertNotNull("no solution for trial $t: ${scramble.joinToString(" ")}", phases)
            val flat = phases!!.flatMap { it.moves }
            val out = cube.apply(board, flat)
            assertTrue(
                "not solved for trial $t\nscramble=${scramble.joinToString(" ")}\nsolution=${flat.joinToString(" ")}",
                out.contentEquals(solved),
            )
            assertTrue("rotation left in solution for trial $t", flat.none { it[0] in "xyz" })
            // Real data over 3000 scrambles: average 143, worst 221.
            assertTrue("too many moves (${flat.size}) for trial $t", flat.size <= 240)
            worst = maxOf(worst, flat.size)
            total += flat.size
        }
        assertTrue("average too high (${total / 300})", total <= 300 * 150)
        println("Human3 average=${total / 300} worst=$worst")
    }

    @Test
    fun remappedTriggersMatchRotationConjugates() {
        val rotations = listOf(emptyList(), listOf("y"), listOf("y2"), listOf("y'"))
        val algs = listOf(
            listOf("R", "U", "R'", "U'"),
            listOf("U", "R", "U'", "R'", "U'", "F'", "U", "F"),
        )
        val boards = listOf(
            solved,
            cube.apply(solved, listOf("R", "U", "F'", "L2", "B", "U2", "R'", "D", "F2")),
        )
        for (alg in algs) {
            for (k in 0..3) {
                val remapped = Human3.remap(alg, k)
                assertTrue("rotation left in remapped $alg", remapped.none { it[0] in "xyz" })
                for (board in boards) {
                    val conjugated = rotations[k] + alg + rotations[(4 - k) % 4]
                    assertTrue(
                        "k=$k alg=${alg.joinToString(" ")}",
                        cube.apply(board, remapped).contentEquals(cube.apply(board, conjugated)),
                    )
                }
            }
        }
    }

    @Test
    fun phasesUseNamedBlocks() {
        val board = cube.apply(solved, randomMoves(20))
        val phases = Human3.solve(board)!!
        val names = phases.map { it.name }
        assertTrue(names.contains("Cross"))
        assertTrue(names.any { it.startsWith("First-layer corners") })
        assertTrue(names.any { it.startsWith("Second-layer edges") })
        assertTrue(names.any { it.startsWith("Orient last-layer") })
        assertTrue(names.any { it.startsWith("Permute last-layer") })
        // Every phase except Cross names the algorithm it used.
        assertTrue(phases.filter { it.name != "Cross" }.all { it.alg != null })
    }
}
