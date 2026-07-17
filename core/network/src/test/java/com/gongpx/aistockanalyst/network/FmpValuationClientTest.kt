package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import java.io.IOException
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

class FmpValuationClientTest {
    private val fetchedAt = Instant.parse("2026-07-17T10:00:00Z")

    @Test
    fun `fixture parser maps verified fields and remains partial`() {
        val snapshot = parseFixtures()

        assertEquals(DataSource.FMP, snapshot.source)
        assertEquals(ParseStatus.PARTIAL, snapshot.parseStatus)
        assertEquals(240.0, snapshot.targets.median!!, 0.0)
        assertEquals("strong_buy", snapshot.recommendationKey)
        assertEquals(4_000_000_000_000L, snapshot.marketCap)
        assertNull(snapshot.forwardPe)
        assertNull(snapshot.analystCount)
        assertEquals(Instant.ofEpochSecond(1_784_102_400), snapshot.asOf)
    }

    @Test
    fun `empty optional quote and grades preserve target data as partial`() {
        val snapshot = FmpValuationParser.parse(
            quoteBody = "[]",
            targetBody = fixture("fmp/target-complete.json"),
            gradesBody = "[]",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )

        assertEquals(240.0, snapshot.targets.median!!, 0.0)
        assertNull(snapshot.marketCap)
        assertNull(snapshot.recommendationKey)
        assertEquals(fetchedAt, snapshot.asOf)
    }

    @Test(expected = ProviderPayloadException::class)
    fun `present malformed target fails parsing`() {
        FmpValuationParser.parse(
            quoteBody = "[]",
            targetBody = """[{"symbol":"AAPL","targetMedian":"240"}]""",
            gradesBody = "[]",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )
    }

    @Test(expected = ProviderPayloadException::class)
    fun `partial inverted target range fails parsing`() {
        FmpValuationParser.parse(
            quoteBody = "[]",
            targetBody =
                """[{"symbol":"AAPL","targetLow":260.0,"targetHigh":180.0}]""",
            gradesBody = "[]",
            symbol = StockSymbol.of("AAPL"),
            fetchedAt = fetchedAt,
        )
    }

    @Test
    fun `every non-empty endpoint validates its response symbol`() {
        listOf(
            Triple("""[{"symbol":"MSFT"}]""", "[]", "[]"),
            Triple("[]", """[{"symbol":"MSFT"}]""", "[]"),
            Triple("[]", "[]", """[{"symbol":"MSFT"}]"""),
        ).forEach { bodies ->
            assertSymbolMismatch(
                quoteBody = bodies.first,
                targetBody = bodies.second,
                gradesBody = bodies.third,
            )
        }
    }

    @Test
    fun `client uses query authentication and source mapping`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> {
                requestedUrls += url
                val body = when {
                    "/quote?" in url -> fixture("fmp/quote-complete.json")
                    "price-target-consensus" in url -> fixture("fmp/target-complete.json")
                    else -> fixture("fmp/grades-complete.json")
                }
                return Response.success(body.toResponseBody())
            }
        }

        val snapshot = client(service).fetchValuation(StockSymbol.of("AAPL"))

        assertEquals(DataSource.FMP, snapshot.source)
        assertEquals(3, requestedUrls.size)
        assertTrue(requestedUrls.all { "symbol=AAPL" in it && "apikey=secret-key" in it })
    }

    @Test
    fun `transport exception does not expose FMP query key`() {
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> = throw IOException("failed URL $url")
        }

        val failure = try {
            runBlocking { client(service).fetchValuation(StockSymbol.of("AAPL")) }
            null
        } catch (failure: ProviderTransportException) {
            failure
        }

        assertFalse(failure?.message.orEmpty().contains("secret-key"))
        assertNull(failure?.cause)
    }

    @Test
    fun `auth failure is not treated as empty endpoint data`() {
        val service = object : RawHttpService {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): Response<okhttp3.ResponseBody> =
                Response.error(401, "invalid api key".toResponseBody())
        }

        val failure = try {
            runBlocking { client(service).fetchValuation(StockSymbol.of("AAPL")) }
            null
        } catch (failure: ProviderHttpException) {
            failure
        }

        assertEquals(401, failure?.statusCode)
    }

    private fun parseFixtures() = FmpValuationParser.parse(
        quoteBody = fixture("fmp/quote-complete.json"),
        targetBody = fixture("fmp/target-complete.json"),
        gradesBody = fixture("fmp/grades-complete.json"),
        symbol = StockSymbol.of("AAPL"),
        fetchedAt = fetchedAt,
    )

    private fun assertSymbolMismatch(
        quoteBody: String,
        targetBody: String,
        gradesBody: String,
    ) {
        try {
            FmpValuationParser.parse(
                quoteBody = quoteBody,
                targetBody = targetBody,
                gradesBody = gradesBody,
                symbol = StockSymbol.of("AAPL"),
                fetchedAt = fetchedAt,
            )
            throw AssertionError("Expected a provider symbol mismatch")
        } catch (_: ProviderPayloadException) {
            // Expected.
        }
    }

    private fun client(service: RawHttpService) = FmpValuationClient(
        service = service,
        json = Json { ignoreUnknownKeys = true },
        clock = Clock.fixed(fetchedAt, ZoneOffset.UTC),
        credentialsProvider = { FmpApiCredentials("secret-key") },
    )

    private fun fixture(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)).readText()
}
