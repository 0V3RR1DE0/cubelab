package cube.lab.nativeapp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Generic N x N cube: facelets, layer turns and cubie decoding.
// Face order U D F B R L; facelet = face*n*n + row*n + col.

const val EPS = 1e-6

val FACE_KEYS = arrayOf("U", "D", "F", "B", "R", "L")
val HOME_COLORS = intArrayOf(4, 0, 1, 3, 2, 5) // U D F B R L

class FaceDef(
    val n: DoubleArray,
    val u: DoubleArray,
    val v: DoubleArray,
)

val FACE_DEFS = arrayOf(
    FaceDef(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0)),
    FaceDef(doubleArrayOf(0.0, -1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0)),
    FaceDef(doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0)),
    FaceDef(doubleArrayOf(0.0, 0.0, -1.0), doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0)),
    FaceDef(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0), doubleArrayOf(0.0, 1.0, 0.0)),
    FaceDef(doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 1.0, 0.0)),
)

fun faceIndexOf(key: Char): Int = FACE_KEYS.indexOf(key.toString())

fun dot(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

class Facelet(
    val face: Int,
    val row: Int,
    val col: Int,
    val pos: DoubleArray,
    val normal: DoubleArray,
)

class MoveSpec(val face: Int, val layers: IntArray, val turns: Int, val rotation: Boolean = false)

fun parseMove(name: String): MoveSpec? {
    // Whole-cube rotations, used by human method algorithms. They are applied
    // as a turn of every layer about the matching axis.
    val rot = Regex("^([xyz])(2|')?$").matchEntire(name)
    if (rot != null) {
        val turns = when (rot.groupValues[2]) {
            "2" -> 2
            "'" -> 3
            else -> 1
        }
        val face = when (rot.groupValues[1]) {
            "x" -> faceIndexOf('R')
            "y" -> faceIndexOf('U')
            else -> faceIndexOf('F')
        }
        return MoveSpec(face, IntArray(0), turns, rotation = true)
    }
    // Range notation: "2-3R" = layers 1..2, "2-4Rw" is accepted too.
    val range = Regex("^(\\d+)-(\\d+)([UDFBRL])(w?)(2|')?$").matchEntire(name)
    if (range != null) {
        val a = range.groupValues[1].toIntOrNull() ?: return null
        val b = range.groupValues[2].toIntOrNull() ?: return null
        if (a < 1 || b < a) return null
        val key = range.groupValues[3]
        val suffix = range.groupValues[5]
        val turns = when (suffix) {
            "2" -> 2
            "'" -> 3
            else -> 1
        }
        val layers = IntArray(b - a + 1) { a - 1 + it }
        return MoveSpec(faceIndexOf(key[0]), layers, turns)
    }
    val m = Regex("^(\\d*)([UDFBRL])(w?)(2|')?$").matchEntire(name) ?: return null
    val prefix = m.groupValues[1]
    val key = m.groupValues[2]
    val wide = m.groupValues[3] == "w"
    val suffix = m.groupValues[4]
    val turns = when (suffix) {
        "2" -> 2
        "'" -> 3
        else -> 1
    }
    val layers: IntArray = if (wide) {
        val count = if (prefix.isEmpty()) 2 else prefix.toInt()
        IntArray(count) { it }
    } else {
        if (prefix.isEmpty()) intArrayOf(0) else intArrayOf(prefix.toInt() - 1)
    }
    return MoveSpec(faceIndexOf(key[0]), layers, turns)
}

fun spellMove(face: Int, layers: IntArray, turns: Int): String {
    val key = FACE_KEYS[face]
    val sorted = layers.sorted()
    val stem = when {
        sorted.size == 1 && sorted[0] == 0 -> key
        sorted.size == 1 -> "${sorted[0] + 1}$key"
        sorted.first() == 0 && sorted.size == 2 -> "${key}w"
        sorted.first() == 0 -> "${sorted.size}${key}w"
        else -> {
            require(sorted.last() - sorted.first() + 1 == sorted.size) {
                "non-contiguous layers ${sorted.toList()}"
            }
            "${sorted.first() + 1}-${sorted.last() + 1}$key"
        }
    }
    return stem + when (turns) {
        2 -> "2"
        3 -> "'"
        else -> ""
    }
}

private fun rotateVec(v: DoubleArray, axis: DoubleArray, angle: Double): DoubleArray {
    val c = cos(angle)
    val s = sin(angle)
    val d = dot(v, axis)
    val cx = doubleArrayOf(
        axis[1] * v[2] - axis[2] * v[1],
        axis[2] * v[0] - axis[0] * v[2],
        axis[0] * v[1] - axis[1] * v[0],
    )
    return doubleArrayOf(
        v[0] * c + cx[0] * s + axis[0] * d * (1 - c),
        v[1] * c + cx[1] * s + axis[1] * d * (1 - c),
        v[2] * c + cx[2] * s + axis[2] * d * (1 - c),
    )
}

class MoveInfo(
    val name: String,
    val face: Int,
    val layers: IntArray,
    val turns: Int,
    val axis: DoubleArray,
    val angle: Double,
    val cells: IntArray,
    val perm: IntArray,
)

class Cube(val n: Int) {
    val facelets: List<Facelet> = buildList {
        for (f in 0 until 6) {
            val d = FACE_DEFS[f]
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val cu = c + 0.5 - n / 2.0
                    val cv = n / 2.0 - r - 0.5
                    add(
                        Facelet(
                            f, r, c,
                            doubleArrayOf(
                                d.n[0] * (n / 2.0) + d.u[0] * cu + d.v[0] * cv,
                                d.n[1] * (n / 2.0) + d.u[1] * cu + d.v[1] * cv,
                                d.n[2] * (n / 2.0) + d.u[2] * cu + d.v[2] * cv,
                            ),
                            d.n,
                        ),
                    )
                }
            }
        }
    }

    private val moveCache = HashMap<String, MoveInfo>()

    fun move(name: String): MoveInfo {
        moveCache[name]?.let { return it }
        val spec = parseMove(name) ?: error("bad move $name")
        val f = spec.face
        val axis = FACE_DEFS[f].n
        if (spec.rotation) {
            val angle = -spec.turns * Math.PI / 2.0
            val perm = IntArray(facelets.size) { it }
            val all = IntArray(facelets.size) { it }
            for (i in all) {
                val p = rotateVec(facelets[i].pos, axis, angle)
                val nrm = rotateVec(facelets[i].normal, axis, angle)
                var best = -1
                var bestD = Double.MAX_VALUE
                facelets.forEachIndexed { j, q ->
                    val d = sq(p, q.pos) + sq(nrm, q.normal)
                    if (d < bestD) { bestD = d; best = j }
                }
                require(bestD < 1e-8) { "$name does not snap on ${n}x$n" }
                perm[i] = best
            }
            val seen = HashSet<Int>()
            for (i in all) require(seen.add(perm[i])) { "$name is not a bijection" }
            val info = MoveInfo(
                name, f, IntArray(n) { it }, spec.turns,
                axis, angle, all, perm,
            )
            moveCache[name] = info
            return info
        }
        val wanted = spec.layers.map { n / 2.0 - 0.5 - it }
        val cells = ArrayList<Int>()
        facelets.forEachIndexed { i, fl ->
            val along = dot(fl.pos, axis)
            val normalDot = dot(fl.normal, axis)
            if (normalDot == 1.0) {
                if (spec.layers.contains(0)) cells.add(i)
            } else if (normalDot == -1.0) {
                // The far face plane belongs to the last layer, e.g. `4R`
                // on a 4x4 is L', not a turn of the L stickers alone.
                if (spec.layers.contains(n - 1)) cells.add(i)
            } else if (wanted.any { abs(along - it) < EPS }) {
                cells.add(i)
            }
        }
        val angle = -spec.turns * Math.PI / 2.0
        val perm = IntArray(facelets.size) { it }
        for (i in cells) {
            val p = rotateVec(facelets[i].pos, axis, angle)
            val nrm = rotateVec(facelets[i].normal, axis, angle)
            var best = -1
            var bestD = Double.MAX_VALUE
            facelets.forEachIndexed { j, q ->
                val d = sq(p, q.pos) + sq(nrm, q.normal)
                if (d < bestD) {
                    bestD = d
                    best = j
                }
            }
            require(bestD < 1e-8) { "$name does not snap on ${n}x$n" }
            perm[i] = best
        }
        val seen = HashSet<Int>()
        for (i in cells) require(seen.add(perm[i])) { "$name is not a bijection" }
        val info = MoveInfo(
            spellMove(f, spec.layers, spec.turns), f, spec.layers, spec.turns,
            axis, angle, cells.toIntArray(), perm,
        )
        moveCache[name] = info
        return info
    }

    fun solved(): IntArray = IntArray(6 * n * n) { HOME_COLORS[it / (n * n)] }

    fun isSolved(colors: IntArray): Boolean {
        for (f in 0 until 6) {
            val base = f * n * n
            val c = colors[base]
            if (c < 0) return false
            for (k in 1 until n * n) if (colors[base + k] != c) return false
        }
        return true
    }

    fun apply(colors: IntArray, info: MoveInfo): IntArray {
        val out = IntArray(colors.size)
        for (i in info.perm.indices) out[info.perm[i]] = colors[i]
        return out
    }

    fun apply(colors: IntArray, names: List<String>): IntArray {
        var out = colors
        for (name in names) out = apply(out, move(name))
        return out
    }
}

