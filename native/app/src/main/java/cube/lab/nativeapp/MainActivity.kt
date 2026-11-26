package cube.lab.nativeapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private val Bg = Color(0xFF080A10)
private val Panel = Color(0xFF11151F)
private val Panel2 = Color(0xFF1A2030)
private val Accent = Color(0xFF6EA8FF)
private val AccentInk = Color(0xFF08101F)
private val Muted = Color(0xFF93A0B8)
private val Line = Color(0x1AFFFFFF)
private val Good = Color(0xFF9FE6B4)
private val Bad = Color(0xFFFFC9CC)

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        state = AppState(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = AccentInk,
                    background = Bg,
                    surface = Panel,
                    onSurface = Color(0xFFEEF2FF),
                ),
            ) {
                App(state)
            }
        }
    }

    override fun onDestroy() {
        state.dispose()
        super.onDestroy()
    }
}

@Composable
fun App(state: AppState) {
    BackHandler(enabled = state.scannerOpen || state.screen != Screen.Home) {
        if (state.scannerOpen) state.scannerOpen = false else state.goHome()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (state.screen) {
            Screen.Home -> HomeScreen(state)
            Screen.Cube -> CubeScreen(state)
            Screen.Picker -> PickerScreen(state)
            Screen.Pyraminx -> PyraScreen(state)
            Screen.Training -> TrainingScreen(onClose = { state.goHome() })
        }
        if (state.scannerOpen) {
            FaceScannerScreen(
                n = state.n,
                onCaptured = { state.applyScan(it) },
                onCancel = { state.scannerOpen = false },
            )
        }
        state.message?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF32141A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55FF8C8C)),
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Bad,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun HomeScreen(state: AppState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("CubeLab.", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEEF2FF))
                Text("Input a puzzle, then solve it.", color = Muted, fontSize = 13.sp)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tab("Solver", state.homeTab == 0) { state.homeTab = 0 }
                    Tab("Patterns", state.homeTab == 1) { state.homeTab = 1 }
                    Tab("Training", state.homeTab == 2) { state.homeTab = 2 }
                }
            }
        }
        if (state.homeTab == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = { state.openPyraminx() },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniPyra(Modifier.size(92.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("Pyraminx", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("36 stickers · 4 tips", fontSize = 11.5.sp, color = Muted)
                            Text("Human layer-by-layer", fontSize = 11.5.sp, color = Color(0xFF9FE6B4))
                        }
                    }
                }
            }
            items(7) { index ->
                val id = index + 1
                PuzzleCard(id, "${6 * id * id} stickers") { state.openCube(id) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("Custom cubes", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Paint a solved-state colouring, save it, then solve a scramble into it.",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (nn in 3..7) {
                            TextButton(onClick = { state.openCustomEditor(nn) }) {
                                Text("New $nn×$nn", fontSize = 11.sp, color = Color(0xFFCFE0FF))
                            }
                        }
                    }
                    if (state.customCubes.isEmpty()) {
                        Text("No custom cubes yet.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            items(state.customCubes) { c ->
                CustomCubeCard(c, onClick = { state.startCustom(c) }, onLongClick = { state.deleteCustom(c.id) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Solving works for 1×1–7×7. 5×5–7×7 solutions are long; use the speed slider.",
                    color = Muted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else if (state.homeTab == 1) {
            items(6) { index ->
                val id = index + 2
                PuzzleCard(id, "Pick a target pattern") { state.openPicker(id) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Patterns work on every size 2×2–7×7; impossible patterns are filtered out.",
                    color = Muted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = { state.openTraining() },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Training", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Method tutorials, algorithm explanations, speedcube timer and local records.",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "2×2 Ortega · 3×3 LBL/CFOP · 4×4 reduction · Pyraminx LBL",
                            color = Color(0xFF9FE6B4),
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (selected) Color(0x336EA8FF) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFFCFE0FF) else Muted,
        )
    }
}

@Composable
private fun PuzzleCard(n: Int, sub: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            MiniCube(n, Modifier.fillMaxWidth().aspectRatio(1f))
            Text("Cube $n×$n", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEEF2FF))
            Text(sub, fontSize = 11.5.sp, color = Muted)
        }
    }
}

@Composable
private fun MiniCube(n: Int, modifier: Modifier = Modifier) {
    val renderer = remember(n) { CubeRenderer(n) }
    val solved = remember(n) { Cube(n).solved() }
    Canvas(modifier) {
        renderer.draw(this, solved, -0.62, 0.5, null)
    }
}

@Composable
private fun MiniPyra(modifier: Modifier = Modifier) {
    val renderer = remember { PyraRenderer() }
    val solved = remember { Pyraminx.solvedColors() }
    Canvas(modifier) {
        renderer.draw(this, solved, 0.62, 0.42, null)
    }
}

// ---------------------------------------------------------------- picker

@Composable
private fun PickerScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton("←") { state.goHome() }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Cube ${state.pickerN}×${state.pickerN}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Pick a target pattern", color = Muted, fontSize = 12.sp)
            }
        }
        if (state.pickerLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    PenCard { state.openPatternEditor(state.pickerN) }
                }
                items(state.pickerOptions) { option ->
                    PatternCard(state.pickerN, option) { state.startPattern(option) }
                }
                if (state.pickerOptions.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "No legal patterns of this set on this size. Try Make your own.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PenCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0x226EA8FF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x556EA8FF)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Text("✎", fontSize = 44.sp, color = Color(0xFFCFE0FF))
            }
            Text("Make your own", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Checks whether the pattern is possible", fontSize = 11.5.sp, color = Muted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomCubeCard(c: CustomCube, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        PatternPreview(c.n, c.solved, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text("${c.n}×${c.n} · long-press to delete", fontSize = 10.5.sp, color = Muted)
    }
}

