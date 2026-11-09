package cn.yidongchi.core
import org.junit.Assert.*
import org.junit.Test
class WatchlistTest {
    @Test fun pastedNameAndCodeResolveCorrectExchange() {
        assertEquals("sz300750",Watchlist.symbolFrom("宁德时代（300750）"))
        assertEquals("sh600000",Watchlist.symbolFrom("600000.SH"))
        assertEquals(null,Watchlist.symbolFrom("hello"))
    }
    @Test fun backupMergePreservesExistingGroupingAndDeduplicatesSecurity() {
        val old=listOf(WatchItem("sh600000","浦发","银行",true))
        val merged=Watchlist.merge(old,listOf(WatchItem("sh600000","浦发银行"),WatchItem("sz300750","宁德")))
        assertEquals(2,merged.size);assertEquals("银行",merged.first().group)
    }
    @Test fun sameAlertKeyIsNotRepeatedOnRefresh() {
        val day=java.time.LocalDate.parse("2026-11-09")
        val a=AlertItem("key","sh600000","接近临界价",day)
        assertEquals(1,Watchlist.mergeAlerts(listOf(a.copy(read=true)),listOf(a)).size)
        assertTrue(Watchlist.mergeAlerts(listOf(a.copy(read=true)),listOf(a)).first().read)
    }
}
