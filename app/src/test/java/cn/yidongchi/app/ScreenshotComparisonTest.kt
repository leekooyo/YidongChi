package cn.yidongchi.app

import cn.yidongchi.core.*
import org.junit.Assert.*
import org.junit.Test

class ScreenshotComparisonTest {
    private fun text(symbol:String,suffix:String)=javaClass.getResource("/comparison-20261109/$symbol$suffix")!!.readText()
    private fun snapshot(symbol:String):MarketSnapshot {
        val index=Watchlist.boardFor(symbol).indexSymbol
        val q=MarketParser.quote(symbol,text(symbol,"-quote.txt"))
        val iq=MarketParser.quote(index,text(index,"-quote.txt"))
        return MarketSnapshot(q,iq,MarketParser.withQuote(MarketParser.daily(symbol,text(symbol,"-daily.json")),q),MarketParser.withQuote(MarketParser.daily(index,text(index,"-daily.json")),iq,true))
    }
    @Test fun unchangedIndexNextDayMatchesAllFourScreenshotPrices() {
        for((symbol,rule,expected) in listOf(Triple("sz002242","severe30","26.88"),Triple("sz002086","severe10","4.64"),Triple("sz300949","severe30","64.76"),Triple("sz301697","severe10","41.34"))) {
            val s=snapshot(symbol)
            val result=Projection.project(s.quote.stock,s.bars,s.indexBars,Scenario(),1).first().analysis
            assertEquals(symbol,expected,result.levels.first {it.ruleId==rule&&it.direction==Direction.UP}.price.toPlainString())
            val pool=TomorrowPool.calculate(s.quote.stock,s.bars,s.indexBars)
            assertEquals(symbol,expected,pool.hits.first {it.level.ruleId==rule&&it.level.direction==Direction.UP}.level.price.toPlainString())
        }
    }
    @Test fun todayUsesYesterdayCloseAndDoesNotRollToTomorrowsWindow() {
        for((symbol,rule,expected) in listOf(Triple("sz002242","severe30","26.87"),Triple("sz300949","severe30","63.53"),Triple("sz301697","severe10","41.34"))) {
            val s=snapshot(symbol)
            val result=PriceEngine.analyze(s.quote.stock,s.bars,s.indexBars,s.quote.time.toLocalDate(),s.indexQuote.price)
            assertEquals(symbol,expected,result.levels.first {it.ruleId==rule&&it.direction==Direction.UP}.price.toPlainString())
        }
    }
}
