package cube.lab.nativeapp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Human2SmokeTest {
    private val cube = Cube(2)
    private val solved = IntArray(24) { it / 4 }

    @Test
    fun solvesRandom2x2() {
        var worst = 0
        var total = 0
        repeat(100) { t ->
            var board = solved
            var prev = ""
            repeat(18) {
                var face: String
                do { face = arrayOf("U","D","F","B","R","L")[(Math.random() * 6).toInt()] } while (face == prev)
                prev = face
                board = cube.apply(board, listOf(face + arrayOf("", "'", "2")[(Math.random() * 3).toInt()]))
            }
            val phases = Human2.solve(board)
            assertNotNull("no solution $t", phases)
            val flat = phases!!.flatMap { it.moves }
            assertTrue("not solved $t: ${flat.joinToString(" ")}", cube.apply(board, flat).contentEquals(solved))
            assertTrue("names $t", phases.all { it.name in setOf("First face", "OLL", "PBL") })
            worst = maxOf(worst, flat.size)
            total += flat.size
        }
        println("Human2 average=${total / 100} worst=$worst")
    }
}
