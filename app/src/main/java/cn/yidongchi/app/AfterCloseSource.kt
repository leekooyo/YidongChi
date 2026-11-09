package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

interface AfterCloseSource {
    val requestCount:Int
    suspend fun indexQuotes():Map<String,Quote>
    suspend fun universe():List<Stock>
    suspend fun quotes(stocks:List<Stock>):Map<String,String>
    suspend fun repair(stock:Stock,quote:Quote,date:LocalDate):HistorySeries
    suspend fun indexHistory(symbol:String,provider:String,date:LocalDate):HistorySeries
}

class AfterCloseRepository(directory:File,request:((String,Boolean)->String)?=null):AfterCloseSource {
    private val legacy=PoolRepository(directory,request)
    override val requestCount get()=legacy.requestCount
    private var tencentUnavailable=false
    override suspend fun indexQuotes():Map<String,Quote> = withContext(Dispatchers.IO) {
        val rows=splitQuotes(legacy.fetch("https://qt.gtimg.cn/q=${AfterCloseIndices.symbols.joinToString(",")}",true))
        AfterCloseIndices.symbols.associateWith{MarketParser.quote(it,rows.getValue(it))}
    }
    override suspend fun universe()=legacy.universe()
    override suspend fun quotes(stocks:List<Stock>):Map<String,String> = withContext(Dispatchers.IO) {
        require(stocks.size<=100)
        splitQuotes(legacy.fetch("https://qt.gtimg.cn/q=${stocks.joinToString(","){it.symbol}}",true))
    }
    override suspend fun repair(stock:Stock,quote:Quote,date:LocalDate):HistorySeries = withContext(Dispatchers.IO) {
        var provider=if(tencentUnavailable)"eastmoney"else "tencent"
        val bars=try{legacy.history(stock.symbol,date,AfterCloseIndices.label(provider),true)}catch(e:Exception){
            if(e is CancellationException)throw e
            if(provider!="tencent")throw e
            tencentUnavailable=true;provider="eastmoney";legacy.history(stock.symbol,date,AfterCloseIndices.label(provider),true)
        }
        val metadata=if(bars.size>=35)quote.stock else {
            val secid=(if(stock.symbol.startsWith("sh"))"1."else "0.")+stock.code
            MarketParser.stockMetadata(quote.stock,legacy.fetch("https://push2.eastmoney.com/api/qt/stock/get?secid=$secid&fields=f189"))
        }
        HistorySeries(metadata,provider,MarketParser.withQuote(bars,quote.copy(stock=metadata)))
    }
    override suspend fun indexHistory(symbol:String,provider:String,date:LocalDate):HistorySeries = withContext(Dispatchers.IO) {
        HistorySeries(Stock(symbol,"基准指数",Watchlist.boardFor(symbol)),provider,legacy.history(symbol,date,AfterCloseIndices.label(provider),true))
    }
    companion object {
        fun splitQuotes(text:String):Map<String,String> = text.split(';').mapNotNull{row->Regex("v_((?:sh|sz)\\d{6})=").find(row)?.groupValues?.get(1)?.let{it to "$row;"}}.toMap()
    }
}
