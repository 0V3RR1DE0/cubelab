package cube.lab.nativeapp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Mirrors the app's play/step-back semantics for every step kind. */
class PlaybackReplayTest {
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

    private fun replay(cube: Cube, start: IntArray, steps: List<Step>): IntArray {
        var b = start
        for (s in steps) {
            if (s.info.turns == 2) {
                val q = cube.move(spellMove(s.info.face, s.info.layers, 1))
                repeat(2) { b = cube.apply(b, q) }
            } else {
                b = cube.apply(b, s.info)
            }
        }
        return b
    }

    private fun unwind(cube: Cube, start: IntArray, steps: List<Step>): IntArray {
        var b = start
        for (s in steps.asReversed()) {
            b = cube.apply(b, cube.move(spellMove(s.info.face, s.info.layers, (4 - s.info.turns) % 4)))
        }
        return b
    }

    @Test
    fun human4x4PlaybackAndUnwind() {
        val cube = Cube(4)
        repeat(6) { t ->
            val board = wideScramble(cube, 35)
            val phases = HumanBig4.solve(cube, board)
            assertNotNull("no solve $t", phases)
            val steps = phases!!.flatMap { p ->
                simplifyMoves(cube, p.moves).map { name ->
                    Step(name, cube.move(name).turns, cube.move(name), p.name, p.alg)
                }
            }
            val solved = replay(cube, board, steps)
            assertTrue("playback not solved $t", cube.isSolved(solved))
            assertTrue("playback not home $t", solved.contentEquals(cube.solved()))
            assertArrayEquals("unwind mismatch $t", board, unwind(cube, solved, steps))
        }
    }

    @Test
    fun everyLayerSetRoundTrips() {
        for (n in 2..6) {
            val cube = Cube(n)
            for (face in 0..5) for (mask in 1 until (1 shl n)) for (turns in 1..3) {
                val layers = (0 until n).filter { mask and (1 shl it) != 0 }.toIntArray()
                // Only contiguous layer sets have a legal notation.
                if (layers.last() - layers.first() + 1 != layers.size) continue
                val label = spellMove(face, layers, turns)
                val direct = cube.apply(cube.solved(), cube.move(label))
                var manual = cube.solved()
                for (l in layers) manual = cube.apply(manual, cube.move(spellMove(face, intArrayOf(l), turns)))
                assertArrayEquals("n=$n f=$face l=${layers.toList()} t=$turns", manual, direct)
            }
        }
    }
}
