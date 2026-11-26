package cube.lab.nativeapp

// Human 2x2 Ortega solver: first face, OLL, then PBL.
//
// Boards are face-indexed (sticker = face*4 + row*2 + col) like Human3, so the
// solved reference is 0,0,0,0,1,1,1,1,... and not Cube.solved(), which uses a
// colour scheme.
//
// The first face is a BFS over the four stickers of the D colour: a sticker is
// tracked as (corner slot, orientation) = 24 positions, so four of them span
// 24^4 states. OLL and PBL use the standard algorithms, and every pick is
// checked on the real board instead of assumed.
//
// A 2x2 has no fixed centres, so PBL is the PBL table only up to a whole-cube
// rotation. Each candidate is prefixed by one of the 24 rotations (the R of
// R + alg + R^-1); the R^-1 is not written out because the rotation that fixes
// the final frame is appended to the PBL moves instead.

object Human2 {
    private const val U = 0
    private const val D = 1

    private val cube = Cube(2)
    private val solved = IntArray(24) { it / 4 }
    private val corners = buildCubieMap(cube).corners
    private val faceTurns = Kociemba.MOVE_NAMES

    private val slotOf = HashMap<Int, Int>().also { m ->
        corners.forEachIndexed { s, info -> info.facelets.forEach { m[it] = s } }
    }

    // OLL cases: the seven standard 2x2 corner-orientation algorithms.
    private val OLL_ALGS = listOf(
        "Sune" to listOf("R", "U", "R'", "U", "R", "U2", "R'"),
        "Anti-Sune" to listOf("R", "U2", "R'", "U'", "R", "U'", "R'"),
        "H" to listOf("R2", "U2", "R", "U2", "R2"),
        "Pi" to listOf("R", "U2", "R2", "U'", "R2", "U'", "R2", "U2", "R"),
        "T" to listOf("R", "U", "R'", "U'", "R'", "F", "R", "F'"),
        "U" to listOf("F", "R", "U", "R'", "U'", "F'"),
        "L" to listOf("F", "R'", "F'", "R", "U", "R", "U'", "R'"),
    )

    // Standard PBL algorithms: the four both-layers cases plus the single-layer
    // ones. "Solved" is the both-layers-already-permuted case, finished by AUF.
    // The 2x2 spellings of T/Y-perm are listed next to the 3x3 ones: they are
    // the same permutations with fewer moves. Rotation and AUF variants are
    // generated below.
    private val PBL_ALGS = listOf(
        "Solved" to emptyList(),
        "Adj-adj" to listOf("R2", "U'", "B2", "U2", "R2", "U'", "R2"),
        "Diag-diag" to listOf("R2", "F2", "R2"),
        "Adj-diag" to listOf("R'", "F", "R'", "F2", "R", "U'", "R"),
        "U-adj" to listOf("R2", "U", "R2", "U'", "R2", "U", "R2", "U'", "R2"),
        "T-perm" to listOf(
            "R", "U", "R'", "U'", "R'", "F", "R2", "U'", "R'", "U'", "R", "U", "R'", "F'",
        ),
        "T-perm" to listOf("R", "U2", "R'", "U'", "R", "U2", "R'", "F", "R'", "F'", "R"),
        "Y-perm" to listOf(
            "F", "R", "U'", "R'", "U'", "R", "U", "R'", "F'", "R", "U", "R'", "U'", "R'", "F", "R", "F'",
        ),
        "Y-perm" to listOf("R", "U'", "R'", "U'", "F2", "U'", "R", "U", "R'", "U", "F2"),
    )

    private val AUF = listOf(emptyList(), listOf("U"), listOf("U2"), listOf("U'"))

    // ---------------------------------------------------------------- geometry

    /** Sticker at (slot, orientation) after each face turn. */
    private val stickerTrans: Array<IntArray> by lazy {
        val perms = faceTurns.map { cube.move(it).perm }
        Array(24) { v ->
            val slot = v / 3
            val facelet = corners[slot].facelets[v % 3]
            IntArray(perms.size) { m ->
                val moved = perms[m][facelet]
                val next = slotOf[moved]!!
                next * 3 + corners[next].facelets.indexOf(moved)
            }
        }
    }

    // ---------------------------------------------------------------- first face

    private fun firstFace(board: IntArray): List<String>? {
        // The four stickers that belong on the D face, as (slot, orientation).
        val start = IntArray(4)
        var n = 0
        for (slot in 0 until 8) {
            for (o in 0..2) {
                if (board[corners[slot].facelets[o]] == D) {
                    start[n++] = slot * 3 + o
                    break
                }
            }
        }
        if (n != 4) return null

        val size = 24 * 24 * 24 * 24
        val parent = IntArray(size) { -1 }
        val via = ByteArray(size)
        val queue = IntArray(size)
        val startKey = encode(start[0], start[1], start[2], start[3])
        parent[startKey] = startKey
        queue[0] = startKey
        var head = 0
        var tail = 1
        var goal = -1
        while (head < tail) {
            val key = queue[head++]
            var x = key
            val a = x % 24; x /= 24
            val b = x % 24; x /= 24
            val c = x % 24; x /= 24
            val d = x % 24
            if (onD(a) && onD(b) && onD(c) && onD(d)) {
                goal = key
                break
            }
            for (m in faceTurns.indices) {
                val na = stickerTrans[a][m]
                val nb = stickerTrans[b][m]
                val nc = stickerTrans[c][m]
                val nd = stickerTrans[d][m]
                val next = encode(na, nb, nc, nd)
                if (parent[next] != -1) continue
                parent[next] = key
                via[next] = m.toByte()
                queue[tail++] = next
            }
        }
        if (goal < 0) return null

        val out = ArrayList<String>()
        var key = goal
        while (key != startKey) {
            out.add(faceTurns[via[key].toInt()])
            key = parent[key]
        }
        out.reverse()
        return out
    }