@Composable
private fun PatternCard(n: Int, option: PatternOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            PatternPreview(n, option.board, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(option.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Solve into this", fontSize = 11.5.sp, color = Muted)
        }
    }
}

@Composable
private fun PatternPreview(n: Int, board: IntArray, modifier: Modifier = Modifier) {
    val renderer = remember(n) { CubeRenderer(n) }
    Canvas(modifier) {
        // Three-face 3/4 view, so a pattern preview shows more than the front.
        renderer.draw(this, board, -0.62, 0.5, null)
    }
}

// ---------------------------------------------------------------- cube screen

@Composable
private fun CubeScreen(state: AppState) {
    val title = if (state.n == 1) "Cube 1×1" else "Cube ${state.n}×${state.n}"
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton("←") { state.goHome() }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    state.issue ?: hint(state),
                    color = if (state.issue != null) Bad else Muted,
                    fontSize = 11.5.sp,
                    maxLines = if (state.issue != null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.target != null && state.phase == Phase.Input) {
                Surface(
                    color = Color(0x226EA8FF),
                    shape = RoundedCornerShape(9.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x556EA8FF)),
                ) {
                    Text(
                        "Goal: ${state.targetName}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        fontSize = 10.5.sp,
                        color = Color(0xFFCFE0FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var pos = down.position
                        var lastDist = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val d = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                                if (lastDist > 0f && d > 0f) {
                                    state.zoom = (state.zoom * (d / lastDist)).coerceIn(0.6f, 2.5f)
                                }
                                lastDist = d
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            lastDist = 0f
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                val d = change.position - pos
                                pos = change.position
                                state.dragView(d.x, d.y)
                                change.consume()
                            } else break
                        }
                    }
                },
        ) {
            val renderer = remember(state.n) { CubeRenderer(state.n) }
            Canvas(Modifier.fillMaxSize()) {
                renderer.draw(
                    this,
                    state.board,
                    state.yaw.toDouble(),
                    state.pitch.toDouble(),
                    state.anim,
                    state.highlight,
                    state.zoom,
                )
            }
            val stepNow = state.solution?.steps?.getOrNull(state.currentStep)
            if (stepNow != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    color = Color(0xDD0C1220),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x806EA8FF)),
                ) {
                    Text(
                        stepNow.label + if (stepNow.info.turns == 3) " ↺" else " ↻",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCFE0FF),
                    )
                }
            }
        }

        if (state.solution == null) {
            GuidePanel(state)
            Dock(state)
        } else {
            SolutionPanel(state)
        }
    }
}

