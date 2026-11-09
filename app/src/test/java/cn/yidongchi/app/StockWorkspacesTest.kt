package cn.yidongchi.app

import cn.yidongchi.core.*
import org.junit.Assert.*
import org.junit.Test

class StockWorkspacesTest {
    private val saved=mutableMapOf("selected" to "sz002086","recent" to "sz002086|sz002242")
    private fun workspaces()=StockWorkspaces({key,default->saved[key]?:default},{key,value->saved[key]=value})
    @Test fun migratedSelectionBecomesIndependentAndRestores() {
        val w=workspaces()
        assertEquals(listOf("sz002086","sz002086","sz002086"),w.all.map{it.symbol})
        w.calculation.select("sz002242")
        assertEquals("sz002086",workspaces().forecast.symbol)
        w.calculation.select("sz002242");w.forecast.select("sz300949");w.warnings.select("sz301697")
        val restored=workspaces()
        assertEquals(listOf("sz002242","sz300949","sz301697"),restored.all.map{it.symbol})
        assertEquals("sz002242",restored.calculation.history.first())
        assertEquals("sz300949",restored.forecast.history.first())
    }
    @Test fun searchesAndLateResultsAreIsolated() {
        val w=workspaces();val stock=Stock("sz002242","九阳股份",Board.SZ_MAIN)
        w.calculation.query("九阳");w.forecast.query("奥雅")
        w.calculation.acceptResults("九阳",listOf(stock))
        assertEquals("奥雅",w.forecast.searchText);assertTrue(w.forecast.suggestions.isEmpty())
        w.calculation.query("东方");w.calculation.acceptResults("九阳",listOf(stock))
        assertTrue(w.calculation.suggestions.isEmpty())
        assertEquals("",w.warnings.searchText)
    }
    @Test fun inputsStayWithTheirFeatureAndStockAcrossNavigationAndRestore() {
        val w=workspaces()
        w.calculation.text("index","0").value="1"
        w.forecast.text("index","0").value="-1"
        w.forecast.text("mode","固定涨跌").value="连续涨停"
        w.forecast.text("path").value="2,3,-1"
        w.forecast.flag("reset").value=true
        val previous=w.forecast.text("path")
        w.forecast.select("sz002242")
        assertEquals("",w.forecast.text("path").value)
        previous.value="4,5"
        w.forecast.select("sz002086")
        val r=workspaces()
        assertEquals("1",r.calculation.text("index").value)
        assertEquals("-1",r.forecast.text("index").value)
        assertEquals("连续涨停",r.forecast.text("mode").value)
        assertEquals("4,5",r.forecast.text("path").value)
        assertTrue(r.forecast.flag("reset").value)
    }
    @Test fun warningScopeAndSearchHistoryClearUseTheRightWorkspace() {
        val w=workspaces();w.warnings.select("sz301697")
        val watches=listOf(WatchItem("sz002086","东方海洋"),WatchItem("sz002242","九阳"))
        assertEquals(listOf("sz301697"),w.warningSymbols(watches,false))
        assertEquals(listOf("sz002086","sz002242"),w.warningSymbols(watches,true))
        assertTrue(w.warningSymbols(emptyList(),true).isEmpty())
        w.clearHistory();assertTrue(workspaces().all.all{it.history.isEmpty()})
    }
}
