package com.gongpx.aistockanalyst.datastore

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AesGcmCredentialCodecTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val codec = AesGcmCredentialCodec(keyProvider = { key })

    @Test
    fun `codec round trips credentials without deterministic ciphertext`() {
        val first = codec.encrypt("alpaca_secret_key", "secret-value")
        val second = codec.encrypt("alpaca_secret_key", "secret-value")

        assertNotEquals(first, second)
        assertEquals("secret-value", codec.decrypt("alpaca_secret_key", first))
        assertEquals("secret-value", codec.decrypt("alpaca_secret_key", second))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `codec binds ciphertext to its field name`() {
        val encrypted = codec.encrypt("alpaca_secret_key", "secret-value")

        codec.decrypt("alpaca_key_id", encrypted)
    }
}
