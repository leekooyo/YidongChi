package cn.yidongchi.core
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewRegressionTest {
    private val listing=LocalDate.parse("2026-11-02")
    private val dates=TradingCalendar.nextDays(listing.minusDays(1),6)
    @Test fun ipoSixthDayDoesNotInheritFirstFiveDaysReturns() {
        val stock=Stock("sz001234","新股",Board.SZ_MAIN,listedOn=listing)
        val prices=listOf("10","10","10","20","20","20")
        val raw=dates.mapIndexed {i,d->Bar(d,prices[i].bd(),prices[(i-1).coerceAtLeast(0)].bd())}
        val bars=SecurityEligibility.apply(stock,raw)
        val ix=raw.map {it.copy(close="100".bd(),previousClose="100".bd())}
        val a=PriceEngine.analyze(stock,bars,ix,dates.last(),"100".bd())
        assertEquals("24.00",a.levels.first {it.ruleId=="normal"&&it.direction==Direction.UP}.price.toPlainString())
        assertEquals(dates.last(),a.levels.first().start)
    }
    @Test fun ipoProjectionKeepsRemainingExemptDaysExcluded() {
        val stock=Stock("sz001234","新股",Board.SZ_MAIN,listedOn=listing)
        val bars=SecurityEligibility.apply(stock,dates.take(2).map{Bar(it,"10".bd(),"10".bd())})
        val ix=bars.map {it.copy(close="100".bd(),previousClose="100".bd(),eligible=true)}
        val result=Projection.project(stock,bars,ix,Scenario(),4)
        assertTrue(result.take(3).all {it.analysis.levels.isEmpty()})
        assertEquals(6,result.last().analysis.levels.size)
    }
    @Test fun delistingStatusCannotEnterPriceDeviationWindows() {
        val stock=Stock("sz000001","退市股票",Board.SZ_MAIN,excludedFromDeviation=true)
        assertFalse(SecurityEligibility.apply(stock,listOf(Bar(dates.last(),"10".bd(),"10".bd()))).single().eligible)
    }
    @Test fun futureResetCannotEraseEarlierAnalysis() {
        val stock=Stock("sh600000","浦发",Board.SH_MAIN)
        val bars=listOf(Bar(dates.first(),"10".bd(),"10".bd()))
        val ix=bars.map {it.copy(close="100".bd(),previousClose="100".bd())}
        assertEquals(6,PriceEngine.analyze(stock,bars,ix,dates.first(),"100".bd(),ResetDates(dates.last(),dates.last())).levels.size)
    }
    @Test fun confirmedDirectionCountDoesNotCountCandidatesOrOverlapDuplicates() {
        val events=dates.take(4).mapIndexed {i,d->OfficialEvent("$i","sh600000",d,"normal",Direction.UP,"https://www.sse.com.cn/")}
        val duplicate=events.first().copy(id="provider-copy",confirmed=false)
        val counts=EventLogic.directionCounts(events+duplicate,dates,dates.last(),Board.SH_MAIN)
        assertEquals(4,counts.up);assertTrue(counts.triggered)
        assertEquals(0,counts.down)
    }
    @Test fun confirmedEventReplacesCandidateAcrossProviderIds() {
        val candidate=OfficialEvent("600000-x","sh600000",listing,"normal",null,"https://example.com/",false)
        val confirmed=candidate.copy(id="sh600000-x",direction=Direction.UP,confirmed=true)
        assertEquals(listOf(confirmed),EventLogic.merge(listOf(candidate),listOf(confirmed,candidate)))
    }
    @Test fun missingIndexReplayDayIsSkippedWithoutReplacingItsDate() {
        val stock=Stock("sh600000","浦发",Board.SH_MAIN)
        val bars=listOf(Bar(listing,"10".bd(),"10".bd()),Bar(dates.last(),"11".bd(),"10".bd()))
        val ix=listOf(Bar(dates.last(),"100".bd(),"100".bd()))
        assertEquals(listOf(dates.last()),PriceEngine.replay(stock,bars,ix,ResetDates()).map {it.targetDate})
    }
}
