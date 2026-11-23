package com.example.smartpaymentalert

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureStorage {

private const val KEY_ALIAS = "SmartPaymentAlertKey"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

private fun getKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)

    val existing = keyStore.getKey(KEY_ALIAS, null)
    if (existing is SecretKey) return existing

    val generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        ANDROID_KEYSTORE
    )

    generator.init(
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .build()
    )

    return generator.generateKey()
}

fun encrypt(data: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getKey())

    val encrypted = cipher.doFinal(
        data.toByteArray(Charsets.UTF_8)
    )

    val combined = cipher.iv + encrypted

    return Base64.encodeToString(
        combined,
        Base64.NO_WRAP
    )
}

fun decrypt(data: String): String {
    val combined = Base64.decode(data, Base64.NO_WRAP)

    require(combined.size > 12 + 16) {
        "Invalid encrypted data"
    }

    val iv = combined.copyOfRange(0, 12)
    val encrypted = combined.copyOfRange(
        12,
        combined.size
    )

    val cipher = Cipher.getInstance(TRANSFORMATION)

    cipher.init(
        Cipher.DECRYPT_MODE,
        getKey(),
        GCMParameterSpec(128, iv)
    )

    return String(
        cipher.doFinal(encrypted),
        Charsets.UTF_8
    )
}

}
