package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PoolTest {
    private val day=LocalDate.parse("2026-11-06")
    private val stocks=listOf(Stock("sz002086","东方海洋",Board.SZ_MAIN),Stock("sh600000","浦发银行",Board.SH_MAIN))
    private val ix=listOf(Bar(day,"100".bd(),"100".bd()))
    private val basis=PoolBasis(LocalDateTime.parse("2026-11-06T15:00:00"),mapOf("sz399107" to ix,"sh000002" to ix))
    private fun snapshot(s:Stock):MarketSnapshot {
        val q=Quote(s,"19".bd(),"10".bd(),basis.time,"19".bd(),"19".bd(),"20.9".bd(),"17.1".bd())
        return MarketSnapshot(q,q.copy(stock=Stock(s.board.indexSymbol,"指数",s.board),price="100".bd()),listOf(Bar(day,q.price,q.previousClose)),ix)
    }
    @Test fun universeDeduplicatesAndRejectsIncompleteOrOtherMarkets()=runBlocking {
        val page=PoolCodec.universePage("""{"data":{"total":2,"diff":[{"f12":"002086","f13":0,"f14":"东方海洋"},{"f12":"600000","f13":1,"f14":"浦发银行"}]}}""")
        assertEquals(2,page.total);assertEquals(stocks.map{it.symbol}.toSet(),page.stocks.map{it.symbol}.toSet())
        val pages=PoolUniverse.collect {n->if(n==1)UniversePage(3,stocks) else UniversePage(3,listOf(stocks.last(),Stock("sz300949","奥雅",Board.CHINEXT)))}
        assertEquals(3,pages.size)
        try {PoolUniverse.collect {UniversePage(3,stocks)};fail("重复页不能冒充全市场")}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("完整"))}
    }
    @Test fun scanResumeDoesNotReloadSuccessAndExplicitRetryClearsFailures()=runBlocking {
        val calls=mutableListOf<String>();var fail=true
        val source=object:PoolSource {
            override suspend fun universe()=stocks
            override suspend fun basis()=this@PoolTest.basis
            override suspend fun load(stock:Stock,basis:PoolBasis):MarketSnapshot {
                calls+=stock.symbol
                if(stock==stocks.last()&&fail)error("接口失败")
                return snapshot(stock)
            }
        }
        val first=PoolScanner(source).scan(PoolBatch(basis,stocks),{ResetDates()}){}
        assertEquals(2,first.processed);assertEquals(1,first.failures.size);assertFalse(first.complete)
        val restored=PoolCodec.decode(PoolCodec.encode(first))
        assertEquals(first.basis,restored.basis);assertEquals(first.rows.first().hits.first().level,restored.rows.first().hits.first().level)
        fail=false
        val retry=PoolScanner(source).scan(restored.retryFailures(),{ResetDates()}){}
        assertTrue(retry.complete);assertEquals(2,retry.rows.size);assertEquals(1,calls.count{it==stocks.first().symbol})
        val stable=PoolScanner(source).scan(retry,{ResetDates()}){}
        assertEquals(retry,stable);assertEquals(3,calls.size)
    }
    @Test fun staleSnapshotIsFailureAndCancellationKeepsCompletedProgress()=runBlocking {
        var checkpoint:PoolBatch?=null
        val source=object:PoolSource {
            override suspend fun universe()=stocks
            override suspend fun basis()=this@PoolTest.basis
            override suspend fun load(stock:Stock,basis:PoolBasis):MarketSnapshot {
                if(stock==stocks.last())delay(10000)
                return snapshot(stock)
            }
        }
        val job=launch {PoolScanner(source).scan(PoolBatch(basis,stocks),{ResetDates()}) {checkpoint=it}}
        withTimeout(2000){while(checkpoint==null)yield()};job.cancelAndJoin()
        assertEquals(1,checkpoint!!.processed)
        assertEquals(listOf(stocks.first().symbol),checkpoint!!.rows.map{it.stock.symbol})
    }
    @Test fun onlyMatchingDirectionsAndRulesRemainWithZeroFirst() {
        val low=TomorrowPool.calculate(stocks.first(),listOf(Bar(day,"19".bd(),"10".bd())),ix)
        val high=TomorrowPool.calculate(stocks.last(),listOf(Bar(day,"29".bd(),"10".bd())),ix)
        assertEquals(stocks.last().symbol,TomorrowPool.filtered(listOf(low,high),Direction.UP).first().stock.symbol)
        assertEquals(listOf(stocks.first().symbol),TomorrowPool.filtered(listOf(low,high),Direction.UP,"severe10",setOf(stocks.first().symbol)).map{it.stock.symbol})
        assertTrue(TomorrowPool.filtered(listOf(low,high),Direction.DOWN).isEmpty())
    }
}
