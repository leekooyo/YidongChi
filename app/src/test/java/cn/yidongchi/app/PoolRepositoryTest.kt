package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PoolRepositoryTest {
    private fun text(s:String,suffix:String)=javaClass.getResource("/comparison-20261109/$s$suffix")!!.readText()
    @Test fun pauseDuringBlockingFailureDoesNotStartAnAlternateRequest()=runBlocking {
        lateinit var loading:Job
        var calls=0
        val repo=PoolRepository(Files.createTempDirectory("yidong-cancel").toFile()){url,_->
            if(url.contains("qt.gtimg.cn"))text("sz002086","-quote.txt")else {
                calls++
                if(url.contains("fqkline")){Thread.sleep(150);loading.cancel();error("阻塞请求结束时已暂停")}
                org.json.JSONObject().put("data",org.json.JSONObject().put("klines",org.json.JSONArray(listOf("2026-11-06,1,1,1,1,1,1,1,1,1,1","2026-11-09,1,1,1,1,1,1,1,1,1,1")))).toString()
            }
        }
        val s=MarketParser.quote("sz002086",text("sz002086","-quote.txt"))
        val ix=MarketParser.daily("sz399107",text("sz399107","-daily.json"))
        val basis=PoolBasis(s.time,listOf("sh000002","sz399107","sh000688","sz399102").associateWith{ix})
        loading=launch {repo.load(s.stock,basis)}
        loading.join()
        assertEquals("暂停后不得继续切备用源发请求",1,calls)
    }
    @Test fun verifiedObsoleteConversionCodeIsExcludedInsteadOfRetried()=runBlocking {
        val repo=PoolRepository(Files.createTempDirectory("yidong-legacy").toFile()){_,_->error("不应为历史转换代码请求行情")}
        val basis=PoolBasis(java.time.LocalDateTime.parse("2026-11-09T15:00:00"),emptyMap())
        try{repo.load(Stock("sh600849","上药转换",Board.SH_MAIN),basis);fail("应明确排除历史转换代码")}
        catch(e:PoolExcluded){assertTrue(e.message!!.contains("601607"))}
    }
    @Test fun oldDailyCacheIsPrunedButResumableBatchDateIsPreserved() {
        val directory=Files.createTempDirectory("yidong-retention").toFile()
        val repo=PoolRepository(directory){_,_->error("无需联网")}
        java.io.File(directory,"2026-01-01-tencent-sz002086.json").writeText("old")
        java.io.File(directory,"2026-01-02-tencent-sz002086.json").writeText("resume")
        java.io.File(directory,"2026-11-09-tencent-sz002086.json").writeText("current")
        val batch=PoolBatch(PoolBasis(java.time.LocalDateTime.parse("2026-01-02T15:00:00"),emptyMap()),emptyList())
        repo.save(batch)
        repo.prune(java.time.LocalDate.parse("2026-11-09"))
        assertFalse(java.io.File(directory,"2026-01-01-tencent-sz002086.json").exists())
        assertTrue(java.io.File(directory,"2026-01-02-tencent-sz002086.json").isFile)
        assertTrue(java.io.File(directory,"2026-11-09-tencent-sz002086.json").isFile)
    }
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
