package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteParsersTest {
    private val symbol = StockSymbol.of("NVDA")
    private val fetchedAt = Instant.parse("2026-07-14T11:00:00Z")

    @Test
    fun `Tencent parser maps documented positional fields`() {
        val fields = MutableList(71) { "" }
        fields[0] = "200"
        fields[3] = "203.53"
        fields[4] = "210.96"
        fields[5] = "208.54"
        fields[6] = "121411018"
        fields[30] = "2026-07-13 16:00:01"
        fields[31] = "-7.43"
        fields[32] = "-3.52"
        fields[33] = "210.57"
        fields[34] = "203.00"
        fields[39] = "31.17"
        fields[45] = "49297.00130"
        fields[48] = "236.26"
        fields[49] = "161.78"

        val quote = TencentQuoteParser.parse(
            body = "v_usNVDA=\"${fields.joinToString("~")}\";",
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            fetchedAt = fetchedAt,
        )

        assertEquals(203.53, quote.currentPrice, 0.0)
        assertEquals(-0.0352, quote.changePercent!!, 0.000_001)
        assertEquals(4_929_700_130_000.0, quote.marketCap!!, 0.1)
        assertEquals(Instant.parse("2026-07-13T20:00:01Z"), quote.asOf)
        assertEquals(ParseStatus.VALID, quote.parseStatus)
        assertEquals(DataSource.TENCENT, quote.source)
    }

    @Test
    fun `Sina parser maps fallback fields and exchange timestamp`() {
        val fields = MutableList(36) { "0" }
        fields[0] = "Apple"
        fields[1] = "317.3100"
        fields[2] = "0.63"
        fields[4] = "1.9900"
        fields[5] = "317.0150"
        fields[6] = "323.4500"
        fields[7] = "315.7800"
        fields[8] = "317.4000"
        fields[9] = "200.4500"
        fields[10] = "43257790"
        fields[12] = "4659575748240"
        fields[13] = "8.30"
        fields[14] = "38.23"
        fields[24] = "Jul 14 07:07AM EDT"
        fields[26] = "315.3200"
        fields[29] = "2026"

        val quote = SinaQuoteParser.parse(
            body = "var hq_str_gb_aapl=\"${fields.joinToString(",")}\";",
            symbol = StockSymbol.of("AAPL"),
            exchange = Exchange.NASDAQ,
            fetchedAt = fetchedAt,
        )

        assertEquals(317.31, quote.currentPrice, 0.0)
        assertEquals(38.23, quote.ttmPe!!, 0.0)
        assertEquals(Instant.parse("2026-07-14T11:07:00Z"), quote.asOf)
        assertEquals(DataSource.SINA, quote.source)
    }

    @Test
    fun `provider symbol encoders preserve class shares`() {
        val classShare = StockSymbol.of("BRK.B")

        assertEquals("BRK.B", classShare.toTencentSymbol())
        assertEquals("brk${'$'}b", classShare.toSinaSymbol())
        assertEquals("BRK-B", classShare.toYahooSymbol())
    }
}
