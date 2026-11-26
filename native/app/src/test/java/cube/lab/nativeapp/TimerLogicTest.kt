package cube.lab.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerLogicTest {

    private fun times(vararg ms: Long) = ms.map { SolveTime(it) }

    @Test
    fun inspectionPenaltyBoundaries() {
        assertEquals(Penalty.NONE, TimerLogic.inspectionPenalty(0))
        assertEquals(Penalty.NONE, TimerLogic.inspectionPenalty(14_999))
        assertEquals(Penalty.PLUS2, TimerLogic.inspectionPenalty(15_000))
        assertEquals(Penalty.PLUS2, TimerLogic.inspectionPenalty(16_999))
        assertEquals(Penalty.DNF, TimerLogic.inspectionPenalty(17_000))
    }

    @Test
    fun finalMsForEachPenalty() {
        assertEquals(10_000L, SolveTime(10_000).finalMs)
        assertEquals(12_000L, SolveTime(10_000, Penalty.PLUS2).finalMs)
        assertNull(SolveTime(10_000, Penalty.DNF).finalMs)
    }

    @Test
    fun negativeRawMsIsDnf() {
        assertNull(SolveTime(-1).finalMs)
        assertNull(SolveTime(-1, Penalty.PLUS2).finalMs)
    }

    @Test
    fun ao5WithSingleDnf() {
        // Drop 10.00 (best) and the DNF (worst): (11.00 + 12.00 + 13.00) / 3 = 12.00 s.
        val list = times(10_000, 11_000, 12_000, 13_000) + SolveTime(0, Penalty.DNF)
        assertEquals(12_000L, TimerLogic.aoN(list, 5))
    }

    @Test
    fun ao5WithTwoDnfs() {
        val list = times(10_000, 11_000, 12_000) +
            SolveTime(0, Penalty.DNF) + SolveTime(0, Penalty.DNF)
        assertNull(TimerLogic.aoN(list, 5))
    }

    @Test
    fun ao5WithFewerThanFiveSolves() {
        assertNull(TimerLogic.aoN(times(10_000, 11_000, 12_000, 13_000), 5))
    }

    @Test
    fun aoNTrimsBestAndWorst() {
        // Last 5 of 10 attempts: 9000, 15000, 8000, 14000, 16000
        // Sorted: 8000, 9000, 14000, 15000, 16000
        // Drop 8000 and 16000: (9000 + 14000 + 15000) / 3 = 12666.67 -> 12670 ms.
        val list = times(10_000, 12_000, 11_000, 13_000, 20_000, 9_000, 15_000, 8_000, 14_000, 16_000)
        assertEquals(12_670L, TimerLogic.aoN(list, 5))
    }

    @Test
    fun bestAoNTrimsBestAndWorst() {
        // Windows of 5 and their trimmed means:
        // [10000,12000,11000,13000,20000] -> drop 10000,20000 -> 12000
        // [12000,11000,13000,20000,9000] -> drop 9000,20000  -> 12000
        // [11000,13000,20000,9000,15000] -> drop 9000,20000  -> 13000
        // [13000,20000,9000,15000,8000]  -> drop 8000,20000  -> 12333.33 -> 12330
        // [20000,9000,15000,8000,14000]  -> drop 8000,20000  -> 12666.67 -> 12670
        // [9000,15000,8000,14000,16000]  -> drop 8000,16000  -> 12666.67 -> 12670
        // Best = 12000.
        val list = times(10_000, 12_000, 11_000, 13_000, 20_000, 9_000, 15_000, 8_000, 14_000, 16_000)
        assertEquals(12_000L, TimerLogic.bestAoN(list, 5))
    }

    @Test
    fun bestAoNPicksFasterLaterWindow() {
        // First window averages 10000, the last one averages 5000.
        val list = times(10_000, 10_000, 10_000, 10_000, 10_000, 5_000, 5_000, 5_000, 5_000, 5_000)
        assertEquals(5_000L, TimerLogic.bestAoN(list, 5))
    }

    @Test
    fun aoNRoundsToNearest10ms() {
        // Drop 1000 and 2000: 1005 -> 100.5 cs -> 101 cs -> 1010 ms.
        assertEquals(1_010L, TimerLogic.aoN(times(1_000, 1_005, 2_000), 3))
        // Drop 1000 and 2000: 1004 -> 100.4 cs -> 100 cs -> 1000 ms.
        assertEquals(1_000L, TimerLogic.aoN(times(1_000, 1_004, 2_000), 3))
    }
}
