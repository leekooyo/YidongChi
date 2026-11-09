package cn.yidongchi.app

import android.content.Context
import cn.yidongchi.core.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

class LocalStore(private val context:Context) {
    private val prefs=context.getSharedPreferences("yidongchi",Context.MODE_PRIVATE)
    fun value(key:String,default:String="")=prefs.getString(key,default)?:default
    fun put(key:String,value:String){prefs.edit().putString(key,value).apply()}
    fun watchlist():List<WatchItem> = value("watchlist").takeIf {it.isNotBlank()}?.let {BackupCodec.decode(it).items}?:emptyList()
    fun saveWatchlist(items:List<WatchItem>)=put("watchlist",BackupCodec.encode(BackupData(items)))
    fun backup():String=BackupCodec.encode(BackupData(watchlist(),value("theme","system"),JSONObject().put("threshold",value("threshold","1")).put("large",value("large","false")).put("alert-price",value("alert-price","true")).put("alert-event",value("alert-event","true")).put("alert-data",value("alert-data","true"))))
    fun import(text:String):Int {
        val data=BackupCodec.decode(text)
        File(context.filesDir,"before-import.json").writeText(backup())
        val merged=Watchlist.merge(watchlist(),data.items);saveWatchlist(merged);put("theme",data.theme)
        for(key in listOf("threshold","large","alert-price","alert-event","alert-data")) if(data.settings.has(key))put(key,data.settings.getString(key))
        return merged.size
    }
    fun undoImport():Boolean {val f=File(context.filesDir,"before-import.json");if(!f.exists())return false;val d=BackupCodec.decode(f.readText());saveWatchlist(d.items);put("theme",d.theme);for(key in listOf("threshold","large","alert-price","alert-event","alert-data"))if(d.settings.has(key))put(key,d.settings.getString(key));return true}
    fun snapshot(s:MarketSnapshot) {
        val symbol=s.quote.stock.symbol
        val obj=JSONObject().put("symbol",symbol).put("quote",quoteJson(s.quote)).put("index",quoteJson(s.indexQuote)).put("bars",barsJson(s.bars)).put("indexBars",barsJson(s.indexBars)).put("provider",s.provider)
            .put("normalReset",value("normal-$symbol")).put("severeReset",value("severe-$symbol")).put("ruleVersion",s.ruleVersion)
            .put("knownEventIds",JSONArray(events().filter {it.symbol==symbol}.map {it.id}))
        File(context.filesDir,"${s.quote.stock.symbol}-latest.json").writeText(obj.toString())
        File(context.filesDir,"${s.quote.stock.symbol}-${s.quote.time.toLocalDate()}.json").writeText(obj.toString())
    }
    fun cached(symbol:String,date:String="latest"):MarketSnapshot? {
        val file=File(context.filesDir,"$symbol-$date.json");if(!file.exists())return null
        val o=JSONObject(file.readText())
        val resets=if(o.has("normalReset"))ResetDates(o.optString("normalReset").takeIf {it.isNotBlank()}?.let(LocalDate::parse),o.optString("severeReset").takeIf {it.isNotBlank()}?.let(LocalDate::parse))else null
        return MarketSnapshot(parseQuote(o.getJSONObject("quote")),parseQuote(o.getJSONObject("index")),parseBars(o.getJSONArray("bars")),parseBars(o.getJSONArray("indexBars")),o.getString("provider"),"本地缓存；历史收益与公告重置需核对。",resets,o.optString("ruleVersion","unknown"))
    }
    fun savedDates(symbol:String)=context.filesDir.listFiles().orEmpty().map {it.name.removePrefix("$symbol-").removeSuffix(".json")}.filter {Regex("\\d{4}-\\d{2}-\\d{2}").matches(it)}.distinct().sortedDescending()
    fun events():List<OfficialEvent> {
        val rows=JSONArray(value("events","[]"))
        return (0 until rows.length()).map {val o=rows.getJSONObject(it);OfficialEvent(o.getString("id"),o.getString("symbol"),LocalDate.parse(o.getString("date")),o.getString("kind"),o.optString("direction").takeIf {v->v.isNotBlank()}?.let(Direction::valueOf),o.getString("source"),o.optBoolean("confirmed"))}
    }
    fun saveEvents(events:List<OfficialEvent>)=put("events",JSONArray(events.distinctBy {it.id}.map {JSONObject().put("id",it.id).put("symbol",it.symbol).put("date",it.date).put("kind",it.kind).put("direction",it.direction?.name?:"").put("source",it.sourceUrl).put("confirmed",it.confirmed)}).toString())
    private fun quoteJson(q:Quote)=JSONObject().put("symbol",q.stock.symbol).put("name",q.stock.name).put("listedOn",q.stock.listedOn?.toString()?:"").put("excluded",q.stock.excludedFromDeviation).put("price",q.price).put("previous",q.previousClose).put("time",q.time).put("high",q.high).put("low",q.low).put("upper",q.upper).put("lower",q.lower).put("volume",q.volume)
    private fun parseQuote(o:JSONObject):Quote {val s=o.getString("symbol");return Quote(Stock(s,o.getString("name"),Watchlist.boardFor(s),o.optString("listedOn").takeIf {it.isNotBlank()}?.let(LocalDate::parse),o.optBoolean("excluded")),o.getString("price").bd(),o.getString("previous").bd(),java.time.LocalDateTime.parse(o.getString("time")),o.getString("high").bd(),o.getString("low").bd(),o.optString("upper").toBigDecimalOrNull(),o.optString("lower").toBigDecimalOrNull(),o.optString("volume","0").bd())}
    private fun barsJson(b:List<Bar>)=JSONArray(b.map {JSONObject().put("date",it.date).put("close",it.close).put("previous",it.previousClose).put("eligible",it.eligible)})
    private fun parseBars(a:JSONArray)=(0 until a.length()).map {val o=a.getJSONObject(it);Bar(LocalDate.parse(o.getString("date")),o.getString("close").bd(),o.getString("previous").bd(),eligible=o.optBoolean("eligible",true))}
}