private fun sq(a: DoubleArray, b: DoubleArray): Double =
    (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])

class Step(
    val label: String,
    val turns: Int,
    val info: MoveInfo,
    val phase: String = "",
    val alg: String? = null,
)

/** Cancels/merges turns that are separated only by commuting moves. */
fun simplifyMoves(cube: Cube, names: List<String>): List<String> {
    if (names.size < 2) return names
    val out = names.toMutableList()
    val commute = HashMap<String, Boolean>()
    fun commutes(a: String, b: String): Boolean {
        val key = if (a <= b) "$a|$b" else "$b|$a"
        commute[key]?.let { return it }
        val pa = cube.move(a).perm
        val pb = cube.move(b).perm
        var ok = true
        for (i in pa.indices) {
            if (pa[pb[i]] != pb[pa[i]]) { ok = false; break }
        }
        commute[key] = ok
        return ok
    }
    var changed = true
    while (changed) {
        changed = false
        var i = 0
        while (i < out.size - 1) {
            val a = cube.move(out[i])
            var j = i + 1
            var merged = false
            while (j < out.size) {
                val b = cube.move(out[j])
                if (a.face == b.face && a.layers.contentEquals(b.layers)) {
                    val turns = (a.turns + b.turns) % 4
                    if (turns == 0) {
                        out.removeAt(j)
                        out.removeAt(i)
                    } else {
                        out[i] = spellMove(a.face, a.layers, turns)
                        out.removeAt(j)
                    }
                    changed = true
                    merged = true
                    break
                }
                if (!commutes(out[i], out[j])) break
                j++
            }
            if (!merged) i++
        }
    }
    return out
}

