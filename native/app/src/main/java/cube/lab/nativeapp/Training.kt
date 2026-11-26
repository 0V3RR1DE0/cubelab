package cube.lab.nativeapp

import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

/** A puzzle in the timer's selector; [big] selects Mo3 instead of Ao5/Ao12. */
enum class TrainingPuzzle(val id: String, val label: String, val big: Boolean = false) {
    P2("2x2", "2×2"),
    P3("3x3", "3×3"),
    P4("4x4", "4×4"),
    P5("5x5", "5×5"),
    P6("6x6", "6×6", big = true),
    P7("7x7", "7×7", big = true),
    PYR("pyraminx", "Pyraminx");

    companion object {
        fun fromId(id: String): TrainingPuzzle? = TrainingPuzzle.entries.firstOrNull { it.id == id }
    }
}

/** One tutorial card: what the method does and how it fails. */
data class Lesson(
    val id: String,
    val puzzle: String,
    val title: String,
    val goal: String,
    val algorithmName: String,
    val moves: String,
    val explanation: String,
    val commonMistake: String,
)

object TrainingContent {
    private fun lesson(
        id: String,
        puzzle: String,
        title: String,
        goal: String,
        algorithmName: String,
        moves: String,
        explanation: String,
        commonMistake: String,
    ) = Lesson(id, puzzle, title, goal, algorithmName, moves, explanation, commonMistake)

    private const val ORTEGA = "2×2 Ortega"
    private const val LBL = "3×3 Beginner LBL"
    private const val CFOP = "3×3 CFOP"
    private const val BIG = "4×4–7×7 Reduction"
    private const val PYRA = "Pyraminx LBL"

