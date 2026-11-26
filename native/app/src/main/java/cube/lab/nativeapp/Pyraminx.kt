package cube.lab.nativeapp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Pyraminx: tetrahedron geometry, turns and an exact BFS solver.
// Port of www/src/model.js.

private fun vsub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
private fun vadd(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
private fun vmul(a: DoubleArray, s: Double) = doubleArrayOf(a[0] * s, a[1] * s, a[2] * s)
private fun vcross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
)
private fun vlen(a: DoubleArray) = hypot(hypot(a[0], a[1]), a[2])
private fun vunit(a: DoubleArray) = vmul(a, 1.0 / (vlen(a).takeIf { it != 0.0 } ?: 1.0))
private fun vmid(vararg ps: DoubleArray): DoubleArray {
    var s = doubleArrayOf(0.0, 0.0, 0.0)
    for (p in ps) s = vadd(s, p)
    return vmul(s, 1.0 / ps.size)
}
private fun vdot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

val PYRA_VERTEX_NAMES = arrayOf("U", "R", "L", "B")
// Face 0..3 are opposite vertices 3,2,1,0 respectively.
val PYRA_FACE_NAMES = arrayOf("B", "L", "R", "U")
val PYRA_VERTICES = arrayOf(
    doubleArrayOf(0.0, kotlin.math.sqrt(2.0), 0.0),
    doubleArrayOf(0.8660254037844387, 0.0, 0.5),
    doubleArrayOf(-0.8660254037844387, 0.0, 0.5),
    doubleArrayOf(0.0, 0.0, -1.0),
)
val PYRA_CENTROID = doubleArrayOf(0.0, kotlin.math.sqrt(2.0) / 4, 0.0)
private val FACE_VERTS = arrayOf(
    intArrayOf(0, 2, 1),
    intArrayOf(0, 1, 3),
    intArrayOf(0, 3, 2),
    intArrayOf(2, 3, 1),
)
private val LATTICE = arrayOf(
    arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1)),
    arrayOf(intArrayOf(1, 0), intArrayOf(2, 0), intArrayOf(1, 1)),
    arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1)),
    arrayOf(intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(0, 2)),
    arrayOf(intArrayOf(2, 0), intArrayOf(3, 0), intArrayOf(2, 1)),
    arrayOf(intArrayOf(2, 0), intArrayOf(1, 1), intArrayOf(2, 1)),
    arrayOf(intArrayOf(1, 1), intArrayOf(2, 1), intArrayOf(1, 2)),
    arrayOf(intArrayOf(1, 1), intArrayOf(0, 2), intArrayOf(1, 2)),
    arrayOf(intArrayOf(0, 2), intArrayOf(1, 2), intArrayOf(0, 3)),
)
private val TIP_CELL = intArrayOf(0, 4, 8)
private val CENTER_CELL = intArrayOf(2, 5, 7)
private val CORNER_EDGE_CELLS = arrayOf(intArrayOf(1, 3), intArrayOf(1, 6), intArrayOf(3, 6))

class PyraFace(val verts: Array<DoubleArray>, val normal: DoubleArray)

class PyraCell(
    val face: Int,
    val index: Int,
    val verts: Array<DoubleArray>,
    val centroid: DoubleArray,
    val normal: DoubleArray,
)

class PyraPiece(val vertex: Int, val cells: IntArray, val faceColors: IntArray)

class PyraMove(
    val name: String,
    val axis: Int,
    val axisDir: DoubleArray,
    val angle: Double,
    val cells: IntArray,
    val perm: IntArray,
)

class PyraStep(
    val key: String,
    val axis: Char,
    val tip: Boolean,
    val count: Int,
    val label: String,
    val phase: String = "",
    val alg: String? = null,
)

object Pyraminx {
    val faces = ArrayList<PyraFace>()
    val cells = ArrayList<PyraCell>()
    val edges = ArrayList<PyraPiece>()
    val centers = ArrayList<PyraPiece>()
    val tips = ArrayList<PyraPiece>()
    val moves = LinkedHashMap<String, PyraMove>()
    val turnKeys: List<String>
    val mainKeys: List<String>
    val tipKeys: List<String>

