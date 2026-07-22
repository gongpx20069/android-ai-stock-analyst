package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class EastmoneyChartClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val symbol = StockSymbol.of("AAPL")
    private val secId = EastmoneySecId(105, "AAPL")
    private val fetchedAt = Instant.parse("2026-07-22T13:51:00Z")

    @Test
    fun `fixture search resolves observed markets and conservative BRK alias`() = runBlocking {
        val requests = mutableListOf<String>()
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<ResponseBody> {
                requests += url
                val fixture = when {
                    "keyword=AAPL" in url -> "search_aapl.json"
                    "keyword=IBM" in url -> "search_ibm.json"
                    "keyword=SPY" in url -> "search_spy.json"
                    "keyword=BRK_B" in url -> "search_brk_b.json"
                    "keyword=BRK.B" in url -> "search_empty.json"
                    else -> error("Unexpected URL: $url")
                }
                return Response.success(fixture(fixture).toResponseBody())
            }
        }
        val resolver = EastmoneySymbolResolver(service, json)

        assertEquals(EastmoneySecId(105, "AAPL"), resolver.resolve(StockSymbol.of("AAPL")))
        assertEquals(EastmoneySecId(106, "IBM"), resolver.resolve(StockSymbol.of("IBM")))
        assertEquals(EastmoneySecId(107, "SPY"), resolver.resolve(StockSymbol.of("SPY")))
        assertEquals(EastmoneySecId(106, "BRK_B"), resolver.resolve(StockSymbol.of("BRK.B")))
        assertEquals(5, requests.size)
        assertTrue(requests[3].contains("keyword=BRK.B"))
        assertTrue(requests[4].contains("keyword=BRK_B"))
    }

    @Test
    fun `search parser rejects malformed ambiguous and non-US exact matches`() {
        val malformed = runCatching {
            EastmoneySearchParser.parseExact("""{"code":"1","result":[]}""", "AAPL", json)
        }
        val ambiguous = runCatching {
            EastmoneySearchParser.parseExact(
                """
                {"code":"0","result":[
                  {"code":"AAPL","market":105,"securityTypeName":"美股"},
                  {"code":"aapl","market":106,"securityTypeName":"美股"}
                ]}
                """.trimIndent(),
                "AAPL",
                json,
            )
        }
        val noMatch = EastmoneySearchParser.parseExact(
            """{"code":"0","result":[{"code":"AAPL","market":1,"securityTypeName":"指数"}]}""",
            "AAPL",
            json,
        )

        assertTrue(malformed.exceptionOrNull() is ProviderPayloadException)
        assertTrue(ambiguous.exceptionOrNull() is ProviderPayloadException)
        assertEquals(null, noMatch)
    }

    @Test
    fun `all native intervals parse provider field order and canonical boundaries`() {
        data class Case(
            val interval: BarInterval,
            val timestamp: String,
            val expectedStart: Instant,
            val expectedEnd: Instant,
        )
        val cases = listOf(
            Case(
                BarInterval.ONE_MINUTE,
                "2026-07-15 21:31",
                Instant.parse("2026-07-15T13:30:00Z"),
                Instant.parse("2026-07-15T13:31:00Z"),
            ),
            Case(
                BarInterval.FIVE_MINUTES,
                "2026-07-15 21:35",
                Instant.parse("2026-07-15T13:30:00Z"),
                Instant.parse("2026-07-15T13:35:00Z"),
            ),
            Case(
                BarInterval.FIFTEEN_MINUTES,
                "2026-07-15 21:45",
                Instant.parse("2026-07-15T13:30:00Z"),
                Instant.parse("2026-07-15T13:45:00Z"),
            ),
            Case(
                BarInterval.THIRTY_MINUTES,
                "2026-07-15 22:00",
                Instant.parse("2026-07-15T13:30:00Z"),
                Instant.parse("2026-07-15T14:00:00Z"),
            ),
            Case(
                BarInterval.ONE_HOUR,
                "2026-07-15 22:30",
                Instant.parse("2026-07-15T13:30:00Z"),
                Instant.parse("2026-07-15T14:30:00Z"),
            ),
            Case(
                BarInterval.ONE_DAY,
                "2026-07-15",
                Instant.parse("2026-07-15T04:00:00Z"),
                Instant.parse("2026-07-16T04:00:00Z"),
            ),
            Case(
                BarInterval.ONE_MONTH,
                "2026-06-30",
                Instant.parse("2026-06-01T04:00:00Z"),
                Instant.parse("2026-07-01T04:00:00Z"),
            ),
        )

        cases.forEach { case ->
            val bar = parse(case.interval, case.timestamp).single()
            assertEquals(case.interval, bar.interval)
            assertEquals(case.expectedStart, bar.start)
            assertEquals(case.expectedEnd, bar.endExclusive)
            assertEquals(100.0, bar.open, 0.0)
            assertEquals(101.0, bar.close, 0.0)
            assertEquals(102.0, bar.high, 0.0)
            assertEquals(99.0, bar.low, 0.0)
            assertEquals(1234L, bar.volume)
            assertEquals(DataSource.EASTMONEY_EXPERIMENTAL, bar.source)
        }
    }

    @Test
    fun `intraday end labels handle DST non-DST overnight dates and final short hour`() {
        val summer = parse(BarInterval.ONE_MINUTE, "2026-07-15 21:31").single()
        val winter = parse(BarInterval.ONE_MINUTE, "2026-01-15 22:31").single()
        val overnight = parse(BarInterval.ONE_HOUR, "2026-07-16 00:30").single()
        val finalHour = parse(BarInterval.ONE_HOUR, "2026-07-16 04:00").single()

        assertEquals(Instant.parse("2026-07-15T13:30:00Z"), summer.start)
        assertEquals(Instant.parse("2026-01-15T14:30:00Z"), winter.start)
        assertEquals(Instant.parse("2026-07-15T15:30:00Z"), overnight.start)
        assertEquals(Instant.parse("2026-07-15T19:30:00Z"), finalHour.start)
        assertEquals(Instant.parse("2026-07-15T20:00:00Z"), finalHour.endExclusive)
    }

    @Test
    fun `parser rejects invalid prices ordering volume duplicates and identity`() {
        val invalidRows = listOf(
            "2026-07-15 21:31,NaN,101,102,99,1",
            "2026-07-15 21:31,100,101,100,99,1",
            "2026-07-15 21:31,100,101,102,99,-1",
            "2026-07-15 21:31,100,101,102,99,1.5",
            "2026-07-15 21:31,100,101,102",
        )
        invalidRows.forEach { row ->
            val failure = runCatching {
                EastmoneyKlineParser.parse(
                    body = body(listOf(row)),
                    symbol = symbol,
                    exchange = Exchange.NASDAQ,
                    interval = BarInterval.ONE_MINUTE,
                    secId = secId,
                    fetchedAt = fetchedAt,
                    completedAt = fetchedAt,
                    json = json,
                )
            }
            assertTrue("Expected rejection for $row", failure.exceptionOrNull() != null)
        }

        val duplicate = runCatching {
            EastmoneyKlineParser.parse(
                body = body(
                    listOf(
                        row("2026-07-15 21:31"),
                        row("2026-07-15 21:31"),
                    ),
                ),
                symbol = symbol,
                exchange = Exchange.NASDAQ,
                interval = BarInterval.ONE_MINUTE,
                secId = secId,
                fetchedAt = fetchedAt,
                completedAt = fetchedAt,
                json = json,
            )
        }
        val identity = runCatching {
            EastmoneyKlineParser.parse(
                body = body(listOf(row("2026-07-15 21:31")), code = "MSFT"),
                symbol = symbol,
                exchange = Exchange.NASDAQ,
                interval = BarInterval.ONE_MINUTE,
                secId = secId,
                fetchedAt = fetchedAt,
                completedAt = fetchedAt,
                json = json,
            )
        }

        assertTrue(duplicate.exceptionOrNull() is ProviderPayloadException)
        assertTrue(identity.exceptionOrNull() is ProviderPayloadException)
    }

    @Test
    fun `current incomplete month is discarded`() {
        val bars = EastmoneyKlineParser.parse(
            body = body(
                listOf(
                    row("2026-06-30"),
                    row("2026-07-21"),
                ),
            ),
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MONTH,
            secId = secId,
            fetchedAt = fetchedAt,
            completedAt = fetchedAt,
            json = json,
        )

        assertEquals(1, bars.size)
        assertEquals(Instant.parse("2026-06-01T04:00:00Z"), bars.single().start)
    }

    @Test
    fun `four-hour aggregation anchors at 0930 and allows final short bucket`() {
        val hourly = EastmoneyKlineParser.parse(
            body = body(
                listOf(
                    row("2026-07-15 22:30", volume = 1),
                    row("2026-07-15 23:30", volume = 2),
                    row("2026-07-16 00:30", volume = 3),
                    row("2026-07-16 01:30", volume = 4),
                    row("2026-07-16 02:30", volume = 5),
                    row("2026-07-16 03:30", volume = 6),
                    row("2026-07-16 04:00", volume = 7),
                ),
            ),
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_HOUR,
            secId = secId,
            fetchedAt = fetchedAt,
            completedAt = fetchedAt,
            json = json,
        )

        val bars = EastmoneyFourHourAggregator.completedBars(hourly, fetchedAt)

        assertEquals(2, bars.size)
        assertEquals(Instant.parse("2026-07-15T13:30:00Z"), bars[0].start)
        assertEquals(Instant.parse("2026-07-15T17:30:00Z"), bars[0].endExclusive)
        assertEquals(10L, bars[0].volume)
        assertEquals(Instant.parse("2026-07-15T17:30:00Z"), bars[1].start)
        assertEquals(Instant.parse("2026-07-15T20:00:00Z"), bars[1].endExclusive)
        assertEquals(18L, bars[1].volume)
        assertEquals(DataSource.EASTMONEY_EXPERIMENTAL, bars[1].source)
    }

    @Test
    fun `four-hour aggregation discards buckets with missing hourly coverage`() {
        val hourly = EastmoneyKlineParser.parse(
            body = body(
                listOf(
                    row("2026-07-15 22:30", volume = 1),
                    row("2026-07-16 00:30", volume = 3),
                    row("2026-07-16 01:30", volume = 4),
                    row("2026-07-16 02:30", volume = 5),
                    row("2026-07-16 03:30", volume = 6),
                    row("2026-07-16 04:00", volume = 7),
                ),
            ),
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_HOUR,
            secId = secId,
            fetchedAt = fetchedAt,
            completedAt = fetchedAt,
            json = json,
        )

        val bars = EastmoneyFourHourAggregator.completedBars(hourly, fetchedAt)

        assertEquals(1, bars.size)
        assertEquals(Instant.parse("2026-07-15T17:30:00Z"), bars.single().start)
        assertEquals(18L, bars.single().volume)
    }

    @Test
    fun `client uses exact klt codes fqt zero and rejects materially old intraday ranges`() =
        runBlocking {
            val requests = mutableListOf<String>()
            val service = object : RawHttpService {
                override suspend fun get(
                    url: String,
                    headers: Map<String, String>,
                ): Response<ResponseBody> {
                    requests += url
                    return Response.success(body(emptyList()).toResponseBody())
                }
            }
            val client = EastmoneyChartClient(
                service = service,
                json = json,
                clock = Clock.fixed(fetchedAt, ZoneOffset.UTC),
                symbolResolver = object : EastmoneySecIdResolver {
                    override suspend fun resolve(symbol: StockSymbol): EastmoneySecId = secId
                },
            )
            val expectedCodes = mapOf(
                BarInterval.ONE_MINUTE to "1",
                BarInterval.FIVE_MINUTES to "5",
                BarInterval.FIFTEEN_MINUTES to "15",
                BarInterval.THIRTY_MINUTES to "30",
                BarInterval.ONE_HOUR to "60",
                BarInterval.FOUR_HOURS to "60",
                BarInterval.ONE_DAY to "101",
                BarInterval.ONE_MONTH to "103",
            )
            expectedCodes.forEach { (interval, code) ->
                client.fetchBars(
                    symbol = symbol,
                    exchange = Exchange.NASDAQ,
                    interval = interval,
                    start = fetchedAt.minus(EastmoneyChartCapabilities.preferredHistory(interval)),
                    endExclusive = fetchedAt,
                )
                assertTrue(requests.last().contains("klt=$code"))
                assertTrue(requests.last().contains("fqt=0"))
            }

            val failure = runCatching {
                client.fetchBars(
                    symbol = symbol,
                    exchange = Exchange.NASDAQ,
                    interval = BarInterval.ONE_HOUR,
                    start = fetchedAt.minusSeconds(40L * 86_400L),
                    endExclusive = fetchedAt,
                )
            }
            assertTrue(failure.exceptionOrNull() is EastmoneyChartCapabilityException)
        }

    @Test
    fun `client returns only completed bars contained in the requested range`() = runBlocking {
        val now = Instant.parse("2026-07-15T13:31:30Z")
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<ResponseBody> = Response.success(
                body(
                    listOf(
                        row("2026-07-15 21:31"),
                        row("2026-07-15 21:32"),
                    ),
                ).toResponseBody(),
            )
        }
        val client = EastmoneyChartClient(
            service = service,
            json = json,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            symbolResolver = object : EastmoneySecIdResolver {
                override suspend fun resolve(symbol: StockSymbol): EastmoneySecId = secId
            },
        )

        val bars = client.fetchBars(
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            start = Instant.parse("2026-07-15T13:30:00Z"),
            endExclusive = Instant.parse("2026-07-15T13:33:00Z"),
        )

        assertEquals(1, bars.size)
        assertEquals(Instant.parse("2026-07-15T13:30:00Z"), bars.single().start)
    }

    private fun parse(
        interval: BarInterval,
        timestamp: String,
    ): List<PriceBar> = EastmoneyKlineParser.parse(
        body = body(listOf(row(timestamp))),
        symbol = symbol,
        exchange = Exchange.NASDAQ,
        interval = interval,
        secId = secId,
        fetchedAt = fetchedAt,
        completedAt = fetchedAt,
        json = json,
    )

    private fun body(
        rows: List<String>,
        code: String = "AAPL",
        market: Int = 105,
    ): String = """
        {
          "rc": 0,
          "data": {
            "code": "$code",
            "market": $market,
            "klines": [${rows.joinToString(",") { "\"$it\"" }}]
          }
        }
    """.trimIndent()

    private fun row(
        timestamp: String,
        volume: Long = 1234,
    ): String = "$timestamp,100,101,102,99,$volume,999,1,2,3,4"

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("eastmoney/$name")).readText()
}
