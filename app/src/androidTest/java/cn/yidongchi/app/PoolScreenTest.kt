package cn.yidongchi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        ui.onNode(hasText("开始扫描") or hasText("继续扫描") or hasText("重新扫描")).assertExists()
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
    @Test fun realScanCanPauseAndContinueWithoutResettingProgress() {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        val state=ViewModelProvider(ui.activity)[AppState::class.java]
        ui.waitUntil(35_000){!state.busy&&!state.poolLoading}
        ui.onNodeWithText("明日池").performClick()
        try {
            val previous=state.poolBatch
            ui.runOnIdle{state.active=false;state.startPool(newBatch=true)}
            ui.waitUntil(150_000){state.poolBatch!==previous&&(state.poolBatch?.processed?:0)>=6||state.poolError.isNotBlank()}
            assertEquals("",state.poolError)
            ui.onNodeWithTag("pool-list").performScrollToNode(hasText("暂停扫描"))
            ui.onNodeWithText("暂停扫描").performScrollTo().assertIsDisplayed().performClick()
            ui.waitUntil(35_000){!state.poolRunning}
            val processed=state.poolBatch!!.processed
            assertTrue(processed>=6)
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals(processed,PoolCodec.decode(File(context.filesDir,"pool/batch.json").readText()).processed)
            ui.onNodeWithTag("pool-list").performScrollToNode(hasText("继续扫描"))
            ui.onNodeWithText("继续扫描").performScrollTo().assertIsDisplayed().performClick()
            ui.waitUntil(60_000){(state.poolBatch?.processed?:0)>processed||state.poolError.isNotBlank()}
            assertEquals("",state.poolError)
            assertTrue(state.poolBatch!!.processed>processed)
        }finally{
            ui.runOnIdle{state.pausePool()}
            ui.waitUntil(35_000){!state.poolRunning}
        }
    }
}