fun compressMoves(cube: Cube, names: List<String>): List<Step> {
    data class Acc(var info: MoveInfo, var turns: Int)

    val acc = ArrayList<Acc>()
    for (name in names) {
        val info = cube.move(name)
        val last = acc.lastOrNull()
        if (last != null && last.info.face == info.face) {
            if (last.info.layers.contentEquals(info.layers)) {
                last.turns = (last.turns + info.turns) % 4
                if (last.turns == 0) acc.removeAt(acc.size - 1)
                continue
            }
            // Two adjacent, disjoint layers turning the same way become one
            // wide/range turn (Rw, 3Rw, 2-3R, ...) instead of two animations.
            if (last.turns == info.turns) {
                val disjoint = last.info.layers.none { info.layers.contains(it) }
                val union = (last.info.layers.toList() + info.layers.toList()).distinct().sorted()
                val contiguous = union.isNotEmpty() && union.last() - union.first() + 1 == union.size
                if (disjoint && contiguous) {
                    val label = spellMove(info.face, union.toIntArray(), info.turns)
                    last.info = cube.move(label)
                    last.turns = info.turns
                    continue
                }
            }
        }
        acc.add(Acc(info, info.turns))
    }
    return acc.map { a ->
        val label = spellMove(a.info.face, a.info.layers, a.turns)
        Step(label, a.turns, cube.move(label))
    }
}

