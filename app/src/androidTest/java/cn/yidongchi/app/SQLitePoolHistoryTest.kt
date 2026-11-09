package cn.yidongchi.app

import androidx.test.platform.app.InstrumentationRegistry
import cn.yidongchi.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SQLitePoolHistoryTest {
    @Test fun upsertsSurviveReopenAndIncompleteWorkCannotReplaceCompleteResult() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="history-test.db";context.deleteDatabase(name)
        val day=LocalDate.parse("2026-11-06")
        val s=Stock("sz002086","东方海洋",Board.SZ_MAIN,LocalDate.parse("2007-02-05"))
        val batch=PoolBatch(PoolBasis(day.atTime(15,1),emptyMap()),listOf(s),mapOf(s.symbol to PoolOutcome(excluded=true)))
        SQLitePoolHistory(context,name).use {db->
            db.write(listOf(HistoryWrite(HistorySeries(s,"tencent",listOf(Bar(day,"4".bd(),"3.9".bd()))),true)))
            db.begin(batch);db.finish(batch)
            val next=batch.copy(basis=batch.basis.copy(time=day.plusDays(3).atTime(15,1)),outcomes=emptyMap())
            db.begin(next)
            db.saveQuotes(day.plusDays(3),mapOf(s.symbol to "quote"))
            db.checkpoint(next,listOf(HistoryWrite(HistorySeries(s,"tencent",listOf(Bar(day,"4.1".bd(),"3.9".bd()))),false)))
        }
        SQLitePoolHistory(context,name).use {db->
            assertEquals(batch,db.complete());assertFalse(db.pending()!!.complete)
            assertEquals("quote",db.quotes(day.plusDays(3))[s.symbol])
            val series=db.history(s.symbol)!!
            assertEquals(s,series.stock);assertEquals(1,series.bars.size);assertEquals("4.1".bd(),series.bars.single().close)
            assertNull(db.history(s.symbol,"eastmoney"))
        }
        context.deleteDatabase(name)
    }
}
