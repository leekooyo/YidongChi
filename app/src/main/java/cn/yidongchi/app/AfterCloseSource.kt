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
    suspend fun repair(stock:Stock,quote:Quote,date:LocalDate,today:Boolean=false):HistorySeries
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
    override suspend fun repair(stock:Stock,quote:Quote,date:LocalDate,today:Boolean):HistorySeries = withContext(Dispatchers.IO) {
        var listing=stock.listedOn?.let{quote.stock.copy(listedOn=it)}
        suspend fun metadata():Stock {
            listing?.let{return it}
            val secid=(if(stock.symbol.startsWith("sh"))"1."else "0.")+stock.code
            return MarketParser.stockMetadata(quote.stock,legacy.fetch("https://push2.eastmoney.com/api/qt/stock/get?secid=$secid&fields=f189")).also{listing=it}
        }
        suspend fun daily(provider:String):List<Bar> {
            val bars=try{legacy.history(stock.symbol,date,AfterCloseIndices.label(provider),true)}catch(e:IllegalArgumentException) {
                // The daily parsers need a predecessor, so a verified IPO's first row produces no return bar.
                if(e.message=="日线数据为空"&&SecurityEligibility.initialExempt(metadata(),date))throw PoolExcluded("上市初期无涨跌幅限制，暂不推算")
                throw e
            }
            val last=bars.last()
            val previousSession= today && last.date<date && TradingCalendar.nextDays(last.date,1).single()==date &&
                last.close.compareTo(quote.previousClose)==0
            require(last.date==date||previousSession){"日线尚未更新，稍后重试历史回补"}
            return bars
        }
        var provider=if(tencentUnavailable)"eastmoney"else "tencent"
        val bars=try{daily(provider)}catch(e:Exception){
            if(e is CancellationException||e is PoolExcluded)throw e
            if(provider!="tencent")throw e
            tencentUnavailable=true;provider="eastmoney";daily(provider)
        }
        val effective=if(bars.size>=35)quote.stock.copy(listedOn=stock.listedOn)else metadata()
        HistorySeries(effective,provider,MarketParser.withQuote(bars,quote.copy(stock=effective)))
    }
    override suspend fun indexHistory(symbol:String,provider:String,date:LocalDate):HistorySeries = withContext(Dispatchers.IO) {
        HistorySeries(Stock(symbol,"基准指数",Watchlist.boardFor(symbol)),provider,legacy.history(symbol,date,AfterCloseIndices.label(provider),true))
    }
    companion object {
        fun splitQuotes(text:String):Map<String,String> = text.split(';').mapNotNull{row->Regex("v_((?:sh|sz)\\d{6})=").find(row)?.groupValues?.get(1)?.let{it to "$row;"}}.toMap()
    }
}
