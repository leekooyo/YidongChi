package cn.yidongchi.app
import cn.yidongchi.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MarketParserTest {
    @Test fun quoteUsesExchangeTimestampAndCorrectPriceFields() {
        val fields=MutableList(50){"0"};fields[1]="浦发银行";fields[2]="600000";fields[3]="10.03";fields[4]="10.00";fields[30]="20261109105926";fields[33]="10.05";fields[34]="9.94";fields[47]="11";fields[48]="9"
        val q=MarketParser.quote("sh600000", "v_sh600000=\"${fields.joinToString("~")}\";")
        assertEquals("10.03",q.price.toPlainString());assertEquals("2026-11-09T10:59:26",q.time.toString())
        assertEquals("10.00",q.previousClose.toPlainString())
    }
    @Test fun dailyArrayUsesCloseColumnNotOpenColumn() {
        val json="""{"data":{"sh600000":{"qfqday":[["2026-11-05","9","10","11","8","100"],["2026-11-06","11","12","13","10","200"]]}}}"""
        val bars=MarketParser.daily("sh600000",json)
        assertEquals("12",bars.last().close.toPlainString());assertEquals("10",bars.last().previousClose.toPlainString())
        assertEquals(LocalDate.parse("2026-11-06"),bars.last().date)
    }
    @Test fun backupMergesBySymbolAndRetainsPinnedGroup() {
        val data=BackupCodec.decode("""{"schemaVersion":1,"watchlist":[{"symbol":"sh600000","name":"浦发","group":"银行","pinned":true,"muted":false}],"theme":"dark"}""")
        assertTrue(data.items.first().pinned);assertEquals("dark",data.theme)
        assertEquals(data.items,BackupCodec.decode(BackupCodec.encode(data)).items)
    }
    @Test fun singleDayDisclosureIsNotAbnormalWaveEvent() {
        assertNull(MarketParser.eventKind("日收盘价格涨幅偏离值达到7%的证券"))
        assertEquals("normal",MarketParser.eventKind("连续三个交易日内收盘价格涨幅偏离值累计达到20%的证券"))
        assertEquals("severe30",MarketParser.eventKind("连续三十个交易日内收盘价格涨幅偏离值累计达到200%的证券"))
    }
    @Test fun listingMetadataMakesInitialExemptDaysExplicit() {
        val stock=MarketParser.stockMetadata(Stock("sz001234","新股",Board.SZ_MAIN),"""{"data":{"f189":20261102}}""")
        assertEquals(LocalDate.parse("2026-11-02"),stock.listedOn)
        assertFalse(SecurityEligibility.eligible(stock,LocalDate.parse("2026-11-06")))
    }
    @Test fun recentSnapshotEventsUseMarketCalendarThroughStockSuspension() {
        val fields=MutableList(50){"0"};fields[1]="浦发";fields[3]="10";fields[4]="10";fields[30]="20261109100000";fields[33]="10";fields[34]="10"
        val q=MarketParser.quote("sh600000","x=\"${fields.joinToString("~")}\"")
        val dates=TradingCalendar.nextDays(LocalDate.parse("2026-10-15"),16)
        val ix=dates.map {Bar(it,"100".bd(),"100".bd())}
        val s=MarketSnapshot(q,q,listOf(Bar(dates.first(),"10".bd(),"10".bd()),Bar(dates.last(),"10".bd(),"10".bd())),ix)
        val old=OfficialEvent("old",q.stock.symbol,dates.first(),"normal",Direction.UP,"https://www.sse.com.cn/")
        val suspended=old.copy(id="suspended",date=dates[10])
        assertEquals(listOf(suspended),s.recent(listOf(old,suspended)))
    }
}
