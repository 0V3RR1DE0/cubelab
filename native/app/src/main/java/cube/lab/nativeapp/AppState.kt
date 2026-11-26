package cube.lab.nativeapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class Screen { Home, Cube, Picker, Pyraminx, Training }
enum class Phase { Input, Pattern }

data class Sol(val start: IntArray, val steps: List<Step>, val doneTitle: String)

private class SolveOut(val moves: List<String>, val steps: List<Step>? = null)

private fun humanSteps(cube: Cube, phases: List<HumanPhase>): List<Step> {
    val steps = ArrayList<Step>()
    for (phase in phases) {
        for (name in simplifyMoves(cube, phase.moves)) {
            val info = cube.move(name)
            steps.add(Step(name, info.turns, info, phase.name, phase.alg))
        }
    }
    return steps
}

data class PatternOption(val name: String, val board: IntArray)

data class CustomCube(val id: Long, val name: String, val n: Int, val solved: IntArray)

data class PyraSol(val start: IntArray, val steps: List<PyraStep>)

const val FACE_ORDER_STR = "U F R B L D"
val FACE_ORDER = intArrayOf(0, 2, 4, 3, 5, 1)
val FACE_NAMES = arrayOf("Up", "Down", "Front", "Back", "Right", "Left")

private val FACE_VIEW = arrayOf(
    0f to (PI / 2).toFloat(),
    0f to (-PI / 2).toFloat(),
    0f to 0f,
    PI.toFloat() to 0f,
    (-PI / 2).toFloat() to 0f,
    (PI / 2).toFloat() to 0f,
)