    /** Lessons in teaching order; the UI groups them by [Lesson.puzzle]. */
    val lessons: List<Lesson> = listOf(
        // ------------------------------------------------------------ 2x2 Ortega
        lesson(
            id = "ortega.first-face",
            puzzle = ORTEGA,
            title = "First face",
            goal = "Build one solid 2×2 face, not a full layer.",
            algorithmName = "Corner insert",
            moves = "R U R'",
            explanation = "Keep the target colour on the bottom and drop each corner into the face with a short trigger. " +
                "The trigger here lifts and re-inserts the front-right corner, which is the motion to repeat for the others. " +
                "Nothing outside the face is preserved yet, so the face can be turned freely between inserts.",
            commonMistake = "Building a full layer instead of a face: Ortega only needs the bottom stickers to match, and PBL fixes the sides.",
        ),
        lesson(
            id = "ortega.oll.sune",
            puzzle = ORTEGA,
            title = "OLL · Sune",
            goal = "Orient the top corners when one is already correct.",
            algorithmName = "Sune",
            moves = "R U R' U R U2 R'",
            explanation = "Sune twists three top corners one way and leaves the fourth corner and the first face alone. " +
                "Hold the correctly oriented corner at the back-left before starting. " +
                "Every one-corner-correct OLL case is either Sune or Anti-Sune.",
            commonMistake = "Running Sune from the wrong angle: with the solved corner in another slot you reach a different OLL case.",
        ),
        lesson(
            id = "ortega.oll.antisune",
            puzzle = ORTEGA,
            title = "OLL · Anti-Sune",
            goal = "Orient the top corners for the mirror of the Sune case.",
            algorithmName = "Anti-Sune",
            moves = "R U2 R' U' R U' R'",
            explanation = "Anti-Sune is Sune's inverse: it twists three corners the other way with the solved corner at the back-right. " +
                "The first face stays solid and the fourth corner is untouched. " +
                "Use it when Sune would twist the corners further away from solved.",
            commonMistake = "Using Sune with the solved corner at the back-right; the mirror case needs Anti-Sune.",
        ),
        lesson(
            id = "ortega.oll.h",
            puzzle = ORTEGA,
            title = "OLL · H",
            goal = "Orient all corners when none of them is oriented.",
            algorithmName = "H",
            moves = "R2 U2 R U2 R2",
            explanation = "H twists two diagonal pairs of corners when no top sticker points up. " +
                "The first face survives every move. " +
                "One application leaves the whole top oriented, so only PBL remains.",
            commonMistake = "Using H when only the two adjacent corners need twisting; that is the Pi case.",
        ),
        lesson(
            id = "ortega.oll.pi",
            puzzle = ORTEGA,
            title = "OLL · Pi",
            goal = "Orient all corners when the two oriented ones are adjacent.",
            algorithmName = "Pi",
            moves = "R U2 R2 U' R2 U' R2 U2 R",
            explanation = "Pi handles the case with a pair of adjacent corners correctly oriented. " +
                "It cycles the other two through the top and preserves the first face. " +
                "Because it is longer than Sune, identify it before you start turning.",
            commonMistake = "Confusing Pi with H: check whether the two oriented corners are adjacent (Pi) or diagonal (H).",
        ),
        lesson(
            id = "ortega.oll.t",
            puzzle = ORTEGA,
            title = "OLL · T",
            goal = "Orient the top corners for the T shape.",
            algorithmName = "T",
            moves = "R U R' U' R' F R F'",
            explanation = "T twists the front-right corners and leaves the back pair alone. " +
                "It uses an F turn, so the first face must be held on the bottom and the front face kept clear. " +
                "The trigger finishes with the top face solid.",
            commonMistake = "Forgetting the F move at the end, which leaves the first face broken.",
        ),
        lesson(
            id = "ortega.oll.u",
            puzzle = ORTEGA,
            title = "OLL · U",
            goal = "Orient the top corners for the U shape.",
            algorithmName = "U",
            moves = "R2 D R' U2 R D' R' U2 R'",
            explanation = "U-case uses a temporary D turn to move the bottom layer under the algorithm. " +
                "The D' at the end restores the first face exactly, so the face is not damaged. " +
                "Track the bottom colour and the undo turn comes naturally.",
            commonMistake = "Skipping the D' undo turn, which leaves the first face twisted.",
        ),
        lesson(
            id = "ortega.oll.l",
            puzzle = ORTEGA,
            title = "OLL · L",
            goal = "Orient the top corners for the L shape.",
            algorithmName = "L",
            moves = "F R' F' R U R U' R'",
            explanation = "L twists the two front corners and leaves the back pair oriented. " +
                "The opening F pair only borrows the front layer and restores it before the first face can move. " +
                "It is the last of the seven 2×2 OLL cases.",
            commonMistake = "Starting from the wrong side: the front-right corner must be one of the two that need twisting.",
        ),
        lesson(
            id = "ortega.pbl.adj",
            puzzle = ORTEGA,
            title = "PBL · Adj-adj",
            goal = "Swap an adjacent corner pair in each layer.",
            algorithmName = "PBL adj-adj",
            moves = "R2 U' B2 U2 R2 U' R2",
            explanation = "PBL solves both layers at once. " +
                "Adj-adj swaps one adjacent pair on top and one on the bottom; align both pairs on the same side first. " +
                "That leaves at most a final U turn. " +
                "Corner orientation is preserved, so both faces stay solid.",
            commonMistake = "Forgetting the AUF: PBL assumes the two swapped pairs face the same way.",
        ),
        lesson(
            id = "ortega.pbl.diag",
            puzzle = ORTEGA,
            title = "PBL · Diag-diag",
            goal = "Swap a diagonal corner pair in each layer.",
            algorithmName = "PBL diag-diag",
            moves = "R2 F2 R2",
            explanation = "This is the shortest PBL case: a diagonal swap in each layer collapses to three half turns. " +
                "Both faces stay oriented because half turns do not twist corners. " +
                "Turn U until the two diagonals line up, then apply it.",
            commonMistake = "Applying it with the diagonals rotated relative to each other; twist U first.",
        ),

        // ------------------------------------------------------------ 3x3 beginner LBL
        lesson(
            id = "lbl.cross",
            puzzle = LBL,
            title = "White cross",
            goal = "Solve the four bottom edges around the D centre.",
            algorithmName = "Matched-edge drop",
            moves = "F2",
            explanation = "Plan the cross during inspection: hold the white centre down, match each white edge's side sticker to its centre, then drop it. " +
                "A matched edge drops with a half turn of its side face such as F2, which keeps the centre and the other cross edges in place. " +
                "There is no fixed algorithm; three or four moves per edge is normal.",
            commonMistake = "Turning one quarter instead of two, so the edge lands in the middle layer with the wrong side colour.",
        ),
        lesson(
            id = "lbl.corners",
            puzzle = LBL,
            title = "First-layer corners",
            goal = "Insert all four first-layer corners.",
            algorithmName = "Sexy move (R U R' U')",
            moves = "R U R' U'",
            explanation = "The sexy move cycles three top corners and twists them, so repeating it walks a corner into the front-right slot. " +
                "With the target slot at front-right and the corner above it, one to six repetitions insert the corner. " +
                "It preserves the cross and every corner already solved.",
            commonMistake = "Watching only the white sticker: the corner must enter with white on the bottom, even when that takes several repeats.",
        ),
        lesson(
            id = "lbl.second.right",
            puzzle = LBL,
            title = "Second layer · right insert",
            goal = "Fill the front-right middle slot.",
            algorithmName = "Right insert",
            moves = "U R U' R' U' F' U F",
            explanation = "The right insert takes an edge from the top layer and drops it into the front-right middle slot. " +
                "The first four moves pair the edge with the corner and the last four tuck the pair in. " +
                "The first layer is rebuilt by the final F, so it survives the insert.",
            commonMistake = "Using the right insert in the front-left slot, which pulls out a first-layer corner instead.",
        ),
        lesson(
            id = "lbl.second.left",
            puzzle = LBL,
            title = "Second layer · left insert",
            goal = "Fill the front-left middle slot.",
            algorithmName = "Left insert",
            moves = "U' L' U L U F U' F'",
            explanation = "The left insert is the mirror of the right insert and fills the front-left middle slot. " +
                "It pairs the edge with the front-left corner and rotates the pair into place with F U' F'. " +
                "The cross, first-layer corners and the other three middle edges stay put.",
            commonMistake = "Mirroring only the U turns and not the face turns, which splits the pair again.",
        ),
        lesson(
            id = "lbl.yellow-cross",
            puzzle = LBL,
            title = "Yellow cross",
            goal = "Orient the four top edges.",
            algorithmName = "Line algorithm (F R U R' U' F')",
            moves = "F R U R' U' F'",
            explanation = "This orients the yellow edges into a cross without disturbing the first two layers. " +
                "Use it on a horizontal line; on an L shape put the corner at front-left and apply it once to reach the line. " +
                "A dot needs two applications or the wide variant Fw R U R' U' Fw'.",
            commonMistake = "Stopping after one application on a dot: that only converts it to an L, not a cross.",
        ),
        lesson(
            id = "lbl.yellow-corners.sune",
            puzzle = LBL,
            title = "Yellow corners · Sune",
            goal = "Orient the top corners one Sune at a time.",
            algorithmName = "Sune",
            moves = "R U R' U R U2 R'",
            explanation = "Sune twists three top corners and keeps the yellow cross and the first two layers. " +
                "Hold one correctly oriented corner in a known slot and repeat until all four point up. " +
                "It permutes corners as a side effect, which the next step repairs.",
            commonMistake = "Rotating the whole cube between repetitions: turn U only so the solved layers stay intact.",
        ),
        lesson(
            id = "lbl.yellow-corners.antisune",
            puzzle = LBL,
            title = "Yellow corners · Anti-Sune",
            goal = "Orient the top corners for the mirrored case.",
            algorithmName = "Anti-Sune",
            moves = "R U2 R' U' R U' R'",
            explanation = "Anti-Sune is the inverse of Sune and twists three corners the other way. " +
                "It preserves the yellow cross and the first two layers just like Sune. " +
                "Pick it when Sune leaves the top face further from solid.",
            commonMistake = "Repeating Sune from every angle instead of recognising the mirrored case.",
        ),
        lesson(
            id = "lbl.permute-corners.aa",
            puzzle = LBL,
            title = "Permute corners · Aa",
            goal = "Swap two adjacent top corners.",
            algorithmName = "Aa-perm",
            moves = "R' F R' B2 R F' R' B2 R2",
            explanation = "Aa-perm swaps one adjacent pair of top corners and leaves the other two and all top edges fixed. " +
                "Find a side where the two corners already match, hold it at the back, and apply Aa. " +
                "The first two layers are untouched, so the solve only needs the edge step afterwards.",
            commonMistake = "Using Aa when the matching pair is on the right: that is the Ab case.",
        ),
        lesson(
            id = "lbl.permute-corners.ab",
            puzzle = LBL,
            title = "Permute corners · Ab",
            goal = "Swap two adjacent top corners the other way.",
            algorithmName = "Ab-perm",
            moves = "R2 B2 R F R' B2 R F' R",
            explanation = "Ab-perm is Aa's mirror: it swaps the adjacent corner pair on the other side. " +
                "Hold the matching pair at the back and apply Ab once. " +
                "It preserves corner orientation, so the yellow face stays solid.",
            commonMistake = "Applying Ab twice when one Aa from the right angle would have finished the corners.",
        ),
        lesson(
            id = "lbl.permute-edges.ua",
            puzzle = LBL,
            title = "Permute edges · Ua",
            goal = "Cycle three top edges clockwise.",
            algorithmName = "Ua-perm",
            moves = "R U' R U R U R U' R' U' R2",
            explanation = "Ua-perm cycles three top edges clockwise and leaves the fourth edge and all corners in place. " +
                "Hold the solved edge at the back; one application finishes the last layer. " +
                "It is the standard 3-cycle shared by the beginner method and CFOP.",
            commonMistake = "Applying Ua when the solved edge belongs at the front, which cycles the wrong three edges.",
        ),
        lesson(
            id = "lbl.permute-edges.ub",
            puzzle = LBL,
            title = "Permute edges · Ub",
            goal = "Cycle three top edges counter-clockwise.",
            algorithmName = "Ub-perm",
            moves = "R2 U R U R' U' R' U' R' U R'",
            explanation = "Ub-perm is the inverse edge cycle: three top edges rotate the other way. " +
                "Hold the solved edge at the back and apply Ub once. " +
                "Corners and the first two layers stay solved.",
            commonMistake = "Solving the last layer with Ua twice instead of recognising Ub and saving moves.",
        ),

        // ------------------------------------------------------------ 3x3 CFOP
        lesson(
            id = "cfop.cross",
            puzzle = CFOP,
            title = "Cross",
            goal = "Solve the four bottom edges in eight moves or fewer.",
            algorithmName = "Planned cross",
            moves = "F2",
            explanation = "CFOP starts with the same cross as the beginner method but planned in inspection. " +
                "Choose the easiest colour, solve the edges relative to each other, and prefer half turns such as F2. " +
                "A cross solved in fewer moves leaves more time for F2L.",
            commonMistake = "Solving the cross one edge at a time without planning the order, which wastes moves.",
        ),
        lesson(
            id = "cfop.f2l",
            puzzle = CFOP,
            title = "Intuitive F2L",
            goal = "Solve the first two layers as four corner-edge pairs.",
            algorithmName = "Basic pair insert",
            moves = "U R U' R'",
            explanation = "Intuitive F2L solves a corner and its edge together instead of one piece at a time. " +
                "The four moves shown drop a pair that is already joined above the front-right slot. " +
                "Learn to split, join and insert by watching the two pieces; the cases follow from those three ideas.",
            commonMistake = "Solving the corner first and then hunting the edge; F2L wants both pieces tracked as a pair.",
        ),
        lesson(
            id = "cfop.oll.edges",
            puzzle = CFOP,
            title = "2-look OLL · edges",
            goal = "Orient the four top edges before the corners.",
            algorithmName = "2-look OLL step 1",
            moves = "F R U R' U' F'",
            explanation = "The first look of 2-look OLL orients the top edges with a single trigger. " +
                "Apply it to a line, or to an L with the corner at front-left; a dot needs the wide-move variant. " +
                "Corners are deliberately left free for the second look.",
            commonMistake = "Starting with a corner-orientation algorithm: 2-look OLL always orients edges first.",
        ),
        lesson(
            id = "cfop.oll.corners",
            puzzle = CFOP,
            title = "2-look OLL · corners",
            goal = "Orient the top corners with one or two Sunes.",
            algorithmName = "2-look OLL step 2 (Sune)",
            moves = "R U R' U R U2 R'",
            explanation = "After the top cross, one or two Sunes make the whole top face solid. " +
                "Each Sune twists three corners and preserves the edge orientation and the first two layers. " +
                "Check the top face after every application so the next angle is correct.",
            commonMistake = "Repeating Sune without re-checking: some cases need Anti-Sune or a second Sune from elsewhere.",
        ),
        lesson(
            id = "cfop.pll.t",
            puzzle = CFOP,
            title = "2-look PLL · T-perm",
            goal = "Solve corners when one adjacent pair already matches.",
            algorithmName = "T-perm",
            moves = "R U R' U' R' F R2 U' R' U' R U R' F'",
            explanation = "T-perm swaps two adjacent top corners and two opposite edges in one algorithm. " +
                "In 2-look PLL it finishes the corner step and the edge step repairs the edges afterwards. " +
                "Hold the swapped corners on the right.",
            commonMistake = "Trying to preserve the edges with T-perm; the second PLL look fixes them anyway.",
        ),
        lesson(
            id = "cfop.pll.y",
            puzzle = CFOP,
            title = "2-look PLL · Y-perm",
            goal = "Solve corners when only a diagonal pair matches.",
            algorithmName = "Y-perm",
            moves = "F R U' R' U' R U R' F' R U R' U' R' F R F'",
            explanation = "Y-perm swaps two diagonal corners and two edges, which is the case T-perm cannot take. " +
                "It is longer but avoids an extra setup turn. " +
                "The first two layers are untouched.",
            commonMistake = "Using Y-perm when an adjacent corner pair already matches; T-perm is shorter.",
        ),
        lesson(
            id = "cfop.pll.ua",
            puzzle = CFOP,
            title = "2-look PLL · Ua-perm",
            goal = "Cycle three top edges clockwise.",
            algorithmName = "Ua-perm",
            moves = "R U' R U R U R U' R' U' R2",
            explanation = "Ua-perm cycles three top edges clockwise once the corners are solved. " +
                "Hold the solved edge at the back and apply it once. " +
                "It is a pure edge permutation, so the corners stay in place.",
            commonMistake = "Applying Ua when the solved edge is on the left; that needs Ub.",
        ),
        lesson(
            id = "cfop.pll.ub",
            puzzle = CFOP,
            title = "2-look PLL · Ub-perm",
            goal = "Cycle three top edges counter-clockwise.",
            algorithmName = "Ub-perm",
            moves = "R2 U R U R' U' R' U' R' U R'",
            explanation = "Ub-perm is Ua's inverse and rotates three top edges the other way. " +
                "Hold the solved edge at the back and apply it once. " +
                "Corners and the first two layers are unaffected.",
            commonMistake = "Solving the last layer with Ua twice instead of recognising Ub.",
        ),
        lesson(
            id = "cfop.pll.h",
            puzzle = CFOP,
            title = "2-look PLL · H-perm",
            goal = "Swap two opposite pairs of top edges.",
            algorithmName = "H-perm",
            moves = "2L2 U 2L2 U2 2L2 U 2L2",
            explanation = "H-perm swaps opposite edge pairs, which is the case where no top edge is solved. " +
                "Corners are already correct and stay put. " +
                "It is a pure edge permutation, so it belongs to the second PLL look.",
            commonMistake = "Looking for a solved edge that does not exist; H is identified by its two opposite swaps.",
        ),
        lesson(
            id = "cfop.pll.z",
            puzzle = CFOP,
            title = "2-look PLL · Z-perm",
            goal = "Swap two adjacent pairs of top edges.",
            algorithmName = "Z-perm",
            moves = "2L2 U 2L2 U 2L' U2 2L2 U2 2L'",
            explanation = "Z-perm swaps two adjacent edge pairs and leaves every corner in place. " +
                "Hold one swapped pair on the left; one application solves the top edges. " +
                "It is the last edge case of 2-look PLL.",
            commonMistake = "Applying Z-perm with the swapped pairs on a diagonal; that is H-perm.",
        ),

        // ------------------------------------------------------------ 4x4-7x7 reduction
        lesson(
            id = "big.centres",
            puzzle = BIG,
            title = "Centres",
            goal = "Join each face's centre pieces into one solid block.",
            algorithmName = "Free-slice centres",
            moves = "Rw U Rw'",
            explanation = "Reduction starts by joining the centres of each face. " +
                "There is no fixed algorithm: slice a face with Rw or Uw, move centre pieces over, and slice back. " +
                "Solve the first two opposite centres freely, then use only moves that leave them intact.",
            commonMistake = "Solving centres in an order that forces slice moves through finished centres.",
        ),
        lesson(
            id = "big.edge-pairing",
            puzzle = BIG,
            title = "Edge pairing",
            goal = "Join every pair of wings into one 3×3-style edge.",
            algorithmName = "Wing pairing",
            moves = "Uw' R U R' F R' F' R Uw",
            explanation = "Edge pairing joins the two wings of each edge into a single edge. " +
                "The inner trigger flips one wing without changing the centres, and the Uw moves bring its partner next to it. " +
                "Work through the remaining edges the same way and handle the last two pairs together.",
            commonMistake = "Turning an outer face to fetch a wing, which breaks the centres you just solved.",
        ),
        lesson(
            id = "big.3x3",
            puzzle = BIG,
            title = "3×3 stage",
            goal = "Solve the reduced cube like a 3×3.",
            algorithmName = "Reduced 3×3 stage",
            moves = "R U R' U'",
            explanation = "Once centres are solid and wings are paired, the puzzle behaves like a 3×3. " +
                "Use your normal method, treating each block of four centres as one sticker. " +
                "The trigger shown is only the first-layer corner insert from the beginner method.",
            commonMistake = "Using inner slice moves here, which undo the reduction.",
        ),
        lesson(
            id = "big.parity.oll",
            puzzle = BIG,
            title = "4×4 OLL parity",
            goal = "Flip the single edge that a 3×3 cannot have.",
            algorithmName = "4×4 OLL parity",
            moves = "Rw2 B2 U2 Lw U2 Rw' U2 Rw U2 F2 Rw F2 Lw' B2 Rw2",
            explanation = "OLL parity is the lone flipped edge that appears after reduction. " +
                "This algorithm flips it with wide turns while keeping the centres and paired edges intact. " +
                "Apply it when the reduced cube reaches an impossible OLL case, then continue your OLL.",
            commonMistake = "Applying it before the centres are complete; the wide moves assume a reduced cube.",
        ),
        lesson(
            id = "big.parity.pll",
            puzzle = BIG,
            title = "4×4 PLL parity",
            goal = "Swap the two edges that a 3×3 cannot swap.",
            algorithmName = "4×4 PLL parity",
            moves = "r2 U2 r2 Uw2 r2 Uw2",
            explanation = "PLL parity shows up as two swapped edges after the last layer is otherwise solved. " +
                "The short r2/Uw2 sequence swaps them without touching corners or centres. " +
                "Use it at the end of the solve, then finish with the usual AUF.",
            commonMistake = "Trying a 3×3 PLL: no 3×3 algorithm can swap exactly two edges.",
        ),

        // ------------------------------------------------------------ Pyraminx LBL
        lesson(
            id = "pyra.centres",
            puzzle = PYRA,
            title = "Centres",
            goal = "Align the three centre triangles of each face.",
            algorithmName = "Vertex turn",
            moves = "R",
            explanation = "A centre is the small triangle in the middle of each face; turning a vertex cycles the three centres around it. " +
                "Align each face's centres by turning vertices, not tips. " +
                "Centres must be solved first because later triggers assume they are fixed.",
            commonMistake = "Turning tips to move centres: a tip only moves its own sticker.",
        ),
        lesson(
            id = "pyra.first-layer",
            puzzle = PYRA,
            title = "First layer",
            goal = "Solve the three edges around one face.",
            algorithmName = "X Y X' Y' trigger",
            moves = "R U R' U'",
            explanation = "The first layer is three edges around a face. " +
                "The X Y X' Y' trigger cycles edges without twisting centres, and its conjugates place each edge in turn. " +
                "Choose the face you want on the bottom and solve its three edges.",
            commonMistake = "Using a bare vertex turn to reposition an edge, which twists the centre on that axis.",
        ),
        lesson(
            id = "pyra.ll.sune",
            puzzle = PYRA,
            title = "Last layer · Sune",
            goal = "Cycle the three last-layer edges.",
            algorithmName = "Pyraminx Sune",
            moves = "R U R' U R U R'",
            explanation = "Pyraminx Sune cycles the three last-layer edges while keeping the solved first layer. " +
                "Hold the face you are solving at the front and apply it once per three-cycle. " +
                "It is one of only five last-layer cases in the layer-by-layer method.",
            commonMistake = "Losing track of the first layer: the last-layer face must stay at the front while the trigger runs.",
        ),
        lesson(
            id = "pyra.ll.antisune",
            puzzle = PYRA,
            title = "Last layer · Anti-Sune",
            goal = "Cycle the last-layer edges the other way.",
            algorithmName = "Pyraminx Anti-Sune",
            moves = "R U' R' U' R U' R'",
            explanation = "Anti-Sune is the inverse of Sune and cycles the three last-layer edges counter-clockwise. " +
                "The first layer stays solved. " +
                "Use it when Sune would move the edges one step too far.",
            commonMistake = "Repeating Sune instead of recognising the mirror case.",
        ),
        lesson(
            id = "pyra.ll.two-flip",
            puzzle = PYRA,
            title = "Last layer · 2-flip",
            goal = "Fix two last-layer edges that are flipped relative to each other.",
            algorithmName = "2-flip",
            moves = "L R' L' R U' R U R'",
            explanation = "The 2-flip case has two edges flipped in place. " +
                "The algorithm uses both the L and R vertices, so the centres return to their starting position. " +
                "After it the last layer is oriented and only an edge cycle may remain.",
            commonMistake = "Using only one vertex, which leaves the centres twisted.",
        ),
        lesson(
            id = "pyra.ll.unsexy-r",
            puzzle = PYRA,
            title = "Last layer · R' unsexy R",
            goal = "Solve the three-edge cycle with an R conjugation.",
            algorithmName = "R' unsexy R",
            moves = "R' U' L' U L R",
            explanation = "This case wraps the unsexy trigger in an R' ... R conjugation. " +
                "It cycles the last-layer edges while preserving the first layer and the centres. " +
                "It is the fourth of the five last-layer cases.",
            commonMistake = "Dropping the final R and leaving the centre block twisted.",
        ),
        lesson(
            id = "pyra.ll.unsexy-l",
            puzzle = PYRA,
            title = "Last layer · L unsexy L'",
            goal = "Solve the three-edge cycle with an L conjugation.",
            algorithmName = "L unsexy L'",
            moves = "L U R U' R' L'",
            explanation = "The mirror of the previous case, conjugated with L ... L'. " +
                "It cycles three last-layer edges and restores the centres and first layer. " +
                "Together with Sune and Anti-Sune it covers every edge cycle.",
            commonMistake = "Mixing the L and R conjugations, which cancels the cycle instead of applying it.",
        ),
        lesson(
            id = "pyra.tips",
            puzzle = PYRA,
            title = "Tips last",
            goal = "Fix the four tips after everything else.",
            algorithmName = "Tip turn",
            moves = "u",
            explanation = "A tip is the single-sticker pyramid on each corner and always turns by itself. " +
                "Solve it last, because every main-vertex turn scrambles the tips again. " +
                "One tip needs at most one turn, so this step costs at most four moves.",
            commonMistake = "Solving tips early and then doing main-vertex turns, which scrambles them again.",
        ),
    )

