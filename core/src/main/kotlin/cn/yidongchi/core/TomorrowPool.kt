package cn.yidongchi.core

import java.math.BigDecimal
import java.time.LocalDate

data class PoolHit(val level:PriceLevel,val requiredChange:BigDecimal)
data class PoolStock(val stock:Stock,val targetDate:LocalDate,val price:BigDecimal,val hits:List<PoolHit>,val excludedReason:String?=null)

object TomorrowPool {
    fun calculate(stock:Stock,bars:List<Bar>,index:List<Bar>,resets:ResetDates=ResetDates()):PoolStock {
        val current=bars.last()
        require(index.last().date==current.date){"个股与基准指数日期不一致"}
        val target=TradingCalendar.nextDays(current.date,1).single()
        val excluded=when {
            stock.excludedFromDeviation->"特殊交易阶段"
            !current.eligible->"无涨跌幅限制或资格未确认"
            !SecurityEligibility.eligible(stock,target)->"目标日不参与偏离计算"
            else->null
        }
        if(excluded!=null)return PoolStock(stock,target,current.close,emptyList(),excluded)
        val analysis=Projection.project(stock,bars,index,Scenario(),1,resets).single().analysis
        val hits=analysis.levels.filter {it.ruleId!="normal"&&(it.satisfied||it.reachable)}.map {
            PoolHit(it,if(it.satisfied)BigDecimal.ZERO else it.price.subtract(current.close).ratio(current.close))
        }
        return PoolStock(stock,target,current.close,hits)
    }

    fun filtered(rows:List<PoolStock>,direction:Direction,rule:String?=null,symbols:Set<String>?=null):List<PoolStock> =
        rows.asSequence().filter {symbols==null||it.stock.symbol in symbols}
            .map {row->row.copy(hits=row.hits.filter {it.level.direction==direction&&(rule==null||it.level.ruleId==rule)}.sortedBy {it.requiredChange.abs()})}
            .filter {it.hits.isNotEmpty()}.sortedWith(compareBy<PoolStock>{it.hits.first().requiredChange.abs()}.thenBy{it.stock.symbol}).toList()
}
