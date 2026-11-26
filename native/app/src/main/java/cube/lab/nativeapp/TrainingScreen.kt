package cube.lab.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay

private val TBg = Color(0xFF080A10)
private val TPanel = Color(0xFF11151F)
private val TPanel2 = Color(0xFF1A2030)
private val TAccent = Color(0xFF6EA8FF)
private val TInk = Color(0xFFEEF2FF)
private val TMuted = Color(0xFF93A0B8)
private val TLine = Color(0x1AFFFFFF)
private val TGreen = Color(0xFF9FE6B4)
private val TBad = Color(0xFFFFC9CC)

private const val ARM_MS = 350L
private const val STOP_LOCKOUT_NS = 500_000_000L

private sealed interface TrainingRoute {
    data object Home : TrainingRoute
    data object Timer : TrainingRoute
    data class LessonRoute(val lesson: Lesson) : TrainingRoute
}

/** Tutorials, speedcube timer and local records. Owns its list -> lesson -> timer navigation. */
@Composable
fun TrainingScreen(onClose: () -> Unit) {
    var route by remember { mutableStateOf<TrainingRoute>(TrainingRoute.Home) }
    BackHandler(enabled = route != TrainingRoute.Home) { route = TrainingRoute.Home }
    Box(Modifier.fillMaxSize().background(TBg)) {
        when (val current = route) {
            TrainingRoute.Home -> TrainingHome(
                onClose = onClose,
                onTimer = { route = TrainingRoute.Timer },
                onLesson = { route = TrainingRoute.LessonRoute(it) },
            )
            TrainingRoute.Timer -> TrainingTimer(onBack = { route = TrainingRoute.Home })
            is TrainingRoute.LessonRoute -> TrainingLesson(current.lesson, onBack = { route = TrainingRoute.Home })
        }
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun TrainingHome(
    onClose: () -> Unit,
    onTimer: () -> Unit,
    onLesson: (Lesson) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TrainingHeader(
            title = "Training",
            subtitle = "Tutorials · timer · local records",
            onBack = onClose,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "timer") { TimerCard(onTimer) }
            TrainingContent.tracks.forEach { (track, lessons) ->
                item(key = "track:$track") {
                    Text(
                        track,
                        modifier = Modifier.padding(top = 10.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TAccent,
                    )
                }
                items(lessons, key = { it.id }) { lesson ->
                    LessonRow(lesson, onClick = { onLesson(lesson) })
                }
            }
        }
    }
}

@Composable
private fun TimerCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x226EA8FF),
        border = BorderStroke(1.dp, Color(0x556EA8FF)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Speedcube timer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TInk)
                Text("Hold to arm · inspection · Ao5/Ao12", fontSize = 11.5.sp, color = TMuted)
            }
            Text("→", fontSize = 20.sp, color = TAccent)
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = TPanel,
        border = BorderStroke(1.dp, TLine),
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)) {
            Text(lesson.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = TInk)
            Text(lesson.goal, fontSize = 11.5.sp, color = TMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(lesson.algorithmName, fontSize = 10.5.sp, color = Color(0xFF8BC4FF), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------------------------------------------------------------- lesson

@Composable
private fun TrainingLesson(lesson: Lesson, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TrainingHeader(title = lesson.title, subtitle = lesson.puzzle, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(lesson.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TInk)
            Text(lesson.goal, fontSize = 13.sp, color = TMuted, modifier = Modifier.padding(top = 4.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = TPanel2,
                border = BorderStroke(1.dp, TLine),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(lesson.algorithmName, fontSize = 11.sp, color = TMuted)
                    Text(
                        lesson.moves,
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = TInk,
                    )
                }
            }

            Text(
                "How it works",
                modifier = Modifier.padding(top = 18.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TAccent,
            )
            Text(
                lesson.explanation,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = TInk,
            )

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0x1AFFC9CC),
                border = BorderStroke(1.dp, Color(0x33FFC9CC)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Common mistake", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TBad)
                    Text(
                        lesson.commonMistake,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = TInk,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------------------------------------------------------------- timer

@Composable
private fun TrainingTimer(onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember(context) { File(context.filesDir, TrainingStore.FILE_NAME) }
    var solves by remember { mutableStateOf(TrainingStore.read(file)) }

    var puzzle by remember { mutableStateOf(TrainingPuzzle.P3) }
    var inspectionOn by remember { mutableStateOf(false) }

    var runningSince by remember { mutableStateOf<Long?>(null) }
    var inspectionSince by remember { mutableStateOf<Long?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var inspectionMs by remember { mutableLongStateOf(0L) }
    var pressed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var activePenalty by remember { mutableStateOf(Penalty.NONE) }
    var lastDnf by remember { mutableStateOf(false) }
    var lockoutUntil by remember { mutableStateOf(0L) }
    var hint by remember { mutableStateOf<String?>(null) }

    val puzzleSolves = remember(solves, puzzle) { solves.filter { it.puzzle == puzzle } }
    val stats = remember(puzzleSolves, puzzle.big) {
        trainingSummary(puzzleSolves.map { it.time }, puzzle.big)
    }

    fun startTimer(now: Long) {
        activePenalty = inspectionSince?.let { TimerLogic.inspectionPenalty((now - it) / 1_000_000) } ?: Penalty.NONE
        inspectionSince = null
        inspectionMs = 0
        elapsedMs = 0
        lastDnf = false
        runningSince = now
        hint = null
    }

    fun stopTimer(now: Long) {
        val since = runningSince ?: return
        runningSince = null
        val rawMs = ((now - since) / 1_000_000).coerceAtLeast(0)
        val solve = TrainingSolve(puzzle, rawMs, activePenalty, System.currentTimeMillis())
        val next = solves + solve
        solves = next
        if (!TrainingStore.write(file, next)) hint = "Could not save this solve."
        elapsedMs = solve.time.finalMs ?: rawMs
        lastDnf = solve.time.finalMs == null
        activePenalty = Penalty.NONE
        lockoutUntil = now + STOP_LOCKOUT_NS
        armed = false
    }

    // Arms the pad once the finger has been held long enough.
    LaunchedEffect(pressed) {
        if (!pressed) {
            armed = false
            return@LaunchedEffect
        }
        delay(ARM_MS)
        if (pressed) armed = true
    }

    // Drives the running clock and the inspection countdown.
    LaunchedEffect(runningSince, inspectionSince) {
        while (runningSince != null || inspectionSince != null) {
            val now = System.nanoTime()
            runningSince?.let { elapsedMs = (now - it) / 1_000_000 }
            inspectionSince?.let { inspectionMs = (now - it) / 1_000_000 }
            delay(16)
        }
    }

    val running = runningSince != null
    val inspecting = inspectionSince != null
    val display = when {
        running -> formatTime(elapsedMs)
        inspecting -> formatTime((TimerLogic.INSPECTION_LIMIT_MS - inspectionMs).coerceAtLeast(0))
        lastDnf -> "DNF"
        else -> formatTime(elapsedMs)
    }
    val penaltyNow = if (inspecting) TimerLogic.inspectionPenalty(inspectionMs) else Penalty.NONE

    val padColor = when {
        running -> Color(0xFF14243A)
        armed -> Color(0x3333CC66)
        pressed -> Color(0x33CC4444)
        else -> TPanel
    }
    val padHint = when {
        hint != null -> hint!!
        running -> "Tap to stop"
        armed -> "Release to start"
        pressed -> "Keep holding…"
        inspecting -> "Hold 0.35 s, then release to start"
        inspectionOn -> "Touch to begin inspection"
        else -> "Hold 0.35 s to arm, release to start"
    }

    Column(Modifier.fillMaxSize()) {
        TrainingHeader(
            title = "Timer",
            subtitle = "Hold the pad · release to start · tap to stop",
            onBack = onBack,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(TrainingPuzzle.entries) { entry ->
                val selected = entry == puzzle
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (selected) Color(0x336EA8FF) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selected) Color(0x806EA8FF) else TLine),
                    modifier = Modifier.clickable(enabled = !running) {
                        puzzle = entry
                        elapsedMs = 0
                        inspectionSince = null
                        inspectionMs = 0
                        hint = null
                    },
                ) {
                    Text(
                        entry.label,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) TInk else TMuted,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "15 s inspection",
                modifier = Modifier.weight(1f),
                fontSize = 11.5.sp,
                color = TMuted,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (inspectionOn) Color(0x336EA8FF) else Color.Transparent,
                border = BorderStroke(1.dp, if (inspectionOn) Color(0x806EA8FF) else TLine),
                modifier = Modifier.clickable(enabled = !running) {
                    inspectionOn = !inspectionOn
                    if (!inspectionOn) {
                        inspectionSince = null
                        inspectionMs = 0
                    }
                },
            ) {
                Text(
                    if (inspectionOn) "On" else "Off",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (inspectionOn) TInk else TMuted,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Single", formatTime(stats.singleMs), Modifier.weight(1f))
            if (puzzle.big) {
                StatCard("Mo3", formatTime(stats.mo3Ms), Modifier.weight(1f))
            } else {
                StatCard("Ao5", formatTime(stats.ao5Ms), Modifier.weight(1f))
                StatCard("Ao12", formatTime(stats.ao12Ms), Modifier.weight(1f))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(padColor)
                .border(1.dp, if (armed) TGreen else TLine, RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val now = System.nanoTime()
                        if (now < lockoutUntil) return@awaitEachGesture
                        if (runningSince != null) {
                            stopTimer(now)
                            return@awaitEachGesture
                        }
                        hint = null
                        if (inspectionOn && inspectionSince == null) inspectionSince = now
                        pressed = true
                        try {
                            var heldMs = 0L
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    heldMs = (System.nanoTime() - now) / 1_000_000
                                    break
                                }
                            }
                            val wasArmed = armed
                            when {
                                wasArmed || heldMs >= ARM_MS -> startTimer(System.nanoTime())
                                heldMs < 100 -> Unit // accidental brush
                                else -> hint = "Hold for 0.35 s to arm"
                            }
                        } finally {
                            pressed = false
                            armed = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    display,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = TInk,
                    maxLines = 1,
                )
                if (inspecting) {
                    Text(
                        when (penaltyNow) {
                            Penalty.PLUS2 -> "Inspection · +2 if you start now"
                            Penalty.DNF -> "Inspection · DNF if you start now"
                            Penalty.NONE -> "Inspection · 15 s"
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.5.sp,
                        color = if (penaltyNow == Penalty.NONE) TMuted else TBad,
                    )
                }
                Text(
                    padHint,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                    fontSize = 12.sp,
                    color = if (armed) TGreen else TMuted,
                )
            }
        }

        RecentSolves(puzzleSolves)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = TPanel,
        border = BorderStroke(1.dp, TLine),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 10.sp, color = TMuted)
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TInk,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentSolves(puzzleSolves: List<TrainingSolve>) {
    val recent = puzzleSolves.takeLast(5).reversed()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (recent.isEmpty()) {
            Text("No solves yet.", fontSize = 11.5.sp, color = TMuted)
        } else {
            recent.forEachIndexed { i, solve ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = TPanel,
                    border = BorderStroke(1.dp, TLine),
                ) {
                    Column(
                        Modifier.padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("#${puzzleSolves.size - i}", fontSize = 9.5.sp, color = TMuted)
                        Text(
                            formatTime(solve.time.finalMs),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TInk,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared

@Composable
private fun TrainingHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TPanel2)
                .border(1.dp, TLine, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", fontSize = 16.sp, color = TInk)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TInk)
            Text(subtitle, fontSize = 11.5.sp, color = TMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
