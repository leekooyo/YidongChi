package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

class PoolRepository(private val directory:File,private val request:(String,Boolean)->String=MarketRepository()::get):PoolSource {
    private val indexSymbols=listOf("sh000002","sz399107","sh000688","sz399102")
    init {directory.mkdirs()}
    override suspend fun universe():List<Stock> = withContext(Dispatchers.IO) {
        PoolUniverse.collect {page->PoolCodec.universePage(request("https://push2.eastmoney.com/api/qt/clist/get?pn=$page&pz=100&po=1&np=1&fltt=2&invt=2&fid=f12&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f12,f13,f14",false))}
    }
    private fun history(symbol:String,date:LocalDate,provider:String):List<Bar> {
        val source=if(provider=="腾讯公开行情")"tencent"else "eastmoney"
        val cache=File(directory,"$date-$source-$symbol.json")
        fun parse(text:String)=if(source=="tencent")MarketParser.daily(symbol,text)else MarketParser.eastDaily(symbol,text)
        if(cache.isFile)return parse(cache.readText())
        val text=if(source=="tencent")request("https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$symbol,day,,,120,qfq",false) else {
            val secid=(if(symbol.startsWith("sh"))"1."else "0.")+symbol.drop(2)
            request("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=$secid&klt=101&fqt=1&beg=20260101&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",false)
        }
        return parse(text).also{require(it.isNotEmpty()){"日线数据为空"};cache.writeText(text)}
    }
    override suspend fun basis():PoolBasis=withContext(Dispatchers.IO) {
        val text=request("https://qt.gtimg.cn/q=${indexSymbols.joinToString(",")}",true)
        val quotes=indexSymbols.associateWith{s->MarketParser.quote(s,text.split(';').first{it.contains("v_$s=")})}
        val dates=quotes.values.map{it.time.toLocalDate()}.distinct()
        require(dates.size==1){"板块指数行情日期不一致"}
        val date=dates.single();TradingCalendar.nextDays(date,1)
        var provider="腾讯公开行情"
        val history=try{indexSymbols.associateWith{history(it,date,provider)}}catch(e:Exception){if(e is CancellationException)throw e;provider="腾讯即时报价 / 东方财富前复权日线";indexSymbols.associateWith{history(it,date,provider)}}
        PoolBasis(quotes.values.maxOf{it.time},history.mapValues{(s,b)->MarketParser.withQuote(b,quotes.getValue(s),true)},provider)
    }
    override suspend fun load(stock:Stock,basis:PoolBasis):MarketSnapshot=withContext(Dispatchers.IO) {
        val raw=MarketParser.quote(stock.symbol,request("https://qt.gtimg.cn/q=${stock.symbol}",true))
        require(raw.time.toLocalDate()==basis.time.toLocalDate()){"个股行情日期不一致或停牌"}
        if(raw.volume.signum()==0)throw PoolExcluded("停牌或当日无成交")
        val bars=history(stock.symbol,basis.time.toLocalDate(),basis.provider)
        val metadata=if(bars.size>=35)raw.stock else {
            val secid=(if(stock.symbol.startsWith("sh"))"1."else "0.")+stock.code
            MarketParser.stockMetadata(raw.stock,request("https://push2.eastmoney.com/api/qt/stock/get?secid=$secid&fields=f189",false))
        }
        val quote=raw.copy(stock=metadata)
        val aligned=MarketParser.withQuote(bars,quote)
        if(aligned.size<2)throw PoolExcluded("历史日线不足")
        val index=basis.indices.getValue(stock.board.indexSymbol)
        val iq=Quote(Stock(stock.board.indexSymbol,"基准指数",stock.board),index.last().close,index.last().previousClose,basis.time,index.last().high,index.last().low,null,null)
        MarketSnapshot(quote,iq,aligned,index,basis.provider)
    }
    fun cached():PoolBatch?=File(directory,"batch.json").takeIf{it.isFile}?.let{PoolCodec.decode(it.readText())}
    fun save(batch:PoolBatch) {
        val temp=File(directory,"batch.tmp");temp.writeText(PoolCodec.encode(batch))
        java.nio.file.Files.move(temp.toPath(),File(directory,"batch.json").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