private fun hint(state: AppState): String {
    val painted = state.paintedCount()
    val total = state.totalCells
    return when {
        state.phase == Phase.Pattern ->
            if (painted == total) "Pattern complete — press Check." else "Designing pattern · $painted/$total"
        painted == total && state.n <= 7 -> "All sides input — hit Solve."
        painted == total -> "Input complete · solver supports 1×1–7×7"
        else -> "${FACE_NAMES[state.face]} · $painted/$total · tap a cell"
    }
}

@Composable
private fun GuidePanel(state: AppState) {
    Surface(color = Panel, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton("‹") { state.prevFace() }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        "Face ${state.step + 1} of 6 · ${FACE_NAMES[state.face]}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            state.phase == Phase.Pattern -> "Designing the target pattern"
                            state.target != null -> "Input your cube · goal ${state.targetName}"
                            else -> "Input your cube"
                        },
                        fontSize = 11.sp,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = { state.openScanner() },
                    enabled = state.n in 2..7 && state.phase == Phase.Input && !state.solving,
                ) { Text("Scan", fontSize = 12.sp, color = Color(0xFF8BC4FF)) }
                Button(
                    onClick = {
                        if (state.step == 5 && state.phase == Phase.Pattern) state.checkPattern()
                        else state.nextFace()
                    },
                    enabled = state.faceDone(state.face) &&
                        !(state.step == 5 && state.phase == Phase.Input) &&
                        !state.solving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x336EA8FF), contentColor = Color(0xFFCFE0FF)),
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Text(
                        when {
                            state.step == 5 && state.phase == Phase.Pattern -> if (state.solving) "Checking…" else "Check"
                            else -> "Next"
                        },
                        fontSize = 12.sp,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (i in 0..5) {
                    val f = FACE_ORDER[i]
                    val active = i == state.step
                    val done = state.faceDone(f)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) Color(0x336EA8FF) else Color(0x0DFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (active) Color(0x806EA8FF) else Line,
                        ),
                        modifier = Modifier.weight(1f).clickable { state.selectStep(i) },
                    ) {
                        Column(
                            Modifier.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                FACE_KEYS[f],
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFFEEF2FF) else Muted,
                            )
                            Text(
                                if (done) "✓" else " ",
                                fontSize = 10.sp,
                                color = Good,
                            )
                        }
                    }
                }
            }

            FaceGrid(state)
        }
    }
}

@Composable
private fun FaceGrid(state: AppState) {
    val n = state.n
    val gap = 3.dp
    val maxCell = 40.dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            for (r in 0 until n) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until n) {
                        val cell = state.face * n * n + r * n + c
                        val v = state.board[cell]
                        Box(
                            Modifier
                                .size(maxCell)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (v < 0) Color(0xFF39415A) else paletteColor(v))
                                .border(
                                    when {
                                        cell in state.highlight -> 2.dp
                                        cell == state.selectedCell -> 2.dp
                                        else -> 1.dp
                                    },
                                    when {
                                        cell in state.highlight -> Color(0xFFFF6B6B)
                                        cell == state.selectedCell -> Color(0xFF8BC4FF)
                                        else -> Color(0x1AFFFFFF)
                                    },
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { state.paint(cell) },
                        )
                    }
                }
                if (r != n - 1) Spacer(Modifier.height(gap))
            }
        }
    }
}