// ---------------------------------------------------------------- cubies

val CORNER_POS = arrayOf(
    intArrayOf(1, 1, 1), intArrayOf(-1, 1, 1), intArrayOf(-1, 1, -1), intArrayOf(1, 1, -1),
    intArrayOf(1, -1, 1), intArrayOf(-1, -1, 1), intArrayOf(-1, -1, -1), intArrayOf(1, -1, -1),
)
val EDGE_POS = arrayOf(
    intArrayOf(1, 1, 0), intArrayOf(0, 1, 1), intArrayOf(-1, 1, 0), intArrayOf(0, 1, -1),
    intArrayOf(1, -1, 0), intArrayOf(0, -1, 1), intArrayOf(-1, -1, 0), intArrayOf(0, -1, -1),
    intArrayOf(1, 0, 1), intArrayOf(-1, 0, 1), intArrayOf(-1, 0, -1), intArrayOf(1, 0, -1),
)

private val EDGE_REF = intArrayOf(0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0)

private fun faceOfAxis(axis: Int, sign: Int): Int = when (axis) {
    0 -> if (sign > 0) 4 else 5
    1 -> if (sign > 0) 0 else 1
    else -> if (sign > 0) 2 else 3
}

class CubieInfo(val faces: IntArray, val facelets: IntArray)
class CubieMap(val corners: List<CubieInfo>, val edges: List<CubieInfo>)

