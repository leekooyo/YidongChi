package cn.yidongchi.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

object TradingCalendar {
    // 2026年度沪深休市安排；调休周末仍不是交易日。
    private val holidays=buildSet {
        for((start,end) in listOf("2026-01-01" to "2026-01-03","2026-02-15" to "2026-02-23","2026-04-04" to "2026-04-06","2026-05-01" to "2026-05-05","2026-06-19" to "2026-06-21","2026-09-25" to "2026-09-27","2026-10-01" to "2026-10-07")) {
            var d=LocalDate.parse(start);while(d<=LocalDate.parse(end)){add(d);d=d.plusDays(1)}
        }
    }
    fun isTradingDay(date:LocalDate)=date.dayOfWeek!=DayOfWeek.SATURDAY&&date.dayOfWeek!=DayOfWeek.SUNDAY&&date !in holidays
    fun nextDays(after:LocalDate,count:Int):List<LocalDate> {
        val result=mutableListOf<LocalDate>();var d=after
        while(result.size<count) {d=d.plusDays(1);require(d.year==2026){"尚未收录 ${d.year} 年官方交易日历"};if(isTradingDay(d))result+=d}
        return result
    }
}

object Projection {
    fun project(stock:Stock,bars:List<Bar>,index:List<Bar>,scenario:Scenario,count:Int=10,resets:ResetDates=ResetDates()):List<ProjectionDay> {
        val history=bars.toMutableList();val ix=index.toMutableList();val days=TradingCalendar.nextDays(history.last().date,count)
        val results=mutableListOf<ProjectionDay>();var seen=false;var state=resets
        for((i,date) in days.withIndex()) {
            val prev=history.last().close;val indexPrev=ix.last().close
            val stockPct=scenario.stockPath.getOrNull(i)?:scenario.dailyStockPercent
            val indexPct=scenario.indexPath.getOrNull(i)?:scenario.dailyIndexPercent
            val close=when(scenario.limitPath) {1->PriceEngine.limits(stock,prev).second;-1->PriceEngine.limits(stock,prev).first;else->PriceEngine.tick(prev.multiply(BigDecimal.ONE.add(stockPct.movePointLeft(2))),stock.tick,RoundingMode.HALF_UP)}
            val indexClose=indexPrev.multiply(BigDecimal.ONE.add(indexPct.movePointLeft(2)),MC)
            history+=Bar(date,close,prev);ix+=Bar(date,indexClose,indexPrev)
            val analysis=PriceEngine.analyze(stock,history,ix,date,indexClose,state)
            val hit=analysis.levels.any {it.satisfied}
            results+=ProjectionDay(date,close,indexClose,analysis,hit&&!seen,seen)
            if(scenario.simulateReset&&hit) {
                val next=TradingCalendar.nextDays(date,1).first()
                state=ResetDates(if(analysis.levels.any {it.ruleId=="normal"&&it.satisfied})next else state.normal,if(analysis.levels.any {it.ruleId!="normal"&&it.satisfied})next else state.severe)
            }
            seen=seen||hit
        }
        return results
    }
    fun recentEvents(events:List<OfficialEvent>,calendar:List<LocalDate>,asOf:LocalDate):List<OfficialEvent> {
        val dates=calendar.filter {it<=asOf}.distinct().sorted().takeLast(10).toSet()
        return events.filter {it.date in dates}.distinctBy {it.id}.sortedByDescending {it.date}
    }
}
