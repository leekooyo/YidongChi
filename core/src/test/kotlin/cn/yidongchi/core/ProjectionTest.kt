package cn.yidongchi.core
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProjectionTest {
    private val day=LocalDate.parse("2026-11-06")
    private val stock=Stock("sh600000","浦发银行",Board.SH_MAIN)
    @Test fun scenarioCompoundsFromPreviousSimulatedClose() {
        val bars=listOf(Bar(day,"10".bd(),"10".bd()))
        val ix=listOf(Bar(day,"100".bd(),"100".bd()))
        val result=Projection.project(stock,bars,ix,Scenario(dailyStockPercent="10".bd()),2)
        assertEquals(listOf("11.00","12.10"),result.map {it.close.toPlainString()})
        assertEquals(LocalDate.parse("2026-11-09"),result.first().date)
        assertEquals("11.00",result.last().analysis.previousClose.toPlainString())
    }
    @Test fun oldPriceRiseRollsOutOfTenDayWindow() {
        val dates=TradingCalendar.nextDays(LocalDate.parse("2026-10-19"),12)
        val bars=dates.mapIndexed {i,d->Bar(d,(if(i==0)"10" else "15").bd(),(if(i<2)"10" else "15").bd())}
        val ix=dates.map {Bar(it,"100".bd(),"100".bd())}
        val a=Projection.project(stock,bars,ix,Scenario(),1).first().analysis
        assertEquals("30.00",a.levels.first {it.ruleId=="severe10"&&it.direction==Direction.UP}.price.toPlainString())
    }
    @Test fun recentEventsUseTenTradingSessionsNotTenCalendarDays() {
        val dates=TradingCalendar.nextDays(LocalDate.parse("2026-10-23"),11)
        val e=dates.mapIndexed {i,d->OfficialEvent("$i",stock.symbol,d,"severe10",Direction.UP,"https://www.sse.com.cn/")}
        val recent=Projection.recentEvents(e,dates,dates.last())
        assertEquals(10,recent.size)
        assertFalse(recent.any {it.date==dates.first()})
    }
    @Test fun simulatedTriggerNeverBecomesOfficialEvent() {
        val result=Projection.project(stock,listOf(Bar(day,"10".bd(),"10".bd())),listOf(Bar(day,"100".bd(),"100".bd())),Scenario(dailyStockPercent="10".bd()),4)
        assertEquals(1,result.count {it.firstTrigger})
        assertTrue(result.last().afterHypotheticalTrigger)
    }
    @Test fun knownHolidayIsExcluded() {
        assertEquals(LocalDate.parse("2026-10-08"),TradingCalendar.nextDays(LocalDate.parse("2026-09-30"),1).single())
    }
}
