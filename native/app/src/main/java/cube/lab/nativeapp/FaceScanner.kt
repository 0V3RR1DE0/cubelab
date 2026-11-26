package cube.lab.nativeapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.Executors

private const val FRAME_WINDOW = 5
private const val GRID_FRACTION = 0.86f

private val ScannerBg = Color(0xFF080A10)
private val ScannerInk = Color(0xFFEEF2FF)
private val ScannerMuted = Color(0xFF93A0B8)
private val ScannerLine = Color(0x33FFFFFF)
private val ScannerGridInk = Color(0xE611151F)
private val ScannerError = Color(0xFFFFC9CC)

private val ScannerPalette = intArrayOf(
    0xFFF5C518.toInt(),
    0xFF37C46D.toInt(),
    0xFFE5555B.toInt(),
    0xFF4C8DFF.toInt(),
    0xFFF2F5FA.toInt(),
    0xFFFF9F43.toInt(),
)

// Real sticker inks, not the decorative UI palette. Ink orange is far enough
// from red in hue that dim/neon orange no longer collapses onto red.
private val ScannerRefs = intArrayOf(
    0xFFFFD500.toInt(), // yellow
    0xFF009E60.toInt(), // green
    0xFFC41E3A.toInt(), // red
    0xFF0051BA.toInt(), // blue
    0xFFF2F5FA.toInt(), // white
    0xFFFF5800.toInt(), // orange
)

private val ScannerModel = InkColourModel(
    prototypes = ScannerRefs.map { colour ->
        ColourScience.toLab(
            Rgb(
                r = (colour shr 16) and 0xFF,
                g = (colour shr 8) and 0xFF,
                b = colour and 0xFF,
            ),
        )
    },
)

/**
 * Full-screen single-face scanner. Fills an n x n grid from the camera and returns
 * palette indices row-major, top-left first.
 */