class AppState(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var screen by mutableStateOf(Screen.Home)
    var scannerOpen by mutableStateOf(false)
    var homeTab by mutableIntStateOf(0)
    var n by mutableIntStateOf(3)
    var cube by mutableStateOf(Cube(3))
    var board by mutableStateOf(IntArray(54) { -1 })
    var step by mutableIntStateOf(0)
    var selected by mutableIntStateOf(0)
    var selectedCell by mutableStateOf<Int?>(null)
    var yaw by mutableFloatStateOf(FACE_VIEW[0].first)
    var pitch by mutableFloatStateOf(FACE_VIEW[0].second)
    var anim by mutableStateOf<TurnAnim?>(null)
    var solution by mutableStateOf<Sol?>(null)
    var playing by mutableStateOf(false)
    var stepping by mutableStateOf(false)
    var solving by mutableStateOf(false)
    private val prefs = context.getSharedPreferences("cube_lab", Context.MODE_PRIVATE)
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f).coerceIn(0.1f, 2.5f))
    var zoom by mutableFloatStateOf(1f)

    fun changeSpeed(v: Float) {
        speed = v.coerceIn(0.1f, 2.5f)
        prefs.edit().putFloat("speed", speed).apply()
    }

    /** Synchronous save on slider release, so a force-stop cannot lose it. */
    fun persistSpeed() {
        prefs.edit().putFloat("speed", speed).commit()
    }
    var phase by mutableStateOf(Phase.Input)
    var target by mutableStateOf<IntArray?>(null)
    var targetName by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var pickerN by mutableIntStateOf(3)
    var pickerOptions by mutableStateOf<List<PatternOption>>(emptyList())
    var pickerLoading by mutableStateOf(false)
    var solutionDone by mutableStateOf(false)
    var playIndex by mutableIntStateOf(0)
    var currentStep by mutableIntStateOf(-1)
    private var pauseRequested = false

    var pyraBoard by mutableStateOf(IntArray(36) { -1 })
    var pyraSelected by mutableIntStateOf(0)
    var pyraAnim by mutableStateOf<PyraAnim?>(null)
    var pyraSolution by mutableStateOf<PyraSol?>(null)
    var pyraPlaying by mutableStateOf(false)
    var pyraStepping by mutableStateOf(false)
    var pyraDone by mutableStateOf(false)
    var issue by mutableStateOf<String?>(null)
    var highlight by mutableStateOf<Set<Int>>(emptySet())
    var pyraPlayIndex by mutableIntStateOf(0)
    var pyraCurrentStep by mutableIntStateOf(-1)
    var pyraStep by mutableIntStateOf(0)
    var pyraSelectedCell by mutableStateOf<Int?>(null)
    var customMode by mutableStateOf(false)
    var customCubes by mutableStateOf<List<CustomCube>>(emptyList())
    private val customsFile get() = File(context.filesDir, "custom_cubes.json")
    private var pyraPauseRequested = false

    private var viewJob: Job? = null
    private var playJob: Job? = null
    private var messageJob: Job? = null
    private var solveJob: Job? = null
    private var checkJob: Job? = null
    private var pickerJob: Job? = null

    var filling by mutableStateOf(false)
        private set

    init { loadCustoms() }

    val face: Int get() = FACE_ORDER[step]
    val totalCells: Int get() = 6 * n * n
    val colourCap: Int
        get() = if (n == 1) 1 else n * n

    fun dispose() {
        scope.cancel()
    }

    fun notify(text: String) {
        message = text
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(2600)
            message = null
        }
    }

    private fun emptyBoard(nn: Int = n) = IntArray(6 * nn * nn) { -1 }

    private fun cancelSolve() {
        solveJob?.cancel()
        solving = false
    }

    private fun loadCustoms() {
        customCubes = try {
            val f = customsFile
            if (!f.exists()) emptyList()
            else {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val a = o.getJSONArray("solved")
                    CustomCube(
                        o.getLong("id"),
                        o.getString("name"),
                        o.getInt("n"),
                        IntArray(a.length()) { a.getInt(it) },
                    )
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun persistCustoms(list: List<CustomCube>) {
        try {
            val arr = JSONArray()
            for (c in list) {
                val o = JSONObject()
                o.put("id", c.id)
                o.put("name", c.name)
                o.put("n", c.n)
                o.put("solved", JSONArray(c.solved.toList()))
                arr.put(o)
            }
            customsFile.writeText(arr.toString())
        } catch (t: Throwable) {
            notify("Could not save the custom cube.")
        }
    }

    fun saveCustom(n0: Int, solved: IntArray) {
        val c = CustomCube(System.currentTimeMillis(), "Custom ${n0}×$n0", n0, solved.copyOf())
        val list = customCubes + c
        customCubes = list
        persistCustoms(list)
    }

    fun deleteCustom(id: Long) {
        val list = customCubes.filter { it.id != id }
        customCubes = list
        persistCustoms(list)
        notify("Custom cube deleted.")
    }

    fun openCustomEditor(n0: Int) {
        openCube(n0)
        phase = Phase.Pattern
        customMode = true
        notify("Paint the solved state of your custom ${n0}×$n0.")
    }

    fun startCustom(c: CustomCube) {
        openCube(c.n)
        target = c.solved.copyOf()
        targetName = c.name
        phase = Phase.Input
        notify("Input your ${c.n}×${c.n} scramble.")
    }

    fun openCube(id: Int) {
        customMode = false
        issue = null
        highlight = emptySet()
        playJob?.cancel()
        viewJob?.cancel()
        solveJob?.cancel()
        checkJob?.cancel()
        solving = false
        n = id
        cube = Cube(id)
        board = emptyBoard(id)
        selectedCell = null
        target = null
        targetName = ""
        phase = Phase.Input
        step = 0
        selected = 0
        solution = null
        anim = null
        playing = false
        yaw = FACE_VIEW[FACE_ORDER[0]].first
        pitch = FACE_VIEW[FACE_ORDER[0]].second
        screen = Screen.Cube
    }

    fun openPatternEditor(id: Int) {
        openCube(id)
        phase = Phase.Pattern
        notify("Paint the target pattern, face by face.")
    }

    fun openTraining() {
        playJob?.cancel()
        viewJob?.cancel()
        solveJob?.cancel()
        checkJob?.cancel()
        solving = false
        screen = Screen.Training
    }

    fun openScanner() {
        if (n in 2..7 && phase == Phase.Input) scannerOpen = true
    }

    fun applyScan(values: IntArray) {
        scannerOpen = false
        val size = n * n
        if (values.size != size) {
            notify("Scan failed — try the face again.")
            return
        }
        val next = board.copyOf()
        val base = face * size
        for (i in 0 until size) next[base + i] = if (values[i] in 0..5) values[i] else -1
        board = next
        selectedCell = null
        afterPaint()
        notify("Face scanned — correct any misread cell, then press Next.")
    }

    fun openPyraminx() {
        playJob?.cancel()
        viewJob?.cancel()
        screen = Screen.Pyraminx
        pyraBoard = IntArray(36) { -1 }
        pyraSelected = 0
        pyraSolution = null
        pyraPlaying = false
        pyraDone = false
        pyraAnim = null
        pyraStep = 0
        val view = pyraFaceView(0)
        yaw = view.first
        pitch = view.second
        scope.launch { withContext(Dispatchers.Default) { Pyraminx.buildDistances() } }
    }

    fun selectPyraColour(colour: Int) {
        pyraSelected = colour.coerceIn(0, 3)
        val cell = pyraSelectedCell
        if (cell != null) {
            if (paintPyraCell(cell, pyraSelected)) pyraSelectedCell = null
        } else {
            val e = nextPyraEmptyCell()
            if (e != null) paintPyraCell(e, pyraSelected)
        }
        afterPyraPaint()
    }

    fun pyraPainted(): Int = pyraBoard.count { it >= 0 }

    fun pyraFaceDone(face: Int): Boolean {
        val base = face * 9
        for (i in 0 until 9) if (pyraBoard[base + i] < 0) return false
        return true
    }

    fun pyraFaceView(face: Int): Pair<Float, Float> {
        val n = Pyraminx.faces[face.coerceIn(0, 3)].normal
        val yaw = atan2(-n[0], n[2])
        val pitch = atan2(n[1], hypot(n[0], n[2]))
        return yaw.toFloat() to pitch.toFloat()
    }

    fun selectPyraFace(face: Int) {
        val f = face.coerceIn(0, 3)
        pyraStep = f
        pyraSelectedCell = null
        val v = pyraFaceView(f)
        animateView(v.first, v.second)
    }

    fun nextPyraFace() {
        if (pyraStep >= 3) return
        if (!pyraFaceDone(pyraStep)) return
        selectPyraFace(pyraStep + 1)
    }

    fun prevPyraFace() {
        if (pyraStep > 0) selectPyraFace(pyraStep - 1)
    }

    fun pyraUsed(colour: Int): Int = pyraBoard.count { it == colour }

    fun pyraRemaining(colour: Int): Int = (9 - pyraUsed(colour)).coerceAtLeast(0)

    private fun afterPyraPaint() {
        if (pyraRemaining(pyraSelected) == 0) {
            val next = (0 until 4).firstOrNull { pyraRemaining(it) > 0 }
            if (next != null) pyraSelected = next
        }
    }

    private fun paintPyraCell(cell: Int, colour: Int): Boolean {
        if (pyraBoard[cell] != colour && pyraUsed(colour) >= 9) {
            notify("Only 9 stickers can be this colour.")
            return false
        }
        val next = pyraBoard.copyOf()
        next[cell] = colour
        pyraBoard = next
        return true
    }

    private fun nextPyraEmptyCell(): Int? {
        val base = pyraStep * 9
        for (i in 0 until 9) if (pyraBoard[base + i] < 0) return base + i
        for (i in 0 until 36) if (pyraBoard[i] < 0) return i
        return null
    }

    fun paintPyra(cell: Int) {
        if (pyraSolution != null) {
            playJob?.cancel()
            pyraPlaying = false
            pyraSolution = null
            pyraDone = false
            pyraAnim = null
        }
        if (pyraBoard[cell] >= 0) {
            if (pyraSelectedCell == cell) {
                val next = pyraBoard.copyOf()
                next[cell] = -1
                pyraBoard = next
                pyraSelectedCell = null
            } else {
                pyraSelectedCell = cell
            }
            return
        }
        if (paintPyraCell(cell, pyraSelected)) {
            pyraSelectedCell = null
            afterPyraPaint()
        }
    }

    fun autofillPyraminx() {
        if (pyraSolution != null) {
            playJob?.cancel()
            pyraPlaying = false
            pyraSolution = null
            pyraDone = false
            pyraAnim = null
        }
        val filled = InputFill.completePyraminx(pyraBoard)
        if (filled == null) {
            notify("Those painted stickers cannot be completed into a solved Pyraminx.")
            return
        }
        pyraBoard = filled
        pyraSelectedCell = null
    }

    fun clearPyraminx() {
        playJob?.cancel()
        pyraPlaying = false
        pyraStepping = false
        pyraSolution = null
        pyraDone = false
        pyraAnim = null
        pyraBoard = IntArray(36) { -1 }
        pyraSelectedCell = null
        selectPyraFace(0)
    }

    fun solvePyraminx() {
        if (solving || pyraSolution != null) return
        if (pyraBoard.any { it < 0 }) {
            notify("Paint all 36 stickers first.")
            return
        }
        val input = pyraBoard.copyOf()
        solving = true
        scope.launch {
            val phases = withContext(Dispatchers.Default) { HumanPyra.solve(input) }
            solving = false
            if (phases == null) {
                notify("Those stickers cannot be a real Pyraminx.")
                return@launch
            }
            if (phases.isEmpty()) {
                notify("This Pyraminx is already solved.")
                return@launch
            }
            val steps = withContext(Dispatchers.Default) {
                phases.flatMap { p ->
                    Pyraminx.mergeMoves(p.moves).map { s ->
                        PyraStep(s.key, s.axis, s.tip, s.count, s.label, p.name, p.alg)
                    }
                }
            }
            pyraSolution = PyraSol(input, steps)
            pyraDone = false
            pyraPlayIndex = 0
            pyraCurrentStep = -1
            pyraPlaying = false
            pyraStepping = false
            animateView(0.62f, 0.42f)
        }
    }

    fun playPyraminx() {
        val sol = pyraSolution ?: return
        if (pyraStepping) return
        if (pyraPlaying) {
            pyraPauseRequested = true
            return
        }
        if (pyraDone) {
            pyraPlayIndex = 0
            pyraBoard = sol.start.copyOf()
            pyraDone = false
        }
        if (pyraPlayIndex >= sol.steps.size) return
        pyraPlaying = true
        pyraPauseRequested = false
        playJob = scope.launch {
            var i = pyraPlayIndex
            while (i < sol.steps.size && !pyraPauseRequested) {
                val step = sol.steps[i]
                val move = Pyraminx.moves[step.key] ?: break
                pyraCurrentStep = i
                val total = step.count
                for (q in 0 until total) {
                    val duration = (450 / speed).toInt().coerceAtLeast(200)
                    animateValue(duration) { v ->
                        pyraAnim = PyraAnim(move, move.angle * smooth(v))
                    }
                    pyraBoard = Pyraminx.applyPerm(pyraBoard, move.perm)
                    pyraAnim = null
                    delay((220 / speed).toLong())
                }
                i++
                pyraPlayIndex = i
                if (pyraPauseRequested) break
            }
            if (pyraPlayIndex >= sol.steps.size) pyraDone = Pyraminx.isSolved(pyraBoard)
            pyraPlaying = false
            pyraCurrentStep = -1
            pyraAnim = null
        }
    }

    fun stepPyraminxForward() {
        val sol = pyraSolution ?: return
        if (pyraPlaying || pyraStepping) return
        if (pyraPlayIndex >= sol.steps.size) return
        val index = pyraPlayIndex
        val step = sol.steps[index]
        val move = Pyraminx.moves[step.key] ?: return
        pyraStepping = true
        pyraCurrentStep = index
        pyraAnim = null
        playJob = scope.launch {
            for (q in 0 until step.count) {
                val duration = (450 / speed).toInt().coerceAtLeast(200)
                animateValue(duration) { v -> pyraAnim = PyraAnim(move, move.angle * smooth(v)) }
                pyraBoard = Pyraminx.applyPerm(pyraBoard, move.perm)
                pyraAnim = null
                delay((220 / speed).toLong())
            }
            pyraPlayIndex = index + 1
            pyraDone = pyraPlayIndex >= sol.steps.size
            pyraStepping = false
        }
    }

    fun stepPyraminxBackward() {
        val sol = pyraSolution ?: return
        if (pyraPlaying || pyraStepping) return
        if (pyraPlayIndex <= 0) return
        val index = pyraPlayIndex - 1
        val step = sol.steps[index]
        val invKey = if (step.key.endsWith("'")) step.key.dropLast(1) else step.key + "'"
        val move = Pyraminx.moves[invKey] ?: return
        pyraStepping = true
        pyraCurrentStep = index
        pyraAnim = null
        playJob = scope.launch {
            for (q in 0 until step.count) {
                val duration = (450 / speed).toInt().coerceAtLeast(200)
                animateValue(duration) { v -> pyraAnim = PyraAnim(move, move.angle * smooth(v)) }
                pyraBoard = Pyraminx.applyPerm(pyraBoard, move.perm)
                pyraAnim = null
                delay((220 / speed).toLong())
            }
            pyraPlayIndex = index
            pyraDone = false
            pyraStepping = false
        }
    }

    fun jumpPyraminx(index: Int) {
        val sol = pyraSolution ?: return
        playJob?.cancel()
        pyraPlaying = false
        pyraStepping = false
        pyraPauseRequested = false
        var b = sol.start.copyOf()
        for (i in 0..index) {
            val step = sol.steps.getOrNull(i) ?: break
            val move = Pyraminx.moves[step.key] ?: continue
            repeat(step.count) { b = Pyraminx.applyPerm(b, move.perm) }
        }
        pyraBoard = b
        pyraAnim = null
        pyraPlayIndex = index + 1
        pyraDone = index == sol.steps.size - 1
        pyraCurrentStep = -1
    }

    fun closePyraminxSolution() {
        playJob?.cancel()
        pyraPlaying = false
        pyraStepping = false
        pyraPauseRequested = false
        pyraSolution = null
        pyraAnim = null
        pyraDone = false
        pyraCurrentStep = -1
        pyraPlayIndex = 0
    }

    fun openPicker(id: Int) {
        pickerJob?.cancel()
        pickerN = id
        screen = Screen.Picker
        pickerLoading = true
        pickerOptions = emptyList()
        pickerJob = scope.launch {
            val options = withContext(Dispatchers.Default) {
                if (!Kociemba.ensure(context)) return@withContext emptyList()
                val nn = id
                val cubeN = Cube(nn)
                val list = ArrayList<PatternOption>()
                val seen = HashSet<String>()
                val names = patternNames(nn)
                for (i in names.indices) {
                    val b = patternBoard(nn, i)
                    if (i > 0 && cubeN.isSolved(b)) continue
                    if (!seen.add(b.joinToString(","))) continue
                    val ok = if (nn <= 3) patternPossible(b, nn)
                    else runCatching { BigCube.solve(cubeN, b) != null }.getOrDefault(false)
                    if (ok) list.add(PatternOption(names[i], b))
                }
                list
            }
            if (pickerN != id || screen != Screen.Picker) return@launch
            pickerOptions = options
            pickerLoading = false
        }
    }

    fun startPattern(option: PatternOption) {
        n = pickerN
        cube = Cube(n)
        board = emptyBoard(n)
        selectedCell = null
        target = option.board.copyOf()
        targetName = option.name
        phase = Phase.Input
        step = 0
        selected = 0
        solution = null
        anim = null
        yaw = FACE_VIEW[FACE_ORDER[0]].first
        pitch = FACE_VIEW[FACE_ORDER[0]].second
        screen = Screen.Cube
        notify("Input your cube to reach ${option.name}.")
    }

    fun goHome() {
        playJob?.cancel()
        viewJob?.cancel()
        solveJob?.cancel()
        checkJob?.cancel()
        pickerJob?.cancel()
        solving = false
        playing = false
        solution = null
        anim = null
        scannerOpen = false
        screen = Screen.Home
    }

    // ------------------------------------------------------------ input

    fun paintedCount(): Int = board.count { it >= 0 }

    fun faceDone(face: Int): Boolean {
        val b = face * n * n
        for (i in 0 until n * n) if (board[b + i] < 0) return false
        return true
    }

    fun allDone(): Boolean = paintedCount() == totalCells

    fun used(colour: Int): Int = board.count { it == colour }

    fun remaining(colour: Int): Int = (colourCap - used(colour)).coerceAtLeast(0)

    private fun afterPaint() {
        issue = null
        highlight = emptySet()
        if (n <= 3 && allDone()) {
            val d = diagnoseCube(cube, board, n)
            if (d != null) {
                issue = d.message
                highlight = d.cells.toSet()
            }
        }
        if (remaining(selected) == 0) {
            val next = (0 until 6).firstOrNull { remaining(it) > 0 }
            if (next != null) selected = next
        }
    }

    private fun paintCell(cell: Int, colour: Int): Boolean {
        if (board[cell] != colour && used(colour) >= colourCap) {
            notify("Only $colourCap stickers can be this colour.")
            return false
        }
        val next = board.copyOf()
        next[cell] = colour
        board = next
        afterPaint()
        return true
    }

    fun nextEmptyCell(): Int? {
        val base = face * n * n
        for (i in 0 until n * n) if (board[base + i] < 0) return base + i
        for (i in 0 until totalCells) if (board[i] < 0) return i
        return null
    }

    /** Palette tap: replace a selected cell, otherwise fill the next empty slot. */
    fun selectColour(colour: Int) {
        cancelSolve()
        if (solution != null) {
            solution = null
            playing = false
            stepping = false
            playJob?.cancel()
        }
        selected = colour
        val cell = selectedCell
        if (cell != null) {
            if (paintCell(cell, colour)) selectedCell = null
        } else {
            val e = nextEmptyCell()
            if (e != null) paintCell(e, colour)
        }
    }

    fun paint(cell: Int) {
        cancelSolve()
        if (solution != null) {
            solution = null
            playing = false
            stepping = false
            playJob?.cancel()
        }
        if (board[cell] >= 0) {
            if (selectedCell == cell) {
                // second tap on the same filled cell removes its colour
                val next = board.copyOf()
                next[cell] = -1
                board = next
                selectedCell = null
                afterPaint()
            } else {
                selectedCell = cell
            }
            return
        }
        if (used(selected) >= colourCap) {
            notify("Only $colourCap stickers can be this colour.")
            return
        }
        selectedCell = null
        val next = board.copyOf()
        next[cell] = selected
        board = next
        afterPaint()
    }

    /** Complete the painted cells into a legal cube (scheme/piece aware). */
    fun autofill() {
        if (filling) return
        cancelSolve()
        val snapshot = board.copyOf()
        val nn = n
        filling = true
        scope.launch {
            val filled = withContext(Dispatchers.Default) { InputFill.complete(nn, snapshot) }
            filling = false
            if (nn != n || screen != Screen.Cube || !board.contentEquals(snapshot)) return@launch
            if (filled == null) {
                notify("Those painted stickers cannot be completed into a real cube.")
                return@launch
            }
            board = filled
            selectedCell = null
            afterPaint()
        }
    }

    fun clearBoard() {
        cancelSolve()
        issue = null
        highlight = emptySet()
        playJob?.cancel()
        playing = false
        stepping = false
        solution = null
        solutionDone = false
        anim = null
        selectedCell = null
        board = emptyBoard()
    }

    fun selectStep(index: Int) {
        val s = index.coerceIn(0, 5)
        if (s == step) return
        selectedCell = null
        step = s
        animateView(FACE_VIEW[FACE_ORDER[s]].first, FACE_VIEW[FACE_ORDER[s]].second)
    }

    fun nextFace() {
        if (step == 5) {
            if (phase == Phase.Pattern) checkPattern()
            return
        }
        if (!faceDone(face)) return
        selectStep(step + 1)
    }

    fun prevFace() {
        selectStep(step - 1)
    }

    fun animateView(targetYaw: Float, targetPitch: Float) {
        viewJob?.cancel()
        val startYaw = yaw
        var delta = targetYaw - startYaw
        delta = ((delta + PI.toFloat()) % (2 * PI.toFloat()) + 2 * PI.toFloat()) % (2 * PI.toFloat()) -
            PI.toFloat()
        val startPitch = pitch
        viewJob = scope.launch {
            animateValue(600) { v ->
                yaw = startYaw + delta * ease(v)
                pitch = startPitch + (targetPitch - startPitch) * ease(v)
            }
            yaw = targetYaw
            pitch = targetPitch
        }
    }

    fun dragView(dx: Float, dy: Float) {
        viewJob?.cancel()
        yaw -= dx * 0.01f
        pitch = (pitch + dy * 0.01f).coerceIn(-1.5f, 1.5f)
    }

    // ------------------------------------------------------------ solving

    fun checkPattern() {
        if (phase != Phase.Pattern || !allDone()) return
        val nn = n
        val draft = board.copyOf()
        if (customMode) {
            val faces = (0 until 6).map { draft[it * nn * nn] }.toSet()
            if (faces.size < 6) {
                notify("Force cubes with duplicate colours are not supported yet.")
                return
            }
        }
        checkJob = scope.launch {
            solving = true
            val ok = withContext(Dispatchers.Default) {
                if (!Kociemba.ensure(context)) return@withContext false
                if (nn <= 3) patternPossible(draft, nn)
                else {
                    val cubeN = Cube(nn)
                    val perm = BigCube.inferHomePerm(cubeN, draft) ?: return@withContext false
                    BigCube.solve(cubeN, BigCube.mapToHome(draft, perm)) != null
                }
            }
            solving = false
            if (nn != n || screen != Screen.Cube || phase != Phase.Pattern ||
                !board.contentEquals(draft)
            ) return@launch
            if (!ok) {
                notify("That pattern is not a real cube state.")
                return@launch
            }
            if (customMode) {
                saveCustom(nn, draft)
                target = draft
                targetName = "Custom ${nn}×$nn"
                customMode = false
                phase = Phase.Input
                board = emptyBoard()
                step = 0
                selectStep(0)
                animateView(FACE_VIEW[FACE_ORDER[0]].first, FACE_VIEW[FACE_ORDER[0]].second)
                notify("Custom cube saved. Now input your scramble.")
                return@launch
            }
            target = draft
            targetName = "your pattern"
            phase = Phase.Input
            board = emptyBoard()
            step = 0
            selectStep(0)
            animateView(FACE_VIEW[FACE_ORDER[0]].first, FACE_VIEW[FACE_ORDER[0]].second)
            notify("Pattern is possible. Now input your cube.")
        }
    }

    fun solve() {
        if (solving || phase != Phase.Input) return
        if (!allDone()) {
            notify("Input every face first.")
            return
        }
        if (n > 7) {
            notify("Solving ${n}×$n is not supported.")
            return
        }
        if (n == 1) {
            notify("A 1×1 cannot be turned.")
            return
        }
        val input = board.copyOf()
        val earlyIssue = diagnoseCube(cube, input, n)
        if (earlyIssue != null) {
            issue = earlyIssue.message
            highlight = earlyIssue.cells.toSet()
            notify(earlyIssue.message)
            return
        }
        issue = null
        highlight = emptySet()
        val wantedTarget = target?.copyOf()
        val nn = n
        val cubeNow = cube
        playJob?.cancel()
        solving = true
        solveJob = scope.launch {
            val result: SolveOut? = withContext(Dispatchers.Default) {
                if (!Kociemba.ensure(context)) return@withContext null
                if (nn >= 4) {
                    val perm = BigCube.inferHomePerm(cubeNow, input) ?: return@withContext null
                    val mappedInput = BigCube.mapToHome(input, perm)
                    if (wantedTarget != null) {
                        val targetPerm = BigCube.inferHomePerm(cubeNow, wantedTarget) ?: return@withContext null
                        val mappedTarget = BigCube.mapToHome(wantedTarget, targetPerm)
                        val toSolvedInput = BigCube.solve(cubeNow, mappedInput) ?: return@withContext null
                        val toSolvedTarget = BigCube.solve(cubeNow, mappedTarget) ?: return@withContext null
                        val all = toSolvedInput + invSeq(toSolvedTarget)
                        val out = cubeNow.apply(mappedInput, all)
                        if (!out.contentEquals(mappedTarget)) return@withContext null
                        if (!cubeNow.apply(input, all).contentEquals(wantedTarget)) return@withContext null
                        return@withContext SolveOut(all)
                    }
                    if (nn == 4) {
                        val phases = runCatching { HumanBig4.solve(cubeNow, mappedInput) }.getOrNull()
                        if (phases != null) {
                            val flat = phases.flatMap { it.moves }
                            val mappedOut = cubeNow.apply(mappedInput, flat)
                            val originalOut = cubeNow.apply(input, flat)
                            if (cubeNow.isSolved(mappedOut) && cubeNow.isSolved(originalOut)) {
                                return@withContext SolveOut(flat, humanSteps(cubeNow, phases))
                            }
                        }
                    }
                    return@withContext BigCube.solve(cubeNow, mappedInput)?.let { SolveOut(it) }
                }
                if (wantedTarget != null) {
                    val moves = solveInto(cubeNow, input, wantedTarget, nn) ?: return@withContext null
                    if (!cubeNow.apply(input, moves).contentEquals(wantedTarget)) return@withContext null
                    SolveOut(moves)
                } else if (nn == 3) {
                    val scheme = schemeFor(cubeNow, input, nn) ?: return@withContext null
                    val state = decodeCubies(input, cubeNow, scheme) ?: return@withContext null
                    if (!isValidState(state, nn)) return@withContext null
                    val faceOfColour = IntArray(6) { -1 }
                    scheme.forEachIndexed { f, c -> faceOfColour[c] = f }
                    if (faceOfColour.any { it < 0 }) return@withContext null
                    val faceBoard = IntArray(54) { faceOfColour[input[it]] }
                    val phases = Human3.solve(faceBoard) ?: return@withContext null
                    val out = cubeNow.apply(input, phases.flatMap { it.moves })
                    if (!cubeNow.isSolved(out)) return@withContext null
                    SolveOut(phases.flatMap { it.moves }, humanSteps(cubeNow, phases))
                } else if (nn == 2) {
                    val scheme = schemeFor(cubeNow, input, nn) ?: return@withContext null
                    val state = decodeCubies(input, cubeNow, scheme) ?: return@withContext null
                    if (!isValidState(state, nn)) return@withContext null
                    val faceOfColour = IntArray(6) { -1 }
                    scheme.forEachIndexed { f, c -> faceOfColour[c] = f }
                    if (faceOfColour.any { it < 0 }) return@withContext null
                    val faceBoard = IntArray(24) { faceOfColour[input[it]] }
                    val phases = Human2.solve(faceBoard) ?: return@withContext null
                    val out = cubeNow.apply(input, phases.flatMap { it.moves })
                    if (!cubeNow.isSolved(out)) return@withContext null
                    SolveOut(phases.flatMap { it.moves }, humanSteps(cubeNow, phases))
                } else {
                    val scheme = schemeFor(cubeNow, input, nn) ?: return@withContext null
                    val state = decodeCubies(input, cubeNow, scheme) ?: return@withContext null
                    if (!isValidState(state, nn)) return@withContext null
                    val moves = if (nn == 2) Kociemba.solveCorners(state.cp, state.co)
                    else Kociemba.solveCube(state)
                    if (moves == null) return@withContext null
                    val out = cubeNow.apply(input, moves)
                    if (!cubeNow.isSolved(out)) return@withContext null
                    SolveOut(moves)
                }
            }
            solving = false
            if (nn != n || cubeNow !== cube || screen != Screen.Cube ||
                !board.contentEquals(input)
            ) return@launch
            if (result == null) {
                if (nn >= 4) {
                    issue = "This ${nn}×$nn state could not be solved. Check the colours (rotated colour schemes are fine)."
                    notify(issue!!)
                    return@launch
                }
                val still = diagnoseCube(cubeNow, input, nn)
                if (still != null) {
                    issue = still.message
                    highlight = still.cells.toSet()
                    notify(still.message)
                } else {
                    issue = "The pieces look valid but no solution was found. Please report this."
                    notify(issue!!)
                }
                return@launch
            }
            if (result.moves.isEmpty()) {
                notify(if (wantedTarget != null) "Already at that pattern." else "This cube is already solved.")
                return@launch
            }
            var steps = result.steps ?: withContext(Dispatchers.Default) {
                compressMoves(cubeNow, simplifyMoves(cubeNow, result.moves))
            }
            // Never claim a solve the played sequence does not actually produce.
            val played = withContext(Dispatchers.Default) {
                cubeNow.apply(input, steps.map { it.label })
            }
            val reached = if (wantedTarget != null) played.contentEquals(wantedTarget)
            else cubeNow.isSolved(played)
            if (!reached) {
                val raw = result.moves.map { name -> Step(name, cubeNow.move(name).turns, cubeNow.move(name)) }
                val rawPlayed = withContext(Dispatchers.Default) { cubeNow.apply(input, raw.map { it.label }) }
                val rawReached = if (wantedTarget != null) rawPlayed.contentEquals(wantedTarget)
                else cubeNow.isSolved(rawPlayed)
                if (rawReached) {
                    steps = raw
                } else {
                    issue = "The solution did not solve this ${nn}×$nn state. Please report it."
                    notify(issue!!)
                    return@launch
                }
            }
            solution = Sol(input, steps, if (wantedTarget != null) "Pattern reached!" else "Solved!")
            solutionDone = false
            playIndex = 0
            currentStep = -1
            playing = false
            stepping = false
            animateView(-0.62f, 0.5f)
        }
    }

    fun play() {
        val sol = solution ?: return
        if (stepping) return
        if (playing) {
            pauseRequested = true
            return
        }
        if (solutionDone) {
            playIndex = 0
            board = sol.start.copyOf()
            solutionDone = false
        }
        if (playIndex >= sol.steps.size) return
        playing = true
        pauseRequested = false
        playJob = scope.launch {
            var i = playIndex
            while (i < sol.steps.size && !pauseRequested) {
                val stepNow = sol.steps[i]
                currentStep = i
                val info = stepNow.info
                val quarters = if (info.turns == 2) 2 else 1
                val qInfo = if (info.turns == 2) {
                    cube.move(spellMove(info.face, info.layers, 1))
                } else info
                val qAngle = if (info.turns == 3) PI / 2 else -PI / 2
                for (q in 0 until quarters) {
                    val duration = (400 / speed).toInt().coerceAtLeast(180)
                    animateValue(duration) { v ->
                        anim = TurnAnim(qInfo, qAngle * smooth(v))
                    }
                    board = cube.apply(board, qInfo)
                    anim = null
                    delay((220 / speed).toLong())
                }
                i++
                playIndex = i
                if (pauseRequested) break
            }
            if (playIndex >= sol.steps.size) solutionDone = true
            playing = false
            currentStep = -1
            anim = null
        }
    }

    fun stepForward() {
        val sol = solution ?: return
        if (playing || stepping) return
        if (playIndex >= sol.steps.size) return
        val index = playIndex
        val stepNow = sol.steps[index]
        val info = stepNow.info
        stepping = true
        currentStep = index
        anim = null
        playJob = scope.launch {
            val quarters = if (info.turns == 2) 2 else 1
            val qInfo = if (info.turns == 2) cube.move(spellMove(info.face, info.layers, 1)) else info
            val qAngle = if (info.turns == 3) PI / 2 else -PI / 2
            for (q in 0 until quarters) {
                val duration = (400 / speed).toInt().coerceAtLeast(180)
                animateValue(duration) { v -> anim = TurnAnim(qInfo, qAngle * smooth(v)) }
                board = cube.apply(board, qInfo)
                anim = null
                delay((220 / speed).toLong())
            }
            playIndex = index + 1
            solutionDone = playIndex >= sol.steps.size
            stepping = false
        }
    }

    fun stepBackward() {
        val sol = solution ?: return
        if (playing || stepping) return
        if (playIndex <= 0) return
        val index = playIndex - 1
        val info = sol.steps[index].info
        val invTurns = (4 - info.turns) % 4
        stepping = true
        currentStep = index
        anim = null
        playJob = scope.launch {
            if (invTurns == 2) {
                val q = cube.move(spellMove(info.face, info.layers, 1))
                repeat(2) {
                    val duration = (400 / speed).toInt().coerceAtLeast(180)
                    animateValue(duration) { v -> anim = TurnAnim(q, (PI / 2) * smooth(v)) }
                    board = cube.apply(board, q)
                    anim = null
                    delay((220 / speed).toLong())
                }
            } else {
                val inv = cube.move(spellMove(info.face, info.layers, invTurns))
                val angle = if (invTurns == 3) PI / 2 else -PI / 2
                val duration = (400 / speed).toInt().coerceAtLeast(180)
                animateValue(duration) { v -> anim = TurnAnim(inv, angle * smooth(v)) }
                board = cube.apply(board, inv)
                anim = null
                delay((220 / speed).toLong())
            }
            playIndex = index
            solutionDone = false
            stepping = false
        }
    }

    fun jumpTo(index: Int) {
        val sol = solution ?: return
        playJob?.cancel()
        playing = false
        stepping = false
        pauseRequested = false
        anim = null
        var b = sol.start.copyOf()
        for (i in 0..index) b = cube.apply(b, sol.steps.getOrNull(i)?.info ?: break)
        board = b
        playIndex = index + 1
        currentStep = -1
        solutionDone = index == sol.steps.size - 1
    }

    fun closeSolution() {
        playJob?.cancel()
        playing = false
        stepping = false
        pauseRequested = false
        solution = null
        anim = null
        solutionDone = false
        currentStep = -1
        playIndex = 0
    }

    private fun smooth(t: Float): Float = t * t * (3 - 2 * t)

    private fun ease(t: Float): Float {
        val q = t.coerceIn(0f, 1f)
        return q * q * (3 - 2 * q)
    }

    private suspend fun animateValue(durationMs: Int, block: (Float) -> Unit) {
        val start = System.nanoTime()
        while (true) {
            val t = (((System.nanoTime() - start) / 1_000_000.0) / durationMs).toFloat().coerceIn(0f, 1f)
            block(t)
            if (t >= 1f) break
            delay(16)
        }
    }
}

private fun isValidState(state: CubieState, n: Int): Boolean {
    if (n == 2) {
        if (state.cp.toSet().size != 8) return false
        return state.co.sum() % 3 == 0
    }
    return Kociemba.isValid3(state)
}

fun schemeFor(cube: Cube, board: IntArray, n: Int): IntArray? = when (n) {
    3 -> centreScheme(board)
    2 -> infer2x2Scheme(cube, board)
    else -> null
}

private fun composeCorners(a: CubieState, b: CubieState): CubieState {
    val cp = IntArray(8)
    val co = IntArray(8)
    for (i in 0 until 8) {
        cp[i] = a.cp[b.cp[i]]
        co[i] = (a.co[b.cp[i]] + b.co[i]) % 3
    }
    return CubieState(cp, co, IntArray(0), IntArray(0))
}

private fun invertCornersOnly(c: CubieState): CubieState {
    val cp = IntArray(8)
    for (i in 0 until 8) cp[c.cp[i]] = i
    val co = IntArray(8)
    for (i in 0 until 8) co[i] = (3 - c.co[cp[i]]) % 3
    return CubieState(cp, co, IntArray(0), IntArray(0))
}

/** Moves that take `input` to `target` (target colours mapped into the input scheme). */
fun solveInto(cube: Cube, input: IntArray, target: IntArray, n: Int): List<String>? {
    val inputScheme = schemeFor(cube, input, n) ?: return null
    val targetScheme = schemeFor(cube, target, n) ?: return null
    // target colour -> input colour, matched face by face using each board's own scheme
    val map = IntArray(6)
    for (c in 0 until 6) {
        val tf = targetScheme.indexOf(c)
        if (tf < 0) return null
        map[c] = inputScheme[tf]
    }
    val wanted = IntArray(target.size) { i ->
        val c = target[i]
        if (c < 0) -1 else map[c]
    }
    val a = decodeCubies(input, cube, inputScheme) ?: return null
    val t = decodeCubies(wanted, cube, inputScheme) ?: return null
    val moves = if (n == 2) {
        val inv = invertCornersOnly(t)
        val composed = composeCorners(inv, a)
        Kociemba.solveCorners(composed.cp, composed.co)
    } else {
        val invC = invertCorners(t)
        val invE = invertEdges(t)
        val inv = CubieState(invC.cp, invC.co, invE.ep, invE.eo)
        val composed = composeStates(inv, a)
        Kociemba.solveCube(composed)
    } ?: return null
    val out = cube.apply(input, moves)
    if (!out.contentEquals(wanted)) return null
    return moves
}

// ---------------------------------------------------------------- patterns

private data class PatternAlgo(val name: String, val moves: List<String>)

// Classic face-turn algorithms. They are legal on every cube size >= 2, so the
// generated target is always a real cube; the picker dedupes the solved or
// duplicate variants per size (e.g. several 2x2 algorithms collapse to solved).
private val PATTERN_ALGOS = listOf(
    PatternAlgo("Checkerboard", listOf("R2", "L2", "U2", "D2", "F2", "B2")),
    PatternAlgo("Six spots", listOf("U", "D'", "R", "L'", "F", "B'", "U", "D'")),
    PatternAlgo(
        "Cube in a cube",
        listOf("F", "L", "F", "U2", "R'", "U", "F2", "L2", "U'", "L'", "B", "D'", "B'", "L2", "U"),
    ),
    PatternAlgo(
        "Cube in a cube in a cube",
        listOf("U'", "L'", "U'", "F'", "R2", "B'", "R", "F", "U", "B2", "U", "B'", "L", "U'", "F", "U", "R", "F'"),
    ),
    PatternAlgo(
        "Anaconda",
        listOf("L", "U", "B'", "U'", "R", "L'", "B", "R'", "F", "B'", "D", "R", "D'", "F'"),
    ),
    PatternAlgo(
        "Cross",
        listOf("U", "F", "B'", "L", "R'", "U", "D'", "F'", "U'", "D", "L'", "R", "F'", "B", "U"),
    ),
    PatternAlgo("Four spots", listOf("F2", "B2", "U", "D2", "L2", "R2", "U", "D2")),
    PatternAlgo(
        "Superflip",
        listOf("U", "R2", "F", "B", "R", "B2", "R", "U2", "L", "B2", "R", "U'", "D'", "R2", "F", "R'", "L", "B2", "U2", "F2"),
    ),
    PatternAlgo("Tetris", listOf("L", "R", "F", "B", "U'", "D'", "L'", "R'")),
    PatternAlgo("Vertical stripes", listOf("F", "U", "D'", "L2", "B2", "U2", "F2", "R2", "D2")),
)

val PATTERN_NAMES = listOf("Solved") + PATTERN_ALGOS.map { it.name }

// A 2x2 only keeps the corner projection of the 3x3 art, so most of the classic
// set collapses to a rotation of solved. These are six real 2x2 patterns.
private val PATTERN_2 = listOf(
    PatternAlgo("Vertical stripes", PATTERN_ALGOS[8].moves),
    PatternAlgo("Horizontal bands", listOf("U")),
    PatternAlgo("Double bands", listOf("U", "D")),
    PatternAlgo("Diagonal bands", PATTERN_ALGOS[9].moves),
    PatternAlgo("Two-colour", PATTERN_ALGOS[2].moves),
    PatternAlgo("Four-colour", PATTERN_ALGOS[3].moves),
)

fun patternNames(n: Int): List<String> =
    if (n == 2) listOf("Solved") + PATTERN_2.map { it.name } else PATTERN_NAMES

fun patternBoard(n: Int, index: Int): IntArray {
    val cube = Cube(n)
    if (index <= 0) return cube.solved()
    val algo = if (n == 2) {
        PATTERN_2.getOrNull(index - 1) ?: return cube.solved()
    } else {
        PATTERN_ALGOS[(index - 1) % PATTERN_ALGOS.size]
    }
    return cube.apply(cube.solved(), algo.moves)
}

fun patternPossible(board: IntArray, n: Int): Boolean {
    if (n == 1) {
        for (f in 0 until 6) {
            val c = board[f]
            if (c < 0) return false
            for (g in f + 1 until 6) if (board[g] == c) return false
        }
        return true
    }
    val cube = Cube(n)
    val scheme = schemeFor(cube, board, n) ?: return false
    val state = decodeCubies(board, cube, scheme) ?: return false
    return if (n == 2) {
        if (state.cp.toSet().size != 8 || state.co.sum() % 3 != 0) return false
        val moves = Kociemba.solveCorners(state.cp, state.co) ?: return false
        val c = Cube(2)
        c.isSolved(c.apply(board, moves))
    } else {
        val moves = Kociemba.solveCube(state) ?: return false
        val c = Cube(3)
        c.isSolved(c.apply(board, moves))
    }
}
