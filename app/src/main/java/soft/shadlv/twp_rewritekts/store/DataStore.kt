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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    @RequiresApi(Build.VERSION_CODES.S)
    fun initKeyStore() {
        val hasStrongBox: Boolean = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_STRONGBOX_KEYSTORE
        )

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) return

        try {
            if (hasStrongBox) {
                val key = generateKey(useStrongBox = true)
                checkKeySecurityLevel(key)
            } else {
                val key = generateKey(false)
                checkKeySecurityLevel(key)
            }
        } catch (ex: Exception) {
            try {
                val key = generateKey(useStrongBox = false)
                checkKeySecurityLevel(key)
            } catch (ex: Exception) {
                Log.e(SECURITY_KEY_TAG, "Error generate key store", ex)
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
            } else {
                setIsStrongBoxBacked(false)
            }
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkKeySecurityLevel(secretKey: SecretKey): Boolean {
        val factory = SecretKeyFactory.getInstance(
            secretKey.algorithm, ANDROID_KEYSTORE
        )

        val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
        val securityLevel = keyInfo.securityLevel
        when (securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> Log.i(SECURITY_KEY_TAG, "Security: StrongBox")
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> Log.i(
                SECURITY_KEY_TAG,
                "Security: TEE"
            )

            else -> Log.w(SECURITY_KEY_TAG, "Unsupported security")
        }

        val isHardwareBacked = securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                || securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX

        return isHardwareBacked
    }

    fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
}

class HardwareSecurityException(message: String?) : Exception(message)

@RequiresApi(Build.VERSION_CODES.S)
class DataStore(val context: Context) {

    companion object {
        const val PROXY_CONFIG_FILE_NAME = "proxy_config.data"
    }

    val manager: SecureKeyManager = SecureKeyManager(context)

    init {
        manager.initKeyStore()
    }

    inline fun <reified T> encryptObject(inputObject: T): ByteArray {
        val jsonString = Json.encodeToString(inputObject)
        val bytesToEncrypt = jsonString.toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, manager.getSecretKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(bytesToEncrypt)

        val combined = iv + encryptedBytes
        return combined
    }

    inline fun <reified T> decryptObject(inputBytes: ByteArray): T? {
        return try {
            val iv = inputBytes.sliceArray(0 until 12)
            val encryptedBytes = inputBytes.sliceArray(12 until inputBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, manager.getSecretKey(), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val jsonString = String(decryptedBytes, Charsets.UTF_8)

            Json.decodeFromString<T>(jsonString)
        } catch (ex: Exception) {
            Log.e(DATA_STORE_TAG, "Error decrypt object", ex)
            null
        }
    }

    inline fun <reified T> saveObject(inputObject: T) {
        val inputByteArray = encryptObject(inputObject)

        context.openFileOutput(PROXY_CONFIG_FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(inputByteArray)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    inline fun <reified T> getObject(): T? {
        return try {
            val result = runCatching {
                val outputByteArray: ByteArray
                context.openFileInput(PROXY_CONFIG_FILE_NAME).use { fis ->
                    outputByteArray = fis.readAllBytes()
                }
                outputByteArray
            }
            var outputObject: T? = null

            result.onSuccess { bytes ->
                outputObject = decryptObject<T>(bytes)
            }.onFailure {
                Log.w(DATA_STORE_TAG, "Config file not found")
                outputObject = null
            }

            outputObject
        } catch (ex: Exception) {
            Log.e(DATA_STORE_TAG, "Saved object read error", ex)
            return null
        }
    }
}

@Serializable
data class ProxyConfig(
    val host: String,
    val port: Int,
    val dcip: String,
    val secret: String,
)
