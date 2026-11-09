package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AfterCloseRepositoryTest {
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
