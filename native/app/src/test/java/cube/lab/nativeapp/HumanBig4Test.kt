package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Acceptance checks for the human 4x4 reduction solver:
 * centres, edges, the reduced 3x3 and the real OLL/PLL parity algorithms.
 */
class HumanBig4Test {
    private val cube = Cube(4)

    private fun wideScramble(rnd: Random, count: Int): Pair<IntArray, List<String>> {
        val pool = ArrayList<String>()
        for (k in FACE_KEYS) for (s in arrayOf("", "'", "2")) pool.add(k + s)
        for (k in FACE_KEYS) for (s in arrayOf("", "'")) {
            pool.add(k + "w" + s)
            pool.add("2" + k + s)
            pool.add("3" + k + s)
        }
        var board = cube.solved()
        val moves = ArrayList<String>()
        var prevFace = ' '
        repeat(count) {
            var m: String
            var face: Char
            do {
                m = pool[rnd.nextInt(pool.size)]
                face = m.firstOrNull { it in "UDFBRL" } ?: ' '
            } while (face == prevFace)
            prevFace = face
            moves.add(m)
            board = cube.apply(board, cube.move(m))
        }
        return board to moves
    }

    private fun checkPhases(phases: List<HumanPhase>) {
        val names = phases.map { it.name }
        assertTrue("unknown phase: $names", names.all { it in setOf("Centres", "Edges", "3x3", "Parity") })
        val iC = names.indexOf("Centres")
        val iE = names.indexOf("Edges")
        val i3 = names.indexOf("3x3")
        val iP = names.indexOf("Parity")
        assertEquals("Centres must be first: $names", 0, iC)
        assertTrue("Edges must follow Centres: $names", iC < iE)
        assertTrue("3x3 must follow Edges: $names", iE < i3)
        if (iP >= 0) {
            assertTrue("Parity must come after the 3x3 stage starts: $names", i3 < iP)
        }
    }

    @Test
    fun solvesThirtyWideScrambles() {
        val rnd = Random(20261125)
        var total = 0
        var worst = 0
        var paritySeen = 0
        repeat(30) { trial ->
            val (board, scramble) = wideScramble(rnd, 40)
            val phases = HumanBig4.solve(cube, board)
            assertNotNull("no solution for trial $trial: ${scramble.joinToString(" ")}", phases)
            checkPhases(phases!!)

            val flat = phases.flatMap { it.moves }
            val out = cube.apply(board, flat)
            assertTrue(
                "not solved on trial $trial\nscramble=${scramble.joinToString(" ")}\nphases=${phases.map { it.name }}",
                cube.isSolved(out),
            )
            assertTrue("not the home scheme on trial $trial", out.contentEquals(cube.solved()))
            assertTrue("too many moves (${flat.size}) on trial $trial", flat.size <= 260)

            val parity = phases.firstOrNull { it.name == "Parity" }
            if (parity != null) {
                paritySeen++
                assertNotNull("parity phase has no algorithm name", parity.alg)
                assertTrue("not a real parity alg: ${parity.alg}", parity.alg!!.contains("parity"))
                assertTrue("empty parity algorithm", parity.moves.isNotEmpty())
                if (parity.alg!!.contains("OLL")) {
                    assertTrue("OLL parity alg is not the 4x4 one", parity.moves.contains("2R2") && parity.moves.contains("B2"))
                }
                if (parity.alg!!.contains("PLL")) {
                    assertTrue("PLL parity alg is not the 4x4 one", parity.moves.contains("Uw2"))
                }
            }

            total += flat.size
            worst = maxOf(worst, flat.size)
        }
        println("HumanBig4 average=${total / 30} worst=$worst parity=$paritySeen")
        assertTrue("no parity case in 30 wide scrambles", paritySeen > 0)
    }

    @Test
    fun solvedCubeNeedsNoMoves() {
        val phases = HumanBig4.solve(cube, cube.solved())
        assertNotNull(phases)
        assertTrue(phases!!.isEmpty())
    }

    @Test
    fun sourceAvoidsComputerSolvers() {
        val candidates = listOf(
            File("src/main/java/cube/lab/nativeapp/HumanBig4.kt"),
            File("app/src/main/java/cube/lab/nativeapp/HumanBig4.kt"),
            File("native/app/src/main/java/cube/lab/nativeapp/HumanBig4.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("HumanBig4.kt not found from ${File(".").absolutePath}", file)
        val source = file!!.readText()
        assertFalse(source.contains("BigCube.solve"))
        assertFalse(source.contains("Kociemba"))
    }
}
