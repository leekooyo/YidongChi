package cn.yidongchi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotDisplayTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun eastOceanShowsTodayAndNextDayForTheSameSevereRule() {
        ui.waitForIdle()
        if(ui.onAllNodesWithText("开始使用").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("开始使用").performClick()
        val state=ViewModelProvider(ui.activity)[AppState::class.java]
        ui.waitUntil(35_000){!state.busy}
        ui.runOnIdle {
            state.active=false
            state.store.put("index-sz002086","0")
            state.store.put("normal-sz002086","")
            state.store.put("severe-sz002086","")
            state.select("sz002086")
        }
        ui.waitUntil(35_000){state.snapshots["sz002086"]?.quote?.stock?.name=="东方海洋"}
        ui.onNodeWithText("计算").performClick()
        ui.onNodeWithText("4.55").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("次日",substring=true).performScrollTo().performClick()
        ui.onNodeWithText("4.64").performScrollTo().assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"east-ocean-next.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
