package cube.lab.nativeapp

// Human layer-by-layer 3x3 solver.
//
// Cross        optimal search over the four D edges (what a cuber plans in inspection)
// F1 corners   "sexy move" trigger, conjugated to each bottom slot
// F2 edges     right/left insert algorithms, conjugated to each middle slot
// Last layer   named OCLL / CP / EP algorithms, with AUF chosen by testing the
//              result against each phase's goal
//
// F1/F2 use a 24-state local BFS over those real triggers: it finds the shortest
// sequence of triggers that puts the target piece home. A trigger is aimed at its
// slot by remapping its face letters instead of writing `y^k alg y^-k`, so the
// solution contains no cube rotations and every check compares against the
// ordinary solved board.
//
// The solver works on a face-index board: every sticker holds the index of the
// face its colour belongs to, so a solved cube is 0..5 repeated nine times.

/** One labelled block of a human-method solution. */
class HumanPhase(val name: String, val alg: String?, val moves: List<String>)

private class Trace(var board: IntArray, private val cube: Cube) {
    val moves = ArrayList<String>()

    fun put(ms: List<String>) {
        if (ms.isEmpty()) return
        moves.addAll(ms)
        board = cube.apply(board, ms)
    }

    fun put(vararg ms: String) = put(ms.toList())
}

object Human3 {
    private const val U = 0
    private const val D = 1
    private const val F = 2
    private const val B = 3
    private const val R = 4
    private const val L = 5

    private val cube = Cube(3)
    private val map = buildCubieMap(cube)
    private val solved = IntArray(54) { it / 9 }

    private val cornerInfos = map.corners
    private val edgeInfos = map.edges
    private val cornerInfo = cornerInfos.associateBy { it.faces.toSet() }
    private val edgeInfo = edgeInfos.associateBy { it.faces.toSet() }
    private val cornerSlot = cornerInfos.withIndex().associate { it.value.faces.toSet() to it.index }
    private val edgeSlot = edgeInfos.withIndex().associate { it.value.faces.toSet() to it.index }

    private val U_CORNERS = listOf(
        setOf(U, F, R), setOf(U, F, L), setOf(U, B, R), setOf(U, B, L),
    )
    private val D_CORNERS = listOf(
        setOf(D, F, R), setOf(D, F, L), setOf(D, B, R), setOf(D, B, L),
    )
    private val D_EDGES = listOf(
        setOf(D, F), setOf(D, R), setOf(D, B), setOf(D, L),
    )
    private val MID_EDGES = listOf(
        setOf(F, R), setOf(F, L), setOf(B, R), setOf(B, L),
    )

    private val SEXY = listOf("R", "U", "R'", "U'")
    private val RIGHT_INSERT = listOf("U", "R", "U'", "R'", "U'", "F'", "U", "F")

    private val LINE = listOf("F", "R", "U", "R'", "U'", "F'")
    private val L_SHAPE = listOf("Fw", "R", "U", "R'", "U'", "Fw'")

    private val OCLL = listOf(
        "Sune" to listOf("R", "U", "R'", "U", "R", "U2", "R'"),
        "Anti-Sune" to listOf("R", "U2", "R'", "U'", "R", "U'", "R'"),
        "H" to listOf("R", "U", "R'", "U", "R", "U'", "R'", "U", "R", "U2", "R'"),
        "Pi" to listOf("R", "U2", "R2", "U'", "R2", "U'", "R2", "U2", "R"),
        "U" to listOf("R2", "D", "R'", "U2", "R", "D'", "R'", "U2", "R'"),
        "T" to listOf("Rw", "U", "R'", "U'", "Rw'", "F", "R", "F'"),
        "L" to listOf("F'", "Rw", "U", "R'", "U'", "Rw'", "F", "R"),
    )

