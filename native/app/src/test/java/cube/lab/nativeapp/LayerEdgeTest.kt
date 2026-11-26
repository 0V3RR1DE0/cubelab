package cube.lab.nativeapp

import org.junit.Assert.assertTrue
import org.junit.Test

class LayerEdgeTest {
    @Test
    fun farLayerMovesAreFullFaceTurns() {
        val cases = listOf(
            4 to ("4R" to "L'"),
            4 to ("4U" to "D'"),
            4 to ("4F" to "B'"),
            5 to ("5R" to "L'"),
            6 to ("6R" to "L'"),
        )
        for ((n, pair) in cases) {
            val cube = Cube(n)
            val a = cube.apply(cube.solved(), listOf(pair.first))
            val b = cube.apply(cube.solved(), listOf(pair.second))
            assertTrue("${pair.first} on ${n}x$n should equal ${pair.second}", a.contentEquals(b))
        }
    }

    @Test
    fun fullWidthWideTurnsAreRotations() {
        val cube = Cube(4)
        val x = cube.apply(cube.solved(), listOf("x"))
        val wide = cube.apply(cube.solved(), listOf("4Rw"))
        assertTrue("4Rw should equal x", x.contentEquals(wide))
    }
}
