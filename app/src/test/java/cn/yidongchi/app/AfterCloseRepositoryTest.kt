package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

class AfterCloseRepositoryTest {
    private val stock=Stock("sz002086","东方海洋",Board.SZ_MAIN)
    private val date=LocalDate.parse("2026-11-09")
    private val quote=Quote(stock,"4.2".bd(),"4.1".bd(),date.atTime(15,1),"4.2".bd(),"4.1".bd(),"4.51".bd(),"3.69".bd(),"100".bd())
    @Test fun staleHistoryCannotBePatchedIntoSuccessfulRepair()=runBlocking {
        val source=AfterCloseRepository(Files.createTempDirectory("lagged-history").toFile()){url,_->when {
            url.contains("fqkline")->"""{"data":{"sz002086":{"qfqday":[["2026-11-04","4","4","4","4"],["2026-11-05","4","4.1","4.1","4"]]}}}"""
            url.contains("kline/get")->"""{"data":{"klines":["2026-11-04,4,4,4,4","2026-11-05,4,4.1,4.1,4"]}}"""
            else->"""{"data":{"f189":20070205}}"""
        }}
        try{source.repair(stock,quote,date);fail("missing last traded session")}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("日线尚未"))}
    }
    @Test fun firstDayIpoIsExcludedOnlyAfterVerifiedListingDate()=runBlocking {
        suspend fun attempt(listed:Int):HistorySeries {
            val source=AfterCloseRepository(Files.createTempDirectory("ipo-history").toFile()){url,_->when {
                url.contains("fqkline")->"""{"data":{"sz002086":{"qfqday":[["2026-11-09","4","4.2","4.2","4"]]}}}"""
                url.contains("kline/get")->"""{"data":{"klines":["2026-11-09,4,4.2,4.2,4"]}}"""
                else->"""{"data":{"f189":$listed}}"""
            }}
            return source.repair(stock,quote,date)
        }
        try{attempt(20261109);fail("first-day IPO")}
        catch(e:PoolExcluded){assertTrue(e.message!!.contains("上市"))}
        try{attempt(20070205);fail("ordinary empty history")}
        catch(e:PoolExcluded){throw AssertionError("arbitrary empty histories cannot be excluded",e)}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("为空"))}
    }
    @Test fun oneHundredQuotesUseOneRequestAndPreserveEverySymbol()=runBlocking {
        val stocks=(1..100).map{Stock("sh60"+it.toString().padStart(4,'0'),"样本",Board.SH_MAIN)}
        val urls=mutableListOf<String>()
        val source=AfterCloseRepository(Files.createTempDirectory("quote-batch").toFile()){url,gbk->assertTrue(gbk);urls+=url;stocks.joinToString(""){"v_${it.symbol}=\"\";"}}
        val result=source.quotes(stocks)
        assertEquals(stocks.map{it.symbol}.toSet(),result.keys)
        assertEquals(1,source.requestCount);assertEquals(1,urls.size)
        assertEquals(100,urls.single().substringAfter("q=").split(',').size)
    }
}
