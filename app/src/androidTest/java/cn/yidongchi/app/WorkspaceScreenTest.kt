package cn.yidongchi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.yidongchi.core.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceScreenTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun ready():AppState {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        val state=ViewModelProvider(ui.activity)[AppState::class.java]
        ui.waitUntil(40_000){!state.busy}
        ui.runOnIdle{state.active=false;state.setWarningScope(false)}
        return state
    }
    private fun snapshot(symbol:String,name:String):MarketSnapshot {
        val stock=Stock(symbol,name,Watchlist.boardFor(symbol))
        val dates=TradingCalendar.nextDays(LocalDate.parse("2026-09-21"),31)
        val bars=dates.map{Bar(it,"10".bd(),"10".bd())}
        val ix=dates.map{Bar(it,"100".bd(),"100".bd())}
        val q=Quote(stock,"10".bd(),"10".bd(),LocalDateTime.of(dates.last(),LocalTime.of(15,0)),"10".bd(),"10".bd(),"11".bd(),"9".bd())
        return MarketSnapshot(q,q.copy(price="100".bd(),previousClose="100".bd()),bars,ix)
    }
    private fun choose(feature:String,code:String) {
        ui.onNodeWithTag("stock-search-$feature").performScrollTo().performTextReplacement(code)
        val suggestion=hasText(code) and hasClickAction() and !hasSetTextAction()
        ui.waitUntil(8000){ui.onAllNodes(suggestion).fetchSemanticsNodes().size==1}
        ui.onNode(suggestion).performClick()
    }
    @Test fun forecastAndWarningsExposeIndependentStockSelectors() {
        ready()
        ui.onNodeWithText("推演").performClick()
        ui.onNodeWithTag("stock-search-forecast").assertExists()
        ui.onNodeWithText("预警").performClick()
        ui.onNodeWithTag("stock-search-warnings").assertExists()
        ui.onNodeWithText("全部自选").assertExists()
    }
    @Test fun cancelledEventLoadRestoresItsOwnStatus() {
        val state=ready()
        val cardScope=CoroutineScope(Job()+Dispatchers.Main.immediate)
        var pending:Job?=null
        try {
            ui.runOnIdle {
                state.snapshots["sz002086"]=snapshot("sz002086","事件取消样本")
                state.eventStatuses["sz002086"]="已有记录"
                state.eventStatuses["sz002242"]="另一股票状态"
                pending=cardScope.launch(start=CoroutineStart.UNDISPATCHED){state.loadEvents("sz002086")}
                cardScope.cancel()
            }
            ui.waitUntil(40_000){pending!!.isCompleted}
            ui.runOnIdle {
                assertEquals("已有记录",state.eventStatuses["sz002086"])
                assertEquals("另一股票状态",state.eventStatuses["sz002242"])
            }
        }finally{cardScope.cancel()}
    }
    @Test fun threePagesKeepDifferentStocksAndForecastInputsWhilePoolOnlyChangesCalculation() {
        val state=ready()
        val original=state.workspaces.all.map{it.symbol}
        try {
            ui.runOnIdle {
                state.workspaces.calculation.select("sz002086")
                state.workspaces.forecast.select("sz002242")
                state.workspaces.warnings.select("sz300949")
                state.snapshots["sz002242"]=snapshot("sz002242","推演独立样本")
                state.workspaces.forecast.text("mode","固定涨跌").value="连续涨停"
                state.workspaces.forecast.text("index","0").value="0"
                state.workspaces.forecast.flag("reset").value=false
            }
            ui.onNodeWithText("计算").performClick()
            choose("calculation","300750")
            ui.runOnIdle {
                assertEquals("sz300750",state.selected)
                assertEquals("sz002242",state.workspaces.forecast.symbol)
                assertEquals("sz300949",state.workspaces.warnings.symbol)
            }
            ui.onNodeWithText("推演").performClick()
            ui.onNodeWithText("连续涨停").performScrollTo().assertIsSelected()
            ui.onNodeWithText("情景收盘 11.00").assertExists()
            choose("forecast","300949")
            ui.onNodeWithText("预警").performClick()
            choose("warnings","301697")
            ui.runOnIdle {
                assertEquals("sz300750",state.selected)
                assertEquals("sz300949",state.workspaces.forecast.symbol)
                assertEquals("sz301697",state.workspaces.warnings.symbol)
                state.workspaces.forecast.text("mode","固定涨跌").value="连续跌停"
                state.openPoolStock("sz002242")
                assertEquals("sz300949",state.workspaces.forecast.symbol)
                assertEquals("sz301697",state.workspaces.warnings.symbol)
                assertEquals("连续跌停",state.workspaces.forecast.text("mode").value)
            }
            ui.onNodeWithText("计算").performClick()
            ui.onNodeWithText("次日",substring=true).performScrollTo().assertIsSelected()
            ui.onNodeWithText("指数不变").assertIsSelected()
        }finally{ui.runOnIdle{state.workspaces.all.zip(original).forEach{(workspace,symbol)->workspace.select(symbol)}}}
    }
    @Test fun warningsCanShowAllWatchlistAndRecordTheWarningsStock() {
        val state=ready()
        val original=state.workspaces.all.map{it.symbol}
        val originalEvents=state.events
        val originalWatches=state.watches.map{it.symbol}.toSet()
        val added=listOf(Stock("sz002086","自选样本A",Board.SZ_MAIN),Stock("sz002242","自选样本B",Board.SZ_MAIN),Stock("sz003999","缺失行情样本",Board.SZ_MAIN))
        try {
            ui.runOnIdle {
                state.workspaces.calculation.select("sz002086")
                state.workspaces.warnings.select("sz300949")
                state.snapshots["sz002086"]=snapshot("sz002086","自选样本A")
                state.snapshots["sz002242"]=snapshot("sz002242","自选样本B")
                state.snapshots["sz300949"]=snapshot("sz300949","预警独立样本")
                state.errors["sz003999"]="测试数据缺失"
                added.forEach{state.add(it)}
            }
            ui.onNodeWithText("预警").performClick()
            ui.onNodeWithTag("warning-list").performScrollToNode(hasTestTag("warning-stock-sz300949"))
            val record=hasText("按原公告补充确认记录") and hasAnyAncestor(hasTestTag("warning-stock-sz300949"))
            ui.onNode(record).performScrollTo().assertIsDisplayed().performClick()
            ui.onNode(hasSetTextAction() and hasText("原公告 HTTPS 链接")).performScrollTo().performTextReplacement("https://www.szse.cn/")
            ui.onNodeWithText("保存记录").performClick()
            ui.runOnIdle {
                assertTrue(state.events.any{it.symbol=="sz300949"&&it.confirmed&&it.sourceUrl=="https://www.szse.cn/"})
                assertEquals("sz002086",state.selected)
            }
            ui.onNodeWithTag("warning-list").performScrollToNode(hasText("全部自选"))
            ui.onNodeWithText("全部自选").performClick()
            for(symbol in listOf("sz002086","sz002242","sz003999")) {
                ui.onNodeWithTag("warning-list").performScrollToNode(hasTestTag("warning-stock-$symbol"))
                ui.onNodeWithTag("warning-stock-$symbol").assertExists()
            }
            ui.onNodeWithText("缺失行情样本",substring=true).assertExists()
            ui.runOnIdle{assertEquals("sz300949",state.workspaces.warnings.symbol)}
        }finally{ui.runOnIdle{
            added.filter{it.symbol !in originalWatches}.forEach{state.remove(it.symbol)}
            state.store.saveEvents(originalEvents)
            state.setWarningScope(false)
            state.workspaces.all.zip(original).forEach{(workspace,symbol)->workspace.select(symbol)}
        }}
    }
}
