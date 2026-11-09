package cn.yidongchi.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PriceEngineTest {
    private val day = LocalDate.parse("2026-11-09")
    private val stock = Stock("sh600000", "浦发银行", Board.SH_MAIN)
    private fun bar(offset: Long, close: String, prev: String) = Bar(day.plusDays(offset), close.bd(), prev.bd())

    @Test fun endpointReturnIsNotSumOfDailyReturns() {
        val bars = listOf(bar(-1, "11", "10"), bar(0, "12.1", "11"))
        assertEquals(0, PriceEngine.deviation(bars, listOf(bar(-1,"100","100"),bar(0,"101","100"))).compareTo("0.20".bd()))
    }
    @Test fun upperThresholdRoundsToFirstTriggeringTick() {
        assertEquals("12.11", PriceEngine.threshold("10".bd(), "1.0105".bd(), ".20".bd(), ".01".bd(), Direction.UP).toPlainString())
    }
    @Test fun lowerThresholdRoundsDownAndEqualityTriggers() {
        assertEquals("8.10", PriceEngine.threshold("10".bd(), "1.0105".bd(), "-.20".bd(), ".01".bd(), Direction.DOWN).toPlainString())
    }
    @Test fun windowIncludesTwoDayTriggerAndIndependentSevereReset() {
        val bars = listOf(bar(-2,"10","10"),bar(-1,"11","10"),bar(0,"12.1","11"))
        val ix = bars.map { it.copy(close="100".bd(), previousClose="100".bd()) }
        val a = PriceEngine.analyze(stock,bars,ix,day,"100".bd(),ResetDates(normal=day.minusDays(1)))
        val normal = a.levels.first { it.ruleId=="normal" && it.direction==Direction.UP }
        assertEquals(day.minusDays(1),normal.start)
        assertEquals("12.00",normal.price.toPlainString())
        assertEquals(day.minusDays(2),a.levels.first { it.ruleId=="severe10" && it.direction==Direction.UP }.start)
    }
    @Test fun indexMissingDateDoesNotSilentlyShortenWindow() {
        val bars=listOf(bar(-1,"11","10"),bar(0,"12","11"))
        val a=PriceEngine.analyze(stock,bars,listOf(bar(0,"101","100")),day,"101".bd())
        assertTrue(a.levels.all { it.start==day })
    }
    @Test fun bShareTickRemainsIndependent() {
        assertEquals("1.211", PriceEngine.threshold("1".bd(),"1.0105".bd(),".20".bd(),".001".bd(),Direction.UP).toPlainString())
    }
    @Test fun exDividendUsesReferenceRatherThanYesterdayRawClose() {
        val bars=listOf(bar(-1,"10","10"),bar(0,"9.9","9"))
        val ix=bars.map { it.copy(close="100".bd(),previousClose="100".bd()) }
        assertEquals(0,PriceEngine.deviation(bars,ix).compareTo(".1".bd()))
    }
    @Test fun noLimitDayBreaksCandidateWindow() {
        val bars=listOf(bar(-1,"11","10").copy(eligible=false),bar(0,"12","11"))
        val ix=bars.map { it.copy(close="100".bd(),previousClose="100".bd()) }
        assertTrue(PriceEngine.analyze(stock,bars,ix,day,"100".bd()).levels.all {it.start==day})
    }
    @Test fun upperThresholdBelowTodayFloorMeansAlreadySatisfiedThroughoutRange() {
        val bars=listOf(bar(-1,"15","10"),bar(0,"15","15"))
        val ix=bars.map { it.copy(close="100".bd(),previousClose="100".bd()) }
        val level=PriceEngine.analyze(stock,bars,ix,day,"100".bd()).levels.first {it.ruleId=="normal"&&it.direction==Direction.UP}
        assertTrue(level.satisfied)
        assertTrue(level.reachable)
    }
}
