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
    fun project(stock:Stock,bars:List<Bar>,index:List<Bar>,scenario:Scenario,count:Int=10,resets:ResetDates=ResetDates(),events:List<OfficialEvent> = emptyList()):List<ProjectionDay> {
        require(!stock.excludedFromDeviation){"该特殊交易阶段不参与本工具异动推演"}
        require(bars.last().eligible||SecurityEligibility.initialExempt(stock,bars.last().date)){"当前为无涨跌幅限制或资格未确认阶段，不能推断未来适用状态"}
        val history=bars.toMutableList();val ix=index.toMutableList();val days=TradingCalendar.nextDays(history.last().date,count)
        val results=mutableListOf<ProjectionDay>();var seen=false;var state=resets;var normalReset=resets.normal
        val scenarioEvents=events.toMutableList()
        for((i,date) in days.withIndex()) {
            val prev=history.last().close;val indexPrev=ix.last().close
            val stockPct=scenario.stockPath.getOrNull(i)?:scenario.dailyStockPercent
            val indexPct=scenario.indexPath.getOrNull(i)?:scenario.dailyIndexPercent
            val eligible=SecurityEligibility.eligible(stock,date)
            require(scenario.limitPath==0||eligible){"该目标日为无涨跌幅限制阶段，不能使用连续涨跌停情景"}
            val close=when(scenario.limitPath) {1->PriceEngine.limits(stock,prev).second;-1->PriceEngine.limits(stock,prev).first;else->PriceEngine.tick(prev.multiply(BigDecimal.ONE.add(stockPct.movePointLeft(2))),stock.tick,RoundingMode.HALF_UP)}
            val indexClose=indexPrev.multiply(BigDecimal.ONE.add(indexPct.movePointLeft(2)),MC)
            history+=Bar(date,close,prev,eligible=eligible);ix+=Bar(date,indexClose,indexPrev)
            val analysis=PriceEngine.analyze(stock,history,ix,date,indexClose,state)
            val hit=analysis.levels.any {it.satisfied}
            // 仅在独立的“假设次日重置”路径内构造非重叠事件；永不写入正式事件库。
            if(scenario.simulateReset) {
                val ordinary=PriceEngine.analyze(stock,history,ix,date,indexClose,ResetDates(normalReset,state.severe)).levels.firstOrNull {it.ruleId=="normal"&&it.satisfied}
                if(ordinary!=null) {scenarioEvents+=OfficialEvent("scenario-$date",stock.symbol,date,"normal",ordinary.direction,"scenario",true);normalReset=TradingCalendar.nextDays(date,1).first()}
            }
            val directions=EventLogic.directionCounts(scenarioEvents,ix.map {it.date},date,stock.board,state.severe)
            results+=ProjectionDay(date,close,indexClose,analysis,hit&&!seen,seen,directions)
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