@Composable
private fun Dock(state: AppState) {
    Surface(color = Panel2, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val remaining = state.remaining(i)
                    val selected = state.selected == i
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .alpha(if (remaining == 0) 0.5f else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(paletteColor(i))
                                .then(
                                    if (selected) Modifier.border(2.dp, Color(0xFFEEF2FF), RoundedCornerShape(12.dp))
                                    else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                                )
                                .clickable { state.selectColour(i) },
                        )
                        Text(
                            "$remaining",
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remaining == 0) Good else Muted,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { state.clearBoard() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                ) { Text("Clear", fontSize = 13.sp) }
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = { state.autofill() },
                    enabled = state.paintedCount() < state.totalCells && !state.filling,
                ) { Text(if (state.filling) "Filling…" else "Fill rest", fontSize = 12.sp, color = Muted) }
                Spacer(Modifier.width(6.dp))
                if (state.n <= 7 && state.phase == Phase.Input) {
                    Button(
                        onClick = { state.solve() },
                        enabled = state.allDone() && !state.solving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk),
                    ) {
                        Text(if (state.solving) "Solving…" else "Solve", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- solution

@Composable
private fun SolutionPanel(state: AppState) {
    val sol = state.solution ?: return
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentStep) {
        if (state.currentStep >= 0) listState.animateScrollToItem(state.currentStep)
    }
    Surface(color = Panel, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.solutionDone) sol.doneTitle else "Solution",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.currentStep >= 0) "Now: ${sol.steps[state.currentStep].label}" else "${sol.steps.size} moves",
                        fontSize = 11.5.sp,
                        color = if (state.currentStep >= 0) Color(0xFF8BC4FF) else Muted,
                    )
                    val phase = sol.steps.getOrNull(if (state.currentStep >= 0) state.currentStep else state.playIndex)?.phase
                    if (!phase.isNullOrEmpty()) {
                        Text(phase, fontSize = 10.5.sp, color = Muted, maxLines = 1)
                    }
                }
                TextButton(
                    onClick = { state.stepBackward() },
                    enabled = !state.playing && !state.stepping && state.playIndex > 0,
                ) { Text("◀", fontSize = 15.sp, color = Color(0xFFCFE0FF)) }
                TextButton(
                    onClick = { state.stepForward() },
                    enabled = !state.playing && !state.stepping && state.playIndex < sol.steps.size,
                ) { Text("▶", fontSize = 15.sp, color = Color(0xFFCFE0FF)) }
                Button(
                    onClick = { state.play() },
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x336EA8FF), contentColor = Color(0xFFCFE0FF)),
                ) {
                    Text(
                        when {
                            state.playing -> "Pause"
                            state.playIndex == 0 && !state.solutionDone -> "Solve"
                            else -> "Play"
                        },
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { state.closeSolution() }) { Text("×", fontSize = 20.sp, color = Muted) }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(sol.steps) { i, step ->
                    val active = i == state.currentStep
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (active) Accent else Color(0x0DFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color.Transparent else Line),
                        modifier = Modifier.clickable { state.jumpTo(i) },
                    ) {
                        Text(
                            step.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) AccentInk else Color(0xFFEEF2FF),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Speed", fontSize = 11.sp, color = Muted)
                Slider(
                    value = state.speed,
                    onValueChange = { state.changeSpeed(it) },
                    onValueChangeFinished = { state.persistSpeed() },
                    valueRange = 0.1f..2.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- pyraminx

@Composable
private fun PyraScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton("←") { state.goHome() }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Pyraminx", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(pyraHint(state), color = Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val renderer = remember { PyraRenderer() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var pos = down.position
                        var total = 0f
                        var lastDist = 0f
                        var pinched = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val d = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                                if (lastDist > 0f && d > 0f) {
                                    state.zoom = (state.zoom * (d / lastDist)).coerceIn(0.6f, 2.5f)
                                }
                                lastDist = d
                                pinched = true
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            lastDist = 0f
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                val d = change.position - pos
                                pos = change.position
                                total += abs(d.x) + abs(d.y)
                                if (total > 10f) {
                                    state.dragView(d.x, d.y)
                                    change.consume()
                                }
                            } else {
                                if (!pinched && total <= 10f) {
                                    val hit = renderer.hitTest(
                                        down.position.x,
                                        down.position.y,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        state.yaw.toDouble(),
                                        state.pitch.toDouble(),
                                        state.zoom,
                                    )
                                    if (hit != null) state.paintPyra(hit)
                                }
                                break
                            }
                        }
                    }
                },
        ) {
            renderer.draw(
                this,
                state.pyraBoard,
                state.yaw.toDouble(),
                state.pitch.toDouble(),
                state.pyraAnim,
                state.zoom,
            )
        }
        val stepNow = state.pyraSolution?.steps?.getOrNull(state.pyraCurrentStep)
        if (stepNow != null) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                Surface(
                    color = Color(0xDD0C1220),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x806EA8FF)),
                ) {
                    Text(
                        stepNow.label + if (stepNow.key.endsWith("'")) " ↺" else " ↻",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCFE0FF),
                    )
                }
            }
        }
        if (state.pyraSolution == null) PyraDock(state) else PyraSolutionPanel(state)
    }
}

