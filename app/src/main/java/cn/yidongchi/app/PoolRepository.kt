package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

class PoolRepository(private val directory:File,private val request:((String,Boolean)->String)?=null):PoolSource {
    var requestCount=0;private set
    private val market=MarketRepository()
    private val indexSymbols=listOf("sh000002","sz399107","sh000688","sz399102")
    private val requestGate=Mutex()
    private var lastRequest=0L
    @Volatile private var primaryUnavailable=false
    private val alternateGate=Mutex()
    private var alternate:PoolBasis?=null
    private val alternateProvider="腾讯即时报价 / 东方财富前复权日线"
    init {directory.mkdirs()}
    internal suspend fun fetch(url:String,gbk:Boolean=false):String {
        currentCoroutineContext().ensureActive()
        requestGate.withLock {delay((100L-(System.currentTimeMillis()-lastRequest)).coerceAtLeast(0));lastRequest=System.currentTimeMillis()}
        currentCoroutineContext().ensureActive()
        return suspendCancellableCoroutine {continuation->
            val connection=java.util.concurrent.atomic.AtomicReference<java.net.HttpURLConnection?>()
            continuation.invokeOnCancellation{connection.get()?.disconnect()}
            try {
                requestCount++
                val text=if(request!=null)request.invoke(url,gbk)else market.get(url,gbk){opened->
                    connection.set(opened)
                    if(!continuation.isActive)throw CancellationException("扫描已暂停")
                }
                if(continuation.isActive)continuation.resumeWith(Result.success(text))
            }catch(e:Exception){if(continuation.isActive)continuation.resumeWith(Result.failure(e))}
        }
    }
    override suspend fun universe():List<Stock> = withContext(Dispatchers.IO) {
        PoolUniverse.collect {page->PoolCodec.universePage(fetch("https://push2.eastmoney.com/api/qt/clist/get?pn=$page&pz=100&po=1&np=1&fltt=2&invt=2&fid=f12&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f12,f13,f14"))}
    }
    internal suspend fun history(symbol:String,date:LocalDate,provider:String,fresh:Boolean=false):List<Bar> {
        val source=if(provider=="腾讯公开行情")"tencent"else "eastmoney"
        val cache=File(directory,"$date-$source-$symbol.json")
        fun parse(text:String)=if(source=="tencent")MarketParser.daily(symbol,text)else MarketParser.eastDaily(symbol,text)
        if(!fresh&&cache.isFile)return parse(cache.readText())
        val text=if(source=="tencent")fetch("https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$symbol,day,,,${if(symbol in indexSymbols)320 else 120},qfq") else {
            val secid=(if(symbol.startsWith("sh"))"1."else "0.")+symbol.drop(2)
            fetch("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=$secid&klt=101&fqt=1&beg=20260101&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61")
        }
        return parse(text).also{require(it.isNotEmpty()){"日线数据为空"};if(!fresh)cache.writeText(text)}
    }
    override suspend fun basis():PoolBasis=withContext(Dispatchers.IO) {
        val text=fetch("https://qt.gtimg.cn/q=${indexSymbols.joinToString(",")}",true)
        val quotes=indexSymbols.associateWith{s->MarketParser.quote(s,text.split(';').first{it.contains("v_$s=")})}
        val dates=quotes.values.map{it.time.toLocalDate()}.distinct()
        require(dates.size==1){"板块指数行情日期不一致"}
        val date=dates.single();TradingCalendar.nextDays(date,1)
        prune(date)
        var provider="腾讯公开行情"
        val history=try{indexSymbols.associateWith{history(it,date,provider)}}catch(e:Exception){if(e is CancellationException)throw e;primaryUnavailable=true;provider=alternateProvider;indexSymbols.associateWith{history(it,date,provider)}}
        PoolBasis(quotes.values.maxOf{it.time},history.mapValues{(s,b)->MarketParser.withQuote(b,quotes.getValue(s),true)},provider)
    }
    private suspend fun alternateBasis(basis:PoolBasis):PoolBasis=alternateGate.withLock {
        alternate?.takeIf{it.time==basis.time}?:run {
            val indices=indexSymbols.associateWith{s->
                val last=basis.indices.getValue(s).last()
                val quote=Quote(Stock(s,"基准指数",Watchlist.boardFor(s)),last.close,last.previousClose,basis.time,last.high,last.low,null,null)
                MarketParser.withQuote(history(s,basis.time.toLocalDate(),alternateProvider),quote,true)
            }
            PoolBasis(basis.time,indices,alternateProvider).also{alternate=it}
        }
    }
    override suspend fun load(stock:Stock,basis:PoolBasis):MarketSnapshot=withContext(Dispatchers.IO) {
        // 发行人2025半年报：600849已变更为601607。清单仍保留“上药转换”占位记录。
        // https://dataclouds.cninfo.com.cn/shgonggao/2025/2025-08-29/0137cf4083e511f0a61ffa163e957f7a.pdf
        if(stock.symbol=="sh600849"&&stock.name=="上药转换")throw PoolExcluded("历史转换代码，现证券代码为601607")
        val raw=MarketParser.quote(stock.symbol,fetch("https://qt.gtimg.cn/q=${stock.symbol}",true))
        require(raw.time.toLocalDate()==basis.time.toLocalDate()){"个股行情日期不一致或停牌"}
        if(raw.volume.signum()==0)throw PoolExcluded("停牌或当日无成交")
        var effective=basis
        val bars=if(basis.provider=="腾讯公开行情") {
            // 优先使用已缓存的主源日线；主源拒绝后不对其持续重试。
            val primaryCached=File(directory,"${basis.time.toLocalDate()}-tencent-${stock.symbol}.json").isFile
            if(!primaryUnavailable||primaryCached)try {history(stock.symbol,basis.time.toLocalDate(),basis.provider)}
            catch(e:Exception){if(e is CancellationException)throw e;primaryUnavailable=true;effective=alternateBasis(basis);history(stock.symbol,basis.time.toLocalDate(),effective.provider)}
            else {effective=alternateBasis(basis);history(stock.symbol,basis.time.toLocalDate(),effective.provider)}
        }else history(stock.symbol,basis.time.toLocalDate(),basis.provider)
        val metadata=if(bars.size>=35)raw.stock else {
            val secid=(if(stock.symbol.startsWith("sh"))"1."else "0.")+stock.code
            MarketParser.stockMetadata(raw.stock,fetch("https://push2.eastmoney.com/api/qt/stock/get?secid=$secid&fields=f189"))
        }
        val quote=raw.copy(stock=metadata)
        val aligned=MarketParser.withQuote(bars,quote)
        if(aligned.size<2)throw PoolExcluded("历史日线不足")
        val index=effective.indices.getValue(stock.board.indexSymbol)
        val iq=Quote(Stock(stock.board.indexSymbol,"基准指数",stock.board),index.last().close,index.last().previousClose,basis.time,index.last().high,index.last().low,null,null)
        MarketSnapshot(quote,iq,aligned,index,effective.provider)
    }
    fun cached():PoolBatch?=File(directory,"batch.json").takeIf{it.isFile}?.let{PoolCodec.decode(it.readText())}
    fun prune(current:LocalDate) {
        val protected=runCatching{cached()?.basis?.time?.toLocalDate()}.getOrNull()
        val name=Regex("^(\\d{4}-\\d{2}-\\d{2})-(tencent|eastmoney)-(sh|sz)\\d{6}\\.json$")
        directory.listFiles()?.filter{it.isFile}?.forEach{file->
            val date=name.matchEntire(file.name)?.groupValues?.get(1)?.let(LocalDate::parse)
            if(date!=null&&date<current.minusDays(7)&&date!=protected)file.delete()
        }
    }
    fun save(batch:PoolBatch) {
        val temp=File(directory,"batch.tmp");temp.writeText(PoolCodec.encode(batch))
        java.nio.file.Files.move(temp.toPath(),File(directory,"batch.json").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
