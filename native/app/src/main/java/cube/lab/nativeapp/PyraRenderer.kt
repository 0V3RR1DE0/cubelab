package cube.lab.nativeapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class PyraAnim(val move: PyraMove, val angle: Double)

private const val MAIN_SCALE = 0.34f

class PyraRenderer {
    private val model = Pyraminx

    private fun project(
        p: DoubleArray,
        yaw: Double,
        pitch: Double,
        cx: Float,
        cy: Float,
        s: Float,
    ): Tri {
        val q = doubleArrayOf(
            p[0] - PYRA_CENTROID[0],
            p[1] - PYRA_CENTROID[1],
            p[2] - PYRA_CENTROID[2],
        )
        val cyaw = cos(yaw)
        val syaw = sin(yaw)
        val x1 = q[0] * cyaw + q[2] * syaw
        val z1 = -q[0] * syaw + q[2] * cyaw
        val cp = cos(pitch)
        val sp = sin(pitch)
        val y2 = q[1] * cp - z1 * sp
        val z2 = q[1] * sp + z1 * cp
        return Tri(cx + (x1 * s).toFloat(), cy - (y2 * s).toFloat(), z2)
    }

    private class Tri(val x: Float, val y: Float, val z: Double)

    private fun visible(normal: DoubleArray, yaw: Double, pitch: Double): Boolean {
        val cyaw = cos(yaw)
        val syaw = sin(yaw)
        val x1 = normal[0] * cyaw + normal[2] * syaw
        val z1 = -normal[0] * syaw + normal[2] * cyaw
        val cp = cos(pitch)
        val sp = sin(pitch)
        val z2 = normal[1] * sp + z1 * cp
        return z2 > 0.0
    }

    private fun rotated(p: DoubleArray, axis: DoubleArray, angle: Double): DoubleArray {
        val c = cos(angle)
        val s = sin(angle)
        val v = doubleArrayOf(p[0] - PYRA_CENTROID[0], p[1] - PYRA_CENTROID[1], p[2] - PYRA_CENTROID[2])
        val d = v[0] * axis[0] + v[1] * axis[1] + v[2] * axis[2]
        val cx = doubleArrayOf(
            axis[1] * v[2] - axis[2] * v[1],
            axis[2] * v[0] - axis[0] * v[2],
            axis[0] * v[1] - axis[1] * v[0],
        )
        return doubleArrayOf(
            PYRA_CENTROID[0] + v[0] * c + cx[0] * s + axis[0] * d * (1 - c),
            PYRA_CENTROID[1] + v[1] * c + cx[1] * s + axis[1] * d * (1 - c),
            PYRA_CENTROID[2] + v[2] * c + cx[2] * s + axis[2] * d * (1 - c),
        )
    }

    private fun rotNormal(n: DoubleArray, axis: DoubleArray, angle: Double): DoubleArray {
        val c = cos(angle)
        val s = sin(angle)
        val d = n[0] * axis[0] + n[1] * axis[1] + n[2] * axis[2]
        val cx = doubleArrayOf(
            axis[1] * n[2] - axis[2] * n[1],
            axis[2] * n[0] - axis[0] * n[2],
            axis[0] * n[1] - axis[1] * n[0],
        )
        return doubleArrayOf(
            n[0] * c + cx[0] * s + axis[0] * d * (1 - c),
            n[1] * c + cx[1] * s + axis[1] * d * (1 - c),
            n[2] * c + cx[2] * s + axis[2] * d * (1 - c),
        )
    }

    private class Bucket {
        val path = Path()
        fun add(tris: List<Tri>) {
            if (tris.isEmpty()) return
            path.moveTo(tris[0].x, tris[0].y)
            for (i in 1 until tris.size) path.lineTo(tris[i].x, tris[i].y)
            path.close()
        }
    }

