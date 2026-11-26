package cube.lab.nativeapp

// Human layer-by-layer Pyraminx solver.
//
// Centres      main-vertex turns until the four axial pieces are consistent
// First layer  BFS over the three edges of one face with the real commutators
//              X Y X' Y' (the R B R' B' / L' B' L B family, conjugated to every
//              pair of axes).  Main-vertex AUF turns are deliberately absent:
//              a bare vertex turn twists the centre on that axis and nothing in
//              the rest of the solve can undo it, so the turns have to balance
//              and re-positioning is done by picking the right trigger instead.
// Last layer   the five standard algorithms, relabelled for the chosen face
// Tips         last, one turn each, because every main turn scrambles them
//
// Boards hold face indices (face f == index f), so a solved cell satisfies
// board[cell] == cell / 9.

object HumanPyra {
    private const val FACE_COUNT = 4
    private val VERTEX_NAMES = listOf("U", "R", "L", "B")
    private val OPPOSITE = intArrayOf(3, 2, 1, 0) // face -> the vertex it is opposite

    private val LL_ALGS = listOf(
        "Sune" to listOf("R", "U", "R'", "U", "R", "U", "R'"),
        "Anti-Sune" to listOf("R", "U'", "R'", "U'", "R", "U'", "R'"),
        "2-flip" to listOf("L", "R'", "L'", "R", "U'", "R", "U", "R'"),
        "R' unsexy R" to listOf("R'", "U'", "L'", "U", "L", "R"),
        "L unsexy L'" to listOf("L", "U", "R", "U'", "R'", "L'"),
    )

    private val solved = Pyraminx.solvedColors()
    private val edgeCells = Array(6) { Pyraminx.edges[it].cells }
    private val edgeFaces = Array(6) { Pyraminx.edges[it].faceColors }
    private val faceEdges = Array(FACE_COUNT) { f -> (0 until 6).filter { f in edgeFaces[it] }.toIntArray() }
    private val tipCells: Set<Int> = Pyraminx.tips.flatMap { it.cells.toList() }.toSet()
    private val centreCells: List<Int> = Pyraminx.centers.flatMap { it.cells.toList() }

    /** X Y X' Y' for every ordered pair of distinct axes, both signs. */
    private val TRIGGERS: List<List<String>> = buildList {
        for (a in VERTEX_NAMES) for (b in VERTEX_NAMES) {
            if (a == b) continue
            for (sa in listOf(1, -1)) for (sb in listOf(1, -1)) {
                val x = a + if (sa < 0) "'" else ""
                val y = b + if (sb < 0) "'" else ""
                add(listOf(x, y, invert(x), invert(y)))
            }
        }
    }

    private fun invert(key: String) = if (key.endsWith("'")) key.dropLast(1) else "$key'"
    private fun axis(key: String) = VERTEX_NAMES.indexOf(key[0].toString())

    /** (slot, orient) -> (slot, orient) for a move, packed as slot * 2 + orient. */
    private val edgeTrans: Map<String, IntArray> = Pyraminx.turnKeys.associateWith { key ->
        val perm = Pyraminx.moves[key]!!.perm
        IntArray(12) { v ->
            val moved = perm[edgeCells[v / 2][v % 2]]
            val slot = (0 until 6).first { edgeCells[it].contains(moved) }
            slot * 2 + edgeCells[slot].indexOf(moved)
        }
    }

    // ---------------------------------------------------------------- public

