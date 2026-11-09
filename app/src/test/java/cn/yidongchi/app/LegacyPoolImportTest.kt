package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File
import java.time.LocalDate

class LegacyPoolImportTest {
    @Test fun importsActualBarDateAndBothProviderFamiliesWithoutNetwork()=runBlocking {
        val directory=Files.createTempDirectory("old-pool").toFile()
        val raw="""{"data":{"sz002086":{"qfqday":[["2026-11-05","4","4","4","4"],["2026-11-06","4","4.1","4.2","4"]]}}}"""
        File(directory,"2026-11-09-tencent-sz002086.json").writeText(raw)
        File(directory,"2026-11-09-eastmoney-sz002086.json").writeText("""{"data":{"klines":["2026-11-05,4,4,4,4","2026-11-06,4,4.1,4.2,4"]}}""")
        File(directory,"2026-11-08-tencent-sz002086.json").writeText("broken")
        val store=MemoryHistoryStore()
        LegacyPoolImport(directory,store).run()
        assertEquals(LocalDate.parse("2026-11-06"),store.history("sz002086","tencent")!!.bars.last().date)
        assertEquals("4.1".bd(),store.history("sz002086","eastmoney")!!.bars.last().close)
        assertEquals("done",store.metadata("legacy-import"))
        // Once imported, even unavailable old files do not trigger re-import or a cold scan.
        File(directory,"2026-11-09-tencent-sz002086.json").writeText("broken")
        LegacyPoolImport(directory,store).run()
        assertEquals(2,store.histories.size)
    }
    @Test fun batchParserPreservesEveryReturnedSymbolIncludingInvalidRows() {
        val raw="v_sz002086=\"1~东方海洋~002086~4.1\";v_sh600000=\"\";"
        assertEquals(setOf("sz002086","sh600000"),AfterCloseRepository.splitQuotes(raw).keys)
    }
}

class MemoryHistoryStore:PoolHistoryStore {
    val histories=linkedMapOf<Pair<String,String>,HistorySeries>()
    private val meta=mutableMapOf<String,String>()
    private var last:PoolBatch?=null
    private var work:PoolBatch?=null
    private var quoteDay:LocalDate?=null
    private val raw=mutableMapOf<String,String>()
    override fun history(symbol:String,provider:String?)=if(provider!=null)histories[symbol to provider] else histories.values.filter{it.stock.symbol==symbol}.maxWithOrNull(compareBy<HistorySeries>{it.bars.last().date}.thenBy{it.provider=="tencent"})
    override fun write(writes:List<HistoryWrite>) {writes.forEach{w->val k=w.series.stock.symbol to w.series.provider;histories[k]=IncrementalHistory.merge(histories[k],w)}}
    override fun complete()=last
    override fun pending()=work
    override fun begin(batch:PoolBatch,clearQuotes:Boolean) {work=batch;if(clearQuotes||quoteDay!=batch.basis.time.toLocalDate()){raw.clear();quoteDay=batch.basis.time.toLocalDate()}}
    override fun checkpoint(batch:PoolBatch,writes:List<HistoryWrite>) {write(writes);work=batch.copy(outcomes=work?.outcomes.orEmpty()+batch.outcomes)}
    override fun finish(batch:PoolBatch){require(batch.complete);last=batch;work=null}
    override fun quotes(date:LocalDate)=if(quoteDay==date)raw.toMap()else emptyMap()
    override fun saveQuotes(date:LocalDate,quotes:Map<String,String>) {if(quoteDay!=date){raw.clear();quoteDay=date};raw.putAll(quotes)}
    override fun metadata(key:String)=meta[key]
    override fun metadata(key:String,value:String){meta[key]=value}
}
