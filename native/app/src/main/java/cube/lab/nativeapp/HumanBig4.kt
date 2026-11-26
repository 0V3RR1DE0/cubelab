package cube.lab.nativeapp

// Human 4x4 reduction solver.
//
// Order: centres (bar building and insert/restore), edge pairing
// (slice-flip-slice), then the reduced cube with Human3, plus the real OLL/PLL
// parity algorithms when the reduced state needs them.
//
// A parity case makes the reduced cube an illegal 3x3 position, so the fix is
// applied inside the 3x3 stage, exactly where a human meets it at OLL/PLL.
// The reported list is then Centres, Edges, 3x3 (orientation), Parity,
// 3x3 (Human3), so the required phase order still reads through.

object HumanBig4 {
    private const val N = 4
    private const val NN = 16
    private const val TOTAL = 96

    private val cube = Cube(N)
    private val model = BigModel(N)
    private val HOME = HOME_COLORS

    private val U = 0
    private val D = 1
    private val F = 2
    private val B = 3
    private val R = 4
    private val L = 5

    // ------------------------------------------------------------------ API

    /**
     * [board] is 96 colour stickers in the HOME_COLORS scheme.
     * Returns the human phases, or null when the position cannot be solved.
     */
    fun solve(cube: Cube, board: IntArray): List<HumanPhase>? {
        if (cube.n != N || board.size != TOTAL) return null
        // A HOME-scheme board is used as-is; only an unusual relabelled scheme
        // needs the corner-based remap.
        val mapped = if (BigCube.decodeLabels(model, board) != null) {
            board
        } else {
            BigCube.inferHomeBoard(cube, board) ?: return null
        }
        return try {
            solveMapped(cube, mapped)
        } catch (t: Throwable) {
            null
        }
    }

    private fun solveMapped(input: Cube, start: IntArray): List<HumanPhase>? {
        if (input.isSolved(start)) return emptyList()
        var board = start
        val phases = ArrayList<HumanPhase>()

        // ---- centres -----------------------------------------------------
        val centre = bestCentres(board) ?: return null
        board = centre.first
        phases.add(HumanPhase("Centres", "Bars and insert/restore", centre.second))

        // ---- edges -------------------------------------------------------
        val edges = pairAll(board) ?: return null
        board = edges.first
        phases.add(HumanPhase("Edges", "Slice-flip-slice pairing", edges.second))

        // ---- parity ------------------------------------------------------
        val parity = parityMoves(board) ?: return null
        val parityName = parity.second
        val parityList = parity.first

        // ---- 3x3 ---------------------------------------------------------
        var best: Triple<List<String>, List<String>, Int>? = null
        for (u in U_AUF) for (d in D_AUF) {
            val pre = u + d
            val cand = eval3x3(board, pre, parityList) ?: continue
            val current = best
            if (current == null || cand.third < current.third) best = cand
        }
        // A short hill-climb over outer pre-moves removes Human3's worst cases:
        // an extra turn is cheap when it saves a long F2L/last-layer route.
        var cur = best ?: return null
        for (round in 0 until 3) {
            var improved = false
            for (m in OUTER) {
                val cand = eval3x3(board, cur.first + m, parityList) ?: continue
                if (cand.third < cur.third) { cur = cand; improved = true }
            }
            if (!improved) break
        }

        val pre = simplifyMoves(cube, cur.first)
        if (parityList.isEmpty()) {
            val all = simplifyMoves(cube, pre + cur.second)
            phases.add(HumanPhase("3x3", "Human3 layer-by-layer", all))
            board = cube.apply(board, pre)
            board = cube.apply(board, cur.second)
        } else {
            // The orientation/AUF belongs to the 3x3 stage; the parity fix sits
            // between it and the reduced solve, because it makes the reduced
            // cube a legal 3x3 position.
            phases.add(HumanPhase("3x3", "Reduced-cube orientation", pre))
            phases.add(HumanPhase("Parity", parityName, parityList))
            phases.add(HumanPhase("3x3", "Human3 layer-by-layer", cur.second))
            board = cube.apply(board, pre)
            board = cube.apply(board, parityList)
            board = cube.apply(board, cur.second)
        }

        return if (input.isSolved(board)) phases else null
    }

    // ------------------------------------------------------------ centres

