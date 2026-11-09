package cn.yidongchi.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.yidongchi.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 外部服务集成验证；与离线单元测试分开，断网时允许明确失败。 */
@RunWith(AndroidJUnit4::class)
class LiveMarketTest {
    @Test fun allFourBoardsAndBShareSourcesProduceAlignedSnapshots()=runBlocking {
        val repository=MarketRepository()
        for(symbol in listOf("sh600000","sz000001","sh688981","sz300750","sh900901","sz200012")) {
            val s=repository.load(symbol)
            assertEquals(symbol,s.quote.stock.symbol)
            assertEquals(s.quote.time.toLocalDate(),s.indexQuote.time.toLocalDate())
            assertTrue("$symbol has historical bars",s.bars.size>=30)
            val a=PriceEngine.analyze(s.quote.stock,s.bars,s.indexBars,s.quote.time.toLocalDate(),s.indexQuote.price)
            assertEquals(6,a.levels.size)
            println("LIVE $symbol ${s.quote.stock.name} ${s.quote.time} ${s.quote.price} index=${s.quote.stock.board.indexSymbol} levels=${a.levels.size}")
        }
    }
}
