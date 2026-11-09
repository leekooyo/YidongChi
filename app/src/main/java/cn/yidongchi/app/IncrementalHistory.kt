package cn.yidongchi.app

import cn.yidongchi.core.*
import java.time.LocalDate

data class HistorySeries(val stock:Stock,val provider:String,val bars:List<Bar>)
data class HistoryWrite(val series:HistorySeries,val replace:Boolean)
sealed interface HistoryDecision {
    data class Write(val value:HistoryWrite):HistoryDecision
    data object Repair:HistoryDecision
    data object Excluded:HistoryDecision
}

object IncrementalHistory {
    fun plan(series:HistorySeries?,quote:Quote,date:LocalDate,index:Boolean=false):HistoryDecision {
        if(!index&&quote.volume.signum()==0)return HistoryDecision.Excluded
        require(quote.time.toLocalDate()==date){"收盘行情未就绪"}
        if(series==null||series.bars.isEmpty())return HistoryDecision.Repair
        if(!index&&series.bars.size<35&&series.stock.listedOn==null)return HistoryDecision.Repair
        val last=series.bars.last()
        if(last.date>date)return HistoryDecision.Repair
        val preceding=series.bars.lastOrNull{it.date<date}?:return HistoryDecision.Repair
        if(TradingCalendar.nextDays(preceding.date,1).first()!=date)return HistoryDecision.Repair
        if(preceding.close.compareTo(quote.previousClose)!=0)return HistoryDecision.Repair
        val stock=quote.stock.copy(listedOn=series.stock.listedOn)
        val bar=Bar(date,quote.price,quote.previousClose,quote.high,quote.low,eligible=index||quote.upper!=null)
        return HistoryDecision.Write(HistoryWrite(HistorySeries(stock,series.provider,listOf(bar)),false))
    }
    fun merge(old:HistorySeries?,write:HistoryWrite):HistorySeries {
        val bars=if(write.replace)write.series.bars else ((old?.bars?:emptyList())+write.series.bars).associateBy{it.date}.values.sortedBy{it.date}
        return write.series.copy(bars=bars.takeLast(if(write.series.stock.symbol in AfterCloseIndices.symbols)320 else 120))
    }
}

object AfterCloseIndices {
    val symbols=listOf("sh000002","sz399107","sh000688","sz399102")
    fun provider(label:String)=if(label=="腾讯公开行情"||label=="tencent")"tencent"else "eastmoney"
    fun label(provider:String)=if(provider=="tencent")"腾讯公开行情"else "腾讯即时报价 / 东方财富前复权日线"
}