    private val CP_ALGS = listOf(
        "T-perm" to listOf("R", "U", "R'", "U'", "R'", "F", "R2", "U'", "R'", "U'", "R", "U", "R'", "F'"),
        "Y-perm" to listOf(
            "F", "R", "U'", "R'", "U'", "R", "U", "R'", "F'", "R", "U", "R'", "U'", "R'", "F", "R", "F'",
        ),
        "Aa-perm" to listOf("R'", "F", "R'", "B2", "R", "F'", "R'", "B2", "R2"),
        "Ab-perm" to listOf("R2", "B2", "R", "F", "R'", "B2", "R", "F'", "R"),
    )

    private val EP_ALGS = listOf(
        "Ua-perm" to listOf("R", "U'", "R", "U", "R", "U", "R", "U'", "R'", "U'", "R2"),
        "Ub-perm" to listOf("R2", "U", "R", "U", "R'", "U'", "R'", "U'", "R'", "U", "R'"),
        "H-perm" to listOf("2L2", "U", "2L2", "U2", "2L2", "U", "2L2"),
        "Z-perm" to listOf("2L2", "U", "2L2", "U", "2L'", "U2", "2L2", "U2", "2L'"),
    )

    private fun rotSeq(k: Int): List<String> = when (((k % 4) + 4) % 4) {
        1 -> listOf("y")
        2 -> listOf("y2")
        3 -> listOf("y'")
        else -> emptyList()
    }

    /** Move sequence for the trigger when it is held at `slot` instead of front-right. */
    private fun conjugate(alg: List<String>, slot: Set<Int>, corner: Boolean): List<String> {
        val k = rotationFor(solved, slot, corner)
        // `y^k alg y^-k` is the same sequence with every face letter remapped, so
        // the solution needs no rotation moves.
        return remap(alg, k)
    }

    /**
     * `alg` rewritten for a cube turned by `y^k`: the face a move acts on changes,
     * the turn direction does not (a rotation preserves orientation).
     */
    internal fun remap(alg: List<String>, k: Int): List<String> {
        val turned = if (k == 0) solved else cube.apply(solved, rotSeq(k))
        // Each face of the turned board shows the colour of the face that now sits
        // there, which is exactly the old-letter -> new-letter map.
        val faces = FACE_KEYS.withIndex().associate { (f, key) ->
            key[0] to FACE_KEYS[turned[f * 9 + 4]][0]
        }
        return alg.map { move -> faces.getValue(move[0]) + move.substring(1) }
    }

    private val F1_ACTIONS: List<List<String>> by lazy {
        val out = ArrayList<List<String>>()
        out.add(listOf("U")); out.add(listOf("U'")); out.add(listOf("U2"))
        for (slot in D_CORNERS) out.add(conjugate(SEXY, slot, corner = true))
        out
    }

    private val F2_ACTIONS: List<List<String>> by lazy {
        val out = ArrayList<List<String>>()
        out.add(listOf("U")); out.add(listOf("U'")); out.add(listOf("U2"))
        // The right insert is anchored at its slot; the left insert targets the
        // slot on the other side, so it must not be conjugated as if it were FR.
        for (slot in MID_EDGES) out.add(conjugate(RIGHT_INSERT, slot, corner = false))
        out
    }

    private val F1_TRANS: Array<IntArray> by lazy { buildTransitions(cornerInfos, 3, F1_ACTIONS) }
    private val F2_TRANS: Array<IntArray> by lazy { buildTransitions(edgeInfos, 2, F2_ACTIONS) }

    private fun buildTransitions(
        infos: List<CubieInfo>,
        perSlot: Int,
        actions: List<List<String>>,
    ): Array<IntArray> {
        val perms = actions.map { seq -> seq.map { cube.move(it).perm } }
        val slotOf = HashMap<Int, Int>()
        infos.forEachIndexed { s, info -> info.facelets.forEach { slotOf[it] = s } }
        return Array(infos.size * perSlot) { value ->
            val slot = value / perSlot
            val orient = value % perSlot
            val info = infos[slot]
            IntArray(actions.size) { ai ->
                var ref = info.facelets[orient]
                for (perm in perms[ai]) ref = perm[ref]
                val ns = slotOf[ref] ?: error("transition lost")
                ns * perSlot + infos[ns].facelets.indexOf(ref)
            }
        }
    }

