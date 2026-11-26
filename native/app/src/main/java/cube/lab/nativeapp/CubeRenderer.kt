package cube.lab.nativeapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TurnAnim(
    val info: MoveInfo,
    val angle: Double,
)

private val PALETTE = intArrayOf(
    0xFFF5C518.toInt(),
    0xFF37C46D.toInt(),
    0xFFE5555B.toInt(),
    0xFF4C8DFF.toInt(),
    0xFFF2F5FA.toInt(),
    0xFFFF9F43.toInt(),
)
private const val EMPTY = 0xFF3A4252.toInt()
private const val DARK = 0xFF2A3446.toInt()

private val SHADE = floatArrayOf(1f, 0.8f, 0.94f, 0.78f, 0.88f, 0.84f)

fun paletteColor(index: Int): Color = Color(PALETTE[index])

fun shaded(index: Int, face: Int): Color {
    val c = Color(PALETTE[index])
    val s = SHADE[face]
    return Color(c.red * s, c.green * s, c.blue * s, 1f)
}

class Quad(val pts: Array<DoubleArray>, val color: Int)

class CubeRenderer(val n: Int) {
    val cube = Cube(n)

    private val scale = 1.0 / n

    // sticker quads at rest, in model units
    private val stickerQuads: Array<Array<DoubleArray>> = Array(6 * n * n) { i ->
        val f = i / (n * n)
        val rem = i % (n * n)
        val r = rem / n
        val c = rem % n
        quadFor(f, r, c)
    }

    private val bodyFaces: Array<Array<DoubleArray>> = Array(6) { f ->
        val def = FACE_DEFS[f]
        val pts = arrayOf(
            doubleArrayOf(-1.0, -1.0), doubleArrayOf(1.0, -1.0),
            doubleArrayOf(1.0, 1.0), doubleArrayOf(-1.0, 1.0),
        ).map { (du, dv) ->
            doubleArrayOf(
                def.n[0] * 0.5 + def.u[0] * 0.5 * du + def.v[0] * 0.5 * dv,
                def.n[1] * 0.5 + def.u[1] * 0.5 * du + def.v[1] * 0.5 * dv,
                def.n[2] * 0.5 + def.u[2] * 0.5 * du + def.v[2] * 0.5 * dv,
            )
        }.toTypedArray()
        pts
    }

    private val cubieCenters: Array<DoubleArray> = buildList {
        for (x in 0 until n) for (y in 0 until n) for (z in 0 until n) {
            add(doubleArrayOf((x + 0.5) / n - 0.5, (y + 0.5) / n - 0.5, (z + 0.5) / n - 0.5))
        }
    }.toTypedArray()

    private fun quadFor(f: Int, r: Int, c: Int): Array<DoubleArray> {
        val def = FACE_DEFS[f]
        val half = 0.86 / 2.0
        val cu = c + 0.5 - n / 2.0
        val cv = n / 2.0 - r - 0.5
        fun p(du: Double, dv: Double): DoubleArray = doubleArrayOf(
            (def.n[0] * (n / 2.0) + def.u[0] * (cu + du) + def.v[0] * (cv + dv)) * scale +
                def.n[0] * 0.0025,
            (def.n[1] * (n / 2.0) + def.u[1] * (cu + du) + def.v[1] * (cv + dv)) * scale +
                def.n[1] * 0.0025,
            (def.n[2] * (n / 2.0) + def.u[2] * (cu + du) + def.v[2] * (cv + dv)) * scale +
                def.n[2] * 0.0025,
        )
        return arrayOf(p(-half, -half), p(half, -half), p(half, half), p(-half, half))
    }

    private fun cubieBox(center: DoubleArray): Array<Array<DoubleArray>> {
        val half = (0.5 / n) * 0.985
        return Array(6) { f ->
            val def = CUBIE_FACES[f]
            arrayOf(
                doubleArrayOf(-1.0, -1.0), doubleArrayOf(1.0, -1.0),
                doubleArrayOf(1.0, 1.0), doubleArrayOf(-1.0, 1.0),
            ).map { (du, dv) ->
                doubleArrayOf(
                    center[0] + def.n[0] * half + def.u[0] * half * du + def.v[0] * half * dv,
                    center[1] + def.n[1] * half + def.u[1] * half * du + def.v[1] * half * dv,
                    center[2] + def.n[2] * half + def.u[2] * half * du + def.v[2] * half * dv,
                )
            }.toTypedArray()
        }
    }

