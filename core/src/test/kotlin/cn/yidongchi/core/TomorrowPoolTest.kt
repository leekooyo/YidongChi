package cn.yidongchi.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TomorrowPoolTest {
    private val day=LocalDate.parse("2026-11-06")
    private val stock=Stock("sz002086","东方海洋",Board.SZ_MAIN)
    private fun calc(price:String,previous:String="10",resets:ResetDates=ResetDates())=
        TomorrowPool.calculate(stock,listOf(Bar(day,price.bd(),previous.bd())),listOf(Bar(day,"100".bd(),"100".bd())),resets)
    @Test fun nextTradingDayAndReachableUpThreshold() {
        val result=calc("19")
        assertEquals(LocalDate.parse("2026-11-09"),result.targetDate)
        val up=result.hits.single {it.level.ruleId=="severe10"&&it.level.direction==Direction.UP}
        assertEquals(0,"20".bd().compareTo(up.level.price))
        assertEquals(0,"20".bd().subtract("19".bd()).ratio("19".bd()).compareTo(up.requiredChange))
        assertFalse(result.hits.any {it.level.ruleId=="normal"})
    }
    @Test fun downAndExactLimitAreIncludedButOneTickOutsideIsNot() {
        assertTrue(calc("5.3").hits.any {it.level.ruleId=="severe10"&&it.level.direction==Direction.DOWN&&it.requiredChange.signum()<0})
        assertTrue(calc("18.18").hits.any {it.level.ruleId=="severe10"&&it.level.direction==Direction.UP})
        assertFalse(calc("18.17").hits.any {it.level.ruleId=="severe10"&&it.level.direction==Direction.UP})
    }
    @Test fun satisfiedConditionsHaveZeroRequiredChangeAndMultipleRulesRemain() {
        val r=calc("29")
        assertEquals(0,r.hits.first {it.level.ruleId=="severe10"&&it.level.direction==Direction.UP}.requiredChange.signum())
        assertTrue(r.hits.any {it.level.ruleId=="severe30"&&it.level.direction==Direction.UP})
    }
    @Test fun futureResetRemovesOldGrowthAndExemptionIsNotANegativeResult() {
        assertTrue(calc("19",resets=ResetDates(severe=day.plusDays(3))).hits.isEmpty())
        val result=TomorrowPool.calculate(stock.copy(excludedFromDeviation=true),listOf(Bar(day,"19".bd(),"10".bd())),listOf(Bar(day,"100".bd(),"100".bd())))
        assertTrue(result.hits.isEmpty())
        assertNotNull(result.excludedReason)
    }
    @Test fun oldRiseLeavesTenDayWindowAndHolidayUsesOfficialCalendar() {
        val dates=TradingCalendar.nextDays(LocalDate.parse("2026-10-19"),10)
        val bars=dates.mapIndexed {i,d->Bar(d,"19".bd(),(if(i==0)"10" else "19").bd())}
        val result=TomorrowPool.calculate(stock,bars,dates.map{Bar(it,"100".bd(),"100".bd())})
        assertFalse(result.hits.any {it.level.ruleId=="severe10"&&it.level.direction==Direction.UP})
        val holiday=LocalDate.parse("2026-09-30")
        assertEquals(LocalDate.parse("2026-10-08"),TomorrowPool.calculate(stock,listOf(Bar(holiday,"19".bd(),"10".bd())),listOf(Bar(holiday,"100".bd(),"100".bd()))).targetDate)
    }
    @Test fun missingRelevantIndexDateIsNotASuccessfulNegative() {
        val dates=TradingCalendar.nextDays(LocalDate.parse("2026-10-19"),12)
        val bars=dates.mapIndexed{i,d->Bar(d,"19".bd(),(if(i==0)"10"else "19").bd())}
        val indices=dates.filter{it!=dates[3]}.map{Bar(it,"100".bd(),"100".bd())}
        try{TomorrowPool.calculate(stock,bars,indices);fail("指数缺日必须报未计算")}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("指数日期不完整"))}
    }
}