@Composable
fun FaceScannerScreen(
    n: Int,
    onCaptured: (IntArray) -> Unit,
    onCancel: () -> Unit,
) {
    require(n > 0) { "n must be positive" }

    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var denied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        denied = !allowed
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        LiveScanner(n, onCaptured, onCancel)
    } else {
        Box(
            Modifier.fillMaxSize().background(ScannerBg).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            MessageCard(
                title = if (denied) "Camera access denied" else "Camera access needed",
                body = if (denied) {
                    "CubeLab needs the camera to read a face. Allow it here, or enable Camera " +
                        "for CubeLab in system settings if Android no longer shows the prompt."
                } else {
                    "CubeLab needs the camera to read one face at a time."
                },
                actionLabel = "Retry",
                onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onCancel = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LiveScanner(
    n: Int,
    onCaptured: (IntArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(n) { Executors.newSingleThreadExecutor() }
    val frames = remember(n) { FrameWindow(n) }
    var captured by remember { mutableStateOf<IntArray?>(null) }
    var edited by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var status by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf(false) }

    val controller = remember(context, n) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Drop stale frames instead of queueing them behind a slow analysis.
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setImageAnalysisOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
        }
    }

    val analyzer = remember(n) {
        object : ImageAnalysis.Analyzer {
            override fun analyze(image: ImageProxy) {
                try {
                    if (!frames.frozen &&
                        image.format == PixelFormat.RGBA_8888 &&
                        image.planes.isNotEmpty()
                    ) {
                        val plane = image.planes[0]
                        val crop = image.cropRect
                        frames.push(
                            sampleCells(
                                rgba = plane.buffer,
                                width = image.width,
                                height = image.height,
                                rowStride = plane.rowStride,
                                pixelStride = plane.pixelStride,
                                n = n,
                                rotationDegrees = image.imageInfo.rotationDegrees,
                                cropWidth = crop.width(),
                                cropHeight = crop.height(),
                                gridFraction = GRID_FRACTION,
                            ),
                        )
                    }
                } finally {
                    // Every frame must be released or the pipeline stalls.
                    image.close()
                }
            }
        }
    }

    DisposableEffect(controller, analyzer, executor, lifecycleOwner) {
        val bound = runCatching {
            controller.setImageAnalysisAnalyzer(executor, analyzer)
            controller.bindToLifecycle(lifecycleOwner)
        }
        if (bound.isFailure) cameraError = true

        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }

    fun capture() {
        val window = frames.snapshot()
        if (window.isEmpty()) {
            status = "No frames yet — hold the cube steady and try again."
            return
        }
        frames.frozen = true
        val medians = List(n * n) { cell -> medianRgb(window.map { it[cell] }) }
        captured = IntArray(n * n) { cell ->
            ScannerModel.classify(ColourScience.toLab(medians[cell])).index ?: -1
        }
        edited = emptySet()
        status = null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    preview.scaleType = PreviewView.ScaleType.FILL_CENTER
                    preview.controller = controller
                }
            },
            update = { preview -> preview.controller = controller },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(GRID_FRACTION)
                .aspectRatio(1f),
        ) {
            ScanGrid(n, captured, edited) { i ->
                val g = captured
                if (g != null && i in g.indices) {
                    captured = g.copyOf().also { it[i] = (it[i] + 1) % 6 }
                    edited = edited + i
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            color = Color(0xCC11151F),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ScannerLine),
        ) {
            Text(
                if (captured == null) "Fit one ${n}×${n} face inside the grid"
                else "Tap a cell to change its colour · Rotate if sideways",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = ScannerInk,
            )
        }

        val grid = captured
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Color(0xDD11151F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ScannerLine),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (grid == null) {
                        Text(
                            "Put one sticker in every cell, then capture.",
                            color = ScannerMuted,
                            fontSize = 11.5.sp,
                        )
                        status?.let { message ->
                            Text(
                                message,
                                modifier = Modifier.padding(top = 4.dp),
                                color = ScannerError,
                                fontSize = 11.5.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { capture() }) { Text("Capture") }
                            OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        }
                    } else {
                        ColourCounts(grid)
                        val unknown = grid.count { it !in 0..5 }
                        if (unknown > 0) {
                            Text(
                                "$unknown unreadable cell(s) — tap them to set a colour",
                                modifier = Modifier.padding(top = 4.dp),
                                color = ScannerError,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                captured = rotateGrid(grid, n)
                                edited = edited.mapTo(HashSet()) { rotateIndex(it, n) }
                            }) {
                                Text("Rotate")
                            }
                            Button(onClick = { onCaptured(grid) }, enabled = grid.all { it in 0..5 }) {
                                Text("Use")
                            }
                            OutlinedButton(onClick = {
                                captured = null
                                edited = emptySet()
                                frames.frozen = false
                                frames.clear()
                                status = null
                            }) { Text("Retake") }
                            OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        if (cameraError) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                MessageCard(
                    title = "Camera unavailable",
                    body = "The camera could not be started. Close other apps using it and try again.",
                    onCancel = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScanGrid(n: Int, captured: IntArray?, edited: Set<Int>, onTap: (Int) -> Unit) {
    Canvas(
        Modifier.fillMaxSize().pointerInput(captured, n) {
            if (captured != null) {
                detectTapGestures { offset ->
                    val cell = size.width / n.toFloat()
                    if (cell > 0f) {
                        val col = (offset.x / cell).toInt().coerceIn(0, n - 1)
                        val row = (offset.y / cell).toInt().coerceIn(0, n - 1)
                        onTap(row * n + col)
                    }
                }
            }
        },
    ) {
        val cell = size.width / n
        if (captured != null && captured.size == n * n) {
            for (row in 0 until n) {
                for (col in 0 until n) {
                    val i = row * n + col
                    val value = captured[i]
                    drawRect(
                        color = if (value >= 0) paletteColor(value).copy(alpha = 0.88f)
                        else Color(0xFF2A2F3C),
                        topLeft = Offset(col * cell, row * cell),
                        size = ComposeSize(cell, cell),
                    )
                    if (value < 0) {
                        val inset = cell * 0.25f
                        drawLine(
                            ScannerError,
                            Offset(col * cell + inset, row * cell + inset),
                            Offset((col + 1) * cell - inset, (row + 1) * cell - inset),
                            strokeWidth = 2.dp.toPx(),
                        )
                        drawLine(
                            ScannerError,
                            Offset((col + 1) * cell - inset, row * cell + inset),
                            Offset(col * cell + inset, (row + 1) * cell - inset),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    if (i in edited) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(col * cell + 2.dp.toPx(), row * cell + 2.dp.toPx()),
                            size = ComposeSize(cell - 4.dp.toPx(), cell - 4.dp.toPx()),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
        }

        val stroke = 2.dp.toPx()
        for (i in 0..n) {
            val line = i * cell
            drawLine(ScannerGridInk, Offset(line, 0f), Offset(line, size.height), stroke)
            drawLine(ScannerGridInk, Offset(0f, line), Offset(size.width, line), stroke)
        }
        drawRect(
            color = ScannerGridInk,
            topLeft = Offset.Zero,
            size = size,
            style = Stroke(stroke * 2),
        )
    }
}

@Composable
private fun ColourCounts(grid: IntArray) {
    val counts = IntArray(6)
    grid.forEach { if (it in 0..5) counts[it]++ }
    val distinct = counts.count { it > 0 }

    Text(
        "Detected $distinct of 6 colours",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = ScannerInk,
    )
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (colour in 0..5) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(paletteColor(colour), CircleShape))
                Text(" ${counts[colour]}", fontSize = 12.sp, color = ScannerMuted)
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF11151F),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ScannerLine),
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ScannerInk)
            Text(
                body,
                modifier = Modifier.padding(top = 8.dp),
                color = ScannerMuted,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionLabel != null && onAction != null) {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

private class FrameWindow(private val n: Int) {
    private val lock = Any()
    private val frames = ArrayDeque<List<Rgb>>()

    @Volatile
    var frozen = false

    fun push(cells: List<Rgb>) {
        if (cells.size != n * n) return
        synchronized(lock) {
            if (frames.size >= FRAME_WINDOW) frames.removeFirst()
            frames.addLast(cells)
        }
    }

    fun snapshot(): List<List<Rgb>> = synchronized(lock) { frames.toList() }

    fun clear() = synchronized(lock) { frames.clear() }
}

private fun Context.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
