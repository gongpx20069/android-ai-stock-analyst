package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class YahooFinanceParserTest {
    private val completeResponse = """
        {
          "quoteSummary": {
            "result": [{
              "financialData": {
                "targetLowPrice": {"raw": 180.0},
                "targetMedianPrice": {"raw": 240.0},
                "targetHighPrice": {"raw": 300.0},
                "forwardPE": {"raw": 28.5},
                "numberOfAnalystOpinions": {"raw": 42},
                "recommendationKey": "buy"
              },
              "summaryDetail": {
                "fiftyDayAverage": {"raw": 210.0},
                "twoHundredDayAverage": {"raw": 190.0},
                "fiftyTwoWeekLow": {"raw": 150.0},
                "fiftyTwoWeekHigh": {"raw": 250.0},
                "averageVolume": {"raw": 50000000}
              },
              "price": {"marketCap": {"raw": 4000000000000}},
              "assetProfile": {"sector": "Technology"}
            }],
            "error": null
          }
        }
    """.trimIndent()

    @Test
    fun `parser tolerates missing optional subtrees`() {
        val snapshot = YahooFinanceParser.parse(
            body = completeResponse,
            symbol = StockSymbol.of("NVDA"),
            fetchedAt = Instant.parse("2026-07-14T12:00:00Z"),
        )

        assertEquals(240.0, snapshot.targets.median!!, 0.0)
        assertEquals(42, snapshot.analystCount)
        assertEquals("Technology", snapshot.sector)
        assertEquals(null, snapshot.industry)
        assertEquals(ParseStatus.VALID, snapshot.parseStatus)
    }

    @Test
    fun `client initializes Yahoo session and maps class share symbol`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> {
                requestedUrls += url
                return when {
                    url == "https://fc.yahoo.com" ->
                        Response.error(404, "".toResponseBody())
                    url.endsWith("/v1/test/getcrumb") ->
                        Response.success("crumb value".toResponseBody())
                    else -> Response.success(completeResponse.toResponseBody())
                }
            }
        }
        val client = YahooFinanceClient(
            service = service,
            json = Json { ignoreUnknownKeys = true },
            clock = Clock.fixed(
                Instant.parse("2026-07-14T12:00:00Z"),
                ZoneOffset.UTC,
            ),
        )

        client.fetchValuation(StockSymbol.of("BRK.B"))

        assertEquals("https://fc.yahoo.com", requestedUrls[0])
        assertEquals(
            "https://query2.finance.yahoo.com/v1/test/getcrumb",
            requestedUrls[1],
        )
        assertEquals(true, requestedUrls[2].contains("/BRK-B?"))
        assertEquals(true, requestedUrls[2].contains("crumb=crumb+value"))
    }

    @Test
    fun `client renews Yahoo session once after forbidden response`() = runBlocking {
        var crumbRequests = 0
        var summaryRequests = 0
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> = when {
                url == "https://fc.yahoo.com" ->
                    Response.error(404, "".toResponseBody())
                url.endsWith("/v1/test/getcrumb") -> {
                    crumbRequests += 1
                    Response.success("crumb-$crumbRequests".toResponseBody())
                }
                else -> {
                    summaryRequests += 1
                    if (summaryRequests == 1) {
                        Response.error(403, "".toResponseBody())
                    } else {
                        Response.success(completeResponse.toResponseBody())
                    }
                }
            }
        }
        val client = YahooFinanceClient(
            service = service,
            json = Json { ignoreUnknownKeys = true },
            clock = Clock.fixed(
                Instant.parse("2026-07-14T12:00:00Z"),
                ZoneOffset.UTC,
            ),
        )

        val snapshot = client.fetchValuation(StockSymbol.of("AAPL"))

        assertEquals(2, crumbRequests)
        assertEquals(2, summaryRequests)
        assertEquals(240.0, snapshot.targets.median!!, 0.0)
    }
}
