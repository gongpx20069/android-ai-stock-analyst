package com.gongpx.aistockanalyst.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AlpacaCredentials(
    val keyId: String,
    val secretKey: String,
) {
    init {
        require(keyId.isNotBlank()) { "Alpaca key ID is required" }
        require(secretKey.isNotBlank()) { "Alpaca secret key is required" }
    }
}

data class FinnhubApiKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Finnhub API key is required" }
    }

    override fun toString(): String = "FinnhubApiKey(<redacted>)"
}

data class FmpApiKey(val value: String) {
    init {
        require(value.isNotBlank()) { "FMP API key is required" }
    }

    override fun toString(): String = "FmpApiKey(<redacted>)"
}

interface MarketDataCredentialsStore {
    suspend fun getAlpacaCredentials(): AlpacaCredentials?

    suspend fun hasAlpacaCredentials(): Boolean = getAlpacaCredentials() != null

    suspend fun setAlpacaCredentials(credentials: AlpacaCredentials)

    suspend fun clearAlpacaCredentials()

    suspend fun getFinnhubApiKey(): FinnhubApiKey?

    suspend fun hasFinnhubApiKey(): Boolean = getFinnhubApiKey() != null

    suspend fun setFinnhubApiKey(apiKey: FinnhubApiKey)

    suspend fun clearFinnhubApiKey()

    suspend fun getFmpApiKey(): FmpApiKey?

    suspend fun hasFmpApiKey(): Boolean = getFmpApiKey() != null

    suspend fun setFmpApiKey(apiKey: FmpApiKey)

    suspend fun clearFmpApiKey()
}

class EncryptedMarketDataCredentialsStore internal constructor(
    private val dispatcher: CoroutineDispatcher,
    private val codec: CredentialCodec,
    private val storage: CredentialStorage,
) : MarketDataCredentialsStore {
    constructor(context: Context) : this(
        dispatcher = Dispatchers.IO,
        codec = AesGcmCredentialCodec(
            keyProvider = AndroidKeystoreKeyProvider(),
        ),
        storage = SharedPreferencesCredentialStorage(context),
    )

    override suspend fun getAlpacaCredentials(): AlpacaCredentials? = withContext(dispatcher) {
        val encryptedKeyId = storage.get(ALPACA_KEY_ID)
        val encryptedSecret = storage.get(ALPACA_SECRET_KEY)
        if (encryptedKeyId == null && encryptedSecret == null) {
            return@withContext null
        }
        if (encryptedKeyId == null || encryptedSecret == null) {
            throw CredentialsStorageException(
                "Stored Alpaca credentials are incomplete",
            )
        }

        try {
            AlpacaCredentials(
                keyId = codec.decrypt(ALPACA_KEY_ID, encryptedKeyId),
                secretKey = codec.decrypt(ALPACA_SECRET_KEY, encryptedSecret),
            )
        } catch (failure: GeneralSecurityException) {
            throw CredentialsStorageException(
                "Unable to decrypt Alpaca credentials",
                failure,
            )
        } catch (failure: IllegalArgumentException) {
            throw CredentialsStorageException(
                "Stored Alpaca credentials are invalid",
                failure,
            )
        }
    }

    override suspend fun setAlpacaCredentials(
        credentials: AlpacaCredentials,
    ) = withContext(dispatcher) {
        val encryptedKeyId: String
        val encryptedSecret: String
        try {
            encryptedKeyId = codec.encrypt(ALPACA_KEY_ID, credentials.keyId.trim())
            encryptedSecret = codec.encrypt(ALPACA_SECRET_KEY, credentials.secretKey.trim())
        } catch (failure: GeneralSecurityException) {
            throw CredentialsStorageException(
                "Unable to encrypt Alpaca credentials",
                failure,
            )
        }

        if (!storage.put(
            mapOf(
                ALPACA_KEY_ID to encryptedKeyId,
                ALPACA_SECRET_KEY to encryptedSecret,
            ),
        )) {
            throw CredentialsStorageException(
                "Unable to persist Alpaca credentials",
            )
        }
    }

    override suspend fun clearAlpacaCredentials() = withContext(dispatcher) {
        if (!storage.remove(setOf(ALPACA_KEY_ID, ALPACA_SECRET_KEY))) {
            throw CredentialsStorageException(
                "Unable to clear Alpaca credentials",
            )
        }
    }

    override suspend fun getFinnhubApiKey(): FinnhubApiKey? =
        readApiKey(
            fieldName = FINNHUB_API_KEY,
            providerName = "Finnhub",
            create = ::FinnhubApiKey,
        )

    override suspend fun setFinnhubApiKey(apiKey: FinnhubApiKey) =
        writeApiKey(FINNHUB_API_KEY, "Finnhub", apiKey.value)

    override suspend fun clearFinnhubApiKey() =
        clearApiKey(FINNHUB_API_KEY, "Finnhub")

    override suspend fun getFmpApiKey(): FmpApiKey? =
        readApiKey(
            fieldName = FMP_API_KEY,
            providerName = "FMP",
            create = ::FmpApiKey,
        )

    override suspend fun setFmpApiKey(apiKey: FmpApiKey) =
        writeApiKey(FMP_API_KEY, "FMP", apiKey.value)

    override suspend fun clearFmpApiKey() =
        clearApiKey(FMP_API_KEY, "FMP")

    private suspend fun <T> readApiKey(
        fieldName: String,
        providerName: String,
        create: (String) -> T,
    ): T? = withContext(dispatcher) {
        val encrypted = storage.get(fieldName) ?: return@withContext null
        try {
            create(codec.decrypt(fieldName, encrypted))
        } catch (failure: GeneralSecurityException) {
            throw CredentialsStorageException(
                "Unable to decrypt $providerName API key",
                failure,
            )
        } catch (failure: IllegalArgumentException) {
            throw CredentialsStorageException(
                "Stored $providerName API key is invalid",
                failure,
            )
        }
    }

    private suspend fun writeApiKey(
        fieldName: String,
        providerName: String,
        value: String,
    ) = withContext(dispatcher) {
        val encrypted = try {
            codec.encrypt(fieldName, value.trim())
        } catch (failure: GeneralSecurityException) {
            throw CredentialsStorageException(
                "Unable to encrypt $providerName API key",
                failure,
            )
        }
        if (!storage.put(mapOf(fieldName to encrypted))) {
            throw CredentialsStorageException("Unable to persist $providerName API key")
        }
    }

    private suspend fun clearApiKey(
        fieldName: String,
        providerName: String,
    ) = withContext(dispatcher) {
        if (!storage.remove(setOf(fieldName))) {
            throw CredentialsStorageException("Unable to clear $providerName API key")
        }
    }

    companion object {
        private const val ALPACA_KEY_ID = "alpaca_key_id"
        private const val ALPACA_SECRET_KEY = "alpaca_secret_key"
        private const val FINNHUB_API_KEY = "finnhub_api_key"
        private const val FMP_API_KEY = "fmp_api_key"
    }
}

