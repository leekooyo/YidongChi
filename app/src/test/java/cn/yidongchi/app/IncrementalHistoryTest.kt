package cn.yidongchi.app

import cn.yidongchi.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class IncrementalHistoryTest {
    private val date=LocalDate.parse("2026-11-09")
    private val stock=Stock("sz002086","东方海洋",Board.SZ_MAIN,LocalDate.parse("2007-02-05"))
    private val old=HistorySeries(stock,"tencent",listOf(Bar(date.minusDays(3),"4".bd(),"3.9".bd())))
    private fun quote(day:LocalDate=date,previous:String="4",volume:String="100")=Quote(stock,"4.1".bd(),previous.bd(),day.atTime(15,1),"4.2".bd(),"3.9".bd(),"4.4".bd(),"3.6".bd(),volume.bd())
    @Test fun nextSessionAppendsAndRetainsMetadataAndSource() {
        val plan=IncrementalHistory.plan(old,quote(),date)
        assertTrue(plan is HistoryDecision.Write)
        val write=(plan as HistoryDecision.Write).value
        assertFalse(write.replace);assertEquals("tencent",write.series.provider)
        assertEquals(stock,write.series.stock);assertEquals(listOf(date),write.series.bars.map{it.date})
    }
    @Test fun sameDayReplacesWithoutDuplicatingAndChecksPrecedingReference() {
        val current=old.copy(bars=old.bars+Bar(date,"4.05".bd(),"4".bd()))
        assertTrue(IncrementalHistory.plan(current,quote(),date) is HistoryDecision.Write)
        assertTrue(IncrementalHistory.plan(current,quote(previous="3.5"),date) is HistoryDecision.Repair)
    }
    @Test fun missingSessionsOrCorporateReferenceChangesRequireRepair() {
        assertTrue(IncrementalHistory.plan(old,quote(date.plusDays(1)),date.plusDays(1)) is HistoryDecision.Repair)
        assertTrue(IncrementalHistory.plan(old,quote(previous="3.5"),date) is HistoryDecision.Repair)
        assertTrue(IncrementalHistory.plan(null,quote(),date) is HistoryDecision.Repair)
    }
    @Test fun suspendedDoesNotCreateBarAndStaleActiveQuoteFails() {
        assertTrue(IncrementalHistory.plan(old,quote(date.minusDays(3),volume="0"),date) is HistoryDecision.Excluded)
        try {IncrementalHistory.plan(old,quote(date.minusDays(3)),date);fail("stale")}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("未就绪"))}
    }
    @Test fun shortHistoryWithoutListingMetadataRequiresRepair() {
        assertTrue(IncrementalHistory.plan(old.copy(stock=stock.copy(listedOn=null)),quote(),date) is HistoryDecision.Repair)
        val noLimit=quote().copy(upper=null,lower=null)
        val plan=IncrementalHistory.plan(old,noLimit,date) as HistoryDecision.Write
        assertFalse(plan.value.series.bars.single().eligible)
    }
}