    private val CENTRES: IntArray = run {
        val out = ArrayList<Int>()
        for (f in 0 until 6) for (r in 1..2) for (c in 1..2) out.add(f * NN + r * N + c)
        out.toIntArray()
    }

    private fun facePositions(f: Int): Set<Int> =
        (1..2).flatMap { r -> (1..2).map { c -> f * NN + r * N + c } }.toSet()

    private fun sidePositions(faces: IntArray): IntArray =
        CENTRES.filter { it / NN in faces.toList() }.toIntArray()

    private fun destPerm(seq: List<String>): IntArray {
        var d = IntArray(TOTAL) { it }
        for (m in seq) {
            val p = model.move(m).perm
            val next = IntArray(TOTAL)
            for (i in 0 until TOTAL) next[i] = p[d[i]]
            d = next
        }
        return d
    }

    private class CentreMoves(val seqs: List<List<String>>, val allowed: IntArray) {
        val perms: List<IntArray> = seqs.map { seq ->
            val d = destPerm(seq)
            val idx = HashMap<Int, Int>()
            allowed.forEachIndexed { i, x -> idx[x] = i }
            IntArray(allowed.size) { i -> idx[d[allowed[i]]] ?: error("centre move leaves region") }
        }
    }

    private fun subsetBfs(start: IntArray, goal: Set<Int>, moves: CentreMoves): List<String>? {
        val idx = HashMap<Int, Int>()
        moves.allowed.forEachIndexed { i, x -> idx[x] = i }
        var startMask = 0
        for (x in start) startMask = startMask or (1 shl idx[x]!!)
        var goalMask = 0
        for (x in goal) goalMask = goalMask or (1 shl idx[x]!!)

        val prev = HashMap<Int, Int>()
        val via = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        prev[startMask] = -1
        queue.add(startMask)
        while (queue.isNotEmpty()) {
            val mask = queue.removeFirst()
            if (mask and goalMask.inv() == 0) {
                val out = ArrayList<Int>()
                var x = mask
                while (prev[x] != -1) {
                    out.add(via[x]!!)
                    x = prev[x]!!
                }
                out.reverse()
                return out.flatMap { moves.seqs[it] }
            }
            moves.perms.forEachIndexed { mi, perm ->
                var next = 0
                for (i in perm.indices) if ((mask shr i) and 1 == 1) next = next or (1 shl perm[i])
                if (!prev.containsKey(next)) {
                    prev[next] = mask
                    via[next] = mi
                    queue.add(next)
                }
            }
        }
        return null
    }

    private fun centreStage(
        board: IntArray,
        colour: Int,
        moves: CentreMoves,
    ): Pair<IntArray, List<String>>? {
        val stickers = CENTRES.filter { HOME.indexOf(board[it]) == colour }.toIntArray()
        if (stickers.any { it !in moves.allowed }) return null
        if (stickers.all { it / NN == colour }) return board to emptyList()
        val seq = subsetBfs(stickers, facePositions(colour), moves) ?: return null
        val out = cube.apply(board, seq)
        if (facePositions(colour).any { HOME.indexOf(out[it]) != colour }) return null
        return out to seq
    }

    private fun faceTurns(faces: IntArray): List<List<String>> = faces.flatMap { f ->
        listOf(listOf(FACE_KEYS[f]), listOf(FACE_KEYS[f] + "'"), listOf(FACE_KEYS[f] + "2"))
    }

    private fun sliceTurns(bases: List<String>): List<List<String>> = bases.flatMap { b ->
        listOf(listOf(b), listOf(b + "'"), listOf(b + "2"))
    }

    private fun dMacros(): List<List<String>> = listOf(
        listOf("2R", "D2", "2R'", "D2"),
        listOf("2L", "D2", "2L'", "D2"),
        listOf("2F", "D2", "2F'", "D2"),
        listOf("2B", "D2", "2B'", "D2"),
    ).flatMap { listOf(it, invSeq(it)) }

    private fun rMacros(): List<List<String>> = listOf(
        listOf("2U", "R2", "2U'", "R2"),
        listOf("2D", "B2", "2D'", "B2"),
        listOf("2U", "B2", "2U'", "B2"),
        listOf("2D", "L2", "2D'", "L2"),
    ).flatMap { listOf(it, invSeq(it)) }

