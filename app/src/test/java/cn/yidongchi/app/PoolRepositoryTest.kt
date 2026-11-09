package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PoolRepositoryTest {
    private fun text(s:String,suffix:String)=javaClass.getResource("/comparison-20261109/$s$suffix")!!.readText()
    @Test fun indexHistoryIsSharedAndStockHistorySurvivesRepositoryRecreation()=runBlocking {
        val directory=Files.createTempDirectory("yidong-pool-test").toFile()
        val calls=mutableListOf<String>()
        val indexSymbols=listOf("sh000002","sz399107","sh000688","sz399102")
        val fetch:(String,Boolean)->String={url,_->
            calls+=url
            when {
                url.contains("qt.gtimg.cn")-> {
                    val symbols=url.substringAfter("q=").split(',')
                    symbols.joinToString(";"){s->if(s in indexSymbols)text("sz399107","-quote.txt").replace("v_sz399107=","v_$s=")else text(s,"-quote.txt")}
                }
                url.contains("fqkline")-> {
                    val s=url.substringAfter("param=").substringBefore(',')
                    if(s in indexSymbols)text("sz399107","-daily.json").replace("sz399107",s)else text(s,"-daily.json")
                }
                else->error("意外请求 $url")
            }
        }
        val repo=PoolRepository(directory,fetch)
        val basis=repo.basis()
        val stock=Stock("sz002086","东方海洋",Board.SZ_MAIN)
        val first=repo.load(stock,basis)
        val again=PoolRepository(directory,fetch)
        val secondBasis=again.basis()
        val second=again.load(stock,secondBasis)
        assertEquals(5,calls.count{it.contains("fqkline")})
        assertEquals(first.bars,second.bars)
        assertEquals(basis,secondBasis)
        assertEquals("4.64",TomorrowPool.calculate(second.quote.stock,second.bars,second.indexBars).hits.first{it.level.ruleId=="severe10"&&it.level.direction==Direction.UP}.level.price.toPlainString())
        val batch=PoolBatch(basis,listOf(stock))
        repo.save(batch);assertEquals(batch,again.cached())
    }
    @Test fun blockedPrimaryUsesPairedAlternateHistoryAndKeepsSource()=runBlocking {
        val directory=Files.createTempDirectory("yidong-pool-fallback").toFile()
        var alternateCalls=0
        val primary=PoolRepository(directory){url,_->
            if(url.contains("qt.gtimg.cn"))text("sz002086","-quote.txt")
            else if(url.contains("fqkline"))error("HTTP 501 WAF")
            else {
                alternateCalls++
                val symbol=url.substringAfter("secid=").substringBefore('&').substringAfter('.')
                val source=if(symbol=="002086")"sz002086"else "sz399107"
                val data=org.json.JSONObject(text(source,"-daily.json")).getJSONObject("data").getJSONObject(source)
                val raw=data.optJSONArray("qfqday")?:data.getJSONArray("day")
                val lines=(0 until raw.length()).map{i->val a=raw.getJSONArray(i);listOf(a.getString(0),a.getString(1),a.getString(2),a.getString(3),a.getString(4),"1","1","1","1","1","1").joinToString(",")}
                org.json.JSONObject().put("data",org.json.JSONObject().put("klines",org.json.JSONArray(lines))).toString()
            }
        }
        val symbol="sz002086";val q=MarketParser.quote(symbol,text(symbol,"-quote.txt"))
        val ix=MarketParser.withQuote(MarketParser.daily("sz399107",text("sz399107","-daily.json")),MarketParser.quote("sz399107",text("sz399107","-quote.txt")),true)
        val basis=PoolBasis(q.time,listOf("sh000002","sz399107","sh000688","sz399102").associateWith{ix})
        val result=primary.load(q.stock,basis)
        assertTrue(result.provider.contains("东方财富"))
        assertEquals(5,alternateCalls)
        assertEquals("4.64",TomorrowPool.calculate(result.quote.stock,result.bars,result.indexBars).hits.first{it.level.ruleId=="severe10"&&it.level.direction==Direction.UP}.level.price.toPlainString())
    }
}