    fun solve(input: IntArray): List<HumanPhase>? {
        if (input.size != 36) return null
        val counts = HashMap<Int, Int>()
        for (c in input) counts[c] = (counts[c] ?: 0) + 1
        if (counts.size != FACE_COUNT || counts.values.any { it != 9 }) return null

        // A centre piece shows three of the four colours; the missing one is the
        // colour of the face opposite its vertex.  This also fixes each face colour.
        val faceColor = IntArray(FACE_COUNT)
        for (v in 0 until FACE_COUNT) {
            val seen = Pyraminx.centers[v].cells.map { input[it] }.toSet()
            if (seen.size != 3) return null
            val missing = counts.keys.firstOrNull { it !in seen } ?: return null
            faceColor[OPPOSITE[v]] = missing
        }
        if (faceColor.toSet().size != FACE_COUNT) return null

        val board = IntArray(36) { faceColor.indexOf(input[it]) }
        if (board.contentEquals(solved)) return emptyList()

        val centreMoves = solveCentres(board) ?: return null
        val afterCentres = apply(board, centreMoves)

        // "Closest" face first: most of its three edges already home, then shortest.
        val plans = (0 until FACE_COUNT).mapNotNull { face -> firstLayer(afterCentres, face)?.let { face to it } }
        val faces = plans.sortedByDescending { (face, plan) ->
            faceEdges[face].count { edgeSolved(afterCentres, it) } * 100 - plan.moves.size
        }
        for ((face, layer) in faces) {
            val afterLayer = apply(afterCentres, layer.moves)
            val ll = lastLayer(afterLayer, face) ?: continue
            val llMoves = ll.flatMap { it.moves }
            val afterLL = apply(afterLayer, llMoves)
            val tips = solveTips(afterLL) ?: continue
            if (!apply(afterLL, tips).contentEquals(solved)) continue
            return listOf(
                HumanPhase("Centres", "Axial centres".takeIf { centreMoves.isNotEmpty() }, centreMoves),
                HumanPhase("First layer", layer.label.takeIf { layer.moves.isNotEmpty() }, layer.moves),
                HumanPhase("Last layer", ll.joinToString(" + ") { it.alg ?: "" }.takeIf { llMoves.isNotEmpty() }, llMoves),
                HumanPhase("Tips", "Tips".takeIf { tips.isNotEmpty() }, tips),
            )
        }
        return null
    }

    // -------------------------------------------------------------- centres

    private fun axialSolved(board: IntArray): Boolean =
        centreCells.all { board[it] == it / 9 }

    private fun centreKey(board: IntArray): String {
        val sb = StringBuilder(12)
        for (cell in centreCells) sb.append(board[cell])
        return sb.toString()
    }

    private fun solveCentres(start: IntArray): List<String>? {
        if (axialSolved(start)) return emptyList()
        val queue = ArrayDeque<IntArray>()
        val paths = HashMap<String, List<String>>()
        queue.add(start)
        paths[centreKey(start)] = emptyList()
        while (queue.isNotEmpty()) {
            val board = queue.removeFirst()
            val path = paths[centreKey(board)]!!
            for (key in Pyraminx.mainKeys) {
                val next = Pyraminx.applyPerm(board, Pyraminx.moves[key]!!.perm)
                val k = centreKey(next)
                if (paths.containsKey(k)) continue
                val nextPath = path + key
                if (axialSolved(next)) return nextPath
                paths[k] = nextPath
                queue.add(next)
            }
        }
        return null
    }

    // ---------------------------------------------------------- first layer

    private class LayerPlan(val moves: List<String>, val label: String)

    private fun edgeSolved(board: IntArray, slot: Int): Boolean =
        edgeCells[slot].all { board[it] == it / 9 }

