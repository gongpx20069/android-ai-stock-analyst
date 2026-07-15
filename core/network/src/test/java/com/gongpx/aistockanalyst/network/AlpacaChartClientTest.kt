package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AlpacaChartClientTest {
    private val symbol = StockSymbol.of("AAPL")
    private val clock = Clock.fixed(
        Instant.parse("2026-07-15T15:45:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun `client authenticates with headers and follows opaque pagination`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> {
                requests += url to headers
                val body = if (url.contains("page_token=next-token")) {
                    responseBody(
                        timestamp = "2026-07-15T13:31:00Z",
                        nextPageToken = null,
                    )
                } else {
                    responseBody(
                        timestamp = "2026-07-15T13:30:00Z",
                        nextPageToken = "next-token",
                    )
                }
                return Response.success(body.toResponseBody())
            }
        }
        val client = client(service)

        val bars = client.fetchBars(
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            start = Instant.parse("2026-07-15T13:30:00Z"),
            endExclusive = Instant.parse("2026-07-15T13:32:00Z"),
        )

        assertEquals(2, requests.size)
        assertEquals("key-id", requests.first().second["APCA-API-KEY-ID"])
        assertEquals("secret", requests.first().second["APCA-API-SECRET-KEY"])
        assertFalse(requests.first().first.contains("secret"))
        assertTrue(requests.first().first.contains("feed=iex"))
        assertTrue(requests.first().first.contains("adjustment=split"))
        assertEquals(2, bars.size)
        assertEquals(DataSource.ALPACA_IEX, bars.first().source)
    }

    @Test
    fun `client excludes current incomplete bar`() = runBlocking {
        val service = fixedService(
            """
            {
              "bars": {
                "AAPL": [
                  {"t":"2026-07-15T15:44:00Z","o":210.0,"h":211.0,"l":209.0,"c":210.5,"v":1000},
                  {"t":"2026-07-15T15:45:00Z","o":210.5,"h":212.0,"l":210.0,"c":211.0,"v":900}
                ]
              },
              "next_page_token": null
            }
            """.trimIndent(),
        )

        val bars = client(service).fetchBars(
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            start = Instant.parse("2026-07-15T15:44:00Z"),
            endExclusive = Instant.parse("2026-07-15T15:47:00Z"),
        )

        assertEquals(1, bars.size)
        assertEquals(Instant.parse("2026-07-15T15:44:00Z"), bars.single().start)
    }

    @Test
    fun `monthly parser calculates exchange local end boundary`() {
        val page = AlpacaBarsParser.parse(
            body = responseBody(
                timestamp = "2026-03-01T05:00:00Z",
                nextPageToken = null,
            ),
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MONTH,
            fetchedAt = clock.instant(),
        )

        assertEquals(
            Instant.parse("2026-04-01T04:00:00Z"),
            page.bars.single().endExclusive,
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `parser rejects malformed symbol bar collection`() {
        AlpacaBarsParser.parse(
            body = """{"bars":{"AAPL":{}},"next_page_token":null}""",
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            fetchedAt = clock.instant(),
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `parser rejects explicit null symbol bars`() {
        AlpacaBarsParser.parse(
            body = """{"bars":{"AAPL":null},"next_page_token":null}""",
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            fetchedAt = clock.instant(),
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `parser rejects string encoded numeric fields`() {
        AlpacaBarsParser.parse(
            body = """
                {
                  "bars": {
                    "AAPL": [
                      {"t":"2026-07-15T13:30:00Z","o":"210.0","h":211.0,"l":209.0,"c":210.5,"v":1000}
                    ]
                  },
                  "next_page_token": null
                }
            """.trimIndent(),
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            fetchedAt = clock.instant(),
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `parser rejects malformed pagination token`() {
        AlpacaBarsParser.parse(
            body = """{"bars":{"AAPL":[]},"next_page_token":{}}""",
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            fetchedAt = clock.instant(),
        )
    }

    @Test(expected = AlpacaCredentialsMissingException::class)
    fun `client requires user credentials`() {
        runBlocking {
            AlpacaChartClient(
                service = fixedService("""{"bars":{},"next_page_token":null}"""),
                json = Json { ignoreUnknownKeys = true },
                clock = clock,
                credentialsProvider = { null },
            ).fetchBars(
                symbol = symbol,
                exchange = Exchange.NASDAQ,
                interval = BarInterval.ONE_DAY,
                start = Instant.parse("2026-07-01T04:00:00Z"),
                endExclusive = Instant.parse("2026-07-02T04:00:00Z"),
            )
        }
    }

    private fun client(service: RawHttpService): AlpacaChartClient = AlpacaChartClient(
        service = service,
        json = Json { ignoreUnknownKeys = true },
        clock = clock,
        credentialsProvider = {
            AlpacaApiCredentials(
                keyId = "key-id",
                secretKey = "secret",
            )
        },
    )

    private fun fixedService(body: String): RawHttpService = object : RawHttpService {
        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): Response<okhttp3.ResponseBody> = Response.success(body.toResponseBody())
    }

    private fun responseBody(
        timestamp: String,
        nextPageToken: String?,
    ): String = """
        {
          "bars": {
            "AAPL": [
              {
                "t": "$timestamp",
                "o": 210.0,
                "h": 211.0,
                "l": 209.0,
                "c": 210.5,
                "v": 1000,
                "n": 10,
                "vw": 210.3
              }
            ]
          },
          "next_page_token": ${nextPageToken?.let { "\"$it\"" } ?: "null"},
          "currency": "USD"
        }
    """.trimIndent()
}