fun buildCubieMap(cube: Cube): CubieMap {
    val g = cube.facelets
    fun nearest(point: DoubleArray, faces: IntArray): Int {
        var best = -1
        var bestD = Double.MAX_VALUE
        g.forEachIndexed { i, fl ->
            if (!faces.contains(fl.face)) return@forEachIndexed
            val d = sq(point, fl.pos)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }
    val n = cube.n
    val corners = CORNER_POS.map { pos ->
        val outer = DoubleArray(3) { pos[it] * n / 2.0 }
        val faces = IntArray(3) { faceOfAxis(it, pos[it]) }
        CubieInfo(faces, IntArray(3) { nearest(outer, intArrayOf(faces[it])) })
    }
    val edges = EDGE_POS.map { pos ->
        val outer = DoubleArray(3) { pos[it] * n / 2.0 }
        val faces = pos.indices.filter { pos[it] != 0 }.map { faceOfAxis(it, pos[it]) }.toIntArray()
        CubieInfo(faces, IntArray(faces.size) { nearest(outer, intArrayOf(faces[it])) })
    }
    return CubieMap(corners, edges)
}

private fun key(faces: IntArray): String {
    val sorted = faces.sorted()
    return sorted.joinToString(",")
}

class CubieState(
    val cp: IntArray,
    val co: IntArray,
    val ep: IntArray,
    val eo: IntArray,
)

private val cornerOrderCache = HashMap<String, IntArray>()

fun cornerOrder(cube: Cube, map: CubieMap, position: Int): IntArray {
    val k = "${cube.n}:$position"
    cornerOrderCache[k]?.let { return it }
    val faces = map.corners[position].faces
    val a = FACE_DEFS[faces[0]].n
    val b = FACE_DEFS[faces[1]].n
    val c = FACE_DEFS[faces[2]].n
    val triple = a[0] * (b[1] * c[2] - b[2] * c[1]) +
        a[1] * (b[2] * c[0] - b[0] * c[2]) +
        a[2] * (b[0] * c[1] - b[1] * c[0])
    var order = if (triple > 0) intArrayOf(faces[0], faces[2], faces[1]) else faces.copyOf()
    val ud = order.indexOfFirst { it == 0 || it == 1 }
    order = IntArray(3) { order[(ud + it) % 3] }
    cornerOrderCache[k] = order
    return order
}

private fun isUpDown(f: Int) = f == 0 || f == 1

fun decodeCubies(colors: IntArray, cube: Cube, scheme: IntArray): CubieState? {
    val n = cube.n
    if (n != 2 && n != 3) return null
    val colourToFace = IntArray(6) { -1 }
    val seen = HashSet<Int>()
    for (f in 0 until 6) {
        val c = scheme[f]
        if (c < 0 || c > 5 || !seen.add(c)) return null
        colourToFace[c] = f
    }
    if (colors.any { it < 0 || colourToFace[it] < 0 }) return null

    val map = buildCubieMap(cube)
    val cornerLookup = map.corners.mapIndexed { i, c -> key(c.faces) to i }.toMap()
    val edgeLookup = map.edges.mapIndexed { i, e -> key(e.faces) to i }.toMap()

    val cp = IntArray(8)
    val co = IntArray(8)
    val ep = IntArray(12)
    val eo = IntArray(12)

    for (p in 0 until 8) {
        val c = map.corners[p]
        val stickers = c.faces.mapIndexed { k, face -> face to colors[c.facelets[k]] }
        val homeFaces = stickers.map { colourToFace[it.second] }
        if (homeFaces.toSet().size != 3) return null
        val piece = cornerLookup[key(homeFaces.toIntArray())] ?: return null
        cp[p] = piece
        val up = stickers.firstOrNull { isUpDown(colourToFace[it.second]) } ?: return null
        co[p] = cornerOrder(cube, map, p).indexOf(up.first).also { if (it < 0) return null }
    }

    if (n == 3) {
        for (p in 0 until 12) {
            val e = map.edges[p]
            val stickers = e.faces.mapIndexed { k, face -> face to colors[e.facelets[k]] }
            val homeFaces = stickers.map { colourToFace[it.second] }
            if (homeFaces[0] == homeFaces[1]) return null
            val piece = edgeLookup[key(homeFaces.toIntArray())] ?: return null
            ep[p] = piece
            val pieceRef = map.edges[piece].faces[EDGE_REF[piece]]
            val ref = stickers.firstOrNull { colourToFace[it.second] == pieceRef } ?: return null
            eo[p] = if (ref.first == e.faces[EDGE_REF[p]]) 0 else 1
        }
        if (ep.toSet().size != 12) return null
    }
    if (cp.toSet().size != 8) return null
    return CubieState(cp, co, ep, eo)
}

fun composeStates(a: CubieState, b: CubieState): CubieState {
    val cp = IntArray(8)
    val co = IntArray(8)
    for (i in 0 until 8) {
        cp[i] = a.cp[b.cp[i]]
        co[i] = (a.co[b.cp[i]] + b.co[i]) % 3
    }
    val ep = IntArray(12)
    val eo = IntArray(12)
    for (i in 0 until 12) {
        ep[i] = a.ep[b.ep[i]]
        eo[i] = (a.eo[b.ep[i]] + b.eo[i]) % 2
    }
    return CubieState(cp, co, ep, eo)
}

fun invertCorners(c: CubieState): CubieState {
    val cp = IntArray(8)
    for (i in 0 until 8) cp[c.cp[i]] = i
    val co = IntArray(8)
    for (i in 0 until 8) co[i] = (3 - c.co[cp[i]]) % 3
    return CubieState(cp, co, IntArray(0), IntArray(0))
}

fun invertEdges(e: CubieState): CubieState {
    val ep = IntArray(12)
    for (i in 0 until 12) ep[e.ep[i]] = i
    val eo = IntArray(12)
    for (i in 0 until 12) eo[i] = (2 - e.eo[ep[i]]) % 2
    return CubieState(IntArray(0), IntArray(0), ep, eo)
}

fun permParity(list: IntArray): Int {
    var p = 0
    for (i in list.indices) for (j in i + 1 until list.size) if (list[j] < list[i]) p = p xor 1
    return p
}

// The 24 whole-cube rotations as colour schemes.
val ROTATION_SCHEMES: List<IntArray> = run {
    fun axisOf(normal: DoubleArray): Pair<Int, Int> {
        val axis = normal.indexOfFirst { it != 0.0 }
        return axis to if (normal[axis] > 0) 1 else -1
    }
    fun faceForNormal(normal: DoubleArray): Int {
        val (axis, sign) = axisOf(normal)
        for (f in 0 until 6) {
            val (fa, fs) = axisOf(FACE_DEFS[f].n)
            if (fa == axis && fs == sign) return f
        }
        return -1
    }
    val perms = listOf(
        intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2),
        intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0),
    )
    val out = ArrayList<IntArray>()
    for (p in perms) {
        for (mask in 0 until 8) {
            val signs = intArrayOf(
                if (mask and 1 != 0) -1 else 1,
                if (mask and 2 != 0) -1 else 1,
                if (mask and 4 != 0) -1 else 1,
            )
            fun col(v: DoubleArray): DoubleArray {
                val r = DoubleArray(3)
                for (i in 0 until 3) r[p[i]] += signs[i] * v[i]
                return r
            }
            val a = col(doubleArrayOf(1.0, 0.0, 0.0))
            val b = col(doubleArrayOf(0.0, 1.0, 0.0))
            val c = col(doubleArrayOf(0.0, 0.0, 1.0))
            val det = a[0] * (b[1] * c[2] - b[2] * c[1]) -
                a[1] * (b[0] * c[2] - b[2] * c[0]) +
                a[2] * (b[0] * c[1] - b[1] * c[0])
            if (det < 0) continue
            val scheme = IntArray(6)
            for (f in 0 until 6) {
                val target = faceForNormal(col(FACE_DEFS[f].n))
                scheme[target] = HOME_COLORS[f]
            }
            if (out.none { it.contentEquals(scheme) }) out.add(scheme)
        }
    }
    out
}

