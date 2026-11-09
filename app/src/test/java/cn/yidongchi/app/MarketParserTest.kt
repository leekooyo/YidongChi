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
}
