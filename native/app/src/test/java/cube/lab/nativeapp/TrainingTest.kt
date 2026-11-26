package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrainingTest {

    // ------------------------------------------------------------ content

    @Test
    fun contentHasAtLeastOneLessonPerMethod() {
        val ids = TrainingContent.lessons.map { it.id }.toSet()
        val required = setOf(
            // 2x2 Ortega
            "ortega.first-face",
            "ortega.oll.sune",
            "ortega.oll.antisune",
            "ortega.oll.h",
            "ortega.oll.pi",
            "ortega.oll.t",
            "ortega.oll.u",
            "ortega.oll.l",
            "ortega.pbl.adj",
            "ortega.pbl.diag",
            // 3x3 beginner LBL
            "lbl.cross",
            "lbl.corners",
            "lbl.second.right",
            "lbl.second.left",
            "lbl.yellow-cross",
            "lbl.yellow-corners.sune",
            "lbl.yellow-corners.antisune",
            "lbl.permute-corners.aa",
            "lbl.permute-corners.ab",
            "lbl.permute-edges.ua",
            "lbl.permute-edges.ub",
            // 3x3 CFOP
            "cfop.cross",
            "cfop.f2l",
            "cfop.oll.edges",
            "cfop.oll.corners",
            "cfop.pll.t",
            "cfop.pll.y",
            "cfop.pll.ua",
            "cfop.pll.ub",
            "cfop.pll.h",
            "cfop.pll.z",
            // 4x4-7x7 reduction
            "big.centres",
            "big.edge-pairing",
            "big.3x3",
            "big.parity.oll",
            "big.parity.pll",
            // Pyraminx LBL
            "pyra.centres",
            "pyra.first-layer",
            "pyra.ll.sune",
            "pyra.ll.antisune",
            "pyra.ll.two-flip",
            "pyra.ll.unsexy-r",
            "pyra.ll.unsexy-l",
            "pyra.tips",
        )
        assertEquals("missing lessons: ${required - ids}", emptySet<String>(), required - ids)
        assertEquals(
            listOf("2×2 Ortega", "3×3 Beginner LBL", "3×3 CFOP", "4×4–7×7 Reduction", "Pyraminx LBL"),
            TrainingContent.tracks.map { it.first },
        )
        TrainingContent.tracks.forEach { (track, lessons) ->
            assertTrue("track $track has no lessons", lessons.isNotEmpty())
        }
    }

    @Test
    fun everyLessonHasNonBlankTextAndAnAlgorithm() {
        for (lesson in TrainingContent.lessons) {
            assertTrue(lesson.id, lesson.puzzle.isNotBlank())
            assertTrue(lesson.id, lesson.title.isNotBlank())
            assertTrue(lesson.id, lesson.goal.isNotBlank())
            assertTrue(lesson.id, lesson.algorithmName.isNotBlank())
            assertTrue(lesson.id, lesson.moves.isNotBlank())
            assertTrue(lesson.id, lesson.explanation.isNotBlank())
            assertTrue(lesson.id, lesson.commonMistake.isNotBlank())
        }
        assertEquals(
            TrainingContent.lessons.size,
            TrainingContent.lessons.map { it.id }.toSet().size,
        )
        assertEquals(
            5,
            TrainingContent.lessons.count { it.id.startsWith("pyra.ll.") },
        )
    }

    @Test
    fun parityLessonsUseTheStandardAlgorithms() {
        val byId = TrainingContent.lessons.associateBy { it.id }
        assertEquals(
            "Rw2 B2 U2 Lw U2 Rw' U2 Rw U2 F2 Rw F2 Lw' B2 Rw2",
            byId.getValue("big.parity.oll").moves,
        )
        assertEquals("r2 U2 r2 Uw2 r2 Uw2", byId.getValue("big.parity.pll").moves)
    }

    // ------------------------------------------------------------ storage

    @Test
    fun storageRoundTrip() {
        val file = File.createTempFile("training-times", ".txt")
        file.deleteOnExit()
        val solves = listOf(
            TrainingSolve(TrainingPuzzle.P3, 12_340, Penalty.NONE, 1_000),
            TrainingSolve(TrainingPuzzle.P6, 98_760, Penalty.PLUS2, 2_000),
            TrainingSolve(TrainingPuzzle.PYR, 4_560, Penalty.DNF, 3_000),
        )

        assertTrue(TrainingStore.write(file, solves))
        assertEquals(solves, TrainingStore.read(file))

        val lines = file.readLines()
        assertEquals(3, lines.size)
        assertEquals(listOf("3x3", "12340", "NONE", "1000"), lines[0].split('|'))
        assertFalse("tmp file left behind", File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test
    fun storageSkipsCorruptLinesAndMissingFileIsEmpty() {
        val dir = File(System.getProperty("java.io.tmpdir"), "training-test-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        dir.deleteOnExit()

        assertTrue(TrainingStore.read(File(dir, "missing.txt")).isEmpty())

        val file = File(dir, TrainingStore.FILE_NAME)
        file.writeText(
            """
            3x3|10000|NONE|1
            garbage
            2x2|not-a-number|NONE|2
            9x9|5000|NONE|3
            2x2|6000|BOGUS|4
            2x2|7000|NONE|not-a-time
            2x2|-5|NONE|5
            pyraminx|8000|PLUS2|6
            """.trimIndent(),
        )

        val loaded = TrainingStore.read(file)
        assertEquals(2, loaded.size)
        assertEquals(TrainingPuzzle.P3, loaded[0].puzzle)
        assertEquals(TrainingPuzzle.PYR, loaded[1].puzzle)
        assertEquals(Penalty.PLUS2, loaded[1].penalty)
        assertEquals(8_000L, loaded[1].rawMs)
    }

    // ------------------------------------------------------------ summary

    @Test
    fun summaryPicksSingleAo5AndAo12() {
        val times = (1..12).map { SolveTime(it * 10_000L) }

        val summary = trainingSummary(times, bigCube = false)
        // Best of all twelve.
        assertEquals(10_000L, summary.singleMs)
        // Last five: 80 90 100 110 120 -> drop 80 and 120 -> 100 s.
        assertEquals(100_000L, summary.ao5Ms)
        // All twelve: drop 10 and 120 -> mean of 20..110 = 65 s.
        assertEquals(65_000L, summary.ao12Ms)
        assertNull(summary.mo3Ms)
        assertNull(trainingSummary(times.take(4), bigCube = false).ao5Ms)
    }

    @Test
    fun summaryHandlesDnfs() {
        val oneDnf = listOf(
            SolveTime(10_000),
            SolveTime(11_000),
            SolveTime(12_000),
            SolveTime(13_000),
            SolveTime(0, Penalty.DNF),
        )
        val summary = trainingSummary(oneDnf, bigCube = false)
        assertEquals(10_000L, summary.singleMs) // the DNF is not a single
        assertEquals(12_000L, summary.ao5Ms) // DNF is the dropped worst

        val twoDnfs = listOf(
            SolveTime(10_000),
            SolveTime(11_000),
            SolveTime(12_000),
            SolveTime(0, Penalty.DNF),
            SolveTime(0, Penalty.DNF),
        )
        assertNull(trainingSummary(twoDnfs, bigCube = false).ao5Ms)

        val ao12WithOneDnf = (1..11).map { SolveTime(it * 1_000L) } +
            SolveTime(0, Penalty.DNF)
        // Drop 1 s and the DNF: mean of 2..11 s = 6.5 s.
        assertEquals(6_500L, trainingSummary(ao12WithOneDnf, bigCube = false).ao12Ms)
    }

    @Test
    fun bigCubesUseMo3() {
        val times = listOf(
            SolveTime(10_000),
            SolveTime(20_000),
            SolveTime(30_000),
            SolveTime(40_000),
        )
        val summary = trainingSummary(times, bigCube = true)
        assertEquals(10_000L, summary.singleMs)
        assertEquals(30_000L, summary.mo3Ms) // (20 + 30 + 40) / 3
        assertNull(summary.ao5Ms)
        assertNull(summary.ao12Ms)

        val dnfLast = times.dropLast(1) + SolveTime(0, Penalty.DNF)
        assertNull(trainingSummary(dnfLast, bigCube = true).mo3Ms)
    }

    @Test
    fun timeFormatting() {
        assertEquals("00.00", formatTime(0L))
        assertEquals("01.23", formatTime(1_230L))
        assertEquals("12.34", formatTime(12_345L))
        assertEquals("1:02.34", formatTime(62_345L))
        assertEquals("DNF", formatTime(null))
    }
}
