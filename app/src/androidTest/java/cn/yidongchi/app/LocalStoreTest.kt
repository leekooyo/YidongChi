package cn.yidongchi.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.yidongchi.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class LocalStoreTest {
    private fun store(name:String):LocalStore {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val isolated=object:ContextWrapper(base){
            override fun getSharedPreferences(n:String,mode:Int)=base.getSharedPreferences("test-$name-$n",mode)
            override fun getFilesDir()=File(base.cacheDir,"test-$name").apply{mkdirs()}
        }
        return LocalStore(isolated)
    }
    @Test fun undoBackupImportRestoresReminderAndLargeTextSettings() {
        val store=store("backup")
        store.put("threshold","1");store.put("large","false");store.put("theme","light")
        store.saveWatchlist(listOf(WatchItem("sh600000","浦发")))
        val incoming=BackupCodec.encode(BackupData(listOf(WatchItem("sz300750","宁德")),"dark",JSONObject().put("threshold","3").put("large","true")))
        store.import(incoming)
        assertEquals("3",store.value("threshold"));assertTrue(store.undoImport())
        assertEquals("1",store.value("threshold"));assertEquals("false",store.value("large"));assertEquals("light",store.value("theme"))
        assertEquals(1,store.watchlist().size)
    }
    @Test fun laterResetCannotRewriteCapturedSnapshotContext() {
        val store=store("history");val symbol="sh600000"
        store.put("normal-$symbol","2026-11-02");store.put("severe-$symbol","2026-11-02")
        val date=LocalDate.parse("2026-11-06")
        val quote=Quote(Stock(symbol,"浦发",Board.SH_MAIN),"10".bd(),"10".bd(),LocalDateTime.parse("2026-11-06T15:00:00"),"10".bd(),"10".bd(),"11".bd(),"9".bd())
        store.snapshot(MarketSnapshot(quote,quote,listOf(Bar(date,"10".bd(),"10".bd())),listOf(Bar(date,"100".bd(),"100".bd()))))
        store.put("normal-$symbol","2026-11-09");store.put("severe-$symbol","2026-11-09")
        val saved=store.cached(symbol,"2026-11-06")!!
        assertEquals(LocalDate.parse("2026-11-02"),saved.recordedResets?.normal)
        assertEquals("2026-07-06-v1",saved.ruleVersion)
    }
}