    private fun bMacros(): List<List<String>> = listOf(
        listOf("2U", "B2", "2U'", "B2"),
        listOf("2D", "L2", "2D'", "L2"),
    ).flatMap { listOf(it, invSeq(it)) }

    private fun solveCentres(board: IntArray): Pair<IntArray, List<String>>? {
        var b = board
        val all = ArrayList<String>()

        val uMoves = CentreMoves(model.gens.map { listOf(it) }, CENTRES)
        var step = centreStage(b, U, uMoves) ?: return null
        b = step.first; all.addAll(step.second)

        val dAllowed = CENTRES.filter { it / NN != U }.toIntArray()
        val dMoves = CentreMoves(faceTurns(intArrayOf(D, F, B, R, L)) + dMacros(), dAllowed)
        step = centreStage(b, D, dMoves) ?: return null
        b = step.first; all.addAll(step.second)

        val sides = sidePositions(intArrayOf(F, B, R, L))
        val fMoves = CentreMoves(
            faceTurns(intArrayOf(F, B, R, L)) + sliceTurns(listOf("2U", "3U")),
            sides,
        )
        step = centreStage(b, F, fMoves) ?: return null
        b = step.first; all.addAll(step.second)

        val rAllowed = sidePositions(intArrayOf(B, R, L))
        val rMoves = CentreMoves(faceTurns(intArrayOf(B, R, L)) + rMacros(), rAllowed)
        step = centreStage(b, R, rMoves) ?: return null
        b = step.first; all.addAll(step.second)

        val bAllowed = sidePositions(intArrayOf(B, L))
        val bMoves = CentreMoves(faceTurns(intArrayOf(B, L)) + bMacros(), bAllowed)
        step = centreStage(b, B, bMoves) ?: return null
        b = step.first; all.addAll(step.second)

        for (f in 0 until 6) if (facePositions(f).any { HOME.indexOf(b[it]) != f }) return null
        return b to all
    }

    /** Tries whole-cube starts as well: the fixed centre order suits some frames. */
    private fun bestCentres(board: IntArray): Pair<IntArray, List<String>>? {
        var bestBoard = board
        var bestMoves: List<String>? = null
        var bestCost = Int.MAX_VALUE
        for (rotation in ROTATIONS) {
            val rotated = if (rotation.isEmpty()) board else cube.apply(board, rotation)
            val solved = solveCentres(rotated) ?: continue
            val cost = rotation.size + solved.second.size
            if (cost < bestCost) {
                bestCost = cost
                bestBoard = solved.first
                bestMoves = rotation + solved.second
            }
        }
        return bestMoves?.let { bestBoard to it }
    }

    // -------------------------------------------------------------- edges

    private val OUTER: List<String> = buildList {
        for (k in FACE_KEYS) for (s in arrayOf("", "'", "2")) add(k + s)
    }
    private val OUTER_PERMS: List<IntArray> by lazy { OUTER.map { cube.move(it).perm } }

    private fun stickerPositions(board: IntArray): IntArray? {
        val labels = BigCube.decodeLabels(model, board) ?: return null
        val pos = IntArray(TOTAL) { -1 }
        for (i in 0 until TOTAL) pos[labels[i]] = i
        return pos
    }

    private fun wingPieces(a: Int, b: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (p in model.pieces) {
            if (p.kind != 1) continue
            val fs = p.stickers.map { HOME[it / NN] }.sorted()
            if (fs == listOf(a, b)) out.add(intArrayOf(p.stickers[0], p.stickers[1]))
        }
        return out
    }

    private fun slotKey(pos: IntArray, piece: IntArray): String {
        val p1 = pos[piece[0]]
        val p2 = pos[piece[1]]
        return "${minOf(p1 / NN, p2 / NN)},${maxOf(p1 / NN, p2 / NN)}"
    }

    private fun pairedCount(board: IntArray): Int {
        val pos = stickerPositions(board) ?: return -1
        val groups = HashMap<String, MutableList<List<Int>>>()
        for (p in model.pieces) {
            if (p.kind != 1) continue
            val key = slotKey(pos, intArrayOf(p.stickers[0], p.stickers[1]))
            groups.getOrPut(key) { ArrayList() }.add(p.stickers.map { HOME[it / NN] }.sorted())
        }
        var n = 0
        for (v in groups.values) if (v.size == 2 && v[0] == v[1]) n++
        return n
    }