    private fun onD(sticker: Int): Boolean = corners[sticker / 3].faces[sticker % 3] == D

    private fun encode(a: Int, b: Int, c: Int, d: Int): Int = a + 24 * (b + 24 * (c + 24 * d))

    // ---------------------------------------------------------------- OLL / PBL

    private fun faceSolid(board: IntArray, face: Int): Boolean {
        val base = face * 4
        return (1 until 4).all { board[base + it] == board[base] }
    }

    private fun oll(board: IntArray): Pair<String, List<String>>? {
        var best: Pair<String, List<String>>? = null
        var bestCost = Int.MAX_VALUE
        for ((name, alg) in OLL_ALGS) for (auf in AUF) {
            val moves = auf + alg
            if (moves.size >= bestCost) continue
            val next = cube.apply(board, moves)
            if (faceSolid(next, D) && faceSolid(next, U)) {
                bestCost = moves.size
                best = name to moves
            }
        }
        return best
    }

    /** The 24 whole-cube orientations, shortest spellings. */
    private val rotationMoves = listOf("x", "x2", "x'", "y", "y2", "y'", "z", "z2", "z'")
    private val rotations: List<List<String>> by lazy {
        val out = ArrayList<List<String>>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<Pair<IntArray, List<String>>>()
        fun add(board: IntArray, seq: List<String>) {
            if (seen.add(board.joinToString(","))) queue.add(board to seq)
        }
        add(solved, emptyList())
        while (queue.isNotEmpty()) {
            val (board, seq) = queue.removeFirst()
            out.add(seq)
            for (move in rotationMoves) add(cube.apply(board, listOf(move)), seq + move)
        }
        out
    }

    private fun compose(seq: List<String>): IntArray {
        var perm = IntArray(24) { it }
        for (move in seq) {
            val p = cube.move(move).perm
            perm = IntArray(24) { p[perm[it]] }
        }
        return perm
    }

    private fun applyPerm(board: IntArray, perm: IntArray): IntArray {
        val out = IntArray(24)
        for (i in out.indices) out[perm[i]] = board[i]
        return out
    }

    private val rotationPerms: List<Pair<List<String>, IntArray>> by lazy {
        rotations.map { it to compose(it) }
    }

    /** Rotation that turns a solved-in-another-frame board into the exact solved board. */
    private fun fixingRotation(board: IntArray): List<String>? {
        for ((seq, perm) in rotationPerms) {
            if (applyPerm(board, perm).contentEquals(solved)) return seq
        }
        return null
    }

    private class Candidate(val name: String, val moves: List<String>, val perm: IntArray)

    private val pblCandidates: List<Candidate> by lazy {
        val out = ArrayList<Candidate>()
        for ((name, alg) in PBL_ALGS) {
            for (rot in rotations) {
                for (up in AUF) for (down in AUF) for (after in AUF) {
                    // Both layers are aligned before the algorithm and the last
                    // layer is AUF'ed after it.
                    val moves = rot + up + down + alg + after
                    out.add(Candidate(name, moves, compose(moves)))
                }
            }
        }
        out
    }

    private fun pbl(board: IntArray): Pair<String, List<String>>? {
        var best: Pair<String, List<String>>? = null
        var bestCost = Int.MAX_VALUE
        for (candidate in pblCandidates) {
            val next = applyPerm(board, candidate.perm)
            if (!cube.isSolved(next)) continue
            val fix = fixingRotation(next) ?: continue
            val cost = candidate.moves.size + fix.size
            if (cost < bestCost) {
                bestCost = cost
                best = candidate.name to (candidate.moves + fix)
            }
        }
        return best
    }

    // ---------------------------------------------------------------- solve

    private fun legal(board: IntArray): Boolean {
        // Face-index values only; decodeCubies indexes its colour table by value.
        if (board.any { it < 0 || it > 5 }) return false
        val state = decodeCubies(board, cube, intArrayOf(0, 1, 2, 3, 4, 5)) ?: return false
        // Corner permutation is free on a 2x2; only the twist sum is invariant.
        return state.co.sum() % 3 == 0
    }

    fun solve(input: IntArray): List<HumanPhase>? {
        if (input.size != 24 || !legal(input)) return null
        if (input.contentEquals(solved)) return emptyList()

        var board = input
        val phases = ArrayList<HumanPhase>()

        val first = firstFace(board) ?: return null
        board = cube.apply(board, first)
        phases.add(HumanPhase("First face", null, first))

        if (!faceSolid(board, U)) {
            val (name, moves) = oll(board) ?: return null
            board = cube.apply(board, moves)
            phases.add(HumanPhase("OLL", name, moves))
        }

        val (name, moves) = pbl(board) ?: return null
        board = cube.apply(board, moves)
        phases.add(HumanPhase("PBL", name, moves))

        return if (board.contentEquals(solved)) phases else null
    }
}
