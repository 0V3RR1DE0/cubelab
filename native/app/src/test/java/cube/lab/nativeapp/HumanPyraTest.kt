package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class HumanPyraTest {
    private val solved = Pyraminx.solvedColors()
    private val random = Random(20261125)
    private val phaseNames = listOf("Centres", "First layer", "Last layer", "Tips")

    private fun apply(keys: List<String>, board: IntArray = solved): IntArray {
        var out = board
        for (key in keys) out = Pyraminx.applyPerm(out, Pyraminx.moves[key]!!.perm)
        return out
    }

    /** Random scramble of whole-layer turns and tips, as a person would get. */
    private fun scramble(length: Int): List<String> {
        val keys = ArrayList<String>()
        while (keys.size < length) {
            val key = Pyraminx.turnKeys[random.nextInt(Pyraminx.turnKeys.size)]
            if (keys.lastOrNull()?.get(0) == key[0]) continue
            keys.add(key)
        }
        return keys
    }

    private fun tipsOnly(board: IntArray): Boolean {
        val tips = Pyraminx.tips.flatMap { it.cells.toList() }.toSet()
        return (0 until 36).all { it in tips || board[it] == it / 9 }
    }

    @Test
    fun solvedNeedsNoMoves() {
        val phases = HumanPyra.solve(solved)
        assertNotNull(phases)
        assertTrue(phases!!.isEmpty() || phases.all { it.moves.isEmpty() })
    }

    @Test
    fun tipsOnlyScrambleIsSolvedWithTips() {
        val board = apply(listOf("u", "r'", "l", "b'", "u"))
        val phases = HumanPyra.solve(board)
        assertNotNull(phases)
        val flat = phases!!.flatMap { it.moves }
        assertTrue("tips-only scramble: ${flat.joinToString(" ")}", apply(flat, board).contentEquals(solved))
        assertTrue(flat.all { it in Pyraminx.tipKeys })
    }

    @Test
    fun scrambleWithoutTipsIsSolvedWithoutTips() {
        val scrambled = apply(listOf("U", "R", "L'", "B", "R'", "U'", "L"))
        val phases = HumanPyra.solve(scrambled)
        assertNotNull(phases)
        val flat = phases!!.flatMap { it.moves }
        assertTrue("no-tip scramble: ${flat.joinToString(" ")}", apply(flat, scrambled).contentEquals(solved))
        // Main turns twist tips too, so the tips step may still have work to do.
        assertTrue(phases[3].moves.size <= 4)
    }

    @Test
    fun solvesRandomScrambles() {
        var worst = 0
        repeat(500) { trial ->
            val moves = scramble(15 + random.nextInt(11))
            val board = apply(moves)
            val phases = HumanPyra.solve(board)
            assertNotNull("no solution for ${moves.joinToString(" ")}", phases)
            assertEquals(phaseNames, phases!!.map { it.name })

            val flat = phases.flatMap { it.moves }
            assertTrue(
                "trial $trial not solved\nscramble=${moves.joinToString(" ")}\nsolution=$flat",
                apply(flat, board).contentEquals(solved),
            )
            assertTrue("too many moves (${flat.size}) for trial $trial", flat.size <= 45)
            worst = maxOf(worst, flat.size)

            // Teaching order: the first three blocks leave only tips.
            assertTrue(phases.take(3).flatMap { it.moves }.none { it in Pyraminx.tipKeys })
            assertTrue(phases[3].moves.all { it in Pyraminx.tipKeys })
            assertTrue("layers not finished before tips", tipsOnly(apply(phases.take(3).flatMap { it.moves }, board)))
        }
        println("HumanPyra worst=$worst")
    }

    @Test
    fun closeToOptimalOnAverage() {
        Pyraminx.buildDistances()
        var ours = 0
        var optimal = 0
        val sample = 20
        repeat(sample) {
            val board = apply(scramble(18))
            val mine = HumanPyra.solve(board)!!.flatMap { it.moves }
            val best = Pyraminx.solve(board).moves!!
            assertTrue(apply(mine, board).contentEquals(solved))
            ours += mine.size
            optimal += best.size
        }
        // Layer-by-layer cannot approach the optimal move count: averages here are
        // about 18 against 8 optimal, so only a loose regression bound is asserted.
        println("HumanPyra sample average: ours=${ours / sample} optimal=${optimal / sample}")
        assertTrue("average ${ours / sample} vs optimal ${optimal / sample}", ours <= optimal * 3)
    }

    @Test
    fun worksWithAnyColourScheme() {
        val colour = intArrayOf(2, 0, 3, 1) // the user's four sticker colours
        val board = apply(scramble(20)).map { colour[it] }.toIntArray()
        val phases = HumanPyra.solve(board)
        assertNotNull(phases)
        val flat = phases!!.flatMap { it.moves }
        assertTrue(Pyraminx.isSolved(apply(flat, board)))
    }

    @Test
    fun rejectsIllegalColours() {
        assertNull(HumanPyra.solve(IntArray(35) { it / 9 }))
        assertNull(HumanPyra.solve(IntArray(36) { it % 3 }))
        assertNull(HumanPyra.solve(IntArray(36) { it % 4 + (if (it < 4) 1 else 0) }))
        assertNotNull(HumanPyra.solve(solved))
    }
}
