package cn.yidongchi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import cn.yidongchi.app.ui.PoolScreen
import cn.yidongchi.app.ui.WatchHome
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import cn.yidongchi.core.*
import java.time.LocalDate
import java.time.LocalDateTime
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class PoolScreenTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun tomorrowPoolEntryAndScanActionAreVisible() {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        val state=ViewModelProvider(ui.activity)[AppState::class.java]
        ui.waitUntil(35_000){!state.busy}
        ui.runOnIdle{state.active=false}
        ui.onNodeWithText("明日池").performClick()
        ui.onNodeWithText("更新收盘数据").assertExists()
        ui.onNodeWithText("全部沪深 A 股").assertExists()
    }
    @Test fun filterAddWatchAndOpenNextDayWithFlatIndex() {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        val state=ViewModelProvider(ui.activity)[AppState::class.java]
        ui.waitUntil(35_000){!state.busy&&!state.poolLoading}
        val stock=Stock("sz002086","候选东方海洋",Board.SZ_MAIN)
        val down=Stock("sh600000","候选下行股",Board.SH_MAIN)
        val day=LocalDate.parse("2026-11-06")
        val index=listOf(Bar(day,"100".bd(),"100".bd()))
        val upResult=TomorrowPool.calculate(stock,listOf(Bar(day,"19".bd(),"10".bd())),index)
        val downResult=TomorrowPool.calculate(down,listOf(Bar(day,"5.3".bd(),"10".bd())),index)
        var wasWatched=false
        ui.runOnIdle {
            state.active=false
            wasWatched=state.watches.any{it.symbol==stock.symbol}
            state.poolBatch=PoolBatch(PoolBasis(LocalDateTime.parse("2026-11-06T15:00:00"),mapOf("sz399107" to index,"sh000002" to index)),listOf(stock,down),mapOf(stock.symbol to PoolOutcome(upResult),down.symbol to PoolOutcome(downResult)))
        }
        ui.onNodeWithText("明日池").performClick()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasTestTag("pool-${stock.symbol}"))
        ui.onNodeWithText("候选东方海洋").assertExists()
        ui.onNodeWithText("候选下行股").assertDoesNotExist()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasText("下行"))
        ui.onNodeWithText("下行").performClick()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasTestTag("pool-${down.symbol}"))
        ui.onNodeWithText("候选下行股").assertExists()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasText("上行"))
        ui.onNodeWithText("上行").performClick()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasTestTag("pool-${stock.symbol}"))
        if(!wasWatched)ui.onNode(hasContentDescription("加入自选") and hasAnyAncestor(hasTestTag("pool-${stock.symbol}"))).performScrollTo().performClick()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasText("只看自选"))
        ui.onNodeWithText("只看自选").performClick()
        ui.onNodeWithTag("pool-list").performScrollToNode(hasTestTag("pool-${stock.symbol}"))
        ui.onNodeWithText("候选东方海洋").assertExists()
        ui.onNodeWithTag("pool-${stock.symbol}").performClick()
        ui.onNodeWithText("次日",substring=true).performScrollTo().assertIsSelected()
        ui.onNodeWithText("指数不变").assertIsSelected()
        ui.runOnIdle{if(!wasWatched)state.remove(stock.symbol)}
    }
    @Test fun sqliteUpdateCanPauseAndContinueWithoutResettingProgress()=checkPoolUpdate(today=false)
    @Test fun todayPoolRefreshesBeforeClose()=checkPoolUpdate(today=true)

    private fun checkPoolUpdate(today:Boolean) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="pool-resume-${System.nanoTime()}.db"
        val db=SQLitePoolHistory(context,name)
        val day=LocalDate.parse("2026-11-09")
        val stocks=(1..300).map{Stock("sh60"+it.toString().padStart(4,'0'),"测试股$it",Board.SH_MAIN,LocalDate.parse("2000-01-01"))}
        val indices=AfterCloseIndices.symbols.map{Stock(it,"指数",Watchlist.boardFor(it))}
        db.metadata("legacy-import","done")
        db.write((stocks+indices).map{s->HistoryWrite(HistorySeries(s,"tencent",listOf(Bar(day.minusDays(3),"4".bd(),"4".bd()))),true)})
        val close=if(today) {
            val previous=day.minusDays(3)
            val candidate=TomorrowPool.calculate(stocks.first(),listOf(Bar(previous,"8.1".bd(),"4".bd())),
                listOf(Bar(previous,"4".bd(),"4".bd())))
            PoolBatch(PoolBasis(previous.atTime(15,1),emptyMap()),stocks,stocks.associate {
                it.symbol to PoolOutcome(candidate.takeIf { row->row.stock.symbol==it.symbol })
            }).also{db.begin(it);db.finish(it)}
        } else null
        val continueQuotes=CompletableDeferred<Unit>()
        val source=object:AfterCloseSource {
            override var requestCount=0
            var batches=0
            override suspend fun indexQuotes():Map<String,Quote>{requestCount++;return indices.associate{s->s.symbol to Quote(s,"4".bd(),"4".bd(),day.atTime(if(today)14 else 15,1),"4".bd(),"4".bd(),null,null)}}
            override suspend fun universe():List<Stock>{
                check(!today){"intraday update must not load the market universe"}
                requestCount++
                return stocks
            }
            override suspend fun quotes(stocks:List<Stock>):Map<String,String> {
                if(today)check(stocks.map{it.symbol}==listOf("sh600001"))
                requestCount++;batches++
                if(batches>1)continueQuotes.await()
                return stocks.associate{s->
                    val f=MutableList(60){"0"};f[1]=s.name;f[3]="4.1";f[4]="4";f[6]="100";f[30]=if(today)"20261109140100" else "20261109150100";f[33]="4.1";f[34]="4";f[47]="4.4";f[48]="3.6"
                    s.symbol to "v_${s.symbol}=\"${f.joinToString("~")}\";"
                }
            }
            override suspend fun repair(stock:Stock,quote:Quote,date:LocalDate,today:Boolean):HistorySeries=error("normal stocks must not repair")
            override suspend fun indexHistory(symbol:String,provider:String,date:LocalDate):HistorySeries=error("normal indices must not repair")
        }
        lateinit var state:AppState
        ui.runOnIdle {
            state=AppState(ui.activity.application,db,source);state.active=false
            ui.activity.setContent {
                if(today)WatchHome(state,{}, {_,_->})
                else PoolScreen(state){}
            }
        }
        ui.waitUntil(35_000){!state.poolLoading}
        try {
            if(today) {
                continueQuotes.complete(Unit)
                ui.onNodeWithText("今日池").performClick()
                ui.onNodeWithTag("pool-list").performScrollToNode(hasText("更新盘中数据"))
                ui.onNodeWithText("更新盘中数据").performScrollTo().performClick()
                ui.waitUntil(35_000){state.todayPoolBatch?.complete==true||state.todayPoolError.isNotBlank()}
                assertEquals("",state.todayPoolError)
                assertEquals(1,state.todayPoolBatch!!.processed)
                assertEquals(close,db.complete())
                assertNull(db.pending())
                return
            }
            ui.runOnIdle{state.startPool()}
            ui.waitUntil(35_000){(state.poolProgress.batch?.processed?:0)>=100||state.poolError.isNotBlank()}
            assertEquals("",state.poolError)
            ui.onNodeWithTag("pool-list").performScrollToNode(hasText("暂停更新"))
            ui.onNodeWithText("暂停更新").performScrollTo().performClick()
            ui.waitUntil(35_000){!state.poolRunning}
            assertEquals(100,state.poolProgress.batch!!.processed)
            assertEquals(100,db.pending()!!.processed)
            assertEquals(100,db.quotes(day).size)
            ui.onNodeWithText("更新收盘数据").performScrollTo().performClick()
            continueQuotes.complete(Unit)
            ui.waitUntil(35_000){!state.poolRunning}
            assertEquals("",state.poolError);assertTrue(state.poolBatch!!.complete)
            assertEquals(300,state.poolBatch!!.processed);assertNull(db.pending())
        }finally {
            ui.runOnIdle{state.pausePool()}
            ui.waitUntil(35_000){!state.poolRunning}
            db.close();context.deleteDatabase(name)
        }
    }
}