    private fun firstLayer(start: IntArray, face: Int): LayerPlan? {
        val targets = faceEdges[face]
        // where each piece sits: pieces are indexed by their home slot
        val at = IntArray(6)
        for (slot in 0 until 6) {
            val a = start[edgeCells[slot][0]]
            val b = start[edgeCells[slot][1]]
            val piece = (0 until 6).firstOrNull {
                (edgeFaces[it][0] == a && edgeFaces[it][1] == b) || (edgeFaces[it][0] == b && edgeFaces[it][1] == a)
            } ?: return null
            at[piece] = slot * 2 + if (edgeFaces[piece][0] == a) 0 else 1
        }
        val state = intArrayOf(at[targets[0]], at[targets[1]], at[targets[2]])
        fun goal(s: IntArray) = s[0] == targets[0] * 2 && s[1] == targets[1] * 2 && s[2] == targets[2] * 2

        if (goal(state)) return LayerPlan(emptyList(), "Already solved")

        val trans = TRIGGERS.map { trig -> trig.map { edgeTrans[it]!! } }
        val startKey = encodeLayer(state)
        val prev = HashMap<Int, Int>()
        val via = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        prev[startKey] = startKey
        queue.add(startKey)
        var endKey = -1
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            val s = decodeLayer(key)
            if (goal(s)) { endKey = key; break }
            for (ai in trans.indices) {
                var next = s
                for (t in trans[ai]) next = intArrayOf(t[next[0]], t[next[1]], t[next[2]])
                val nk = encodeLayer(next)
                if (prev.containsKey(nk)) continue
                prev[nk] = key
                via[nk] = ai
                queue.add(nk)
            }
        }
        if (endKey < 0) return null
        val used = ArrayList<Int>()
        var key = endKey
        while (key != startKey) {
            used.add(via[key]!!)
            key = prev[key]!!
        }
        used.reverse()
        return LayerPlan(used.flatMap { TRIGGERS[it] }, "R B R' B' insert")
    }

    private fun encodeLayer(s: IntArray): Int = (s[0] * 13 + s[1]) * 13 + s[2]

    private fun decodeLayer(v: Int): IntArray {
        val s = IntArray(3)
        var x = v
        for (i in 2 downTo 0) { s[i] = x % 13; x /= 13 }
        return s
    }

    // ------------------------------------------------------------ last layer

    private class LlStep(val alg: String?, val moves: List<String>)

    /**
     * Candidates for `face`: the five algorithms with the vertices relabelled so
     * that face 3 (the layer the algorithms are written for) becomes `face`.
     * Relabellings that are mirror images move the finished layer, so the ones
     * that leave the centres and the layer's edges untouched are filtered out.
     */
    private val safeAlgs: Array<List<Pair<String, List<String>>>> = Array(FACE_COUNT) { face ->
        val opp = OPPOSITE[face]
        val verts = (0 until FACE_COUNT).filter { it != opp }
        val layer = faceEdges[face].flatMap { edgeCells[it].toList() } + centreCells
        buildList {
            for (a in verts) for (b in verts) for (c in verts) {
                if (a == b || a == c || b == c) continue
                val map = IntArray(4)
                map[0] = opp
                listOf(1, 2, 3).forEachIndexed { i, v -> map[v] = listOf(a, b, c)[i] }
                for ((name, alg) in LL_ALGS) {
                    val moves = alg.map { k -> VERTEX_NAMES[map[axis(k)]] + if (k.length > 1) k.substring(1) else "" }
                    val after = apply(solved, moves)
                    if (layer.all { after[it] == solved[it] }) add(name to moves)
                }
            }
        }
    }

    private fun solvedWithoutTips(board: IntArray): Boolean {
        for (cell in 0 until 36) if (cell !in tipCells && board[cell] != cell / 9) return false
        return true
    }

    private fun lastLayer(start: IntArray, face: Int): List<LlStep>? {
        if (solvedWithoutTips(start)) return emptyList()
        val queue = ArrayDeque<IntArray>()
        val seen = HashMap<String, List<LlStep>>()
        fun key(b: IntArray) = String(CharArray(36) { ('a' + b[it]) })
        queue.add(start)
        seen[key(start)] = emptyList()
        while (queue.isNotEmpty()) {
            val board = queue.removeFirst()
            val path = seen[key(board)]!!
            if (path.size >= 4) return null
            for ((name, moves) in safeAlgs[face]) {
                val next = apply(board, moves)
                val k = key(next)
                if (seen.containsKey(k)) continue
                val nextPath = path + LlStep(name, moves)
                if (solvedWithoutTips(next)) return nextPath
                seen[k] = nextPath
                queue.add(next)
            }
        }
        return null
    }

    // ----------------------------------------------------------------- tips

    private fun solveTips(start: IntArray): List<String>? {
        var board = start
        val out = ArrayList<String>()
        for (tip in Pyraminx.tips) {
            if (tip.cells.all { board[it] == it / 9 }) continue
            val keys = listOf(VERTEX_NAMES[tip.vertex].lowercase(), VERTEX_NAMES[tip.vertex].lowercase() + "'")
            val key = keys.firstOrNull { k ->
                Pyraminx.applyPerm(board, Pyraminx.moves[k]!!.perm).let { next -> tip.cells.all { next[it] == it / 9 } }
            } ?: return null
            out.add(key)
            board = Pyraminx.applyPerm(board, Pyraminx.moves[key]!!.perm)
        }
        return out
    }

    private fun apply(board: IntArray, keys: List<String>): IntArray {
        var out = board
        for (k in keys) out = Pyraminx.applyPerm(out, Pyraminx.moves[k]!!.perm)
        return out
    }
}