    init {
        for (fi in 0 until 4) {
            val ai = FACE_VERTS[fi]
            val A = PYRA_VERTICES[ai[0]]
            val B = PYRA_VERTICES[ai[1]]
            val C = PYRA_VERTICES[ai[2]]
            var normal = vunit(vcross(vsub(B, A), vsub(C, A)))
            val centre = vmid(A, B, C)
            if (vdot(normal, vsub(centre, PYRA_CENTROID)) < 0) normal = vmul(normal, -1.0)
            faces.add(PyraFace(arrayOf(A, B, C), normal))
            for (k in 0 until 9) {
                var verts = Array(3) { p ->
                    vadd(A, vadd(vmul(vsub(B, A), LATTICE[k][p][0] / 3.0), vmul(vsub(C, A), LATTICE[k][p][1] / 3.0)))
                }
                if (vdot(vcross(vsub(verts[1], verts[0]), vsub(verts[2], verts[0])), normal) < 0) {
                    verts = arrayOf(verts[0], verts[2], verts[1])
                }
                cells.add(PyraCell(fi, k, verts, vmid(verts[0], verts[1], verts[2]), normal))
            }
        }

        val pairs = arrayOf(intArrayOf(0, 1), intArrayOf(0, 2), intArrayOf(0, 3), intArrayOf(1, 2), intArrayOf(1, 3), intArrayOf(2, 3))
        val edgeCells = Array(6) { ArrayList<Int>() }
        val centerCells = Array(4) { ArrayList<Int>() }
        val tipCells = Array(4) { ArrayList<Int>() }
        FACE_VERTS.forEachIndexed { fi, cornerIdx ->
            val base = fi * 9
            for (k in 0 until 3) {
                val v = cornerIdx[k]
                tipCells[v].add(base + TIP_CELL[k])
                centerCells[v].add(base + CENTER_CELL[k])
            }
            val triple = arrayOf(intArrayOf(0, 1, 1), intArrayOf(0, 2, 3), intArrayOf(1, 2, 6))
            for ((i, j, cell) in triple) {
                val a = cornerIdx[i]
                val b = cornerIdx[j]
                val e = pairs.indexOfFirst { (it[0] == a && it[1] == b) || (it[0] == b && it[1] == a) }
                edgeCells[e].add(base + cell)
            }
        }
        pairs.forEachIndexed { i, pair ->
            val cs = edgeCells[i].sorted().toIntArray()
            edges.add(PyraPiece(0, cs, IntArray(cs.size) { cs[it] / 9 }))
        }
        for (v in 0 until 4) {
            val cs = centerCells[v].sorted().toIntArray()
            centers.add(PyraPiece(v, cs, IntArray(cs.size) { cs[it] / 9 }))
            val ts = tipCells[v].sorted().toIntArray()
            tips.add(PyraPiece(v, ts, IntArray(ts.size) { ts[it] / 9 }))
        }

        fun rotateAbout(p: DoubleArray, axis: DoubleArray, angle: Double): DoubleArray {
            val c = cos(angle)
            val s = sin(angle)
            val d = vdot(p, axis)
            val x = vcross(axis, p)
            return doubleArrayOf(
                p[0] * c + x[0] * s + axis[0] * d * (1 - c),
                p[1] * c + x[1] * s + axis[1] * d * (1 - c),
                p[2] * c + x[2] * s + axis[2] * d * (1 - c),
            )
        }
        fun turnPoint(p: DoubleArray, axis: DoubleArray, angle: Double) =
            vadd(PYRA_CENTROID, rotateAbout(vsub(p, PYRA_CENTROID), axis, angle))

        fun makeMove(axisVertex: Int, layer: IntArray, clockwise: Boolean): PyraMove {
            val axisDir = vunit(vsub(PYRA_VERTICES[axisVertex], PYRA_CENTROID))
            val angle = if (clockwise) -2 * PI / 3 else 2 * PI / 3
            val perm = IntArray(36) { it }
            val seen = HashSet<Int>()
            for (cell in layer) {
                val p = turnPoint(cells[cell].centroid, axisDir, angle)
                var best = -1
                var bestD = Double.MAX_VALUE
                for (j in 0 until 36) {
                    val q = cells[j].centroid
                    val d = (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2])
                    if (d < bestD) {
                        bestD = d
                        best = j
                    }
                }
                require(bestD < 1e-10) { "pyraminx turn snapped badly" }
                require(layer.contains(best)) { "pyraminx turn leaves its layer" }
                require(seen.add(best)) { "pyraminx turn is not a bijection" }
                perm[cell] = best
            }
            return PyraMove(PYRA_VERTEX_NAMES[axisVertex], axisVertex, axisDir, angle, layer, perm)
        }

