package cn.yidongchi.core

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

val MC: MathContext = MathContext.DECIMAL128
fun String.bd() = BigDecimal(this)
fun BigDecimal.ratio(other: BigDecimal): BigDecimal = divide(other, MC)

enum class Board(val label:String,val indexSymbol:String,val normal:BigDecimal,val tick:BigDecimal,val limit:BigDecimal,val currency:String) {
    SH_MAIN("沪市主板","sh000002",".20".bd(),".01".bd(),".10".bd(),"CNY"),
    SZ_MAIN("深市主板","sz399107",".20".bd(),".01".bd(),".10".bd(),"CNY"),
    STAR("科创板","sh000688",".30".bd(),".01".bd(),".20".bd(),"CNY"),
    CHINEXT("创业板","sz399102",".30".bd(),".01".bd(),".20".bd(),"CNY"),
    SH_B("沪市B股","sh000003",".20".bd(),".001".bd(),".10".bd(),"USD"),
    SZ_B("深市B股","sz399108",".20".bd(),".01".bd(),".10".bd(),"HKD")
}
data class Stock(val symbol:String,val name:String,val board:Board) {
    val code get()=symbol.drop(2)
    val tick get()=board.tick
}
data class Bar(val date:LocalDate,val close:BigDecimal,val previousClose:BigDecimal,val high:BigDecimal=close,val low:BigDecimal=close,val turnover:BigDecimal?=null,val eligible:Boolean=true)
enum class Direction { UP, DOWN }
data class ResetDates(val normal:LocalDate?=null,val severe:LocalDate?=null)
data class PriceLevel(val ruleId:String,val label:String,val direction:Direction,val start:LocalDate,val end:LocalDate,val sessions:Int,val price:BigDecimal,val theoretical:BigDecimal,val deviation:BigDecimal,val threshold:BigDecimal,val reachable:Boolean,val satisfied:Boolean)
data class Analysis(val targetDate:LocalDate,val current:BigDecimal,val previousClose:BigDecimal,val indexPrice:BigDecimal,val levels:List<PriceLevel>,val lowerLimit:BigDecimal,val upperLimit:BigDecimal)
data class OfficialEvent(val id:String,val symbol:String,val date:LocalDate,val kind:String,val direction:Direction?,val sourceUrl:String,val confirmed:Boolean=true,val monitorThrough:LocalDate?=null)
data class Scenario(val name:String="价格不变",val dailyStockPercent:BigDecimal=BigDecimal.ZERO,val dailyIndexPercent:BigDecimal=BigDecimal.ZERO,val stockPath:List<BigDecimal> = emptyList(),val indexPath:List<BigDecimal> = emptyList(),val limitPath:Int=0,val simulateReset:Boolean=false)
data class ProjectionDay(val date:LocalDate,val close:BigDecimal,val indexClose:BigDecimal,val analysis:Analysis,val firstTrigger:Boolean,val afterHypotheticalTrigger:Boolean)
data class WatchItem(val symbol:String,val name:String,val group:String="默认",val pinned:Boolean=false,val muted:Boolean=false)
data class AlertItem(val key:String,val symbol:String,val text:String,val date:LocalDate,val read:Boolean=false)
