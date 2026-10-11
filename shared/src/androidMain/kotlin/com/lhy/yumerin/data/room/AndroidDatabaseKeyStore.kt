package com.lhy.yumerin.data.room

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the SQLCipher passphrase encrypted at rest with a key held by Android Keystore.
 * The passphrase is only returned to the database bootstrap boundary.
 */
class AndroidDatabaseKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): ByteArray {
        preferences.getString(PASSPHRASE_KEY, null)?.let { encoded ->
            return decrypt(encoded)
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        check(
            preferences.edit()
                .putString(PASSPHRASE_KEY, encrypt(passphrase))
                .commit()
        ) { "无法保存本地数据库密钥" }
        return passphrase
    }

    private fun encrypt(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(cipher.doFinal(value), Base64.NO_WRAP)
        return "$iv.$payload"
    }

    private fun decrypt(encoded: String): ByteArray {
        val parts = encoded.split('.', limit = 2)
        require(parts.size == 2) { "本地数据库密钥格式无效" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val PREFERENCES = "secure_database"
        const val PASSPHRASE_KEY = "sqlcipher_passphrase"
        const val KEY_ALIAS = "yumerin.database.aes"
        const val PASSPHRASE_BYTES = 32
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
