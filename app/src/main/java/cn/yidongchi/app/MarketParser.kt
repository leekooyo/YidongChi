package cn.yidongchi.app

import cn.yidongchi.core.*
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Quote(val stock:Stock,val price:BigDecimal,val previousClose:BigDecimal,val time:LocalDateTime,val high:BigDecimal,val low:BigDecimal,val upper:BigDecimal?,val lower:BigDecimal?,val volume:BigDecimal=BigDecimal.ZERO)
data class MarketSnapshot(val quote:Quote,val indexQuote:Quote,val bars:List<Bar>,val indexBars:List<Bar>,val provider:String="腾讯公开行情",val notes:String="历史按行情源前复权收益估算；公告重置、历史状态与除权参考未全面核实。",val recordedResets:ResetDates?=null,val ruleVersion:String="2026-07-06-v1") {
    fun recent(events:List<OfficialEvent>)=Projection.recentEvents(events.filter {it.symbol==quote.stock.symbol},indexBars.map {it.date},quote.time.toLocalDate())
}

object MarketParser {
    fun withQuote(bars:List<Bar>,quote:Quote,index:Boolean=false):List<Bar> {
        val date=quote.time.toLocalDate()
        return bars.filter {it.date<date}+Bar(date,quote.price,quote.previousClose,quote.high,quote.low,eligible=index||quote.upper!=null)
    }
    fun stockMetadata(stock:Stock,text:String):Stock {
        val raw=JSONObject(text).getJSONObject("data").getInt("f189").toString()
        val date=LocalDate.parse(raw,DateTimeFormatter.BASIC_ISO_DATE)
        return stock.copy(listedOn=date)
    }
    fun eventKind(reason:String):String?=when {
        !reason.contains("偏离")&&!reason.contains("异常")->null
        reason.contains("三十个交易日")||reason.contains("30个交易日")->"severe30"
        reason.contains("十个交易日")||reason.contains("10个交易日")->"severe10"
        reason.contains("三个交易日")||reason.contains("3个交易日")->"normal"
        else->null
    }
    fun quote(symbol:String,text:String):Quote {
        val body=text.substringAfter("=\"").substringBefore('"')
        require(body.contains('~')) {"未找到该股票行情"}
        val f=body.split('~')
        val price=f[3].bd();require(price.signum()>0){"该股票暂无有效成交价"}
        val stock=Stock(symbol,f[1],Watchlist.boardFor(symbol),excludedFromDeviation=symbol.startsWith("sz")&&f[1].contains("退"))
        fun optional(i:Int)=f.getOrNull(i)?.toBigDecimalOrNull()?.takeIf {it.signum()>0}
        return Quote(stock,price,f[4].bd(),LocalDateTime.parse(f[30],DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),f[33].bd(),f[34].bd(),optional(47),optional(48),optional(6)?:BigDecimal.ZERO)
    }
    fun daily(symbol:String,text:String):List<Bar> {
        val data=JSONObject(text).getJSONObject("data").getJSONObject(symbol)
        val rows=data.optJSONArray("qfqday")?:data.getJSONArray("day")
        val result=mutableListOf<Bar>()
        for(i in 1 until rows.length()) {
            val r=rows.getJSONArray(i);val prev=rows.getJSONArray(i-1)
            result+=Bar(LocalDate.parse(r.getString(0)),r.getString(2).bd(),prev.getString(2).bd(),r.getString(3).bd(),r.getString(4).bd())
        }
        return result.sortedBy {it.date}
    }
    fun eastDaily(symbol:String,text:String):List<Bar> {
        val rows=JSONObject(text).getJSONObject("data").getJSONArray("klines")
        val data=(0 until rows.length()).map {rows.getString(it).split(',')}
        return data.drop(1).mapIndexed {i,r->Bar(LocalDate.parse(r[0]),r[2].bd(),data[i][2].bd(),r[3].bd(),r[4].bd(),r.getOrNull(10)?.toBigDecimalOrNull())}
    }
}

data class BackupData(val items:List<WatchItem>,val theme:String="system",val settings:JSONObject=JSONObject())
object BackupCodec {
    fun encode(data:BackupData):String=JSONObject().put("schemaVersion",1).put("theme",data.theme).put("settings",data.settings).put("watchlist",JSONArray(data.items.map {
        JSONObject().put("symbol",it.symbol).put("name",it.name).put("group",it.group).put("pinned",it.pinned).put("muted",it.muted)
    })).toString(2)
    fun decode(text:String):BackupData {
        val json=JSONObject(text);require(json.getInt("schemaVersion")==1){"不支持这个备份版本"}
        val rows=json.getJSONArray("watchlist")
        val items=(0 until rows.length()).map {val r=rows.getJSONObject(it);val symbol=r.getString("symbol");require(Watchlist.symbolFrom(symbol)==symbol){"备份股票代码无效"};WatchItem(symbol,r.getString("name"),r.optString("group","默认"),r.optBoolean("pinned"),r.optBoolean("muted"))}
        return BackupData(items.distinctBy {it.symbol},json.optString("theme","system"),json.optJSONObject("settings")?:JSONObject())
    }
}
