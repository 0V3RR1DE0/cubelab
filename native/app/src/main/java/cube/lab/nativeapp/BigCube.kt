package cube.lab.nativeapp

// Big-cube solver (4x4 first): solve wings, then centres, then the
// reduced 3x3 with a parity gadget. Everything is built from move
// permutations and small BFS setup searches; no solution lists.

private val BIG_HOME = HOME_COLORS

class BigPiece(
    val id: Int,
    val stickers: IntArray,
    val kind: Int, // 0 corner, 1 edge/wing, 2 centre
)

class BigModel(val n: Int) {
    val total = 6 * n * n
    val NN = n * n

    val pieces = ArrayList<BigPiece>()
    val reps = HashMap<Int, Int>()
    val pids = IntArray(total)
    val byPiece = HashMap<Int, IntArray>()
    val orb = IntArray(total) { -1 }
    val orbitPoints = LinkedHashMap<Int, IntArray>()
    val pieceOrbit = HashMap<Int, Int>()
    val pieceKind = HashMap<Int, Int>()
    val orbitPieces = LinkedHashMap<Int, IntArray>()
    val stickerPieceOrbit = HashMap<Int, Int>()

    val gens: List<String>
    private val cube = Cube(n)
    private val infoCache = HashMap<String, MoveInfo>()

    init {
        val map = HashMap<String, Int>()
        for (i in 0 until total) {
            val f = i / NN
            val r = (i % NN) / n
            val c = (i % NN) % n
            val d = FACE_DEFS[f]
            val cu = c + 0.5 - n / 2.0
            val cv = n / 2.0 - r - 0.5
            val pos = DoubleArray(3) { k ->
                d.n[k] * (n / 2.0) + d.u[k] * cu + d.v[k] * cv
            }
            val lim = n / 2.0 - 0.5
            val idx = IntArray(3) { k ->
                when {
                    pos[k] > lim -> n - 1
                    pos[k] < -lim -> 0
                    else -> Math.round(pos[k] + n / 2.0 - 0.5).toInt()
                }
            }
            val key = idx.joinToString(",")
            val pid = map.getOrPut(key) { map.size }
            pids[i] = pid
            if (!reps.containsKey(pid)) reps[pid] = i
        }
        for ((pid, s) in reps) {
            val stickers = ArrayList<Int>()
            for (i in 0 until total) if (pids[i] == pid) stickers.add(i)
            val arr = stickers.toIntArray()
            byPiece[pid] = arr
            pieces.add(BigPiece(pid, arr, kindAt(s)))
        }
        gens = buildList {
            for (k in FACE_KEYS) { add(k); add("$k'") }
            for (d in 2 until n) for (k in FACE_KEYS) { add("$d$k"); add("$d$k'") }
        }
        // sticker orbits
        var oc = 0
        for (s in 0 until total) {
            if (orb[s] >= 0) continue
            val stack = ArrayList<Int>()
            stack.add(s); orb[s] = oc
            while (stack.isNotEmpty()) {
                val x = stack.removeAt(stack.size - 1)
                for (m in gens) {
                    val y = move(m).perm[x]
                    if (orb[y] < 0) { orb[y] = oc; stack.add(y) }
                }
            }
            oc++
        }
        for (i in 0 until total) {
            val o = orb[i]
            val cur = orbitPoints[o]
            orbitPoints[o] = if (cur == null) intArrayOf(i) else cur + i
        }
        // piece orbits (BFS over pieces using a representative sticker)
        val seenP = HashSet<Int>()
        var pc = 0
        for ((pid, s) in reps) {
            if (!seenP.add(pid)) continue
            val stack = ArrayList<Int>()
            stack.add(s)
            val comp = HashSet<Int>()
            while (stack.isNotEmpty()) {
                val x = stack.removeAt(stack.size - 1)
                comp.add(pids[x])
                for (m in gens) {
                    val y = move(m).perm[x]
                    val q = pids[y]
                    if (seenP.add(q)) stack.add(y)
                }
            }
            for (q in comp) pieceOrbit[q] = pc
            pc++
        }
        for ((pid, o) in pieceOrbit) {
            if (!pieceKind.containsKey(o)) pieceKind[o] = kindAt(reps[pid]!!)
            val cur = orbitPieces[o]
            orbitPieces[o] = if (cur == null) intArrayOf(pid) else cur + pid
        }
        for ((o, pts) in orbitPoints) stickerPieceOrbit[o] = pieceOrbit[pids[pts[0]]]!!
    }

    fun kindAt(i: Int): Int {
        val r = (i % NN) / n
        val c = (i % NN) % n
        val er = r == 0 || r == n - 1
        val ec = c == 0 || c == n - 1
        return when {
            er && ec -> 0
            er || ec -> 1
            else -> 2
        }
    }

    fun move(name: String): MoveInfo = infoCache.getOrPut(name) { cube.move(name) }

    fun identity() = IntArray(total) { it }

