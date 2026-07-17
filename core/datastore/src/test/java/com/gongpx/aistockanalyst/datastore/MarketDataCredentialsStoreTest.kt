package com.gongpx.aistockanalyst.datastore

import javax.crypto.KeyGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketDataCredentialsStoreTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val storage = InMemoryCredentialStorage()
    private val store = EncryptedMarketDataCredentialsStore(
        dispatcher = UnconfinedTestDispatcher(),
        codec = AesGcmCredentialCodec(keyProvider = { key }),
        storage = storage,
    )

    @Test
    fun `provider keys persist encrypted and round trip independently`() = runTest {
        store.setAlpacaCredentials(AlpacaCredentials("alpaca-id", "alpaca-secret"))
        store.setFinnhubApiKey(FinnhubApiKey("finnhub-secret"))
        store.setFmpApiKey(FmpApiKey("fmp-secret"))

        assertEquals(AlpacaCredentials("alpaca-id", "alpaca-secret"), store.getAlpacaCredentials())
        assertEquals(FinnhubApiKey("finnhub-secret"), store.getFinnhubApiKey())
        assertEquals(FmpApiKey("fmp-secret"), store.getFmpApiKey())
        assertTrue(store.hasAlpacaCredentials())
        assertTrue(store.hasFinnhubApiKey())
        assertTrue(store.hasFmpApiKey())
        assertFalse(storage.values.values.any { encrypted ->
            listOf("alpaca-id", "alpaca-secret", "finnhub-secret", "fmp-secret")
                .any(encrypted::contains)
        })
    }

    @Test
    fun `clearing one provider does not clear other credentials`() = runTest {
        store.setAlpacaCredentials(AlpacaCredentials("alpaca-id", "alpaca-secret"))
        store.setFinnhubApiKey(FinnhubApiKey("finnhub-secret"))
        store.setFmpApiKey(FmpApiKey("fmp-secret"))

        store.clearFinnhubApiKey()

        assertNull(store.getFinnhubApiKey())
        assertEquals(AlpacaCredentials("alpaca-id", "alpaca-secret"), store.getAlpacaCredentials())
        assertEquals(FmpApiKey("fmp-secret"), store.getFmpApiKey())
    }
}

private class InMemoryCredentialStorage : CredentialStorage {
    val values = mutableMapOf<String, String>()

    override fun get(fieldName: String): String? = values[fieldName]

    override fun put(values: Map<String, String>): Boolean {
        this.values.putAll(values)
        return true
    }

    override fun remove(fieldNames: Set<String>): Boolean {
        fieldNames.forEach(values::remove)
        return true
    }
}
