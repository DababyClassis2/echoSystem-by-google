package com.echosystem.localshare.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.echosystem.localshare.database.PairingKeyEntity
import com.echosystem.localshare.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PairingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_pairing_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var currentPin: String? = null
    private val KEY_ALIAS = "com.echosystem.localshare.pairing_key"

    fun generatePin(): String {
        val pin = (100000..999999).random().toString()
        currentPin = pin
        return pin
    }

    fun verifyPin(pin: String): Boolean {
        return pin == currentPin
    }

    fun markAsPaired(deviceId: String) {
        prefs.edit().putBoolean("paired_$deviceId", true).apply()
    }

    fun isPaired(deviceId: String): Boolean {
        return prefs.getBoolean("paired_$deviceId", false)
    }

    fun revokePairing(deviceId: String) {
        prefs.edit().remove("paired_$deviceId").apply()
    }

    fun getDeviceNodeName(): String {
        return android.os.Build.MODEL
    }

    fun getLocalIp(): String {
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        if (!sAddr.contains(":")) { // IPv4 checking
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    // Modern RSA KeyPair from KeyStore
    fun getOrCreateLocalKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                setKeySize(2048)
                build()
            }
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    fun getLocalPublicKeyBlob(): ByteArray {
        val kp = getOrCreateLocalKeyPair()
        return kp.public.encoded
    }

    suspend fun saveRemotePublicKey(deviceId: String, publicKeyBytes: ByteArray) {
        val entity = PairingKeyEntity(deviceId, publicKeyBytes)
        deviceRepository.insertPairingKey(entity)
    }

    suspend fun getRemotePublicKey(deviceId: String): ByteArray? {
        return deviceRepository.getPairingKey(deviceId)?.publicKeyBlob
    }
}