internal interface CredentialStorage {
    fun get(fieldName: String): String?

    fun put(values: Map<String, String>): Boolean

    fun remove(fieldNames: Set<String>): Boolean
}

private class SharedPreferencesCredentialStorage(context: Context) : CredentialStorage {
    private val preferences = context.getSharedPreferences(
        "market_data_credentials",
        Context.MODE_PRIVATE,
    )

    override fun get(fieldName: String): String? = preferences.getString(fieldName, null)

    override fun put(values: Map<String, String>): Boolean {
        val editor = preferences.edit()
        values.forEach { (fieldName, value) -> editor.putString(fieldName, value) }
        return editor.commit()
    }

    override fun remove(fieldNames: Set<String>): Boolean {
        val editor = preferences.edit()
        fieldNames.forEach(editor::remove)
        return editor.commit()
    }
}

internal interface CredentialCodec {
    @Throws(GeneralSecurityException::class)
    fun encrypt(
        fieldName: String,
        plaintext: String,
    ): String

    @Throws(GeneralSecurityException::class)
    fun decrypt(
        fieldName: String,
        encodedPayload: String,
    ): String
}

internal class AesGcmCredentialCodec(
    private val keyProvider: () -> SecretKey,
    private val secureRandom: SecureRandom = SecureRandom(),
) : CredentialCodec {
    override fun encrypt(
        fieldName: String,
        plaintext: String,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(fieldName.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.getEncoder().encodeToString(payload)
    }

    override fun decrypt(
        fieldName: String,
        encodedPayload: String,
    ): String {
        val payload = Base64.getDecoder().decode(encodedPayload)
        if (payload.size < 2 + IV_LENGTH + TAG_LENGTH_BYTES) {
            throw GeneralSecurityException("Encrypted credential payload is too short")
        }
        val buffer = ByteBuffer.wrap(payload)
        if (buffer.get() != FORMAT_VERSION) {
            throw GeneralSecurityException("Unsupported credential payload version")
        }
        val ivLength = buffer.get().toInt() and 0xFF
        if (ivLength != IV_LENGTH || buffer.remaining() <= ivLength) {
            throw GeneralSecurityException("Encrypted credential IV is invalid")
        }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(fieldName.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        private const val FORMAT_VERSION: Byte = 1
    }
}

private class AndroidKeystoreKeyProvider : () -> SecretKey {
    @Synchronized
    override fun invoke(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        } catch (failure: IOException) {
            throw GeneralSecurityException("Unable to access Android Keystore", failure)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_stock_analyst_market_data_credentials"
    }
}

class CredentialsStorageException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
