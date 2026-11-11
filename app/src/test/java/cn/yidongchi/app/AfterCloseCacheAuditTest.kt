package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Opt-in audit reads the already downloaded market cache, never scans the network. */
class AfterCloseCacheAuditTest {
    @Test fun existingFullMarketCacheMigratesAndSyntheticNextCloseUsesOnlyBatchRequests()=runBlocking {
        val path=System.getenv("YIDONGCHI_LEGACY_CACHE")
        assumeTrue("local full-market cache audit",!path.isNullOrBlank())
        val directory=File(path!!);val store=MemoryHistoryStore()
        val started=System.nanoTime()
        LegacyPoolImport(directory,store).run()
        val imported=store.complete()!!
        assertEquals(5563,imported.universe.size);assertTrue(imported.complete)
        assertEquals(6,imported.rows.size)
        val importMs=(System.nanoTime()-started)/1_000_000
        val date=TradingCalendar.nextDays(imported.basis.time.toLocalDate(),1).single()
        var repairs=0;var quoteRequests=0;var probes=0;var universeRequests=0
        val exclusions=imported.exclusions.keys
        val source=object:AfterCloseSource {
            override val requestCount get()=quoteRequests+probes+universeRequests+repairs
            override suspend fun indexQuotes():Map<String,Quote>{probes++;return AfterCloseIndices.symbols.associateWith{s->val old=store.history(s)!!;val close=old.bars.last().close;Quote(old.stock,close,close,date.atTime(15,1),close,close,null,null)}}
            override suspend fun universe():List<Stock>{universeRequests++;return imported.universe}
            override suspend fun quotes(stocks:List<Stock>):Map<String,String> {
                quoteRequests++
                return stocks.associate{s->val old=store.history(s.symbol);val price=old?.bars?.last()?.close?:"1".bd();val f=MutableList(60){"0"};f[1]=s.name;f[3]=price.toPlainString();f[4]=price.toPlainString();f[6]=if(s.symbol in exclusions)"0"else "100";f[30]=date.toString().replace("-","")+"150100";f[33]=f[3];f[34]=f[3];f[47]=price.multiply("1.1".bd()).toPlainString();f[48]=price.multiply("0.9".bd()).toPlainString();s.symbol to "v_${s.symbol}=\"${f.joinToString("~")}\";"}
            }
            override suspend fun repair(stock:Stock,quote:Quote,date:LocalDate,today:Boolean):HistorySeries {
                repairs++
                val old=store.history(stock.symbol)?:error("missing historical cache: ${stock.symbol}")
                return old.copy(stock=quote.stock.copy(listedOn=LocalDate.parse("2000-01-01")),bars=MarketParser.withQuote(old.bars,quote))
            }
            override suspend fun indexHistory(symbol:String,provider:String,date:LocalDate):HistorySeries=error("unexpected index history request $symbol/$provider")
        }
        val runStart=System.nanoTime()
        val updater=AfterCloseUpdater(source,store)
        val result=updater.update({ResetDates()}){}
        assertTrue("failures: ${result.failures.entries.take(5)}",result.complete)
        assertEquals(56,quoteRequests)
        val before=source.requestCount
        updater.update({ResetDates()}){}
        assertEquals(1,source.requestCount-before)
        val report=JSONObject().put("inputDate",imported.basis.time.toLocalDate()).put("syntheticDate",date).put("stocks",result.processed).put("importedSeries",store.histories.size).put("importMillis",importMs).put("updateMillis",(System.nanoTime()-runStart)/1_000_000).put("batchQuoteRequests",quoteRequests).put("historyRepairs",repairs).put("sameDayRepeatRequests",source.requestCount-before).put("network",false).put("note","Real cached history, synthetic next-day unchanged prices; not a live-market speed benchmark.")
        val output=System.getenv("YIDONGCHI_AUDIT_REPORT")
        if(output!=null)File(output).writeText(report.toString(2))
        println(report.toString())
    }
}