    /** Lessons grouped by track label, in teaching order. */
    val tracks: List<Pair<String, List<Lesson>>> = lessons.groupBy { it.puzzle }.toList()
}

/** One stored solve, matching the on-disk line format. */
data class TrainingSolve(
    val puzzle: TrainingPuzzle,
    val rawMs: Long,
    val penalty: Penalty,
    val timestamp: Long,
) {
    val time: SolveTime get() = SolveTime(rawMs, penalty)
}

/**
 * The single local record file. Lines are `puzzle|rawMs|penalty|timestamp`;
 * unreadable lines are skipped so one bad write cannot lose the whole history.
 */
object TrainingStore {
    const val FILE_NAME = "training_times.txt"

    fun encode(solves: List<TrainingSolve>): String = solves.joinToString("\n") { s ->
        "${s.puzzle.id}|${s.rawMs}|${s.penalty.name}|${s.timestamp}"
    }

    fun parse(text: String): List<TrainingSolve> = text.lineSequence().mapNotNull(::parseLine).toList()

    private fun parseLine(line: String): TrainingSolve? {
        val parts = line.trim().split('|')
        if (parts.size != 4) return null
        val puzzle = TrainingPuzzle.fromId(parts[0]) ?: return null
        val rawMs = parts[1].toLongOrNull() ?: return null
        if (rawMs < 0) return null
        val penalty = runCatching { Penalty.valueOf(parts[2]) }.getOrNull() ?: return null
        val timestamp = parts[3].toLongOrNull() ?: return null
        return TrainingSolve(puzzle, rawMs, penalty, timestamp)
    }

