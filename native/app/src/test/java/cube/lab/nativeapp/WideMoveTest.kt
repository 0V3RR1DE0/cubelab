package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WideMoveTest {
    @Test
    fun mergesAdjacentLayersIntoWideTurns() {
        val cube = Cube(4)
        val cases = listOf(
            listOf("R", "2R", "3R") to "3Rw",
            listOf("2R", "3R") to "2-3R",
            listOf("4R", "3R") to "3-4R",
            listOf("R'", "2R'", "3R'") to "3Rw'",
        )
        for ((names, label) in cases) {
            val steps = compressMoves(cube, names)
            assertEquals("$names should merge", 1, steps.size)
            assertEquals(label, steps[0].label)
            val a = cube.apply(cube.solved(), names)
            val b = cube.apply(cube.solved(), steps.map { it.label })
            assertTrue("$names changed the result", a.contentEquals(b))
        }
    }

    @Test
    fun rangeMovesParseAndSpell() {
        val cube = Cube(7)
        for (label in listOf("R", "2R", "4R", "3Rw", "2-3R", "2-4Rw", "3Rw2", "2-3R'")) {
            val info = cube.move(label)
            val round = cube.move(spellMove(info.face, info.layers, info.turns))
            assertEquals("layers for $label", info.layers.toList(), round.layers.toList())
            assertEquals("turns for $label", info.turns, round.turns)
        }
    }

    @Test
    fun wideTurnsKeepTheSameBoard() {
        val cube = Cube(5)
        val start = cube.apply(cube.solved(), listOf("R", "U", "2F"))
        val wide = cube.apply(start, listOf("Rw"))
        val split = cube.apply(start, listOf("R", "2R"))
        assertTrue(wide.contentEquals(split))
    }
}
