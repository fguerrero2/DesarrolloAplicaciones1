package ar.edu.fguerrero.da1.agendasalud.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra secretos pequeños (claves, tokens) con una clave AES de Android Keystore que nunca sale
 * del dispositivo: aunque alguien copie los archivos de la app, sin este celular no los puede leer.
 * Formato del resultado: [largo IV][IV][texto cifrado con AES-GCM].
 */
class CifradorKeystore(private val alias: String) {

    fun cifrar(datos: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave()) }
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(datos)
    }

    fun descifrar(datos: ByteArray): ByteArray {
        val largoIv = datos[0].toInt()
        val iv = datos.copyOfRange(1, 1 + largoIv)
        val cipher = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(datos, 1 + largoIv, datos.size - 1 - largoIv)
    }

    /** Borra la clave de Keystore: todo lo cifrado con ella queda ilegible. */
    fun borrarClave() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun clave(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}