    private val CUBIE_FACES = arrayOf(
        FaceDef(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0), doubleArrayOf(0.0, 1.0, 0.0)),
        FaceDef(doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 1.0, 0.0)),
        FaceDef(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0)),
        FaceDef(doubleArrayOf(0.0, -1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0)),
        FaceDef(doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0)),
        FaceDef(doubleArrayOf(0.0, 0.0, -1.0), doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0)),
    )

    private fun project(p: DoubleArray, yaw: Double, pitch: Double, cx: Float, cy: Float, s: Float): Tri {
        val cyaw = cos(yaw)
        val syaw = sin(yaw)
        val x1 = p[0] * cyaw + p[2] * syaw
        val z1 = -p[0] * syaw + p[2] * cyaw
        val cp = cos(pitch)
        val sp = sin(pitch)
        val y2 = p[1] * cp - z1 * sp
        val z2 = p[1] * sp + z1 * cp
        return Tri(cx + (x1 * s).toFloat(), cy - (y2 * s).toFloat(), z2)
    }

    private class Tri(val x: Float, val y: Float, val z: Double)

    private fun rotated(p: DoubleArray, axis: DoubleArray, angle: Double): DoubleArray {
        val c = cos(angle)
        val si = sin(angle)
        val d = dot(p, axis)
        val cx = doubleArrayOf(
            axis[1] * p[2] - axis[2] * p[1],
            axis[2] * p[0] - axis[0] * p[2],
            axis[0] * p[1] - axis[1] * p[0],
        )
        return doubleArrayOf(
            p[0] * c + cx[0] * si + axis[0] * d * (1 - c),
            p[1] * c + cx[1] * si + axis[1] * d * (1 - c),
            p[2] * c + cx[2] * si + axis[2] * d * (1 - c),
        )
    }

    private fun rotNormal(nv: DoubleArray, axis: DoubleArray, angle: Double): DoubleArray =
        rotated(nv, axis, angle)

    private fun visibleNormal(normal: DoubleArray, yaw: Double, pitch: Double): Boolean {
        val cyaw = cos(yaw)
        val syaw = sin(yaw)
        val x1 = normal[0] * cyaw + normal[2] * syaw
        val z1 = -normal[0] * syaw + normal[2] * cyaw
        val cp = cos(pitch)
        val sp = sin(pitch)
        val z2 = normal[1] * sp + z1 * cp
        return z2 > 0.0
    }

    private class Bucket {
        val path = Path()
        var empty = true
        fun add(points: List<Tri>) {
            if (points.isEmpty()) return
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
            path.close()
            empty = false
        }
    }

    fun draw(
        scope: DrawScope,
        colors: IntArray,
        yaw: Double,
        pitch: Double,
        anim: TurnAnim?,
        highlight: Set<Int> = emptySet(),
        zoom: Float = 1f,
    ) {
        val w = scope.size.width
        val h = scope.size.height
        val cx = w / 2f
        val cy = h / 2f
        val s = (minOf(w, h) * 0.47f * zoom)
        val stickers = HashMap<Int, Bucket>()
        val highlights = Path()

        fun bucket(map: HashMap<Int, Bucket>, color: Int): Bucket =
            map.getOrPut(color) { Bucket() }

        fun addQuad(map: HashMap<Int, Bucket>, pts: Array<DoubleArray>, color: Int, yaw0: Double = yaw, pitch0: Double = pitch) {
            val projected = pts.map { project(it, yaw0, pitch0, cx, cy, s) }
            bucket(map, color).add(projected)
        }

        val movingCells = anim?.info?.cells?.toHashSet() ?: emptySet<Int>()
        val movingCubies = if (anim != null) {
            val axis = anim.info.axis
            val sign = when {
                axis[0] != 0.0 -> axis[0]
                axis[1] != 0.0 -> axis[1]
                else -> axis[2]
            }
            cubieCenters.mapIndexedNotNull { i, c ->
                val along = dot(c, axis)
                val inside = anim.info.layers.any { k ->
                    abs(along - sign * (n / 2.0 - 0.5 - k) / n) < 0.3 / n
                }
                if (inside) i else null
            }
        } else emptyList()

        // transparent: only stickers, no inner box
        for (i in 0 until 6 * n * n) {
            if (i in movingCells) continue
            val f = i / (n * n)
            val value = colors.getOrElse(i) { -1 }
            val col = if (value < 0) EMPTY else PALETTE[value]
            val normal = FACE_DEFS[f].n
            if (!visibleNormal(normal, yaw, pitch)) continue
            val pts = stickerQuads[i].map { project(it, yaw, pitch, cx, cy, s) }
            bucket(stickers, blend(col, SHADE[f])).add(pts)
            if (i in highlight) {
                highlights.moveTo(pts[0].x, pts[0].y)
                for (k in 1 until pts.size) highlights.lineTo(pts[k].x, pts[k].y)
                highlights.close()
            }
        }

        fun drawMoving() {
            if (anim == null) return
            val angle = anim.angle
            val axis = anim.info.axis
            val ms = HashMap<Int, Bucket>()
            for (i in movingCells) {
                val f = i / (n * n)
                val value = colors.getOrElse(i) { -1 }
                val col = if (value < 0) EMPTY else PALETTE[value]
                val nrm = rotNormal(FACE_DEFS[f].n, axis, angle)
                if (!visibleNormal(nrm, yaw, pitch)) continue
                addQuad(ms, stickerQuads[i].map { rotated(it, axis, angle) }.toTypedArray(), blend(col, SHADE[f]))
            }
            for ((c, b) in ms) if (!b.empty) scope.drawPath(b.path, Color(c))
        }

        val moveBehind = if (anim != null && movingCubies.isNotEmpty()) {
            val avg = movingCubies.sumOf { project(cubieCenters[it], yaw, pitch, cx, cy, s).z } / movingCubies.size
            avg < 0
        } else false

        if (moveBehind) drawMoving()
        for ((c, b) in stickers) if (!b.empty) scope.drawPath(b.path, Color(c))
        if (!moveBehind) drawMoving()
        if (!highlights.isEmpty) {
            scope.drawPath(
                highlights,
                Color(0xFFFF6B6B),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.022f),
            )
        }
        if (anim != null) drawArrow(scope, anim, yaw, pitch, cx, cy, s)
    }

    private fun drawArrow(
        scope: DrawScope,
        anim: TurnAnim,
        yaw: Double,
        pitch: Double,
        cx: Float,
        cy: Float,
        s: Float,
    ) {
        val info = anim.info
        val def = FACE_DEFS[info.face]
        if (!visibleNormal(def.n, yaw, pitch)) return
        val r = 0.30
        val center = doubleArrayOf(def.n[0] * 0.505, def.n[1] * 0.505, def.n[2] * 0.505)
        val clockwise = anim.angle < 0
        val steps = 18
        val points = ArrayList<Tri>()
        for (i in 0..steps) {
            val t = i / steps.toDouble()
            val a = if (clockwise) 1.15 - 2.3 * t else -1.15 + 2.3 * t
            val p = doubleArrayOf(
                center[0] + def.u[0] * kotlin.math.cos(a) * r + def.v[0] * kotlin.math.sin(a) * r,
                center[1] + def.u[1] * kotlin.math.cos(a) * r + def.v[1] * kotlin.math.sin(a) * r,
                center[2] + def.u[2] * kotlin.math.cos(a) * r + def.v[2] * kotlin.math.sin(a) * r,
            )
            points.add(project(p, yaw, pitch, cx, cy, s))
        }
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        scope.drawPath(path, Color(0xCC081018), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.035f))
        scope.drawPath(path, Color(0xFF8BC4FF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.018f))
        val a = points[points.size - 2]
        val b = points[points.size - 1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = kotlin.math.hypot(dx, dy).takeIf { it > 0.0001f } ?: return
        val ux = dx / len
        val uy = dy / len
        val size = s * 0.045f
        val head = Path()
        head.moveTo(b.x + ux * size, b.y + uy * size)
        head.lineTo(b.x - uy * size * 0.9f, b.y + ux * size * 0.9f)
        head.lineTo(b.x + uy * size * 0.9f, b.y - ux * size * 0.9f)
        head.close()
        scope.drawPath(head, Color(0xFF8BC4FF))
    }
}

private fun blend(argb: Int, shade: Float): Int {
    val r = ((argb shr 16) and 0xFF) * shade
    val g = ((argb shr 8) and 0xFF) * shade
    val b = (argb and 0xFF) * shade
    return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}
