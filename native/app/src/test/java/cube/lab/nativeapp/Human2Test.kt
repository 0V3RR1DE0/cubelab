package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Human2Test {
    private val cube = Cube(2)
    private val solved = IntArray(24) { it / 4 }
    private val phaseNames = setOf("First face", "OLL", "PBL")

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

    /** Solves, applies the moves for real and checks every phase contract. */
    private fun verifiedSolve(board: IntArray, label: String): List<String> {
        val phases = Human2.solve(board)
        assertNotNull("$label: no solution", phases)
        val flat = phases!!.flatMap { it.moves }
        assertTrue(
            "$label: not solved by ${flat.joinToString(" ")}",
            cube.apply(board, flat).contentEquals(solved),
        )
        assertTrue("$label: bad phase names ${phases.map { it.name }}", phases.all { it.name in phaseNames })
        assertTrue("$label: ${flat.size} moves", flat.size <= 35)
        if (!board.contentEquals(solved)) {
            assertEquals("$label: first phase", "First face", phases.first().name)
        }
        return flat
    }

    @Test
    fun solvedCubeNeedsNoMoves() {
        val phases = Human2.solve(solved)
        assertNotNull(phases)
        assertTrue(phases!!.isEmpty())
    }

    @Test
    fun solvesSingleTurns() {
        for (face in arrayOf("U", "D", "F", "B", "R", "L")) {
            for (suffix in arrayOf("", "2", "'")) {
                val move = face + suffix
                verifiedSolve(cube.apply(solved, listOf(move)), move)
            }
        }
        // Whole-cube rotations are legal moves too; a rotated solved cube is not
        // the same board, so the solver must rotate the frame back.
        for (rotation in arrayOf("x", "y", "z", "x2", "y2", "z2")) {
            verifiedSolve(cube.apply(solved, listOf(rotation)), rotation)
        }
    }

    @Test
    fun solvesFixedScrambles() {
        val scrambles = listOf(
            "R U R' U'",
            "F R U' R' U' R U R' F'",
            "R U2 R' U' R U' R'",
            "F' B' F2 B2 F2 D2 F' U2 L R B R2 U2 R U",
            "L' R F2 U' L2 F' L B' R' B' L2 U D' B' F' U2 R' D U' B2 R L U' D U2",
        )
        for (scramble in scrambles) {
            verifiedSolve(cube.apply(solved, scramble.split(" ")), scramble)
        }
    }

    @Test
    fun solvesRandomScrambles() {
        val trials = 200
        var total = 0
        var worst = 0
        var worstScramble = ""
        repeat(trials) { t ->
            val scramble = randomMoves(15 + (Math.random() * 11).toInt())
            val board = cube.apply(solved, scramble)
            val moves = verifiedSolve(board, "trial $t: ${scramble.joinToString(" ")}")
            total += moves.size
            if (moves.size > worst) {
                worst = moves.size
                worstScramble = scramble.joinToString(" ")
            }
        }
        println("Human2 average=${total.toDouble() / trials} worst=$worst ($worstScramble)")
    }

    @Test
    fun firstPhaseIsFirstFace() {
        val board = cube.apply(solved, randomMoves(20))
        assertFalse(board.contentEquals(solved))
        val phases = Human2.solve(board)
        assertNotNull(phases)
        assertTrue(phases!!.isNotEmpty())
        assertEquals("First face", phases.first().name)

        // D face already solid: the phase is still reported, with no moves.
        val upperTurn = cube.apply(solved, listOf("U"))
        val upperPhases = Human2.solve(upperTurn)
        assertNotNull(upperPhases)
        assertEquals("First face", upperPhases!!.first().name)
        assertTrue(upperPhases.first().moves.isEmpty())
    }

    @Test
    fun rejectsIllegalStates() {
        assertNull("wrong size", Human2.solve(IntArray(23)))
        assertNull("one colour only", Human2.solve(IntArray(24)))
        assertNull("out of range", Human2.solve(IntArray(24) { 7 }))

        // Twist a single corner: impossible on a real 2x2.
        val twisted = solved.copyOf()
        val facelets = buildCubieMap(cube).corners[0].facelets
        val first = twisted[facelets[0]]
        twisted[facelets[0]] = twisted[facelets[1]]
        twisted[facelets[1]] = twisted[facelets[2]]
        twisted[facelets[2]] = first
        assertNull("twisted corner", Human2.solve(twisted))
    }
}
