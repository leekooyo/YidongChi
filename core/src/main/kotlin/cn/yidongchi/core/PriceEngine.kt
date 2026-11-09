package cn.yidongchi.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object PriceEngine {
    private val one=BigDecimal.ONE
    fun deviation(bars:List<Bar>, index:List<Bar>):BigDecimal =
        growth(bars).subtract(growth(index))

    fun growth(bars:List<Bar>):BigDecimal = bars.fold(one) { acc,bar -> acc.multiply(bar.close.ratio(bar.previousClose),MC) }

    fun tick(value:BigDecimal,unit:BigDecimal,round:RoundingMode):BigDecimal = value.divide(unit,0,round).multiply(unit)

    fun threshold(base:BigDecimal,indexGrowth:BigDecimal,target:BigDecimal,unit:BigDecimal,direction:Direction):BigDecimal =
        tick(base.multiply(indexGrowth.add(target),MC),unit,if(direction==Direction.UP) RoundingMode.CEILING else RoundingMode.FLOOR)

    fun limits(stock:Stock,previous:BigDecimal):Pair<BigDecimal,BigDecimal> =
        tick(previous.multiply(one.subtract(stock.board.limit)),stock.tick,RoundingMode.HALF_UP).max(stock.tick) to
            tick(previous.multiply(one.add(stock.board.limit)),stock.tick,RoundingMode.HALF_UP)

    fun analyze(stock:Stock,bars:List<Bar>,index:List<Bar>,targetDate:LocalDate,indexPrice:BigDecimal,resets:ResetDates=ResetDates()):Analysis {
        val currentBar=bars.last {it.date==targetDate}
        val indexByDate=index.associateBy {it.date}
        val (lower,upper)=limits(stock,currentBar.previousClose)
        if(!currentBar.eligible||indexByDate[targetDate]==null) return Analysis(targetDate,currentBar.close,currentBar.previousClose,indexPrice,emptyList(),lower,upper)
        val history=bars.filter {it.date<=targetDate}.sortedBy {it.date}
        val output=mutableListOf<PriceLevel>()
        for((ruleId,days) in listOf("normal" to 3,"severe10" to 10,"severe30" to 30)) {
            val reset=if(ruleId=="normal")resets.normal else resets.severe
            val candidates=mutableListOf<PriceLevel>()
            for(length in 1..minOf(days,history.size)) {
                val window=history.takeLast(length)
                if(window.any {!it.eligible||it.date !in indexByDate} || (reset!=null&&window.first().date<reset)) continue
                val ix=window.map { indexByDate.getValue(it.date) }.toMutableList()
                ix[ix.lastIndex]=ix.last().copy(close=indexPrice)
                val beforeGrowth=growth(window.dropLast(1))
                val base=currentBar.previousClose.ratio(beforeGrowth)
                val indexGrowth=growth(ix)
                val deviation=deviation(window,ix)
                val up=when(ruleId) {"normal"->stock.board.normal;"severe10"->one;else->"2".bd()}
                val down=when(ruleId) {"normal"->stock.board.normal.negate();"severe10"->"-.50".bd();else->"-.70".bd()}
                for((direction,target) in listOf(Direction.UP to up,Direction.DOWN to down)) {
                    val price=threshold(base,indexGrowth,target,stock.tick,direction)
                    if(price<stock.tick && direction==Direction.DOWN) continue
                    val reachable=if(direction==Direction.UP)price<=upper else price>=lower
                    val satisfied=if(direction==Direction.UP)deviation>=target else deviation<=target
                    candidates+=PriceLevel(ruleId,when(ruleId){"normal"->"3日异常";"severe10"->"10日严重异动";else->"30日严重异动"},direction,window.first().date,targetDate,length,price,base.multiply(indexGrowth.add(target),MC),deviation,target,reachable,satisfied)
                }
            }
            for(direction in Direction.entries) {
                val filtered=candidates.filter {it.direction==direction}
                val best=if(direction==Direction.UP)filtered.sortedWith(compareBy<PriceLevel>{it.price}.thenBy{it.start}).firstOrNull()
                    else filtered.sortedWith(compareByDescending<PriceLevel>{it.price}.thenBy{it.start}).firstOrNull()
                best?.let(output::add)
            }
        }
        return Analysis(targetDate,currentBar.close,currentBar.previousClose,indexPrice,output,lower,upper)
    }
}