    fun draw(
        scope: DrawScope,
        colors: IntArray,
        yaw: Double,
        pitch: Double,
        anim: PyraAnim?,
        zoom: Float = 1f,
    ) {
        val w = scope.size.width
        val h = scope.size.height
        val cx = w / 2f
        val cy = h / 2f + h * 0.04f
        val s = min(w, h) * MAIN_SCALE * zoom
        val sticks = HashMap<Int, Bucket>()

        fun addTri(bucket: Bucket, pts: Array<DoubleArray>) {
            bucket.add(pts.map { project(it, yaw, pitch, cx, cy, s) })
        }

        fun inset(verts: Array<DoubleArray>, c: DoubleArray, factor: Double) = Array(3) { i ->
            doubleArrayOf(
                c[0] + (verts[i][0] - c[0]) * factor,
                c[1] + (verts[i][1] - c[1]) * factor,
                c[2] + (verts[i][2] - c[2]) * factor,
            )
        }

        val moving = anim?.move?.cells?.toHashSet() ?: emptySet<Int>()
        for (i in model.cells.indices) {
            if (i in moving) continue
            val cell = model.cells[i]
            if (!visible(cell.normal, yaw, pitch)) continue
            val v = colors.getOrElse(i) { -1 }
            val col = if (v < 0) 0xFF3A4252.toInt() else PALETTE_EXT[v]
            addTri(sticks.getOrPut(col) { Bucket() }, inset(cell.verts, cell.centroid, 0.9))
        }

        fun drawMoving() {
            if (anim == null) return
            val ms = HashMap<Int, Bucket>()
            for (i in anim.move.cells) {
                val cell = model.cells[i]
                val n = rotNormal(cell.normal, anim.move.axisDir, anim.angle)
                if (!visible(n, yaw, pitch)) continue
                val pts = Array(3) { rotated(inset(cell.verts, cell.centroid, 0.9)[it], anim.move.axisDir, anim.angle) }
                val v = colors.getOrElse(i) { -1 }
                val col = if (v < 0) 0xFF3A4252.toInt() else PALETTE_EXT[v]
                addTri(ms.getOrPut(col) { Bucket() }, pts)
            }
            for ((c, b) in ms) scope.drawPath(b.path, Color(c))
        }

        val behind = anim != null && anim.move.cells.isNotEmpty() && anim.move.cells
            .map { project(model.cells[it].centroid, yaw, pitch, cx, cy, s).z }
            .average() < 0

        if (behind) drawMoving()
        for ((c, b) in sticks) scope.drawPath(b.path, Color(c))
        if (!behind) drawMoving()
    }

    /** 2D points of one cell of a face for the face-by-face guide. */
    fun projectFaceCell(
        face: Int,
        k: Int,
        yaw: Double,
        pitch: Double,
        w: Float,
        h: Float,
    ): List<Offset> {
        val cx = w / 2f
        val cy = h / 2f
        val s = min(w, h) * 0.42f
        val cell = model.cells[face * 9 + k]
        return cell.verts.map {
            val t = project(it, yaw, pitch, cx, cy, s)
            Offset(t.x, t.y)
        }
    }

    /** Cell index 0..8 of a face under a tap in the face-by-face guide, or null. */
    fun hitFaceCell(face: Int, x: Float, y: Float, w: Float, h: Float, yaw: Double, pitch: Double): Int? {
        val cx = w / 2f
        val cy = h / 2f
        val s = min(w, h) * 0.42f
        for (k in 0 until 9) {
            val cell = model.cells[face * 9 + k]
            val p = cell.verts.map { project(it, yaw, pitch, cx, cy, s) }
            if (inside(p[0], p[1], p[2], x, y)) return k
        }
        return null
    }

    /** Returns the front-most cell under a tap, or null. */
    fun hitTest(x: Float, y: Float, w: Float, h: Float, yaw: Double, pitch: Double, zoom: Float = 1f): Int? {
        val cx = w / 2f
        val cy = h / 2f + h * 0.04f
        val s = min(w, h) * MAIN_SCALE * zoom
        var best: Int? = null
        var bestZ = -Double.MAX_VALUE
        for (i in model.cells.indices) {
            val cell = model.cells[i]
            if (!visible(cell.normal, yaw, pitch)) continue
            val p = cell.verts.map { project(it, yaw, pitch, cx, cy, s) }
            if (inside(p[0], p[1], p[2], x, y)) {
                val z = (p[0].z + p[1].z + p[2].z) / 3
                if (z > bestZ) {
                    bestZ = z
                    best = i
                }
            }
        }
        return best
    }

    private fun inside(a: Tri, b: Tri, c: Tri, px: Float, py: Float): Boolean {
        fun sign(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float =
            (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)
        val d1 = sign(px, py, a.x, a.y, b.x, b.y)
        val d2 = sign(px, py, b.x, b.y, c.x, c.y)
        val d3 = sign(px, py, c.x, c.y, a.x, a.y)
        val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
        val hasPos = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNeg && hasPos)
    }
}

private val PALETTE_EXT = intArrayOf(
    0xFFF5C518.toInt(),
    0xFF37C46D.toInt(),
    0xFFE5555B.toInt(),
    0xFF4C8DFF.toInt(),
    0xFFF2F5FA.toInt(),
    0xFFFF9F43.toInt(),
)
