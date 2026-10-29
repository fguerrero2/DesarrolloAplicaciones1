package ar.edu.fguerrero.da1.agendasalud.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clave aleatoria de la base SQLCipher (RNF02). Se guarda cifrada con una clave AES de
 * Android Keystore que nunca sale del dispositivo. El archivo vive en noBackupFilesDir:
 * si se pierde el dispositivo o se desinstala la app, los datos no se pueden recuperar.
 */
class ClaveBaseDatos(context: Context) {

    private val archivo = File(context.noBackupFilesDir, "db.key")

    fun obtener(): ByteArray =
        if (archivo.exists()) descifrar(archivo.readBytes()) else generar()

    /** Borra la clave guardada y la de Keystore. Sin ella la base anterior queda ilegible. */
    fun borrar() {
        archivo.delete()
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun generar(): ByteArray {
        val clave = ByteArray(LARGO_CLAVE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, claveKeystore()) }
        // Formato: [largo IV][IV][texto cifrado]
        archivo.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(clave))
        return clave
    }

    private fun descifrar(datos: ByteArray): ByteArray {
        val largoIv = datos[0].toInt()
        val iv = datos.copyOfRange(1, 1 + largoIv)
        val cifrado = datos.copyOfRange(1 + largoIv, datos.size)
        val cipher = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, claveKeystore(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(cifrado)
    }

    private fun claveKeystore(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "agendasalud_db"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val LARGO_CLAVE = 32
    }
}