fun infer2x2Scheme(cube: Cube, colors: IntArray): IntArray? {
    for (scheme in ROTATION_SCHEMES) {
        if (decodeCubies(colors, cube, scheme) != null) return scheme
    }
    return null
}

fun centreScheme(colors: IntArray): IntArray? {
    val scheme = IntArray(6) { colors[it * 9 + 4] }
    if (scheme.any { it < 0 } || scheme.toSet().size != 6) return null
    return scheme
}

fun recolourToScheme(colors: IntArray, scheme: IntArray): IntArray {
    val homeFaceOf = IntArray(6)
    HOME_COLORS.forEachIndexed { f, c -> homeFaceOf[c] = f }
    return IntArray(colors.size) { i ->
        val c = colors[i]
        if (c < 0) -1 else scheme[homeFaceOf[c]]
    }
}

fun randomScramble(cube: Cube, count: Int): Pair<IntArray, List<String>> {
    val keys = FACE_KEYS
    var board = cube.solved()
    val names = ArrayList<String>()
    var prev = ""
    for (i in 0 until count) {
        var face: String
        do {
            face = keys[(Math.random() * 6).toInt()]
        } while (face == prev)
        prev = face
        val suffix = arrayOf("", "2", "'")[(Math.random() * 3).toInt()]
        val name = face + suffix
        names.add(name)
        board = cube.apply(board, cube.move(name))
    }
    return board to names
}
