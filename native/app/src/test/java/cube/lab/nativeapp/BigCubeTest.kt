package cube.lab.nativeapp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BigCubeTest {
    @Before
    fun load() {
        val bytes = File("src/main/assets/cube3.bin").readBytes()
        assertTrue(Kociemba.loadFromBytes(bytes))
    }

    @Test
    fun solvesRandom4x4() {
        val cube = Cube(4)
        repeat(200) { i ->
            val (board, _) = randomScramble(cube, 40)
            val moves = BigCube.solve(cube, board)
            assertNotNull("no solution for 4x4 scramble $i", moves)
            val out = cube.apply(board, moves!!)
            assertTrue("4x4 not solved on scramble $i", cube.isSolved(out))
        }
    }

    @Test
    fun solvesRandom5x5() {
        val cube = Cube(5)
        repeat(100) { i ->
            val (board, _) = randomScramble(cube, 50)
            val moves = BigCube.solve(cube, board)
            assertNotNull("no solution for 5x5 scramble $i", moves)
            val out = cube.apply(board, moves!!)
            assertTrue("5x5 not solved on scramble $i", cube.isSolved(out))
        }
    }

    @Test
    fun solvesRandom6x6() {
        val cube = Cube(6)
        repeat(50) { i ->
            val (board, _) = randomScramble(cube, 60)
            val moves = BigCube.solve(cube, board)
            assertNotNull("no solution for 6x6 scramble $i", moves)
            val out = cube.apply(board, moves!!)
            assertTrue("6x6 not solved on scramble $i", cube.isSolved(out))
        }
    }

    @Test
    fun solvesRandom7x7() {
        val cube = Cube(7)
        repeat(50) { i ->
            val (board, _) = randomScramble(cube, 70)
            val moves = BigCube.solve(cube, board)
            assertNotNull("no solution for 7x7 scramble $i", moves)
            val out = cube.apply(board, moves!!)
            assertTrue("7x7 not solved on scramble $i", cube.isSolved(out))
        }
    }

    @Test
    fun solvesInto4x4Pattern() {
        val cube = Cube(4)
        val valid = PATTERN_NAMES.indices.filter { BigCube.solve(cube, patternBoard(4, it)) != null }
        println("4x4 valid patterns: ${valid.map { PATTERN_NAMES[it] }}")
        assertTrue("no non-trivial 4x4 pattern", valid.any { it != 0 })
        val patternIndex = valid.first { it != 0 }
        val target = patternBoard(4, patternIndex)
        repeat(10) { i ->
            val (input, _) = randomScramble(cube, 40)
            val perm = BigCube.inferHomePerm(cube, input)
            assertNotNull("scheme $i", perm)
            val mappedInput = BigCube.mapToHome(input, perm!!)
            val targetPerm = BigCube.inferHomePerm(cube, target)
            assertNotNull("target scheme $i", targetPerm)
            val mappedTarget = BigCube.mapToHome(target, targetPerm!!)
            val toSolvedInput = BigCube.solve(cube, mappedInput)
            val toSolvedTarget = BigCube.solve(cube, mappedTarget)
            assertNotNull("input solve $i", toSolvedInput)
            assertNotNull("target solve $i", toSolvedTarget)
            val all = toSolvedInput!! + invSeq(toSolvedTarget!!)
            val out = cube.apply(mappedInput, all)
            assertTrue("4x4 pattern not reached on $i", out.contentEquals(mappedTarget))
        }
    }

    @Test
    fun solvesRotated4x4Schemes() {
        val cube = Cube(4)
        repeat(20) { i ->
            val (board, _) = randomScramble(cube, 40)
            val rot = ROTATION_SCHEMES[(Math.random() * ROTATION_SCHEMES.size).toInt()]
            val rotated = IntArray(board.size) { rot[board[it]] }
            val mapped = BigCube.inferHomeBoard(cube, rotated)
            assertNotNull("scheme not inferred on $i", mapped)
            val moves = BigCube.solve(cube, mapped!!)
            assertNotNull("no solution for rotated 4x4 scramble $i", moves)
            val out = cube.apply(rotated, moves!!)
            assertTrue("rotated 4x4 not solved on scramble $i", cube.isSolved(out))
        }
    }
}