    /** Missing or corrupt file -> empty list. */
    fun read(file: File): List<TrainingSolve> = try {
        if (file.exists()) parse(file.readText()) else emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    /** Writes via a temporary file and a rename, so a crash cannot truncate the history. */
    fun write(file: File, solves: List<TrainingSolve>): Boolean = try {
        val target = file.absoluteFile
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(encode(solves))
        if (!tmp.renameTo(target)) {
            // Some file systems refuse to rename onto an existing file.
            target.delete()
            if (!tmp.renameTo(target)) {
                target.writeText(encode(solves))
                tmp.delete()
            }
        }
        true
    } catch (t: Throwable) {
        false
    }
}

/** Best single plus the current averages; `mo3` replaces ao5/ao12 on 6×6 and 7×7. */
data class TrainingSummary(
    val singleMs: Long?,
    val ao5Ms: Long?,
    val ao12Ms: Long?,
    val mo3Ms: Long?,
)

/** Pure record summary over one puzzle's solves (oldest first). */
fun trainingSummary(solves: List<SolveTime>, bigCube: Boolean): TrainingSummary {
    val single = solves.mapNotNull { it.finalMs }.minOrNull()
    return if (bigCube) {
        TrainingSummary(single, null, null, mo3(solves))
    } else {
        TrainingSummary(single, TimerLogic.aoN(solves, 5), TimerLogic.aoN(solves, 12), null)
    }
}

/** Mean of the last three; any DNF makes the mean a DNF. Rounded to the nearest 10 ms. */
private fun mo3(solves: List<SolveTime>): Long? {
    if (solves.size < 3) return null
    val last = solves.takeLast(3).map { it.finalMs ?: return null }
    return (last.sum() / 3.0 / 10.0).roundToLong() * 10
}

/** `m:ss.cc` or `ss.cc`; a null time (DNF or not enough solves) prints as `DNF`. */
fun formatTime(ms: Long?): String {
    if (ms == null) return "DNF"
    val totalCs = (ms.coerceAtLeast(0)) / 10
    val cs = totalCs % 100
    val totalSec = totalCs / 100
    return if (totalSec >= 60) {
        String.format(Locale.US, "%d:%02d.%02d", totalSec / 60, totalSec % 60, cs)
    } else {
        String.format(Locale.US, "%02d.%02d", totalSec, cs)
    }
}