private fun pyraHint(state: AppState): String {
    val painted = state.pyraPainted()
    return if (painted == 36) "Ready — hit Solve."
    else "Face ${state.pyraStep + 1} of 4 · ${PYRA_FACE_NAMES[state.pyraStep]} · $painted/36"
}

@Composable
private fun PyraDock(state: AppState) {
    Surface(color = Panel2, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircleButton("‹") { state.prevPyraFace() }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    for (i in 0 until 4) {
                        val selected = state.pyraStep == i
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) Color(0xFF1E2A44) else Color(0xFF151B27))
                                    .then(
                                        if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(11.dp))
                                        else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(11.dp)),
                                    )
                                    .clickable { state.selectPyraFace(i) },
                                contentAlignment = Alignment.Center,
                            ) { Text(PYRA_FACE_NAMES[i], fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            if (state.pyraFaceDone(i)) {
                                Text("✓", fontSize = 9.sp, color = Good)
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { state.nextPyraFace() },
                    enabled = state.pyraStep < 3 && state.pyraFaceDone(state.pyraStep),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCFE0FF)),
                ) { Text("Next", fontSize = 12.sp) }
            }
            val renderer = remember { PyraRenderer() }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .pointerInput(state.pyraStep, state.yaw, state.pitch) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var pos = down.position
                            var total = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    val d = change.position - pos
                                    pos = change.position
                                    total += abs(d.x) + abs(d.y)
                                } else {
                                    if (total <= 10f) {
                                        val hit = renderer.hitFaceCell(
                                            state.pyraStep,
                                            down.position.x,
                                            down.position.y,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                            state.yaw.toDouble(),
                                            state.pitch.toDouble(),
                                        )
                                        if (hit != null) state.paintPyra(state.pyraStep * 9 + hit)
                                    }
                                    break
                                }
                            }
                        }
                    },
            ) {
                val face = state.pyraStep
                val yaw = state.yaw.toDouble()
                val pitch = state.pitch.toDouble()
                for (k in 0 until 9) {
                    val pts = renderer.projectFaceCell(face, k, yaw, pitch, size.width, size.height)
                    val path = Path()
                    path.moveTo(pts[0].x, pts[0].y)
                    path.lineTo(pts[1].x, pts[1].y)
                    path.lineTo(pts[2].x, pts[2].y)
                    path.close()
                    val v = state.pyraBoard[face * 9 + k]
                    drawPath(path, if (v < 0) Color(0xFF2B3242) else paletteColor(v))
                    drawPath(path, Color(0x55000000), style = Stroke(width = 2f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 4) {
                    val remaining = state.pyraRemaining(i)
                    val selected = state.pyraSelected == i
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .alpha(if (remaining == 0) 0.5f else 1f)
                                .clip(RoundedCornerShape(13.dp))
                                .background(paletteColor(i))
                                .then(
                                    if (selected) Modifier.border(2.dp, Color(0xFFEEF2FF), RoundedCornerShape(13.dp))
                                    else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(13.dp)),
                                )
                                .clickable { state.selectPyraColour(i) },
                        )
                        Text(
                            "$remaining",
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remaining == 0) Good else Muted,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { state.clearPyraminx() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                ) { Text("Clear", fontSize = 13.sp) }
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = { state.autofillPyraminx() },
                    enabled = state.pyraPainted() < 36,
                ) { Text("Fill rest", fontSize = 12.sp, color = Muted) }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { state.solvePyraminx() },
                    enabled = state.pyraPainted() == 36 && !state.solving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk),
                ) {
                    Text(if (state.solving) "Solving…" else "Solve", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PyraSolutionPanel(state: AppState) {
    val sol = state.pyraSolution ?: return
    val listState = rememberLazyListState()
    LaunchedEffect(state.pyraCurrentStep) {
        if (state.pyraCurrentStep >= 0) listState.animateScrollToItem(state.pyraCurrentStep)
    }
    Surface(color = Panel, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.pyraDone) "Solved!" else "Solution",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.pyraCurrentStep >= 0) "Now: ${sol.steps[state.pyraCurrentStep].label}" else "${sol.steps.size} moves",
                        fontSize = 11.5.sp,
                        color = if (state.pyraCurrentStep >= 0) Color(0xFF8BC4FF) else Muted,
                    )
                    val phase = sol.steps.getOrNull(if (state.pyraCurrentStep >= 0) state.pyraCurrentStep else state.pyraPlayIndex)?.phase
                    if (!phase.isNullOrEmpty()) {
                        Text(phase, fontSize = 10.5.sp, color = Muted, maxLines = 1)
                    }
                }
                TextButton(
                    onClick = { state.stepPyraminxBackward() },
                    enabled = !state.pyraPlaying && !state.pyraStepping && state.pyraPlayIndex > 0,
                ) { Text("◀", fontSize = 15.sp, color = Color(0xFFCFE0FF)) }
                TextButton(
                    onClick = { state.stepPyraminxForward() },
                    enabled = !state.pyraPlaying && !state.pyraStepping && state.pyraPlayIndex < sol.steps.size,
                ) { Text("▶", fontSize = 15.sp, color = Color(0xFFCFE0FF)) }
                Button(
                    onClick = { state.playPyraminx() },
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x336EA8FF), contentColor = Color(0xFFCFE0FF)),
                ) {
                    Text(
                        when {
                            state.pyraPlaying -> "Pause"
                            state.pyraPlayIndex == 0 && !state.pyraDone -> "Solve"
                            else -> "Play"
                        },
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { state.closePyraminxSolution() }) { Text("×", fontSize = 20.sp, color = Muted) }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(sol.steps) { i, step ->
                    val active = i == state.pyraCurrentStep
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (active) Accent else Color(0x0DFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color.Transparent else Line),
                        modifier = Modifier.clickable { state.jumpPyraminx(i) },
                    ) {
                        Text(
                            step.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) AccentInk else Color(0xFFEEF2FF),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Speed", fontSize = 11.sp, color = Muted)
                Slider(
                    value = state.speed,
                    onValueChange = { state.changeSpeed(it) },
                    onValueChangeFinished = { state.persistSpeed() },
                    valueRange = 0.1f..2.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- small bits

@Composable
private fun CircleButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color(0x990A0E16),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = Modifier.size(38.dp).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 19.sp, color = Muted)
        }
    }
}
