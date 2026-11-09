package cn.yidongchi.app

import cn.yidongchi.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

object PoolCodec {
    fun universePage(text:String):UniversePage {
        val data=JSONObject(text).getJSONObject("data")
        val rows=data.getJSONArray("diff")
        val stocks=(0 until rows.length()).mapNotNull {i->
            val row=rows.getJSONObject(i);val code=row.getString("f12");val market=row.getInt("f13")
            val symbol=when {market==1&&(code.startsWith("60")||code.startsWith("68"))->"sh$code";market==0&&(code.startsWith("00")||code.startsWith("30"))->"sz$code";else->null}
            symbol?.let{Stock(it,row.getString("f14"),Watchlist.boardFor(it))}
        }
        return UniversePage(data.getInt("total"),stocks)
    }
    private fun stock(s:Stock)=JSONObject().put("symbol",s.symbol).put("name",s.name).put("listedOn",s.listedOn?.toString()).put("excluded",s.excludedFromDeviation)
    private fun stock(j:JSONObject):Stock {
        val symbol=j.getString("symbol")
        return Stock(symbol,j.getString("name"),Watchlist.boardFor(symbol),j.optString("listedOn").takeIf{it.isNotEmpty()}?.let(LocalDate::parse),j.optBoolean("excluded"))
    }
    private fun bar(b:Bar)=JSONArray(listOf(b.date.toString(),b.close.toPlainString(),b.previousClose.toPlainString(),b.high.toPlainString(),b.low.toPlainString(),b.eligible))
    private fun bar(j:JSONArray)=Bar(LocalDate.parse(j.getString(0)),j.getString(1).bd(),j.getString(2).bd(),j.getString(3).bd(),j.getString(4).bd(),eligible=j.getBoolean(5))
    private fun hit(h:PoolHit):JSONObject {
        val l=h.level
        return JSONObject().put("rule",l.ruleId).put("label",l.label).put("direction",l.direction.name).put("start",l.start).put("end",l.end).put("sessions",l.sessions)
            .put("price",l.price.toPlainString()).put("theory",l.theoretical.toPlainString()).put("deviation",l.deviation.toPlainString()).put("threshold",l.threshold.toPlainString())
            .put("reachable",l.reachable).put("satisfied",l.satisfied).put("base",l.basePrice.toPlainString()).put("indexStart",l.indexStart.toPlainString()).put("indexEnd",l.indexEnd.toPlainString()).put("required",h.requiredChange.toPlainString())
    }
    private fun hit(j:JSONObject)=PoolHit(PriceLevel(j.getString("rule"),j.getString("label"),Direction.valueOf(j.getString("direction")),LocalDate.parse(j.getString("start")),LocalDate.parse(j.getString("end")),j.getInt("sessions"),j.getString("price").bd(),j.getString("theory").bd(),j.getString("deviation").bd(),j.getString("threshold").bd(),j.getBoolean("reachable"),j.getBoolean("satisfied"),j.getString("base").bd(),j.getString("indexStart").bd(),j.getString("indexEnd").bd()),j.getString("required").bd())
    fun encode(batch:PoolBatch):String {
        val indices=JSONObject();batch.basis.indices.forEach{(s,b)->indices.put(s,JSONArray(b.map{bar(it)}))}
        val results=JSONObject()
        batch.outcomes.forEach{(s,o)->
            val j=JSONObject().put("issue",o.issue).put("excluded",o.excluded).put("provider",o.provider)
            o.result?.let{r->j.put("result",JSONObject().put("stock",stock(r.stock)).put("target",r.targetDate).put("price",r.price.toPlainString()).put("hits",JSONArray(r.hits.map{hit(it)})))}
            results.put(s,j)
        }
        return JSONObject().put("schema",1).put("time",batch.basis.time).put("provider",batch.basis.provider).put("indices",indices).put("universe",JSONArray(batch.universe.map{stock(it)})).put("outcomes",results).toString()
    }
    fun decode(text:String):PoolBatch {
        val j=JSONObject(text);require(j.getInt("schema")==1){"股票池缓存版本不兼容"}
        val ix=j.getJSONObject("indices");val indices=ix.keys().asSequence().associateWith{s->val a=ix.getJSONArray(s);(0 until a.length()).map{bar(a.getJSONArray(it))}}
        val universe=j.getJSONArray("universe").let{a->(0 until a.length()).map{stock(a.getJSONObject(it))}}
        val outcomes=j.getJSONObject("outcomes").let{all->all.keys().asSequence().associateWith{s->
            val o=all.getJSONObject(s);val r=o.optJSONObject("result")
            PoolOutcome(r?.let{val a=it.getJSONArray("hits");PoolStock(stock(it.getJSONObject("stock")),LocalDate.parse(it.getString("target")),it.getString("price").bd(),(0 until a.length()).map{n->hit(a.getJSONObject(n))})},o.optString("issue").takeIf{it.isNotBlank()},o.optBoolean("excluded"),o.optString("provider").takeIf{it.isNotBlank()})
        }}
        return PoolBatch(PoolBasis(LocalDateTime.parse(j.getString("time")),indices,j.getString("provider")),universe,outcomes)
    }
}