    fun apply(labels: IntArray, word: List<String>): IntArray {
        var out = labels
        for (m in word) {
            val p = move(m).perm
            val next = IntArray(total)
            for (i in 0 until total) next[p[i]] = out[i]
            out = next
        }
        return out
    }

    fun applyNames(labels: IntArray, names: List<String>): IntArray = apply(labels, names)
}

fun invMove(m: String): String = when {
    m.endsWith("2") -> m
    m.endsWith("'") -> m.dropLast(1)
    else -> "$m'"
}

fun invSeq(seq: List<String>): List<String> = seq.asReversed().map { invMove(it) }

fun cyclesOf(arr: IntArray, points: IntArray): List<IntArray> {
    val seen = HashSet<Int>()
    val out = ArrayList<IntArray>()
    for (p in points) {
        if (seen.contains(p)) continue
        val c = ArrayList<Int>()
        var j = p
        while (seen.add(j)) {
            c.add(j)
            j = arr[j]
        }
        if (c.size > 1) out.add(c.toIntArray())
    }
    return out
}

fun parityOn(arr: IntArray, points: IntArray): Int {
    val idx = HashMap<Int, Int>()
    points.forEachIndexed { i, p -> idx[p] = i }
    var x = 0
    for (i in points.indices) for (j in i + 1 until points.size) {
        if (idx[arr[points[j]]]!! < idx[arr[points[i]]]!!) x = x xor 1
    }
    return x
}

fun parityOfList(a: IntArray): Int {
    var x = 0
    for (i in a.indices) for (j in i + 1 until a.size) if (a[j] < a[i]) x = x xor 1
    return x
}

fun movementFromLabels(labels: IntArray): IntArray {
    val p = IntArray(labels.size)
    for (i in labels.indices) p[labels[i]] = i
    return p
}

object BigCube {
    private const val HOME = 0

    /**
     * Recolour a big-cube board into the HOME scheme using the corner pieces to
     * infer the cube's colour arrangement. Returns the mapped board or null.
     */
    fun mapToHome(board: IntArray, perm: IntArray): IntArray =
        IntArray(board.size) { BIG_HOME[perm.indexOf(board[it])] }

    fun inferHomeBoard(cube: Cube, board: IntArray): IntArray? =
        inferHomePerm(cube, board)?.let { mapToHome(board, it) }

    /** Returns a face -> colour permutation for the board's scheme, or null. */
    fun inferHomePerm(cube: Cube, board: IntArray): IntArray? {
        val n = cube.n
        val cube3 = Cube(3)
        // corner sticker triples of the 3x3 model, grouped by cubie
        val cornerGroups = ArrayList<IntArray>()
        run {
            val bySign = LinkedHashMap<String, MutableList<Int>>()
            cube3.facelets.forEachIndexed { i, fl ->
                if (fl.pos.all { Math.abs(it) > 0.99 }) {
                    val key = fl.pos.joinToString(",") { if (it > 0) "1" else "-1" }
                    bySign.getOrPut(key) { ArrayList() }.add(i)
                }
            }
            for (g in bySign.values) if (g.size == 3) cornerGroups.add(g.toIntArray())
        }
        fun det(a: DoubleArray, b: DoubleArray, c: DoubleArray): Double =
            a[0] * (b[1] * c[2] - b[2] * c[1]) -
                a[1] * (b[0] * c[2] - b[2] * c[0]) +
                a[2] * (b[0] * c[1] - b[1] * c[0])
        fun chiralityOk(b3: IntArray): Boolean {
            for (g in cornerGroups) {
                val n0 = FACE_DEFS[cube3.facelets[g[0]].face].n
                val n1 = FACE_DEFS[cube3.facelets[g[1]].face].n
                val n2 = FACE_DEFS[cube3.facelets[g[2]].face].n
                val homeFaces = IntArray(3) { BIG_HOME.indexOf(b3[g[it]]) }
                if (homeFaces.any { it < 0 }) return false
                val m0 = FACE_DEFS[homeFaces[0]].n
                val m1 = FACE_DEFS[homeFaces[1]].n
                val m2 = FACE_DEFS[homeFaces[2]].n
                if (Math.signum(det(n0, n1, n2)) != Math.signum(det(m0, m1, m2))) return false
            }
            return true
        }

        val perm = IntArray(6)
        val used = BooleanArray(6)
        var result: IntArray? = null

        fun tryPerm(depth: Int) {
            if (result != null) return
            if (depth == 6) {
                val mapped = IntArray(board.size)
                var bad = false
                for (i in board.indices) {
                    val f = perm.indexOf(board[i])
                    if (f < 0) { bad = true; break }
                    mapped[i] = BIG_HOME[f]
                }
                if (bad) return
                val b3 = cube3.solved()
                for (f in 0 until 6) for (r3 in intArrayOf(0, 2)) for (c3 in intArrayOf(0, 2)) {
                    val r = if (r3 == 0) 0 else n - 1
                    val c = if (c3 == 0) 0 else n - 1
                    b3[f * 9 + r3 * 3 + c3] = mapped[f * n * n + r * n + c]
                }
                if (!chiralityOk(b3)) return
                if (decodeCubies(b3, cube3, BIG_HOME) != null) result = perm.copyOf()
                return
            }
            for (c in 0 until 6) {
                if (used[c]) continue
                used[c] = true
                perm[depth] = c
                tryPerm(depth + 1)
                used[c] = false
                if (result != null) return
            }
        }
        tryPerm(0)
        return result
    }

