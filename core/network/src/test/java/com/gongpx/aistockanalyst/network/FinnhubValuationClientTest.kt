package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FinnhubValuationClientTest {
    private val fetchedAt = Instant.parse("2026-07-17T10:00:00Z")

    @Test
    fun `fixture parser maps complete snapshot and converts market cap millions`() {
        val snapshot = parseFixtures()

        assertEquals(DataSource.FINNHUB, snapshot.source)
        assertEquals(ParseStatus.VALID, snapshot.parseStatus)
        assertEquals(240.0, snapshot.targets.median!!, 0.0)
        assertEquals(42, snapshot.analystCount)
        assertEquals("strong_buy", snapshot.recommendationKey)
        assertEquals(4_000_000_000_000L, snapshot.marketCap)
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), snapshot.asOf)
    }

    @Test
    fun `empty optional endpoints produce partial snapshot`() {
        val snapshot = FinnhubValuationParser.parse(
            priceTargetBody = "{}",
            recommendationBody = "[]",
            metricBody = "{}",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )

        assertEquals(ParseStatus.PARTIAL, snapshot.parseStatus)
        assertNull(snapshot.targets.median)
        assertNull(snapshot.recommendationKey)
        assertEquals(fetchedAt, snapshot.asOf)
    }

    @Test
    fun `negative forward PE remains valid provider data`() {
        val snapshot = FinnhubValuationParser.parse(
            priceTargetBody = "{}",
            recommendationBody = "[]",
            metricBody = """{"symbol":"AAPL","metric":{"forwardPE":-12.5}}""",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )

        assertEquals(-12.5, snapshot.forwardPe!!, 0.0)
        assertEquals(ParseStatus.PARTIAL, snapshot.parseStatus)
    }

    @Test
    fun `date-only last updated remains supported`() {
        val snapshot = FinnhubValuationParser.parse(
            priceTargetBody = """{"symbol":"AAPL","lastUpdated":"2026-07-15"}""",
            recommendationBody = "[]",
            metricBody = "{}",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )

        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), snapshot.asOf)
    }

    @Test(expected = ProviderPayloadException::class)
    fun `present malformed metric fails parsing`() {
        FinnhubValuationParser.parse(
            priceTargetBody = "{}",
            recommendationBody = "[]",
            metricBody = """{"symbol":"AAPL","metric":{"forwardPE":"28.5"}}""",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `provider error payload does not masquerade as partial data`() {
        FinnhubValuationParser.parse(
            priceTargetBody = """{"error":"API key invalid"}""",
            recommendationBody = "[]",
            metricBody = "{}",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `partial inverted target range fails parsing`() {
        FinnhubValuationParser.parse(
            priceTargetBody =
                """{"symbol":"AAPL","targetLow":260.0,"targetHigh":180.0}""",
            recommendationBody = "[]",
            metricBody = "{}",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )
    }

    @Test
    fun `every non-empty endpoint validates its response symbol`() {
        listOf(
            Triple("""{"symbol":"MSFT"}""", "[]", "{}"),
            Triple("{}", """[{"symbol":"MSFT"}]""", "{}"),
            Triple("{}", "[]", """{"symbol":"MSFT","metric":{}}"""),
        ).forEach { bodies ->
            assertSymbolMismatch(
                priceTargetBody = bodies.first,
                recommendationBody = bodies.second,
                metricBody = bodies.third,
            )
        }
    }

    @Test
    fun `client sends key only in Finnhub header and maps all endpoints`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> {
                requests += url to headers
                val body = when {
                    "price-target" in url -> fixture("finnhub/price-target-complete.json")
                    "recommendation" in url -> fixture("finnhub/recommendation-complete.json")
                    else -> fixture("finnhub/metric-complete.json")
                }
                return Response.success(body.toResponseBody())
            }
        }

        val snapshot = client(service).fetchValuation(StockSymbol.of("AAPL"))

        assertEquals(DataSource.FINNHUB, snapshot.source)
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.second["X-Finnhub-Token"] == "secret-key" })
        assertFalse(requests.any { "secret-key" in it.first })
    }

    @Test
    fun `auth failure surfaces as provider HTTP failure`() {
        val service = fixedService(Response.error(403, "".toResponseBody()))

        val failure = try {
            runBlocking { client(service).fetchValuation(StockSymbol.of("AAPL")) }
            null
        } catch (failure: ProviderHttpException) {
            failure
        }

        assertEquals(403, failure?.statusCode)
    }

    private fun parseFixtures() = FinnhubValuationParser.parse(
        priceTargetBody = fixture("finnhub/price-target-complete.json"),
        recommendationBody = fixture("finnhub/recommendation-complete.json"),
        metricBody = fixture("finnhub/metric-complete.json"),
        symbol = StockSymbol.of("AAPL"),
        fetchedAt = fetchedAt,
    )

    private fun assertSymbolMismatch(
        priceTargetBody: String,
        recommendationBody: String,
        metricBody: String,
    ) {
        try {
            FinnhubValuationParser.parse(
                priceTargetBody = priceTargetBody,
                recommendationBody = recommendationBody,
                metricBody = metricBody,
                symbol = StockSymbol.of("AAPL"),
                fetchedAt = fetchedAt,
            )
            throw AssertionError("Expected a provider symbol mismatch")
        } catch (_: ProviderPayloadException) {
            // Expected.
        }
    }

    private fun client(service: RawHttpService) = FinnhubValuationClient(
        service = service,
        json = Json { ignoreUnknownKeys = true },
        clock = Clock.fixed(fetchedAt, ZoneOffset.UTC),
        credentialsProvider = { FinnhubApiCredentials("secret-key") },
    )

    private fun fixedService(
        response: Response<okhttp3.ResponseBody>,
    ): RawHttpService = object : RawHttpService {
        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): Response<okhttp3.ResponseBody> = response
    }

    private fun fixture(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)).readText()
}
