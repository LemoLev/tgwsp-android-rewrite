package soft.shadlv.twp_rewritekts.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.BLOCK_MODE_GCM
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import android.security.keystore.KeyProperties.PURPOSE_DECRYPT
import android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

const val SECURITY_KEY_TAG = "SecureKeyManager"
const val DATA_STORE_TAG = "DataStore"

class SecureKeyManager(val context: Context) {
    companion object {
        private const val KEY_ALIAS = "store_key_data"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private var cachedKey: SecretKey? = null

    fun initKeyStore() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) return

        val hasStrongBox =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        try {
            val key = generateKey(useStrongBox = hasStrongBox)
            checkKeySecurityLevel(key)
        } catch (ex: Exception) {
            if (hasStrongBox) {
                try {
                    val key = generateKey(useStrongBox = false)
                    checkKeySecurityLevel(key)
                } catch (ex2: Exception) {
                    Log.e(SECURITY_KEY_TAG, "Error generating key store", ex2)
                    throw HardwareSecurityException(ex2.message)
                }
            } else {
                Log.e(SECURITY_KEY_TAG, "Error generating key store", ex)
                throw HardwareSecurityException(ex.message)
            }
        }
    }

    private fun generateKey(useStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            PURPOSE_ENCRYPT or PURPOSE_DECRYPT
        ).apply {
            setBlockModes(BLOCK_MODE_GCM)
            setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            if (useStrongBox) {
                setIsStrongBoxBacked(true)
            }
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    fun checkKeySecurityLevel(secretKey: SecretKey): Boolean {
        return try {
            val factory = SecretKeyFactory.getInstance(
                secretKey.algorithm, ANDROID_KEYSTORE
            )

            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                processSecurityLevel(keyInfo.securityLevel)
            } else {

                val isHardware = keyInfo.isInsideSecureHardware
                if (isHardware) {
                    Log.i(SECURITY_KEY_TAG, "Security: Hardware-backed (Legacy check)")
                } else {
                    Log.w(SECURITY_KEY_TAG, "Security: Software-backed")
                }
                isHardware
            }
        } catch (ex: Exception) {
            Log.e(SECURITY_KEY_TAG, "Error checking key security level", ex)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun processSecurityLevel(level: Int): Boolean {
        return when (level) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> {
                Log.i(SECURITY_KEY_TAG, "Security: StrongBox")
                true
            }

            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> {
                Log.i(SECURITY_KEY_TAG, "Security: TEE")
                true
            }

            KeyProperties.SECURITY_LEVEL_SOFTWARE -> {
                Log.w(SECURITY_KEY_TAG, "Security: Software")
                false
            }

            else -> {
                Log.w(SECURITY_KEY_TAG, "Security: Unknown/Unsupported ($level)")
                false
            }
        }
    }

    fun getSecretKey(): SecretKey {
        return cachedKey ?: synchronized(this) {
            cachedKey ?: run {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
                cachedKey = key
                key
            }
        }
    }
}

class HardwareSecurityException(message: String?) : Exception(message)

class DataStoreSecurity(context: Context) {

    companion object {
        const val PROXY_CONFIG_FILE_NAME = "proxy_config.data"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    private val manager: SecureKeyManager = SecureKeyManager(context)

    @PublishedApi
    internal val dataFile = File(context.filesDir, PROXY_CONFIG_FILE_NAME)

    @PublishedApi
    internal val atomicFile = AtomicFile(dataFile)

    init {
        manager.initKeyStore()
    }

    @PublishedApi
    internal fun encryptBytes(bytesToEncrypt: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, manager.getSecretKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(bytesToEncrypt)

        return iv + encryptedBytes
    }

    @PublishedApi
    internal fun decryptBytes(inputBytes: ByteArray): ByteArray? {
        // Минимальная длина для GCM: 12 байт (IV) + 16 байт (Tag) = 28 байт
        if (inputBytes.size < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            Log.e(DATA_STORE_TAG, "Data is too short to be decrypted")
            return null
        }

        return try {
            val iv = inputBytes.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = inputBytes.copyOfRange(GCM_IV_LENGTH, inputBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, manager.getSecretKey(), spec)

            cipher.doFinal(encryptedBytes)
        } catch (ex: Exception) {
            Log.e(DATA_STORE_TAG, "Error decrypting data", ex)
            throw ex
        }
    }

    suspend inline fun <reified T> saveObject(inputObject: T) = withContext(Dispatchers.IO) {
        try {
            val jsonString = Json.encodeToString(inputObject)
            val bytesToEncrypt = jsonString.toByteArray(Charsets.UTF_8)
            val inputByteArray = encryptBytes(bytesToEncrypt)

            val fos = atomicFile.startWrite()
            var success = false
            try {
                fos.write(inputByteArray)
                success = true
            } finally {
                if (success) {
                    atomicFile.finishWrite(fos)
                } else {
                    atomicFile.failWrite(fos)
                }
            }
        } catch (ex: Exception) {
            Log.e(DATA_STORE_TAG, "Error saving object", ex)
            throw ex
        }
    }

    suspend inline fun <reified T> getObject(): T? = withContext(Dispatchers.IO) {
        if (!dataFile.exists()) return@withContext null

        return@withContext runCatching {
            atomicFile.readFully()
        }.mapCatching { bytes ->
            val decryptedBytes = decryptBytes(bytes) ?: throw Exception("Decryption failed")
            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            Json.decodeFromString<T>(jsonString)
        }.onFailure {
            Log.w(DATA_STORE_TAG, "Config file read/parse error: ${it.message}")
        }.getOrThrow()
    }
}

@Serializable
data class ProxyConfig(
    val host: String,
    val port: Int,
    val dcip: String,
    val secret: String,
)