    /** Returns move names that take `board` (HOME colour scheme) to solved, or null. */
    fun solve(cube: Cube, board: IntArray): List<String>? {
        val n = cube.n
        if (n < 4) return null
        return try {
            val model = BigModel(n)
            val labels = decodeLabels(model, board) ?: return null
            val moves = solvePipeline(model, labels)
            // verify colours
            val out = model.apply(labels, moves)
            for (f in 0 until 6) {
                val base = f * model.NN
                val c0 = BIG_HOME[out[base] / model.NN]
                for (k in 1 until model.NN) {
                    if (BIG_HOME[out[base + k] / model.NN] != c0) {
                        return null
                    }
                }
            }
            if (!cube.isSolved(cube.apply(board, moves))) return null
            moves
        } catch (t: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------- decode

    fun decodeLabels(model: BigModel, colours: IntArray): IntArray? {
        val n = model.n
        val total = model.total
        val labels = IntArray(total) { -1 }
        val curPieces = HashMap<Int, IntArray>()
        for (i in 0 until total) {
            val p = model.pids[i]
            val cur = curPieces[p]
            curPieces[p] = if (cur == null) intArrayOf(i) else cur + i
        }
        val homeByKey = HashMap<String, MutableList<Int>>()
        for ((p, ss) in model.byPiece) {
            val key = ss.map { BIG_HOME[it / model.NN] }.sorted().joinToString(",")
            homeByKey.getOrPut(key) { ArrayList() }.add(p)
        }
        val used = HashSet<Int>()
        for ((_, ss) in curPieces) {
            val key = ss.map { colours[it] }.sorted().joinToString(",")
            val cands = homeByKey[key]?.filter { !used.contains(it) } ?: return null
            var chosen = -1
            var assign: IntArray? = null
            for (hp in cands) {
                val hss = model.byPiece[hp]!!
                val taken = HashSet<Int>()
                val a = IntArray(ss.size)
                var ok = true
                for (k in ss.indices) {
                    val i = ss[k]
                    val c = colours[i]
                    var pick = -1
                    for (h in hss) {
                        if (taken.contains(h)) continue
                        if (model.orb[h] != model.orb[i]) continue
                        if (BIG_HOME[h / model.NN] == c) { pick = h; break }
                    }
                    if (pick < 0) { ok = false; break }
                    taken.add(pick)
                    a[k] = pick
                }
                if (ok) { chosen = hp; assign = a; break }
            }
            if (chosen < 0) return null
            used.add(chosen)
            ss.forEachIndexed { k, i -> labels[i] = assign!![k] }
        }
        for (i in 0 until total) if (labels[i] < 0) return null
        return labels
    }

    // ---------------------------------------------------------------- pipeline

    private fun solvePipeline(model: BigModel, start: IntArray): List<String> {
        val n = model.n
        var labels = start
        val steps = ArrayList<String>()
        val wingPieceOrbits = model.pieceKind.entries
            .filter { it.value == 1 }
            .map { it.key }
            .sorted()

        // parity pre-fix (and, for odd n, face centres)
        val pre = preFix(model, labels, wingPieceOrbits)
            ?: error("no parity pre-fix")
        labels = model.apply(labels, pre)
        steps.addAll(pre)

        if (n % 2 == 1) {
            val (l, s) = solveMidges(model, labels)
            labels = l; steps.addAll(s)
        }

        val gadgets = wingGadgets(model)
        val (l2, s2) = solveWings(model, labels, gadgets)
        labels = l2; steps.addAll(s2)

        // centres
        val (lc, sc) = solveCentresFrom(model, labels)
        labels = lc; steps.addAll(sc)
        // final 3x3 stage; on parity mismatch, fix the reduced state.
        var fin = tryFinal(model, labels)
        val dGadget = listOf("Rw2", "F2", "U2", "Rw2", "R2", "U2", "F2", "Rw2")
        if (fin == null && n == 4) {
            // The 4x4 PLL-parity gadget is far cheaper than re-solving wings.
            labels = model.apply(labels, dGadget); steps.addAll(dGadget)
            fin = tryFinal(model, labels)
        }
        if (fin == null) {
            val swapTargets = HashMap<Int, Map<Int, IntArray>>()
            val stickerOrbitsOf = HashMap<Int, MutableList<Int>>()
            for ((so, po) in model.stickerPieceOrbit) stickerOrbitsOf.getOrPut(po) { ArrayList() }.add(so)
            for (po in gadgets.keys) {
                if (model.pieceKind[po] != 1) continue
                if (model.orbitPieces[po]!!.size == 12) continue
                swapTargets[po] = dedgeSwapTargets(model, po, stickerOrbitsOf[po]!!.sorted(), 0, 1)
            }
            val (lw, sw) = solveWings(model, labels, gadgets, swapTargets)
            labels = lw; steps.addAll(sw)
            val (lc2, sc2) = solveCentresFrom(model, labels)
            labels = lc2; steps.addAll(sc2)
            fin = tryFinal(model, labels)
            if (fin == null) {
                labels = model.apply(labels, dGadget); steps.addAll(dGadget)
                fin = tryFinal(model, labels)
            }
        }
        val (l4, s4) = fin ?: error("final stage failed")
        labels = l4; steps.addAll(s4)
        return steps
    }

    // ---------------------------------------------------------------- parity pre-fix

    private fun preFix(model: BigModel, labels: IntArray, wingOrbits: List<Int>): List<String>? {
        val n = model.n
        val repSticker = HashMap<Int, Int>()
        for ((so, po) in model.stickerPieceOrbit) if (!repSticker.containsKey(po)) repSticker[po] = so
        val req = IntArray(wingOrbits.size) { i ->
            parityOn(labels, model.orbitPoints[repSticker[wingOrbits[i]]!!]!!)
        }
        val genPar = model.gens.map { m ->
            val l = model.apply(model.identity(), listOf(m))
            IntArray(wingOrbits.size) { i ->
                parityOn(l, model.orbitPoints[repSticker[wingOrbits[i]]!!]!!)
            }
        }
        val odd = n % 2 == 1
        var sixPoints: IntArray? = null
        var sixStart: IntArray? = null
        var genSix: List<IntArray>? = null
        if (odd) {
            sixPoints = model.orbitPoints.entries.first { model.kindAt(it.value[0]) == 2 && it.value.size == 6 }.value
            val posIdx = HashMap<Int, Int>()
            sixPoints.forEachIndexed { i, p -> posIdx[p] = i }
            sixStart = IntArray(6) { i -> posIdx[labels[sixPoints[i]]]!! }
            genSix = model.gens.map { m ->
                val p = model.move(m).perm
                IntArray(6) { i -> posIdx[p[sixPoints[i]]]!! }
            }
        }
        val key = { six: IntArray?, par: IntArray -> (six?.joinToString(",") ?: "-") + "|" + par.joinToString("") }
        val goalPar = req.joinToString("")
        val goalSix = if (odd) "0,1,2,3,4,5" else "-"
        val goal = "$goalSix|$goalPar"
        // genPar is measured on the identity, so the running parity of the
        // word must equal the scramble's parity to leave every orbit even.
        val startPar = IntArray(wingOrbits.size)
        val startKey = key(sixStart, startPar)
        val queue = ArrayDeque<Pair<IntArray?, IntArray>>()
        val seen = HashMap<String, List<String>>()
        seen[startKey] = emptyList()
        queue.add(sixStart to startPar)
        while (queue.isNotEmpty()) {
            val (six, par) = queue.removeFirst()
            val w = seen[key(six, par)]!!
            if (key(six, par) == goal) return w
            if (w.size >= 12) continue
            for (gi in model.gens.indices) {
                val npar = IntArray(par.size) { par[it] xor genPar[gi][it] }
                var nsix: IntArray? = null
                if (odd) {
                    nsix = IntArray(6)
                    val p = genSix!![gi]
                    for (i in 0 until 6) nsix[p[i]] = six!![i]
                }
                val k = key(nsix, npar)
                if (seen.containsKey(k)) continue
                seen[k] = w + model.gens[gi]
                queue.add(nsix to npar)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- wing gadgets

    fun wingGadgets(model: BigModel): Map<Int, List<String>> {
        val n = model.n
        val wingOrbits = model.pieceKind.entries.filter { it.value == 1 }.map { it.key }.sorted()
        fun sig(seq: List<String>): Map<Int, List<Int>> {
            val labels = model.apply(model.identity(), seq)
            val out = HashMap<Int, List<Int>>()
            for (o in wingOrbits) {
                val pieces = model.orbitPieces[o]!!.sortedArray()
                val idx = HashMap<Int, Int>()
                pieces.forEachIndexed { i, p -> idx[p] = i }
                val perm = pieces.map { p -> idx[model.pids[labels[model.reps[p]!!]]]!! }.toIntArray()
                val seen = HashSet<Int>()
                val cs = ArrayList<Int>()
                for (i in perm.indices) {
                    if (seen.contains(i)) continue
                    var j = i; var c = 0
                    while (seen.add(j)) { c++; j = perm[j] }
                    if (c > 1) cs.add(c)
                }
                out[o] = cs
            }
            return out
        }
        fun classify(s: Map<Int, List<Int>>): Pair<Boolean, Int> {
            var active = 0; var three = -1; var ok = true
            for (o in wingOrbits) {
                val cs = s[o] ?: emptyList()
                if (cs.isEmpty()) continue
                active++
                if (cs.size == 1 && cs[0] == 3) three = o else ok = false
            }
            return (ok && active == 1) to three
        }
        val gadgets = HashMap<Int, List<String>>()
        fun tryAdd(seq: List<String>) {
            val (ok, three) = classify(sig(seq))
            if (ok && !gadgets.containsKey(three)) gadgets[three] = seq
        }
        val inner = (2 until n).toList()
        for (k in inner) {
            val a = listOf("${k}R", "U", "${k}R'", "U'")
            val variants = listOf(
                listOf("U2", "${k}R", "U", "${k}R'", "U"),
                listOf("U2", "${k}R", "U2", "${k}R'", "U2"),
                listOf("U", "${k}R", "U", "${k}R'", "U", "U"),
            )
            for (b in variants) tryAdd(a + b + invSeq(a) + invSeq(b))
        }
        if (gadgets.size < wingOrbits.size) {
            val setupMoves = ArrayList<String>(model.gens)
            for (k in FACE_KEYS) { setupMoves.add("${k}w"); setupMoves.add("${k}w'") }
            val setups = ArrayList<List<String>>()
            setups.add(emptyList())
            for (m in setupMoves) setups.add(listOf(m))
            val pool = ArrayList<List<String>>()
            for (k in inner) {
                val base = listOf("${k}R", "U", "${k}R'", "U'")
                for (s in setups) pool.add(s + base + invSeq(s))
            }
            var i = 0
            while (i < pool.size && gadgets.size < wingOrbits.size) {
                var j = i + 1
                while (j < pool.size) {
                    tryAdd(pool[i] + pool[j] + invSeq(pool[i]) + invSeq(pool[j]))
                    if (gadgets.size == wingOrbits.size) break
                    j++
                }
                i++
            }
        }
        return gadgets
    }

    // ---------------------------------------------------------------- triple setups

    private fun tripleSetups(
        model: BigModel,
        base: IntArray,
        track: Map<String, IntArray>? = null,
    ): Pair<HashMap<Int, List<String>>, (IntArray) -> Int> {
        val total = model.total
        val map = HashMap<Int, List<String>>()
        fun enc(t: IntArray) = (t[0] * total + t[1]) * total + t[2]
        map[enc(base)] = emptyList()
        val queue = ArrayDeque<IntArray>()
        queue.add(base.copyOf())
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            val w = map[enc(t)]!!
            for (m in model.gens) {
                val p = track?.get(m) ?: model.move(m).perm
                val nt = intArrayOf(p[t[0]], p[t[1]], p[t[2]])
                val k = enc(nt)
                if (map.containsKey(k)) continue
                map[k] = w + m
                queue.add(nt)
            }
        }
        return map to ::enc
    }

    private fun solveStickerOrbit(
        model: BigModel,
        labels: IntArray,
        orbit: Int,
        gadget: List<String>,
    ): Pair<IntArray, List<String>> {
        val total = model.total
        val gl = model.apply(model.identity(), gadget)
        val gp = movementFromLabels(gl)
        val points = model.orbitPoints[orbit]!!
        val cs = cyclesOf(gp, points)
        require(cs.size == 1 && cs[0].size == 3) { "wing gadget not a 3-cycle" }
        val base = cs[0]
        val (setupMap, enc) = tripleSetups(model, base)
        var lab = labels
        val steps = ArrayList<String>()
        var guard = 0
        while (true) {
            if (++guard > 5000) error("sticker guard")
            var a = -1
            for (p in points) if (lab[p] != p) { a = p; break }
            if (a < 0) break
            val b = lab[a]
            val c = lab[b]
            // A 3-cycle (a -> b -> c) fixes b and c; for a 2-cycle fall back
            // to a third misplaced position (fixes one).
            val useC = c != a && c != b
            val third = if (useC) c else {
                var x = -1
                for (p in points) if (p != a && p != b && lab[p] != p) { x = p; break }
                if (x < 0) for (p in points) if (p != a && p != b) { x = p; break }
                x
            }
            val setup = setupMap[enc(intArrayOf(a, b, third))] ?: error("no setup")
            val g = invSeq(setup) + gadget + setup
            lab = model.apply(lab, g)
            steps.addAll(g)
            if (lab[b] != b) error("sticker gizmo failed")
        }
        return lab to steps
    }

    private fun solveStickerOrbitTarget(
        model: BigModel,
        labels: IntArray,
        orbit: Int,
        gadget: List<String>,
        target: IntArray,
    ): Pair<IntArray, List<String>> {
        val gl = model.apply(model.identity(), gadget)
        val gp = movementFromLabels(gl)
        val points = model.orbitPoints[orbit]!!
        val cs = cyclesOf(gp, points)
        require(cs.size == 1 && cs[0].size == 3) { "wing gadget not a 3-cycle" }
        val base = cs[0]
        val (setupMap, enc) = tripleSetups(model, base)
        val targetPos = IntArray(model.total)
        for (p in points) targetPos[target[p]] = p
        var lab = labels
        val steps = ArrayList<String>()
        var guard = 0
        while (true) {
            if (++guard > 5000) error("sticker target guard")
            var a = -1
            for (p in points) if (lab[p] != target[p]) { a = p; break }
            if (a < 0) break
            val b = targetPos[lab[a]]
            val c = targetPos[lab[b]]
            val useC = c != a && c != b
            val third = if (useC) c else {
                var x = -1
                for (q in points) if (q != a && q != b && lab[q] != target[q]) { x = q; break }
                if (x < 0) for (q in points) if (q != a && q != b) { x = q; break }
                x
            }
            val setup = setupMap[enc(intArrayOf(a, b, third))] ?: error("no setup")
            val g = invSeq(setup) + gadget + setup
            lab = model.apply(lab, g)
            steps.addAll(g)
            if (lab[b] != target[b]) error("sticker target gizmo failed")
        }
        return lab to steps
    }

    private fun wingColour(model: BigModel, p: Int): String =
        model.byPiece[p]!!.map { BIG_HOME[it / model.NN] }.sorted().joinToString(",")

    private fun homePos(model: BigModel, p: Int, so: Int): Int =
        model.byPiece[p]!!.first { model.orb[it] == so }

    private fun dedgeSwapTargets(
        model: BigModel,
        po: Int,
        sos: List<Int>,
        e1: Int,
        e2: Int,
    ): Map<Int, IntArray> {
        val pieces = model.orbitPieces[po]!!.sortedArray()
        val groups = LinkedHashMap<String, MutableList<Int>>()
        for (p in pieces) groups.getOrPut(wingColour(model, p)) { ArrayList() }.add(p)
        val gs = groups.entries.filter { it.value.size == 2 }.sortedBy { it.key }.map { it.value }
        require(gs.size > maxOf(e1, e2)) { "not enough dedge groups" }
        val a = gs[e1].sortedBy { homePos(model, it, sos[0]) }
        val b = gs[e2].sortedBy { homePos(model, it, sos[0]) }
        val pair = HashMap<Int, Int>()
        for (i in a.indices) { pair[a[i]] = b[i]; pair[b[i]] = a[i] }
        val maps = HashMap<Int, IntArray>()
        for (so in sos) {
            val arr = IntArray(model.total) { it }
            for (p in a + b) {
                val q = pair[p]!!
                arr[homePos(model, q, so)] = homePos(model, p, so)
            }
            maps[so] = arr
        }
        return maps
    }

    private fun solveWings(
        model: BigModel,
        labels: IntArray,
        gadgets: Map<Int, List<String>>,
        swapTargets: Map<Int, Map<Int, IntArray>>? = null,
    ): Pair<IntArray, List<String>> {
        var lab = labels
        val steps = ArrayList<String>()
        val stickerOrbitsOf = HashMap<Int, MutableList<Int>>()
        for ((so, po) in model.stickerPieceOrbit) stickerOrbitsOf.getOrPut(po) { ArrayList() }.add(so)
        for ((po, g) in gadgets.entries.sortedBy { it.key }) {
            if (model.pieceKind[po] != 1) continue
            if (model.orbitPieces[po]!!.size == 12) continue // midges handled separately
            val sos = stickerOrbitsOf[po]!!.sorted()
            val tgts = swapTargets?.get(po)
            var done = true
            for (so in sos) {
                val target = tgts?.get(so)
                for (p in model.orbitPoints[so]!!) {
                    val want = target?.get(p) ?: p
                    if (lab[p] != want) { done = false; break }
                }
                if (!done) break
            }
            if (done) continue
            val (l, s) = if (tgts != null) {
                solveStickerOrbitTarget(model, lab, sos[0], g, tgts[sos[0]]!!)
            } else {
                solveStickerOrbit(model, lab, sos[0], g)
            }
            lab = l; steps.addAll(s)
            for (so in sos) {
                val target = tgts?.get(so)
                for (p in model.orbitPoints[so]!!) {
                    val want = target?.get(p) ?: p
                    if (lab[p] != want) error("wing orbit not solved")
                }
            }
        }
        return lab to steps
    }


    // ---------------------------------------------------------------- midges (odd n)

    private fun solveMidges(model: BigModel, labels: IntArray): Pair<IntArray, List<String>> {
        // 5x5/7x7 only. Ported from the validated reduction prototype.
        val n = model.n
        val total = model.total
        val midgeOrbit = model.orbitPieces.entries.first {
            model.pieceKind[it.key] == 1 && it.value.size == 12
        }.key
        val pieces = model.orbitPieces[midgeOrbit]!!.sortedArray()
        val idxOf = HashMap<Int, Int>()
        pieces.forEachIndexed { i, p -> idxOf[p] = i }
        val mid = (n + 1) / 2
        val gadget = listOf(
            "${mid}R", "U", "${mid}R'", "U'", "U", "${mid}R", "U", "${mid}R'",
            "U", "U", "U", "${mid}R", "U'", "${mid}R'", "U'", "U'", "${mid}R", "U'", "${mid}R'", "U'",
        )
        val flip = listOf("U'", "F", "R'", "F", "R", "F'", "U", "F'")
        fun pieceAt(lab: IntArray): IntArray {
            val at = IntArray(12)
            for (i in 0 until 12) at[i] = idxOf[model.pids[lab[model.reps[pieces[i]]!!]]]!!
            return at
        }
        fun orientVec(lab: IntArray): IntArray {
            val at = pieceAt(lab)
            val o = IntArray(12)
            for (slot in 0 until 12) {
                val pa = pieces[at[slot]]
                val ss = model.byPiece[pieces[slot]]!!
                val ps = model.byPiece[pa]!!
                o[slot] = if (lab[ss[0]] == ps[0]) 0 else 1
            }
            return o
        }
        fun pieceMovement(at: IntArray): IntArray {
            val d = IntArray(12)
            for (p in 0 until 12) for (q in 0 until 12) if (at[q] == p) { d[p] = q; break }
            return d
        }
        val gl = model.apply(model.identity(), gadget)
        val gAt = pieceAt(gl)
        val gMov = pieceMovement(gAt)
        val pcyc = cyclesOf(gMov, IntArray(12) { it }).firstOrNull() ?: error("midge gadget")
        val movePiece = HashMap<String, IntArray>()
        for (m in model.gens) {
            val l = model.apply(model.identity(), listOf(m))
            movePiece[m] = pieceMovement(pieceAt(l))
        }
        var lab = labels
        val steps = ArrayList<String>()
        val at0 = pieceAt(lab)
        if (parityOfList(at0) != 0) {
            var oddGen: String? = null
            for (m in model.gens) {
                val l = model.apply(model.identity(), listOf(m))
                if (parityOfList(pieceAt(l)) != 0) { oddGen = m; break }
            }
            if (oddGen == null) error("no odd midge generator")
            lab = model.apply(lab, listOf(oddGen))
            steps.add(oddGen)
        }
        val (setupMap, enc) = tripleSetups(model, pcyc, movePiece)
        var at = pieceAt(lab)
        var guard = 0
        while (true) {
            if (++guard > 500) error("midge guard")
            var a = -1
            for (i in 0 until 12) if (at[i] != i) { a = i; break }
            if (a < 0) break
            val b = at[a]
            val c = at[b]
            val third = if (c != a && c != b) c else {
                var x = -1
                for (i in 0 until 12) if (i != a && i != b && at[i] != i) { x = i; break }
                if (x < 0) for (i in 0 until 12) if (i != a && i != b) { x = i; break }
                x
            }
            val setup = setupMap[enc(intArrayOf(a, b, third))] ?: error("no midge setup")
            val g = invSeq(setup) + gadget + setup
            lab = model.apply(lab, g)
            steps.addAll(g)
            at = pieceAt(lab)
            if (at[b] != b) error("midge gizmo failed")
        }
        // orientation
        val flL = model.apply(model.identity(), flip)
        val flO = orientVec(flL)
        val baseFlip = flO.indices.filter { flO[it] != 0 }.toIntArray()
        require(baseFlip.size == 2) { "flip gadget is not a pair flip" }
        val pairMap = HashMap<Int, List<String>>()
        fun pk(a: Int, b: Int) = a * 12 + b
        pairMap[pk(baseFlip[0], baseFlip[1])] = emptyList()
        val pq = ArrayDeque<IntArray>()
        pq.add(baseFlip.copyOf())
        while (pq.isNotEmpty()) {
            val t = pq.removeFirst()
            val w = pairMap[pk(t[0], t[1])]!!
            for (m in model.gens) {
                val p = movePiece[m]!!
                val nt = intArrayOf(p[t[0]], p[t[1]])
                val k = pk(nt[0], nt[1])
                if (pairMap.containsKey(k)) continue
                pairMap[k] = w + m
                pq.add(nt)
            }
        }
        guard = 0
        while (true) {
            if (++guard > 200) error("flip guard")
            val o = orientVec(lab)
            val fl = o.indices.filter { o[it] != 0 }
            if (fl.isEmpty()) break
            if (fl.size < 2) error("odd flips")
            val setup = pairMap[pk(fl[0], fl[1])] ?: error("no pair setup")
            val g = invSeq(setup) + flip + setup
            lab = model.apply(lab, g)
            steps.addAll(g)
        }
        return lab to steps
    }

    // ---------------------------------------------------------------- centres

    fun centreGadgets(model: BigModel): Map<Int, List<String>> {
        val n = model.n
        val out = HashMap<Int, List<String>>()
        val inner = (2 until n).toList()
        for (k in inner) for (m in inner) {
            val variants = listOf(
                listOf("${k}R", "U", "${k}R'", "${m}U", "${k}R", "U'", "${k}R'", "${m}U'"),
                listOf("${k}R", "U2", "${k}R'", "${m}U2", "${k}R", "U2", "${k}R'", "${m}U2"),
                listOf("${k}R", "U", "${k}R'", "${m}U'", "${k}R", "U'", "${k}R'", "${m}U"),
            )
            for (seq in variants) {
                val b = model.apply(model.identity(), seq)
                val cs = cyclesOf(b, IntArray(model.total) { it })
                if (cs.size == 1 && cs[0].size == 3 && model.kindAt(cs[0][0]) == 2) {
                    val so = model.orb[cs[0][0]]
                    if (!out.containsKey(so)) out[so] = seq
                }
            }
        }
        return out
    }

    private fun solveCentreOrbit(
        model: BigModel,
        labels: IntArray,
        orbit: Int,
        gadget: List<String>,
    ): Pair<IntArray, List<String>> {
        val total = model.total
        val gl = model.apply(model.identity(), gadget)
        val gp = movementFromLabels(gl)
        val points = model.orbitPoints[orbit]!!
        val cs = cyclesOf(gp, points)
        require(cs.size == 1 && cs[0].size == 3) { "centre gadget not a 3-cycle" }
        val base = cs[0]
        val (setupMap, enc) = tripleSetups(model, base)
        val colourOfHome = { pos: Int -> BIG_HOME[pos / model.NN] }
        val colours = IntArray(6)
        val curByCol = HashMap<Int, MutableList<Int>>()
        val tgtByCol = HashMap<Int, MutableList<Int>>()
        for (p in points) {
            val cc = BIG_HOME[labels[p] / model.NN]
            curByCol.getOrPut(cc) { ArrayList() }.add(p)
            tgtByCol.getOrPut(colourOfHome(p)) { ArrayList() }.add(p)
        }
        val move = IntArray(total) { it }
        for (c in curByCol.keys) {
            val cur = curByCol[c]!!.sorted()
            val tgt = tgtByCol[c]?.sorted() ?: return labels to emptyList()
            for (i in cur.indices) move[cur[i]] = tgt[i]
        }
        if (parityOn(move, points) != 0) {
            var swapped = false
            for (c in curByCol.keys) {
                val cur = curByCol[c]!!
                if (cur.size >= 2) {
                    val a = cur[0]; val b = cur[1]
                    val t = move[a]; move[a] = move[b]; move[b] = t
                    swapped = true; break
                }
            }
            if (!swapped) error("cannot flip centre parity")
        }
        val virtual = IntArray(total)
        for (p in points) virtual[p] = move[p]
        var lab = labels
        val steps = ArrayList<String>()
        var guard = 0
        while (true) {
            if (++guard > 5000) error("centre guard")
            var a = -1
            for (p in points) if (virtual[p] != p) { a = p; break }
            if (a < 0) break
            val b = virtual[a]
            val c = virtual[b]
            val third = if (c != a && c != b) c else {
                var x = -1
                for (p in points) if (p != a && p != b && virtual[p] != p) { x = p; break }
                if (x < 0) for (p in points) if (p != a && p != b) { x = p; break }
                x
            }
            val setup = setupMap[enc(intArrayOf(a, b, third))] ?: error("no centre setup")
            val g = invSeq(setup) + gadget + setup
            lab = model.apply(lab, g)
            steps.addAll(g)
            val t = virtual[third]; virtual[third] = virtual[b]; virtual[b] = virtual[a]; virtual[a] = t
        }
        for (p in points) {
            if (BIG_HOME[lab[p] / model.NN] != colourOfHome(p)) error("centre colours wrong")
        }
        return lab to steps
    }

    // ---------------------------------------------------------------- final 3x3

    private fun buildReducedBoard(model: BigModel, labels: IntArray): IntArray {
        val n = model.n
        val board = IntArray(54)
        val mid = if (n % 2 == 1) (n - 1) / 2 else n / 2 - 1
        for (f in 0 until 6) for (r3 in 0..2) for (c3 in 0..2) {
            val r = when (r3) { 0 -> 0; 2 -> n - 1; else -> mid }
            val c = when (c3) { 0 -> 0; 2 -> n - 1; else -> mid }
            board[f * 9 + r3 * 3 + c3] = BIG_HOME[labels[f * model.NN + r * n + c] / model.NN]
        }
        return board
    }

    private fun tryFinal(model: BigModel, labels: IntArray): Pair<IntArray, List<String>>? {
        val board3 = buildReducedBoard(model, labels)
        val cube3 = Cube(3)
        val state = decodeCubies(board3, cube3, BIG_HOME) ?: return null
        val moves = Kociemba.solveCube(state) ?: return null
        return model.apply(labels, moves) to moves
    }

    private fun solveCentresFrom(model: BigModel, labels: IntArray): Pair<IntArray, List<String>> {
        var lab = labels
        val steps = ArrayList<String>()
        val gadgets = centreGadgets(model)
        for ((o, pts) in model.orbitPoints) {
            if (model.kindAt(pts[0]) != 2) continue
            if (pts.size == 6) continue
            var ok = true
            for (p in pts) {
                if (BIG_HOME[lab[p] / model.NN] != BIG_HOME[p / model.NN]) { ok = false; break }
            }
            if (ok) continue
            val g = gadgets[o] ?: error("no centre gadget $o")
            val (l, s) = solveCentreOrbit(model, lab, o, g)
            lab = l; steps.addAll(s)
        }
        return lab to steps
    }
}