        for (v in 0 until 4) {
            val name = PYRA_VERTEX_NAMES[v]
            val layer = ArrayList<Int>()
            FACE_VERTS.forEachIndexed { fi, cornerIdx ->
                val k = cornerIdx.indexOf(v)
                if (k < 0) return@forEachIndexed
                val base = fi * 9
                layer.add(base + TIP_CELL[k])
                layer.add(base + CENTER_CELL[k])
                CORNER_EDGE_CELLS[k].forEach { layer.add(base + it) }
            }
            val l = layer.toIntArray()
            moves[name] = makeMove(v, l, true)
            moves["$name'"] = makeMove(v, l, false)
            moves[name.lowercase()] = makeMove(v, tips[v].cells, true)
            moves[name.lowercase() + "'"] = makeMove(v, tips[v].cells, false)
        }
        turnKeys = moves.keys.toList()
        mainKeys = PYRA_VERTEX_NAMES.flatMap { listOf(it, "$it'") }
        tipKeys = PYRA_VERTEX_NAMES.flatMap { listOf(it.lowercase(), it.lowercase() + "'") }
    }

    fun applyPerm(colors: IntArray, perm: IntArray): IntArray {
        val out = IntArray(colors.size)
        for (i in perm.indices) out[perm[i]] = colors[i]
        return out
    }

    fun isSolved(colors: IntArray): Boolean {
        for (f in 0 until 4) {
            val c = colors[f * 9]
            if (c < 0) return false
            for (k in 1 until 9) if (colors[f * 9 + k] != c) return false
        }
        return true
    }

    fun mergeMoves(keys: List<String>): List<PyraStep> {
        data class Acc(var key: String, val tip: Boolean, val axis: Char, var quarters: Int, var dir: Int, var count: Int)

        val out = ArrayList<Acc>()
        for (key in keys) {
            val isTip = key == key.lowercase()
            val dir = if (key.endsWith("'")) -1 else 1
            val prev = out.lastOrNull()
            if (!isTip && prev != null && !prev.tip && prev.axis == key[0]) {
                val quarters = (prev.quarters + dir + 4) % 4
                if (quarters == 0) {
                    out.removeAt(out.size - 1)
                } else {
                    prev.quarters = quarters
                    prev.dir = if (quarters == 3) -1 else 1
                    prev.count = if (quarters == 2) 2 else 1
                    prev.key = when (quarters) {
                        2 -> key[0].toString()
                        3 -> key[0] + "'"
                        else -> key[0].toString()
                    }
                }
            } else {
                out.add(Acc(key, isTip, key[0], if (dir == 1) 1 else 3, dir, 1))
            }
        }
        return out.map { a ->
            val label = if (a.tip) a.key.replace("'", "′")
            else a.key[0] + (if (a.count == 2) "2" else if (a.dir < 0) "′" else "")
            PyraStep(a.key, a.axis, a.tip, a.count, label)
        }
    }

    // ------------------------------------------------------------ solver

    private fun readTwist(piece: PyraPiece, colors: IntArray): Int {
        for (r in 0 until 3) {
            var ok = true
            for (i in 0 until 3) {
                if (colors[piece.cells[i]] != piece.faceColors[(i - r + 3) % 3]) {
                    ok = false
                    break
                }
            }
            if (ok) return r
        }
        return -1
    }

    private fun twistedColors(piece: PyraPiece, r: Int): IntArray {
        val cols = IntArray(36) { -1 }
        for (i in 0 until 3) cols[piece.cells[i]] = piece.faceColors[(i - r + 3) % 3]
        return cols
    }

    private val edgeMove = HashMap<String, IntArray>()
    private val centerDelta = HashMap<String, IntArray>()
    private val tipDelta = HashMap<String, IntArray>()

    init {
        for (key in turnKeys) {
            val mv = moves[key]!!
            val t = IntArray(12)
            for (s in 0 until 6) {
                val (a, b) = edges[s].cells
                val na = mv.perm[a]
                val nb = mv.perm[b]
                val s2 = edges.indexOfFirst {
                    (it.cells[0] == na && it.cells[1] == nb) || (it.cells[0] == nb && it.cells[1] == na)
                }
                require(s2 >= 0) { "turn does not map edges to edges" }
                for (o in 0 until 2) {
                    val destOfC0 = if (o == 0) na else nb
                    t[s * 2 + o] = s2 * 2 + if (destOfC0 == edges[s2].cells[0]) 0 else 1
                }
            }
            edgeMove[key] = t
            for ((table, pieces) in listOf(centerDelta to centers, tipDelta to tips)) {
                val delta = IntArray(4)
                pieces.forEachIndexed { v, piece ->
                    var d = -1
                    for (r in 0 until 3) {
                        val cols = twistedColors(piece, r)
                        val after = IntArray(36) { -1 }
                        for (c in 0 until 36) if (cols[c] >= 0) after[mv.perm[c]] = cols[c]
                        val r2 = readTwist(piece, after)
                        require(r2 >= 0) { "twist decode failed" }
                        val dd = (r2 - r + 3) % 3
                        if (d == -1) d = dd else require(d == dd) { "twist is not uniform" }
                    }
                    delta[v] = d
                }
                table[key] = delta
            }
        }
    }

    private const val KEY_COUNT = 720 * 64 * 81
    private const val STATE_COUNT = 933120
    private val dist = ByteArray(KEY_COUNT) { 255.toByte() }
    private var distReady = false
    var reachable = 0
        private set

    private fun packState(e: IntArray, c: IntArray): Int =
        (e[0] or (e[1] shl 4) or (e[2] shl 8) or (e[3] shl 12) or (e[4] shl 16) or (e[5] shl 20) or
            (c[0] shl 24) or (c[1] shl 26) or (c[2] shl 28) or (c[3] shl 30))

    private fun stateKey(st0: Int): Int {
        val st = st0
        val a = st and 15
        val b = (st ushr 4) and 15
        val c = (st ushr 8) and 15
        val d = (st ushr 12) and 15
        val e = (st ushr 16) and 15
        val f = (st ushr 20) and 15
        val pa = a shr 1
        val pb = b shr 1
        val pc = c shr 1
        val pd = d shr 1
        val pe = e shr 1
        val pf = f shr 1
        fun lt(x: Int, y: Int) = if (x < y) 1 else 0
        val rank =
            (lt(pb, pa) + lt(pc, pa) + lt(pd, pa) + lt(pe, pa) + lt(pf, pa)) * 120 +
                (lt(pc, pb) + lt(pd, pb) + lt(pe, pb) + lt(pf, pb)) * 24 +
                (lt(pd, pc) + lt(pe, pc) + lt(pf, pc)) * 6 +
                (lt(pe, pd) + lt(pf, pd)) * 2 +
                lt(pf, pe)
        val ori = (a and 1) or ((b and 1) shl 1) or ((c and 1) shl 2) or
            ((d and 1) shl 3) or ((e and 1) shl 4) or ((f and 1) shl 5)
        return (rank * 64 + ori) * 81 +
            ((st ushr 24) and 3) +
            (((st ushr 26) and 3) * 3) +
            (((st ushr 28) and 3) * 9) +
            ((st ushr 30) * 27)
    }

    private fun applyMain(st: Int, key: String): Int {
        val t = edgeMove[key]!!
        val d = centerDelta[key]!!
        val a = st and 15
        val b = (st ushr 4) and 15
        val c = (st ushr 8) and 15
        val e = (st ushr 12) and 15
        val f = (st ushr 16) and 15
        val g = (st ushr 20) and 15
        val da = t[a and 1]
        val db = t[2 + (b and 1)]
        val dc = t[4 + (c and 1)]
        val de = t[6 + (e and 1)]
        val df = t[8 + (f and 1)]
        val dg = t[10 + (g and 1)]
        return (
            (((a and 1.inv()) or (da and 1)) shl (4 * (da shr 1))) or
                (((b and 1.inv()) or (db and 1)) shl (4 * (db shr 1))) or
                (((c and 1.inv()) or (dc and 1)) shl (4 * (dc shr 1))) or
                (((e and 1.inv()) or (de and 1)) shl (4 * (de shr 1))) or
                (((f and 1.inv()) or (df and 1)) shl (4 * (df shr 1))) or
                (((g and 1.inv()) or (dg and 1)) shl (4 * (dg shr 1))) or
                ((((st ushr 24) and 3) + d[0]) % 3 shl 24) or
                ((((st ushr 26) and 3) + d[1]) % 3 shl 26) or
                ((((st ushr 28) and 3) + d[2]) % 3 shl 28) or
                ((((st ushr 30) + d[3]) % 3) shl 30)
            )
    }

    @Synchronized
    fun buildDistances() {
        if (distReady) return
        java.util.Arrays.fill(dist, 255.toByte())
        val queue = IntArray(STATE_COUNT)
        val depthOf = ByteArray(STATE_COUNT)
        var head = 0
        var tail = 0
        val solved = 0 or (0x2 shl 4) or (0x4 shl 8) or (0x6 shl 12) or (0x8 shl 16) or (0xa shl 20)
        dist[stateKey(solved)] = 0
        queue[tail++] = solved
        while (head < tail) {
            val st = queue[head]
            val depth = depthOf[head].toInt() + 1
            head++
            for (key in mainKeys) {
                val next = applyMain(st, key)
                val k = stateKey(next)
                if ((dist[k].toInt() and 0xFF) == 255) {
                    dist[k] = depth.toByte()
                    if (tail >= queue.size) error("state space larger than expected")
                    depthOf[tail] = depth.toByte()
                    queue[tail++] = next
                }
            }
        }
        reachable = tail
        distReady = true
    }

    private fun decodeState(colors: IntArray): Int? {
        val e = IntArray(6)
        for (s in 0 until 6) {
            val (a, b) = edges[s].cells
            val ca = colors[a]
            val cb = colors[b]
            if (ca < 0 || cb < 0 || ca == cb) return null
            val piece = edges.indexOfFirst {
                (it.faceColors[0] == ca && it.faceColors[1] == cb) ||
                    (it.faceColors[0] == cb && it.faceColors[1] == ca)
            }
            if (piece < 0) return null
            val homeC0 = edges[piece].faceColors[0]
            val ori = if (ca == homeC0) 0 else 1
            e[s] = piece * 2 + ori
        }
        val c = IntArray(4) { readTwist(centers[it], colors) }
        if (c.any { it < 0 }) return null
        return packState(e, c)
    }

    data class Result(val moves: List<String>? = null, val error: String? = null)

    fun solve(userColors: IntArray): Result {
        val painted = userColors.count { it >= 0 }
        if (painted != 36) return Result(error = "Paint all 36 stickers first.")
        val counts = HashMap<Int, Int>()
        for (c in userColors) counts[c] = (counts[c] ?: 0) + 1
        if (counts.size != 4) return Result(error = "A Pyraminx has exactly 4 colours.")
        if (counts.values.any { it != 9 }) return Result(error = "Each colour must appear exactly 9 times.")

        val oppositeFace = intArrayOf(3, 2, 1, 0)
        val faceColor = IntArray(4)
        for (v in 0 until 4) {
            val seen = centers[v].cells.map { userColors[it] }.toSet()
            if (seen.size != 3) return Result(error = "Those stickers cannot be a real Pyraminx.")
            val missing = counts.keys.filter { it !in seen }
            if (missing.size != 1) return Result(error = "Those stickers cannot be a real Pyraminx.")
            faceColor[oppositeFace[v]] = missing[0]
        }
        if (faceColor.toSet().size != 4) return Result(error = "Those stickers cannot be a real Pyraminx.")

        val canon = IntArray(36) { faceColor.indexOf(userColors[it]) }
        val start = decodeState(canon) ?: return Result(error = "Those stickers cannot be a real Pyraminx.")
        buildDistances()
        if ((dist[stateKey(start)].toInt() and 0xFF) == 255) {
            return Result(error = "Those stickers cannot be a real Pyraminx.")
        }

        val movesOut = ArrayList<String>()
        var st = start
        while ((dist[stateKey(st)].toInt() and 0xFF) > 0) {
            val depth = dist[stateKey(st)].toInt() and 0xFF
            var stepped = false
            for (key in mainKeys) {
                val next = applyMain(st, key)
                if ((dist[stateKey(next)].toInt() and 0xFF) == depth - 1) {
                    movesOut.add(key)
                    st = next
                    stepped = true
                    break
                }
            }
            if (!stepped) return Result(error = "Solver got stuck (please report this).")
        }

        var board = canon.copyOf()
        for (key in movesOut) board = applyPerm(board, moves[key]!!.perm)
        for (v in 0 until 4) {
            val twist = readTwist(tips[v], board)
            if (twist == 0) continue
            val key = tipKeys.firstOrNull { k ->
                moves[k]!!.axis == v && (tipDelta[k]!![v] + twist) % 3 == 0
            } ?: return Result(error = "Solver got stuck (please report this).")
            movesOut.add(key)
            board = applyPerm(board, moves[key]!!.perm)
        }
        return Result(moves = movesOut)
    }

    fun solvedColors(): IntArray = IntArray(36) { cells[it].face }
}
