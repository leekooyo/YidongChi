package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime

data class CloseProgress(val stage:String,val batch:PoolBatch?=null,val requests:Int=0,val repairs:Int=0)

class AfterCloseUpdater(private val source:AfterCloseSource,private val store:PoolHistoryStore) {
    suspend fun update(resets:(String)->ResetDates,force:Boolean=false,rebuild:Boolean=false,onProgress:suspend (CloseProgress)->Unit):PoolBatch {
        var repairs=0
        val initialRequests=source.requestCount
        suspend fun progress(stage:String,batch:PoolBatch?=null){onProgress(CloseProgress(stage,batch,source.requestCount-initialRequests,repairs));currentCoroutineContext().ensureActive()}
        progress("探测收盘日期")
        val quotes=source.indexQuotes()
        val dates=quotes.values.map{it.time.toLocalDate()}.distinct()
        require(dates.size==1&&quotes.size==4&&quotes.values.all{it.time.toLocalTime()>=LocalTime.of(15,0)}){"收盘行情尚未就绪，请稍后更新"}
        val date=dates.single();TradingCalendar.nextDays(date,1)
        val complete=store.complete();val pending=store.pending()
        if(!force&&!rebuild&&pending==null&&complete?.basis?.time?.toLocalDate()==date){progress("已是最新收盘数据",complete);return complete}
        require(complete==null||date>=complete.basis.time.toLocalDate()){"行情源日期早于本地结果，请稍后重试"}
        val indexCache=mutableMapOf<Pair<String,String>,HistorySeries>()
        suspend fun index(symbol:String,provider:String):HistorySeries {
            val key=symbol to provider
            return indexCache.getOrPutSuspend(key) {
                val q=quotes.getValue(symbol)
                val old=store.history(symbol,provider)
                val plan=if(rebuild)HistoryDecision.Repair else IncrementalHistory.plan(old,q,date,true)
                val write=if(plan is HistoryDecision.Write)plan.value else HistoryWrite(source.indexHistory(symbol,provider,date).let{it.copy(bars=MarketParser.withQuote(it.bars,q,true))},true)
                store.write(listOf(write))
                IncrementalHistory.merge(old,write)
            }
        }
        var provider="tencent"
        val primary=try {AfterCloseIndices.symbols.associateWith{index(it,provider).bars}}catch(e:Exception) {
            if(e is CancellationException)throw e
            provider="eastmoney";AfterCloseIndices.symbols.associateWith{index(it,provider).bars}
        }
        val basis=PoolBasis(quotes.values.maxOf{it.time},primary,AfterCloseIndices.label(provider))
        var batch=if(!force&&!rebuild&&pending?.basis?.time?.toLocalDate()==date)pending.retryFailures()
            else {progress("同步证券清单");PoolBatch(basis,source.universe())}
        store.begin(batch,clearQuotes=force||rebuild)
        val saved=store.quotes(date).toMutableMap()
        for(chunk in batch.universe.filter{it.symbol !in batch.outcomes}.chunked(100)) {
            progress("同步行情",batch)
            val missing=chunk.filter{it.symbol !in saved}
            if(missing.isNotEmpty()) {
                val incoming=source.quotes(missing)
                // Only stage usable current quotes (or explicit no-trade rows). Missing/stale active rows retry on the next update.
                val reusable=incoming.filter{(symbol,raw)->runCatching{val q=MarketParser.quote(symbol,raw);q.volume.signum()==0||IncrementalHistory.closed(q,date)}.getOrDefault(noTrade(raw))}
                store.saveQuotes(date,reusable);saved.putAll(incoming)
            }
            progress("本地计算",batch)
            val outcomes=linkedMapOf<String,PoolOutcome>();val writes=mutableListOf<HistoryWrite>()
            for(stock in chunk) {
                currentCoroutineContext().ensureActive()
                val outcome=try {
                    if(stock.symbol=="sh600849"&&stock.name=="上药转换")throw PoolExcluded("历史转换代码，现证券代码为601607")
                    val raw=saved[stock.symbol]?:error("未取得个股报价")
                    if(noTrade(raw))throw PoolExcluded("停牌或暂无成交价")
                    val q=MarketParser.quote(stock.symbol,raw)
                    val old=store.history(stock.symbol)
                    val checked=IncrementalHistory.plan(old,q,date)
                    if(checked is HistoryDecision.Excluded)throw PoolExcluded("停牌或当日无成交")
                    val plan=if(rebuild)HistoryDecision.Repair else checked
                    val write=if(plan is HistoryDecision.Write)plan.value else {
                        repairs++;progress("补齐历史",batch)
                        HistoryWrite(source.repair(old?.stock?:stock,q,date),true)
                    }
                    val series=IncrementalHistory.merge(old,write)
                    var ix=index(stock.board.indexSymbol,series.provider)
                    val oldest=series.bars.takeLast(29).first().date
                    if(ix.bars.first().date>oldest) {
                        ix=source.indexHistory(stock.board.indexSymbol,series.provider,date).let{it.copy(bars=MarketParser.withQuote(it.bars,quotes.getValue(stock.board.indexSymbol),true))}
                        store.write(listOf(HistoryWrite(ix,true)));indexCache[stock.board.indexSymbol to series.provider]=ix
                    }
                    val result=TomorrowPool.calculate(series.stock,series.bars,ix.bars,resets(stock.symbol))
                    writes+=write
                    if(result.excludedReason!=null)PoolOutcome(issue=result.excludedReason,excluded=true,provider=AfterCloseIndices.label(series.provider))
                    else PoolOutcome(result=result.takeIf{it.hits.isNotEmpty()},provider=AfterCloseIndices.label(series.provider))
                }catch(e:CancellationException){throw e}
                catch(e:PoolExcluded){PoolOutcome(issue=e.message,excluded=true)}
                catch(e:Exception){PoolOutcome(issue=e.message?:"更新失败")}
                outcomes[stock.symbol]=outcome
            }
            batch=batch.copy(outcomes=batch.outcomes+outcomes)
            store.checkpoint(batch.copy(outcomes=outcomes),writes)
            progress("本地计算",batch)
        }
        if(batch.complete){store.finish(batch);progress("更新完成",batch)}
        else progress("部分失败，保留上次完整结果",batch)
        return batch
    }
    private suspend fun <K,V> MutableMap<K,V>.getOrPutSuspend(key:K,create:suspend ()->V):V=this[key]?:create().also{this[key]=it}
    private fun noTrade(raw:String):Boolean {
        val fields=raw.substringAfter("=\"").substringBefore('"').split('~')
        return fields.size>30&&fields[3].toBigDecimalOrNull()?.signum()==0&&fields[6].toBigDecimalOrNull()?.signum()==0
    }
}
