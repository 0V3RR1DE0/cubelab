package cube.lab.nativeapp

// Pinpoint why a painted cube cannot be solved, so the UI can highlight the
// offending stickers instead of a generic "not a real cube".

class CubeIssue(val message: String, val cells: IntArray)

private val COLOUR_NAMES = arrayOf("yellow", "green", "red", "blue", "white", "orange")

private fun pieceKey(faces: List<Int>): String = faces.sorted().joinToString(",")

fun diagnoseCube(cube: Cube, colors: IntArray, n: Int): CubeIssue? {
    val total = 6 * n * n
    if (colors.size != total || colors.any { it < 0 }) {
        return CubeIssue("Paint all $total stickers first.", IntArray(0))
    }
    val cap = n * n
    val counts = IntArray(6)
    for (c in colors) counts[c]++
    val wrong = counts.indices.filter { counts[it] != cap }
    if (wrong.isNotEmpty()) {
        val detail = wrong.joinToString(", ") { "${COLOUR_NAMES[it]}=${counts[it]}" }
        return CubeIssue("Each colour needs $cap stickers ($detail).", IntArray(0))
    }
    if (n == 1) return null
    if (n > 3) return null

    val scheme = if (n == 3) centreScheme(colors) else infer2x2Scheme(cube, colors)
    if (scheme == null) {
        val cells = if (n == 3) IntArray(6) { it * 9 + 4 } else IntArray(0)
        return CubeIssue("The six centres must be different colours.", cells)
    }
    val colourToFace = IntArray(6) { -1 }
    scheme.forEachIndexed { f, c -> colourToFace[c] = f }

    val map = buildCubieMap(cube)
    val cornerLookup = map.corners.mapIndexed { i, c -> pieceKey(c.faces.toList()) to i }.toMap()
    val edgeLookup = map.edges.mapIndexed { i, e -> pieceKey(e.faces.toList()) to i }.toMap()

    val badCorners = ArrayList<Int>()
    val badEdges = ArrayList<Int>()
    val usedCorners = IntArray(8)
    val usedEdges = IntArray(12)

    for (p in 0 until 8) {
        val c = map.corners[p]
        val homes = c.faces.indices.map { k -> colourToFace[colors[c.facelets[k]]] }
        val key = pieceKey(homes)
        val piece = cornerLookup[key]
        if (piece == null) {
            badCorners.addAll(c.facelets.toList())
        } else {
            usedCorners[piece]++
        }
    }
    if (n == 3) {
        for (p in 0 until 12) {
            val e = map.edges[p]
            val homes = e.faces.indices.map { k -> colourToFace[colors[e.facelets[k]]] }
            val key = pieceKey(homes)
            val piece = edgeLookup[key]
            if (piece == null) {
                badEdges.addAll(e.facelets.toList())
            } else {
                usedEdges[piece]++
            }
        }
    }

    if (badCorners.isNotEmpty()) {
        return CubeIssue("A corner has colours that no corner piece has. Check the highlighted corner.", badCorners.toIntArray())
    }
    if (badEdges.isNotEmpty()) {
        return CubeIssue("An edge has colours that no edge piece has. Check the highlighted edge.", badEdges.toIntArray())
    }
    if (usedCorners.any { it != 1 }) {
        val cells = IntArray(0)
        return CubeIssue("Two corners have the same colours. Check for a duplicated or swapped corner.", cells)
    }
    if (n == 3 && usedEdges.any { it != 1 }) {
        return CubeIssue("Two edges have the same colours. Check for a duplicated or swapped edge.", IntArray(0))
    }

    val state = decodeCubies(colors, cube, scheme)
        ?: return CubeIssue("Those stickers cannot make a real cube.", IntArray(0))

    var coSum = 0
    for (i in 0 until 8) coSum += state.co[i]
    if (coSum % 3 != 0) {
        val cells = ArrayList<Int>()
        for (i in 0 until 8) if (state.co[i] != 0) cells.addAll(map.corners[i].facelets.toList())
        return CubeIssue(
            "A corner is twisted. Corner twists must add up to a whole turn; check the highlighted corner(s).",
            cells.toIntArray(),
        )
    }

    if (n == 3) {
        var eoSum = 0
        for (i in 0 until 12) eoSum += state.eo[i]
        if (eoSum % 2 != 0) {
            val cells = ArrayList<Int>()
            for (i in 0 until 12) if (state.eo[i] != 0) cells.addAll(map.edges[i].facelets.toList())
            return CubeIssue("An edge is flipped. Flips must come in pairs; check the highlighted edge.", cells.toIntArray())
        }
        if ((permParity(state.cp) xor permParity(state.ep)) != 0) {
            return CubeIssue("Two pieces are swapped (parity). A real cube cannot have a single swap.", IntArray(0))
        }
    }
    return null
}