    private fun centresSolved(board: IntArray): Boolean {
        for (f in 0 until 6) {
            val c = board[f * NN + 5]
            for (r in 1..2) for (cc in 1..2) if (board[f * NN + r * N + cc] != c) return false
        }
        return true
    }

    private fun atSlot(a: Int, b: Int, slot: IntArray): Boolean =
        (a == slot[0] && b == slot[1]) || (a == slot[1] && b == slot[0])

    private val FL_U = intArrayOf(F * NN + 1 * N + 0, L * NN + 1 * N + 3)
    private val FL_D = intArrayOf(F * NN + 2 * N + 0, L * NN + 2 * N + 3)
    private val FR_U = intArrayOf(F * NN + 1 * N + 3, R * NN + 1 * N + 0)
    private val FR_D = intArrayOf(F * NN + 2 * N + 3, R * NN + 2 * N + 0)

    private fun findSetup(
        board: IntArray,
        pa: IntArray,
        pb: IntArray,
        allowSameLayer: Boolean = false,
    ): List<String>? {
        val pos = stickerPositions(board) ?: return null
        val start = intArrayOf(pos[pa[0]], pos[pa[1]], pos[pb[0]], pos[pb[1]])
        val prev = HashMap<String, Pair<String, Int>>()
        val queue = ArrayDeque<IntArray>()
        fun key(s: IntArray) = s.joinToString(",")
        prev[key(start)] = "" to -1
        queue.add(start.copyOf())
        var goal: IntArray? = null
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            val fu = atSlot(s[0], s[1], FL_U) || atSlot(s[2], s[3], FL_U)
            val fd = atSlot(s[0], s[1], FL_D) || atSlot(s[2], s[3], FL_D)
            val ru = atSlot(s[0], s[1], FR_U) || atSlot(s[2], s[3], FR_U)
            val rd = atSlot(s[0], s[1], FR_D) || atSlot(s[2], s[3], FR_D)
            val same = allowSameLayer && ((fu && ru) || (fd && rd))
            if ((fu && rd) || (fd && ru) || same) {
                goal = s.copyOf()
                break
            }
            for (mi in OUTER.indices) {
                val p = OUTER_PERMS[mi]
                val ns = intArrayOf(p[s[0]], p[s[1]], p[s[2]], p[s[3]])
                val k = key(ns)
                if (!prev.containsKey(k)) {
                    prev[k] = key(s) to mi
                    queue.add(ns)
                }
            }
        }
        val g = goal ?: return null
        val out = ArrayList<Int>()
        var k = key(g)
        while (true) {
            val (pk, mi) = prev[k]!!
            if (mi < 0) break
            out.add(mi)
            k = pk
        }
        out.reverse()
        return out.map { OUTER[it] }
    }

    private val TRIGGERS: List<List<String>> = listOf(
        listOf("Uw'", "R", "U'", "R'", "Uw"),
        listOf("Uw'", "R", "U", "R'", "Uw"),
        listOf("Uw", "L'", "U", "L", "Uw'"),
        listOf("Uw", "L'", "U'", "L", "Uw'"),
        listOf("Uw'", "R", "U", "R'", "F", "R'", "F'", "R", "Uw"),
        listOf("Uw'", "R", "U'", "R'", "F", "R'", "F'", "R", "Uw"),
        listOf("Uw", "L'", "U", "L", "F'", "L", "F", "L'", "Uw'"),
        listOf("Uw", "L'", "U'", "L", "F'", "L", "F", "L'", "Uw'"),
    )

    private fun unpairedTypes(board: IntArray): List<Pair<Int, Int>> {
        val pos = stickerPositions(board) ?: return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        for (a in 0 until 6) for (b in a + 1 until 6) {
            val ps = wingPieces(a, b)
            if (ps.size != 2) continue
            if (slotKey(pos, ps[0]) != slotKey(pos, ps[1])) out.add(a to b)
        }
        return out
    }

    private fun searchTrigger(
        board: IntArray,
        pieces: List<IntArray>,
        maxStates: Int,
    ): List<String>? {
        val pos = stickerPositions(board) ?: return null
        val start = IntArray(pieces.size * 2)
        for (i in pieces.indices) {
            start[2 * i] = pos[pieces[i][0]]
            start[2 * i + 1] = pos[pieces[i][1]]
        }
        val before = pairedCount(board)
        val prev = HashMap<String, Pair<String, Int>>()
        val queue = ArrayDeque<IntArray>()
        fun key(s: IntArray) = s.joinToString(",")
        prev[key(start)] = "" to -1
        queue.add(start.copyOf())
        var count = 0
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            if (++count > maxStates) break
            val out = ArrayList<Int>()
            var k = key(s)
            while (true) {
                val (pk, mi) = prev[k]!!
                if (mi < 0) break
                out.add(mi)
                k = pk
            }
            out.reverse()
            val setup = out.map { OUTER[it] }
            for (t in TRIGGERS) {
                val seq = setup + t
                val nb = cube.apply(board, seq)
                if (centresSolved(nb) && pairedCount(nb) > before) return seq
            }
            for (mi in OUTER.indices) {
                val p = OUTER_PERMS[mi]
                val ns = IntArray(s.size) { p[s[it]] }
                val kk = key(ns)
                if (!prev.containsKey(kk)) {
                    prev[kk] = key(s) to mi
                    queue.add(ns)
                }
            }
        }
        return null
    }

    private fun pairAll(board0: IntArray): Pair<IntArray, List<String>>? {
        var board = board0
        val moves = ArrayList<String>()
        var guard = 0
        while (pairedCount(board) < 12) {
            if (++guard > 60) return null
            val current = pairedCount(board)
            val types = ArrayList<Pair<Int, Int>>()
            for (a in 0 until 6) for (b in a + 1 until 6) {
                if (wingPieces(a, b).size == 2) types.add(a to b)
            }
            var best: List<String>? = null
            var bestScore = Int.MIN_VALUE
            for ((a, b) in types) {
                val ps = wingPieces(a, b)
                val setup = findSetup(board, ps[0], ps[1]) ?: continue
                for (t in TRIGGERS) {
                    val seq = setup + t
                    val out = cube.apply(board, seq)
                    if (!centresSolved(out)) continue
                    val gain = pairedCount(out) - current
                    if (gain <= 0) continue
                    val score = gain * 100 - seq.size
                    if (score > bestScore) {
                        bestScore = score
                        best = seq
                    }
                }
            }
            if (best == null) {
                val ups = unpairedTypes(board)
                var fallback: List<String>? = null
                if (ups.isNotEmpty()) {
                    val pieces = ArrayList<IntArray>()
                    for ((a, b) in ups.take(2)) pieces.addAll(wingPieces(a, b))
                    fallback = searchTrigger(board, pieces, if (pieces.size <= 4) 30000 else 8000)
                }
                if (fallback == null) {
                    for ((a, b) in ups) {
                        val ps = wingPieces(a, b)
                        val setup = findSetup(board, ps[0], ps[1], allowSameLayer = true) ?: continue
                        for (t in TRIGGERS) {
                            val seq = setup + t
                            val out = cube.apply(board, seq)
                            if (centresSolved(out) && pairedCount(out) > current) {
                                fallback = seq
                                break
                            }
                        }
                        if (fallback != null) break
                    }
                }
                best = fallback ?: return null
            }
            board = cube.apply(board, best)
            moves.addAll(best)
        }
        if (pairedCount(board) != 12 || !centresSolved(board)) return null
        return board to moves
    }

    // ------------------------------------------------------------- parity

    private val OLL_FREE = listOf(
        "2R2", "B2", "U2", "2L", "U2", "2R'", "U2", "2R",
        "U2", "F2", "2R", "F2", "2L'", "B2", "2R2",
    )
    private val PLL_INNER = listOf("2R2", "U2", "2R2", "Uw2", "2R2", "Uw2")

    private fun reducedBoard(board: IntArray): IntArray {
        val b = IntArray(54)
        for (f in 0 until 6) for (r3 in 0..2) for (c3 in 0..2) {
            val r = when (r3) { 0 -> 0; 2 -> 3; else -> 1 }
            val c = when (c3) { 0 -> 0; 2 -> 3; else -> 1 }
            b[f * 9 + r3 * 3 + c3] = HOME.indexOf(board[f * NN + r * N + c])
        }
        return b
    }

    private fun decodeReduced(board: IntArray): CubieState? =
        decodeCubies(reducedBoard(board), Cube(3), intArrayOf(0, 1, 2, 3, 4, 5))

    private fun validReduced(state: CubieState?): Boolean =
        state != null && state.eo.sum() % 2 == 0 && permParity(state.cp) == permParity(state.ep)

    private fun parityMoves(board: IntArray): Pair<List<String>, String>? {
        var state = decodeReduced(board) ?: return null
        val moves = ArrayList<String>()
        val names = ArrayList<String>()
        if (state.eo.sum() % 2 != 0) {
            moves.addAll(OLL_FREE)
            names.add("OLL parity")
            state = decodeReduced(cube.apply(board, moves)) ?: return null
        }
        if (permParity(state.cp) != permParity(state.ep)) {
            moves.addAll(PLL_INNER)
            names.add("PLL parity")
            state = decodeReduced(cube.apply(board, moves)) ?: return null
        }
        if (!validReduced(state)) return null
        return moves to names.joinToString(" + ")
    }

    // --------------------------------------------------------------- 3x3

    private fun expand(m: String): List<String> = when (m) {
        "Rw" -> listOf("x", "L"); "Rw'" -> listOf("L'", "x'"); "Rw2" -> listOf("x2", "L2")
        "Fw" -> listOf("z", "B"); "Fw'" -> listOf("B'", "z'"); "Fw2" -> listOf("z2", "B2")
        "Uw" -> listOf("y", "D"); "Uw'" -> listOf("D'", "y'"); "Uw2" -> listOf("y2", "D2")
        "Lw" -> listOf("x'", "R"); "Lw'" -> listOf("R'", "x"); "Lw2" -> listOf("x2", "R2")
        "Dw" -> listOf("y'", "U"); "Dw'" -> listOf("U'", "y"); "Dw2" -> listOf("y2", "U2")
        "Bw" -> listOf("z'", "F"); "Bw'" -> listOf("F'", "z"); "Bw2" -> listOf("z2", "F2")
        "2L" -> listOf("L'", "x'", "R"); "2L'" -> listOf("R'", "x", "L"); "2L2" -> listOf("x2", "R2", "L2")
        "2R" -> listOf("R'", "x", "L"); "2R'" -> listOf("L'", "x'", "R"); "2R2" -> listOf("x2", "R2", "L2")
        "2U" -> listOf("U'", "y", "D"); "2U'" -> listOf("D'", "y'", "U"); "2U2" -> listOf("y2", "U2", "D2")
        "2D" -> listOf("D'", "y'", "U"); "2D'" -> listOf("U'", "y", "D"); "2D2" -> listOf("y2", "U2", "D2")
        "2F" -> listOf("F'", "z", "B"); "2F'" -> listOf("B'", "z'", "F"); "2F2" -> listOf("z2", "F2", "B2")
        "2B" -> listOf("B'", "z'", "F"); "2B'" -> listOf("F'", "z", "B"); "2B2" -> listOf("z2", "F2", "B2")
        else -> listOf(m)
    }

    private val U_AUF = listOf(emptyList<String>(), listOf("U"), listOf("U2"), listOf("U'"))
    private val D_AUF = listOf(emptyList<String>(), listOf("D"), listOf("D2"), listOf("D'"))

    private fun eval3x3(
        board: IntArray,
        pre: List<String>,
        parity: List<String>,
    ): Triple<List<String>, List<String>, Int>? {
        val b = cube.apply(cube.apply(board, pre), parity)
        val phases = try {
            Human3.solve(reducedBoard(b))
        } catch (t: Throwable) {
            null
        } ?: return null
        val translated = phases.flatMap { it.moves }.flatMap { expand(it) }
        val simplified = simplifyMoves(cube, translated)
        return Triple(pre, simplified, pre.size + parity.size + simplified.size)
    }

    // ---------------------------------------------------------- rotations

    private val ROTATIONS: List<List<String>> by lazy {
        val out = ArrayList<List<String>>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<Pair<IntArray, List<String>>>()
        fun add(b: IntArray, seq: List<String>) {
            if (seen.add(b.joinToString(","))) queue.add(b to seq)
        }
        add(cube.solved(), emptyList())
        while (queue.isNotEmpty()) {
            val (b, seq) = queue.removeFirst()
            out.add(seq)
            for (m in listOf("x", "x2", "x'", "y", "y2", "y'", "z", "z2", "z'")) {
                add(cube.apply(b, listOf(m)), seq + m)
            }
        }
        out
    }
}
