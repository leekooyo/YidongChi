package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AfterCloseUpdaterTest {
    private val day=LocalDate.parse("2026-11-09")
    private val stock=Stock("sz002086","东方海洋",Board.SZ_MAIN,LocalDate.parse("2007-02-05"))
    private fun raw(s:Stock,day:LocalDate,previous:String="4") :String {
        val f=MutableList(60){"0"};f[1]=s.name;f[3]="4.1";f[4]=previous;f[6]="100";f[30]=day.toString().replace("-","")+"150100";f[33]="4.2";f[34]="3.9";f[47]="4.4";f[48]="3.6"
        return "v_${s.symbol}=\"${f.joinToString("~")}\";"
    }
    private inner class Source(val stocks:List<Stock> = listOf(stock)):AfterCloseSource {
        override var requestCount=0
        var repairs=0;var batches=0;var universes=0;var fail=false;var afterQuotes:(suspend ()->Unit)?=null
        override suspend fun indexQuotes():Map<String,Quote>{requestCount++;return AfterCloseIndices.symbols.associateWith{val s=Stock(it,"指数",Watchlist.boardFor(it));Quote(s,"100".bd(),"100".bd(),day.atTime(15,1),"100".bd(),"100".bd(),null,null)}}
        override suspend fun universe():List<Stock>{universes++;requestCount++;return stocks}
        override suspend fun quotes(stocks:List<Stock>):Map<String,String>{batches++;requestCount++;if(fail)error("报价失败");val rows=stocks.associate{it.symbol to raw(it,day)};afterQuotes?.invoke();return rows}
        override suspend fun repair(stock:Stock,quote:Quote,date:LocalDate):HistorySeries {repairs++;return HistorySeries(stock.copy(listedOn=this@AfterCloseUpdaterTest.stock.listedOn),"tencent",listOf(Bar(day.minusDays(3),"4".bd(),"4".bd()),Bar(day,"4.1".bd(),"4".bd())))}
        override suspend fun indexHistory(symbol:String,provider:String,date:LocalDate)=HistorySeries(Stock(symbol,"指数",Watchlist.boardFor(symbol)),provider,listOf(Bar(day.minusDays(3),"100".bd(),"100".bd()),Bar(day,"100".bd(),"100".bd())))
    }
    private fun seed(stocks:List<Stock> = listOf(stock))=MemoryHistoryStore().also{store->
        val series=stocks.map{HistorySeries(it,"tencent",listOf(Bar(day.minusDays(3),"4".bd(),"4".bd())))}+AfterCloseIndices.symbols.map{HistorySeries(Stock(it,"指数",Watchlist.boardFor(it)),"tencent",listOf(Bar(day.minusDays(3),"100".bd(),"100".bd())))}
        store.write(series.map{HistoryWrite(it,true)})
    }
    @Test fun normalNextCloseUsesBatchQuoteAndNoStockHistoryThenSameDayOnlyProbes()=runBlocking {
        val source=Source();val store=seed();val updater=AfterCloseUpdater(source,store)
        val result=updater.update({ResetDates()}){}
        assertTrue(result.complete);assertEquals(0,source.repairs);assertEquals(1,source.batches);assertEquals(2,store.history(stock.symbol)!!.bars.size)
        val requests=source.requestCount
        assertEquals(result,updater.update({ResetDates()}){})
        assertEquals(1,source.requestCount-requests);assertEquals(1,source.universes)
        assertEquals(2,store.history(stock.symbol)!!.bars.size)
    }
    @Test fun gapsRepairOnlyAffectedStocksAndComputeAllStocks()=runBlocking {
        val second=stock.copy(symbol="sz002242",name="九阳")
        val source=Source(listOf(stock,second));val store=seed(source.stocks)
        store.write(listOf(HistoryWrite(HistorySeries(second,"tencent",listOf(Bar(day.minusDays(4),"4".bd(),"4".bd()))),true)))
        val result=AfterCloseUpdater(source,store).update({ResetDates()}){}
        assertTrue(result.complete);assertEquals(2,result.processed);assertEquals(1,source.repairs)
    }
    @Test fun failedUpdatePreservesLastCompleteAndRetryUsesSavedQuotes()=runBlocking {
        val source=Source();val store=seed()
        val previous=PoolBatch(PoolBasis(day.minusDays(3).atTime(15,1),emptyMap()),listOf(stock),mapOf(stock.symbol to PoolOutcome(excluded=true)))
        store.begin(previous);store.finish(previous)
        source.fail=true
        try{AfterCloseUpdater(source,store).update({ResetDates()}){};fail("failure") }catch(e:IllegalStateException){assertEquals("报价失败",e.message)}
        assertEquals(previous,store.complete());assertNotNull(store.pending())
        source.fail=false
        val result=AfterCloseUpdater(source,store).update({ResetDates()}){}
        assertTrue(result.complete);assertEquals(result,store.complete())
    }
    @Test fun cancellationAfterQuoteCheckpointResumesWithoutDownloadingChunk()=runBlocking {
        val source=Source();val store=seed()
        val job=launch {AfterCloseUpdater(source,store).update({ResetDates()}){p->if(p.stage=="本地计算")cancel()}}
        job.join();assertTrue(job.isCancelled)
        assertEquals(1,source.batches);assertEquals(1,store.quotes(day).size)
        val result=AfterCloseUpdater(source,store).update({ResetDates()}){}
        assertTrue(result.complete);assertEquals(1,source.batches)
    }
    @Test fun forceRefreshReplacesSameDayWithoutDuplicates()=runBlocking {
        val source=Source();val store=seed();val updater=AfterCloseUpdater(source,store)
        updater.update({ResetDates()}){}
        updater.update({ResetDates()},force=true){}
        assertEquals(2,source.batches);assertEquals(2,store.history(stock.symbol)!!.bars.size);assertEquals(0,source.repairs)
    }
}
