package cube.lab.nativeapp

// Completes a partially painted board into a legal cube.
//
// "Fill rest" used to place colours in order, which produced impossible
// schemes (e.g. white next to yellow centres). This completes a real cube
// instead:
//  * 1x1: fill the remaining distinct colours.
//  * 2x2/3x3: choose a proper colour scheme, then backtrack over the corner and
//    edge pieces while respecting every painted sticker, and validate the
//    resulting cubie state.
//  * 4x4-7x7: complete to a solved cube in a proper scheme when the painted
//    stickers are consistent with one (big cubes are normally painted face by
//    face, so this is the useful prediction).

object InputFill {
    private val PERMS3 = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(0, 2, 1),
        intArrayOf(1, 0, 2), intArrayOf(1, 2, 0),
        intArrayOf(2, 0, 1), intArrayOf(2, 1, 0),
    )
    private val PERMS2 = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0))

    fun complete(n: Int, board: IntArray): IntArray? {
        if (board.size != 6 * n * n) return null
        return when {
            n == 1 -> complete1(board)
            n == 2 || n == 3 -> completeSmall(n, board)
            else -> completeSolvedBig(n, board)
        }
    }

    private fun complete1(board: IntArray): IntArray? {
        val have = HashSet<Int>()
        for (c in board) {
            if (c < 0) continue
            if (c > 5 || !have.add(c)) return null
        }
        val remaining = (0..5).filter { !have.contains(it) }.toMutableList()
        val out = board.copyOf()
        for (i in out.indices) if (out[i] < 0) {
            if (remaining.isEmpty()) return null
            out[i] = remaining.removeAt(0)
        }
        return if (out.toSet().size == 6) out else null
    }

    private fun completeSmall(n: Int, board: IntArray): IntArray? {
        val cube = Cube(n)
        val map = buildCubieMap(cube)
        for (scheme in ROTATION_SCHEMES) {
            if (n == 3) {
                var ok = true
                for (f in 0 until 6) {
                    val idx = f * 9 + 4
                    if (board[idx] >= 0 && board[idx] != scheme[f]) { ok = false; break }
                }
                if (!ok) continue
            }
            val out = completeWithScheme(n, board, cube, map, scheme) { b ->
                val state = decodeCubies(b, cube, scheme) ?: return@completeWithScheme false
                if (n == 2) state.cp.toSet().size == 8 && state.co.sum() % 3 == 0
                else Kociemba.isValid3(state)
            }
            if (out != null) return out
        }
        return null
    }

    private fun completeWithScheme(
        n: Int,
        board: IntArray,
        cube: Cube,
        map: CubieMap,
        scheme: IntArray,
        validate: (IntArray) -> Boolean,
    ): IntArray? {
        val out = board.copyOf()
        if (n == 3) for (f in 0 until 6) out[f * 9 + 4] = scheme[f]

        val cornerFaces = Array(8) { q -> IntArray(3) { k -> scheme[map.corners[q].faces[k]] } }
        val edgeFaces = Array(12) { q -> IntArray(2) { k -> scheme[map.edges[q].faces[k]] } }
        // Only the three cyclic placements of a corner are physical; a mirrored
        // placement can pass the twist/parity checks but is not reachable.
        val cornerPhysical = Array(8) { p ->
            val ordP = cornerOrder(cube, map, p)
            val pf = map.corners[p].faces
            Array(8) { q ->
                val ordQ = cornerOrder(cube, map, q)
                Array(3) { shift ->
                    IntArray(3) { k ->
                        val idxP = ordP.indexOf(pf[k])
                        scheme[ordQ[(idxP + shift) % 3]]
                    }
                }
            }
        }
        val usedC = BooleanArray(8)
        val usedE = BooleanArray(12)
        var nodes = 0
        val nodeLimit = 4_000_000

        fun placeEdges(i: Int, order: IntArray): Boolean {
            if (i == order.size) return validate(out)
            if (++nodes > nodeLimit) return false
            val p = order[i]
            val facelets = map.edges[p].facelets
            val candidates = (0 until 12).sortedBy { if (it == p) 0 else 1 }
            for (q in candidates) {
                if (usedE[q]) continue
                for (pp in PERMS2) {
                    var ok = true
                    for (k in 0 until 2) {
                        val f = facelets[k]
                        val c = edgeFaces[q][pp[k]]
                        if (out[f] >= 0 && out[f] != c) { ok = false; break }
                    }
                    if (!ok) continue
                    val saved = IntArray(2) { out[facelets[it]] }
                    for (k in 0 until 2) out[facelets[k]] = edgeFaces[q][pp[k]]
                    usedE[q] = true
                    if (placeEdges(i + 1, order)) return true
                    usedE[q] = false
                    for (k in 0 until 2) out[facelets[k]] = saved[k]
                }
            }
            return false
        }

        val cornerOrder = (0 until 8).sortedByDescending { p ->
            map.corners[p].facelets.count { board[it] >= 0 }
        }.toIntArray()
        val edgeOrder = if (n == 3) {
            (0 until 12).sortedByDescending { p ->
                map.edges[p].facelets.count { board[it] >= 0 }
            }.toIntArray()
        } else {
            IntArray(0)
        }

        fun placeCorners(i: Int): Boolean {
            if (i == cornerOrder.size) {
                return if (n == 3) placeEdges(0, edgeOrder) else validate(out)
            }
            if (++nodes > nodeLimit) return false
            val p = cornerOrder[i]
            val facelets = map.corners[p].facelets
            val candidates = (0 until 8).sortedBy { if (it == p) 0 else 1 }
            for (q in candidates) {
                if (usedC[q]) continue
                for (assign in cornerPhysical[p][q]) {
                    var ok = true
                    for (k in 0 until 3) {
                        val f = facelets[k]
                        if (out[f] >= 0 && out[f] != assign[k]) { ok = false; break }
                    }
                    if (!ok) continue
                    val saved = IntArray(3) { out[facelets[it]] }
                    for (k in 0 until 3) out[facelets[k]] = assign[k]
                    usedC[q] = true
                    if (placeCorners(i + 1)) return true
                    usedC[q] = false
                    for (k in 0 until 3) out[facelets[k]] = saved[k]
                }
            }
            return false
        }

        return if (placeCorners(0)) out else null
    }

    private fun completeSolvedBig(n: Int, board: IntArray): IntArray? {
        val nn = n * n
        for (scheme in ROTATION_SCHEMES) {
            var ok = true
            for (i in board.indices) {
                if (board[i] < 0) continue
                if (board[i] != scheme[i / nn]) { ok = false; break }
            }
            if (!ok) continue
            return IntArray(board.size) { scheme[it / nn] }
        }
        return null
    }

    /** Fills a partially painted Pyraminx to a solved Pyraminx, or null. */
    fun completePyraminx(board: IntArray): IntArray? {
        if (board.size != 36) return null
        val faceColour = IntArray(4) { -1 }
        for (f in 0 until 4) {
            for (k in 0 until 9) {
                val c = board[f * 9 + k]
                if (c < 0) continue
                if (faceColour[f] < 0) faceColour[f] = c
                else if (faceColour[f] != c) return null
            }
        }
        val used = faceColour.filter { it >= 0 }.toSet()
        val remaining = (0..3).filter { !used.contains(it) }.toMutableList()
        for (f in 0 until 4) {
            if (faceColour[f] < 0) {
                if (remaining.isEmpty()) return null
                faceColour[f] = remaining.removeAt(0)
            }
        }
        if (faceColour.toSet().size != 4) return null
        return IntArray(36) { faceColour[it / 9] }
    }
}