    private fun localBfs(
        start: Int,
        goal: Int,
        trans: Array<IntArray>,
        actionCount: Int,
        allowed: List<Int> = (0 until actionCount).toList(),
    ): List<Int>? {
        if (start == goal) return emptyList()
        val prev = IntArray(trans.size) { -1 }
        val via = IntArray(trans.size) { -1 }
        val queue = ArrayDeque<Int>()
        prev[start] = start
        queue.add(start)
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            if (s == goal) break
            for (a in allowed) {
                val ns = trans[s][a]
                if (prev[ns] != -1) continue
                prev[ns] = s
                via[ns] = a
                queue.add(ns)
            }
        }
        if (prev[goal] == -1) return null
        val out = ArrayList<Int>()
        var s = goal
        while (s != start) {
            out.add(via[s])
            s = prev[s]
        }
        out.reverse()
        return out
    }

    /** `board` must hold face indices (0..5), not user colours. */
    fun solve(input: IntArray): List<HumanPhase>? {
        if (input.size != 54) return null
        if (input.contentEquals(solved)) return emptyList()
        var board = input.copyOf()
        val phases = ArrayList<HumanPhase>()

        val cross = solveCross(board)
        if (cross == null) return null
        board = cube.apply(board, cross)
        if (cross.isNotEmpty()) phases.add(HumanPhase("Cross", null, cross))

        for (slot in D_CORNERS) {
            val trace = Trace(board, cube)
            solveCorner(trace, slot)
            board = trace.board
            if (!cornerSolved(board, slot)) return null
            val moves = simplifyMoves(cube, trace.moves)
            if (moves.isNotEmpty()) {
                phases.add(HumanPhase("First-layer corners", "Sexy move (R U R' U')", moves))
            }
        }

        for (slot in MID_EDGES) {
            val trace = Trace(board, cube)
            solveEdge(trace, slot)
            board = trace.board
            if (!edgeSolved(board, slot)) return null
            val moves = simplifyMoves(cube, trace.moves)
            if (moves.isNotEmpty()) {
                phases.add(HumanPhase("Second-layer edges", "Right/left insert", moves))
            }
        }

        board = solveLastLayer(board, phases)
        return if (board.contentEquals(solved)) phases else null
    }

    // ------------------------------------------------------------ cross

    private val crossHome = IntArray(4) { edgeSlot[D_EDGES[it]]!! }

    private val crossTrans: Array<IntArray> by lazy {
        val slotOfFacelet = HashMap<Int, Int>()
        edgeInfos.forEachIndexed { s, info -> info.facelets.forEach { slotOfFacelet[it] = s } }
        Array(24) { value ->
            val slot = value / 2
            val orient = value % 2
            val info = edgeInfos[slot]
            IntArray(Kociemba.MOVE_NAMES.size) { m ->
                val perm = cube.move(Kociemba.MOVE_NAMES[m]).perm
                val jd = perm[info.facelets[orient]]
                val ns = slotOfFacelet[jd] ?: error("cross slice lost")
                ns * 2 + edgeInfos[ns].facelets.indexOf(jd)
            }
        }
    }

    private fun solveCross(board: IntArray): List<String>? {
        val start = IntArray(4)
        for (i in 0 until 4) {
            val piece = D_EDGES[i]
            var found = false
            for ((s, info) in edgeInfos.withIndex()) {
                if (setAt(board, info) != piece) continue
                val orient = info.facelets.indexOfFirst { board[it] == D }
                if (orient < 0) return null
                start[i] = s * 2 + orient
                found = true
                break
            }
            if (!found) return null
        }
        val goal = IntArray(4) { i ->
            val home = crossHome[i]
            home * 2 + edgeInfos[home].faces.indexOf(D)
        }
        val startKey = encodeCross(start)
        val goalKey = encodeCross(goal)
        if (startKey == goalKey) return emptyList()

        val total = 24 * 24 * 24 * 24
        val parent = IntArray(total) { -1 }
        val used = ByteArray(total) { -1 }
        val queue = IntArray(total)
        var head = 0
        var tail = 0
        parent[startKey] = startKey
        queue[tail++] = startKey
        var found = false
        while (head < tail) {
            val key = queue[head++]
            if (key == goalKey) { found = true; break }
            val v = decodeCross(key)
            for (m in 0 until 18) {
                val a = crossTrans[v[0]][m]
                val b = crossTrans[v[1]][m]
                val c = crossTrans[v[2]][m]
                val d = crossTrans[v[3]][m]
                val nk = ((a * 24 + b) * 24 + c) * 24 + d
                if (parent[nk] != -1) continue
                parent[nk] = key
                used[nk] = m.toByte()
                queue[tail++] = nk
            }
        }
        if (!found) return null
        val out = ArrayList<String>()
        var key = goalKey
        while (key != startKey) {
            val m = used[key].toInt()
            if (m < 0) return null
            out.add(Kociemba.MOVE_NAMES[m])
            key = parent[key]
        }
        out.reverse()
        return out
    }

    private fun encodeCross(v: IntArray): Int =
        ((v[0] * 24 + v[1]) * 24 + v[2]) * 24 + v[3]

    private fun decodeCross(key: Int): IntArray {
        val v = IntArray(4)
        var x = key
        for (i in 3 downTo 0) {
            v[i] = x % 24
            x /= 24
        }
        return v
    }

    // ------------------------------------------------------------ F1 corners

    private fun solveCorner(trace: Trace, slot: Set<Int>) {
        if (cornerSolved(trace.board, slot)) return
        val info = cornerInfo[slot]!!
        val home = cornerSlot[slot]!!
        val startSlot = cornerIndexWith(trace.board, slot)
        if (startSlot < 0) return
        val startOrient = cornerInfos[startSlot].facelets.indexOfFirst { trace.board[it] == D }
        if (startOrient < 0) return
        val start = startSlot * 3 + startOrient
        val goal = home * 3 + info.faces.indexOf(D)
        val allowed = ArrayList<Int>()
        allowed.add(0); allowed.add(1); allowed.add(2)
        for (i in D_CORNERS.indices) {
            if (!cornerSolved(trace.board, D_CORNERS[i])) allowed.add(3 + i)
        }
        val actions = localBfs(start, goal, F1_TRANS, F1_ACTIONS.size, allowed) ?: return
        for (a in actions) trace.put(F1_ACTIONS[a])
    }

    // ------------------------------------------------------------ F2 edges

    private fun solveEdge(trace: Trace, slot: Set<Int>) {
        if (edgeSolved(trace.board, slot)) return
        val info = edgeInfo[slot]!!
        val home = edgeSlot[slot]!!
        val c0 = info.faces[0]
        val startSlot = edgeIndexWith(trace.board, slot)
        if (startSlot < 0) return
        val startOrient = edgeInfos[startSlot].facelets.indexOfFirst { trace.board[it] == c0 }
        if (startOrient < 0) return
        val start = startSlot * 2 + startOrient
        val goal = home * 2 + info.faces.indexOf(c0)
        val allowed = ArrayList<Int>()
        allowed.add(0); allowed.add(1); allowed.add(2)
        for (i in MID_EDGES.indices) {
            if (!edgeSolved(trace.board, MID_EDGES[i])) allowed.add(3 + i)
        }
        val actions = localBfs(start, goal, F2_TRANS, F2_ACTIONS.size, allowed) ?: return
        for (a in actions) trace.put(F2_ACTIONS[a])
    }

    // ------------------------------------------------------------ last layer

    private val CO_ACTIONS: List<Pair<String, List<String>>> by lazy {
        val out = ArrayList<Pair<String, List<String>>>()
        for ((name, alg) in OCLL) for (u in 0..3) out.add(name to (List(u) { "U" } + alg))
        out
    }

    private val CO_TRANS: Array<IntArray> by lazy {
        val perms = CO_ACTIONS.map { (_, moves) -> moves.map { cube.move(it).perm } }
        Array(81) { value ->
            val v = decodeOrient(value)
            IntArray(CO_ACTIONS.size) { ai ->
                val nv = IntArray(4) { -1 }
                for (i in 0 until 4) {
                    val info = cornerInfo[U_CORNERS[i]]!!
                    var f = info.facelets[v[i]]
                    for (p in perms[ai]) f = p[f]
                    val ns = U_CORNERS.indexOfFirst { cornerInfo[it]!!.facelets.contains(f) }
                    if (ns >= 0) nv[ns] = cornerInfo[U_CORNERS[ns]]!!.facelets.indexOf(f)
                }
                if (nv.any { it < 0 }) value else encodeOrient(nv.toList())
            }
        }
    }

    private val CO_GOAL: Int = encodeOrient(U_CORNERS.map { cornerInfo[it]!!.faces.indexOf(U) })

    private fun encodeOrient(v: List<Int>): Int = ((v[0] * 3 + v[1]) * 3 + v[2]) * 3 + v[3]

    private fun decodeOrient(value: Int): IntArray {
        val v = IntArray(4)
        var x = value
        for (i in 3 downTo 0) {
            v[i] = x % 3
            x /= 3
        }
        return v
    }

    private fun orientationVector(board: IntArray): IntArray = IntArray(4) { i ->
        cornerInfo[U_CORNERS[i]]!!.facelets.indexOfFirst { board[it] == U }
    }

    private fun solveCornerOrientation(start: IntArray, phases: ArrayList<HumanPhase>): IntArray {
        var board = start
        var v = encodeOrient(orientationVector(board).toList())
        if (v == CO_GOAL) return board
        val seq = localBfs(v, CO_GOAL, CO_TRANS, CO_ACTIONS.size) ?: error("CO bfs")
        for (a in seq) {
            val (name, moves) = CO_ACTIONS[a]
            board = cube.apply(board, moves)
            phases.add(HumanPhase("Orient last-layer corners", name, moves))
        }
        v = encodeOrient(orientationVector(board).toList())
        if (v != CO_GOAL) error("CO not solved")
        return board
    }

    private fun solveLastLayer(start: IntArray, phases: ArrayList<HumanPhase>): IntArray {
        var board = start

        board = look(
            board, phases, "Orient last-layer edges",
            listOf("Yellow cross (line)" to LINE, "Yellow cross (L)" to L_SHAPE),
            goal = { orientedUEdges(it) == 4 },
            score = { orientedUEdges(it) },
            max = 6,
        )
        board = solveCornerOrientation(board, phases)
        board = look(
            board, phases, "Permute last-layer corners",
            CP_ALGS,
            goal = { uCornersPlaced(it) },
            score = { 4 - wrongCornerPlacement(it) },
            max = 4,
        )
        board = look(
            board, phases, "Permute last-layer edges",
            EP_ALGS,
            goal = { it.contentEquals(solved) },
            score = { 4 - wrongUEdges(it) },
            max = 4,
        )
        return board
    }

    private fun look(
        start: IntArray,
        phases: ArrayList<HumanPhase>,
        phase: String,
        algs: List<Pair<String, List<String>>>,
        goal: (IntArray) -> Boolean,
        score: (IntArray) -> Int,
        max: Int,
    ): IntArray {
        var board = start
        var guard = 0
        while (!goal(board)) {
            if (++guard > max) error("$phase guard")
            var bestAlg: String? = null
            var bestMoves: List<String>? = null
            var bestBoard: IntArray? = null
            var bestScore = Int.MIN_VALUE
            for ((name, alg) in algs) for (u in 0..3) {
                val moves = List(u) { "U" } + alg
                val next = cube.apply(board, moves)
                val sc = if (goal(next)) 1000 + score(next) else score(next)
                if (sc > bestScore) {
                    bestScore = sc
                    bestAlg = name
                    bestMoves = moves
                    bestBoard = next
                }
            }
            val moves = bestMoves ?: error("no look candidate")
            board = bestBoard ?: error("no look board")
            phases.add(HumanPhase(phase, bestAlg, moves))
        }
        return board
    }

    // ------------------------------------------------------------ helpers

    private fun setAt(board: IntArray, info: CubieInfo): Set<Int> =
        info.facelets.map { board[it] }.toSet()

    private fun centre(board: IntArray, face: Int): Int = board[face * 9 + 4]

    private fun rotationFor(board: IntArray, slot: Set<Int>, corner: Boolean): Int {
        for (k in 0..3) {
            val t = if (k == 0) board else cube.apply(board, listOf(rotSeq(k).first()))
            val centres = if (corner) {
                setOf(centre(t, D), centre(t, F), centre(t, R))
            } else {
                setOf(centre(t, F), centre(t, R))
            }
            if (centres == slot) return k
        }
        error("no rotation for $slot")
    }

    private fun cornerIndexWith(board: IntArray, colours: Set<Int>): Int {
        for ((i, info) in cornerInfos.withIndex()) if (setAt(board, info) == colours) return i
        return -1
    }

    private fun edgeIndexWith(board: IntArray, colours: Set<Int>): Int {
        for ((i, info) in edgeInfos.withIndex()) if (setAt(board, info) == colours) return i
        return -1
    }

    private fun cornerSolved(board: IntArray, slot: Set<Int>): Boolean {
        val info = cornerInfo[slot] ?: return false
        for (i in info.faces.indices) if (board[info.facelets[i]] != info.faces[i]) return false
        return true
    }

    private fun edgeSolved(board: IntArray, slot: Set<Int>): Boolean {
        val info = edgeInfo[slot] ?: return false
        for (i in info.faces.indices) if (board[info.facelets[i]] != info.faces[i]) return false
        return true
    }

    private fun orientedUEdges(board: IntArray): Int {
        var oriented = 0
        for (side in intArrayOf(F, R, B, L)) {
            val info = edgeInfo[setOf(U, side)]!!
            if (board[info.facelets[info.faces.indexOf(U)]] == U) oriented++
        }
        return oriented
    }

    private fun uCornersOriented(board: IntArray): Boolean =
        U_CORNERS.all { cornerInfo[it]!!.let { info -> board[info.facelets[info.faces.indexOf(U)]] == U } }

    private fun wrongCornerOrientation(board: IntArray): Int = U_CORNERS.count {
        cornerInfo[it]!!.let { info -> board[info.facelets[info.faces.indexOf(U)]] != U }
    }

    private fun uCornersPlaced(board: IntArray): Boolean {
        for (slot in U_CORNERS) {
            val info = cornerInfo[slot]!!
            for (i in info.faces.indices) if (board[info.facelets[i]] != info.faces[i]) return false
        }
        return true
    }

    private fun wrongCornerPlacement(board: IntArray): Int = U_CORNERS.count { slot ->
        val info = cornerInfo[slot]!!
        info.faces.indices.any { board[info.facelets[it]] != info.faces[it] }
    }

    private fun wrongUEdges(board: IntArray): Int = intArrayOf(F, R, B, L).count { side ->
        val info = edgeInfo[setOf(U, side)]!!
        info.faces.indices.any { board[info.facelets[it]] != info.faces[it] }
    }
}
