package cn.yidongchi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import java.io.File
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"$name.png")
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun liveCalculationWatchlistForecastAndRuleNavigation() {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        ui.runOnIdle {val state=ViewModelProvider(ui.activity)[AppState::class.java];state.remove("sh600000");state.select("sh600000")}
        ui.onNodeWithText("计算").performClick()
        ui.waitUntil(35_000) {ui.onAllNodesWithText("浦发银行").fetchSemanticsNodes().isNotEmpty()}
        capture("calculation")
        ui.onNodeWithContentDescription("切换自选").performClick()
        ui.onNodeWithText("自选").performClick()
        ui.onNodeWithTag("watchlist").performScrollToNode(hasText("浦发银行"))
        ui.onNodeWithText("浦发银行").assertIsDisplayed()
        capture("watchlist")
        ui.onNodeWithText("推演").performClick()
        ui.onNodeWithText("未来 10 个交易日").assertIsDisplayed()
        capture("forecast")
        ui.onNodeWithText("规则").performClick()
        ui.onNodeWithText("规则有出处").assertIsDisplayed()
        capture("rules")
        ui.onNodeWithText("预警").performClick()
        ui.onNodeWithText("应用内提醒").assertIsDisplayed()
    }
}
