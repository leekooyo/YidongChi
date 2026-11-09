package cn.yidongchi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.yidongchi.core.*
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForecastModeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    @Test fun clearingFixedInputDoesNotDisableLimitUpOrLimitDown() {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        val state=ViewModelProvider(ui.activity)[AppState::class.java]
        ui.waitUntil(35_000){!state.busy}
        ui.runOnIdle {
            state.active=false
            val stock=Stock(state.selected,"测试股票",Watchlist.boardFor(state.selected))
            val dates=TradingCalendar.nextDays(java.time.LocalDate.parse("2026-09-21"),31)
            val bars=dates.map {Bar(it,"10".bd(),"10".bd())}
            val index=dates.map {Bar(it,"100".bd(),"100".bd())}
            val q=Quote(stock,"10".bd(),"10".bd(),LocalDateTime.of(dates.last(),java.time.LocalTime.of(15,0)),"10".bd(),"10".bd(),"11".bd(),"9".bd())
            state.snapshots[state.selected]=MarketSnapshot(q,q.copy(price="100".bd(),previousClose="100".bd()),bars,index)
            state.store.put("scenario-stock-${state.selected}","0")
            state.store.put("scenario-index-${state.selected}","0")
        }
        ui.onNodeWithText("推演").performClick()
        ui.onNode(hasSetTextAction() and hasText("个股每日涨跌幅（%）")).performScrollTo().performTextClearance()
        ui.onNodeWithText("连续涨停").performScrollTo().performClick()
        ui.onNodeWithText("情景收盘 11.00").assertExists()
        ui.onNodeWithText("连续跌停").performScrollTo().performClick()
        ui.onNodeWithText("情景收盘 9.00").assertExists()
    }
}
