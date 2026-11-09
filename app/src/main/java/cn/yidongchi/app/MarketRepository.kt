package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.LocalDate

class MarketRepository {
    private val historyCache=java.util.concurrent.ConcurrentHashMap<String,Pair<Long,List<Bar>>>()
    private fun get(url:String,gbk:Boolean=false):String {
        val conn=URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout=12_000;conn.readTimeout=15_000
        conn.setRequestProperty("User-Agent","Mozilla/5.0")
        conn.setRequestProperty("Referer","https://gu.qq.com/")
        return try {conn.inputStream.use {String(it.readBytes(),if(gbk)Charset.forName("GBK") else Charsets.UTF_8)}} finally {conn.disconnect()}
    }
    suspend fun load(symbol:String):MarketSnapshot=withContext(Dispatchers.IO) {
        val index=Watchlist.boardFor(symbol).indexSymbol
        val quotes=get("https://qt.gtimg.cn/q=$symbol,$index",true).split(';').filter {it.contains("~")}
        val q=MarketParser.quote(symbol,quotes.first {it.contains("v_$symbol=")})
        val iq=MarketParser.quote(index,quotes.first {it.contains("v_$index=")})
        require(q.time.toLocalDate()==iq.time.toLocalDate()){ "个股与基准指数行情日期不一致，请稍后刷新" }
        fun history(s:String):List<Bar> {
            historyCache[s]?.let {if(System.currentTimeMillis()-it.first<300_000)return it.second}
            val bars=MarketParser.daily(s,get("https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$s,day,,,120,qfq"))
            historyCache[s]=System.currentTimeMillis() to bars
            return bars
        }
        var provider="腾讯公开行情"
        val pair=try {coroutineScope {val a=async {history(symbol)};val b=async {history(index)};a.await() to b.await()}}
        catch(e:Exception) {
            if(e is CancellationException)throw e
            provider="腾讯即时报价 / 东方财富前复权日线"
            coroutineScope {
                fun daily(s:String)=async {val secid=(if(s.startsWith("sh"))"1." else "0.")+s.drop(2)
                    MarketParser.eastDaily(s,get("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=$secid&klt=101&fqt=1&beg=20260101&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"))}
                val a=daily(symbol);val b=daily(index);a.await() to b.await()
            }
        }
        fun integrate(bars:List<Bar>,quote:Quote):List<Bar> {
            val date=quote.time.toLocalDate()
            val old=bars.filter {it.date<date}
            val current=Bar(date,quote.price,quote.previousClose,quote.high,quote.low,eligible=quote.upper!=null||quote.stock.symbol==index)
            return old+current
        }
        val bars=integrate(pair.first,q);val ix=integrate(pair.second,iq)
        require(bars.size>=2&&ix.size>=2){"历史日线不足，暂时不能推算"}
        // 不补造停牌日；有历史停牌或参考价不全时维持条件估算标记。
        MarketSnapshot(q,iq,bars,ix,provider)
    }
    suspend fun search(text:String):List<Stock> = withContext(Dispatchers.IO) {
        Watchlist.symbolFrom(text)?.let {return@withContext listOf(Stock(it,it.drop(2),Watchlist.boardFor(it)))}
        val raw=get("https://smartbox.gtimg.cn/s3/?q=${URLEncoder.encode(text,"UTF-8")}&t=all",true).substringAfter("=\"").substringBefore('"')
        val body=Regex("\\\\u([0-9a-fA-F]{4})").replace(raw){it.groupValues[1].toInt(16).toChar().toString()}
        body.split('^').mapNotNull {row->val f=row.split('~');if(f.size>=3&&(f[0]=="sh"||f[0]=="sz")) {
            val symbol=Watchlist.symbolFrom(f[0]+f[1]);symbol?.let {Stock(it,f[2],Watchlist.boardFor(it))}
        }else null}.take(8)
    }
    suspend fun eventCandidates(stock:Stock):List<OfficialEvent> = withContext(Dispatchers.IO) {
        val code=stock.code
        val filter=URLEncoder.encode("(SECURITY_CODE=\"$code\")","UTF-8")
        val data=JSONObject(get("https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_DAILYBILLBOARD_DETAILS&columns=ALL&filter=$filter&pageSize=100&sortColumns=TRADE_DATE&sortTypes=-1")).optJSONObject("result")?.optJSONArray("data")?:return@withContext emptyList()
        (0 until data.length()).mapNotNull {
            val r=data.getJSONObject(it);val reason=r.optString("EXPLANATION")
            val kind=MarketParser.eventKind(reason)?:return@mapNotNull null
            val date=LocalDate.parse(r.getString("TRADE_DATE").take(10))
            OfficialEvent("$code-$date-$kind",stock.symbol,date,kind,null,"https://data.eastmoney.com/stock/lhb,$date,$code.html",confirmed=false)
        }.distinctBy {it.id}
    }
}
