package cn.yidongchi.core

import java.time.LocalDate

object SecurityEligibility {
    fun initialExempt(stock:Stock,date:LocalDate):Boolean {
        val listed=stock.listedOn?:return false
        if(date<listed||date>listed.plusDays(20))return false
        require(listed.year==2026){"该上市年份的初始交易日历尚未收录"}
        return date in TradingCalendar.nextDays(listed.minusDays(1),5)
    }
    fun eligible(stock:Stock,date:LocalDate)=!stock.excludedFromDeviation&&!initialExempt(stock,date)
    fun apply(stock:Stock,bars:List<Bar>)=bars.map {it.copy(eligible=it.eligible&&eligible(stock,it.date))}
}
