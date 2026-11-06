package ar.edu.fguerrero.da1.agendasalud.core.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado del archivo de respaldo con una contraseña elegida por el usuario.
 *
 * A diferencia de la base (cuya clave vive en el Keystore y nunca sale del celular), la clave del
 * respaldo se deriva de la contraseña con PBKDF2: así el archivo se puede abrir en OTRO celular.
 * Si se olvida la contraseña, el respaldo no se puede recuperar, por diseño.
 *
 * Formato: [AGSR][versión][iteraciones][salt 16][iv 12][datos cifrados con AES-256-GCM].
 * El encabezado va como AAD: si alguien lo modifica, el descifrado falla.
 */
object CifradoRespaldo {

    private val MAGIA = "AGSR".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val ITERACIONES = 120_000
    private const val LARGO_SALT = 16
    private const val LARGO_IV = 12
    private const val LARGO_ENCABEZADO = 4 + 1 + 4 + LARGO_SALT + LARGO_IV

    /** El archivo no es un respaldo, está dañado o la contraseña es incorrecta (no se pueden distinguir). */
    class RespaldoIlegibleException : Exception()

    fun cifrar(datos: ByteArray, contrasenia: CharArray, iteraciones: Int = ITERACIONES): ByteArray {
        val aleatorio = SecureRandom()
        val salt = ByteArray(LARGO_SALT).also(aleatorio::nextBytes)
        val iv = ByteArray(LARGO_IV).also(aleatorio::nextBytes)
        val encabezado = ByteBuffer.allocate(LARGO_ENCABEZADO)
            .put(MAGIA).put(VERSION).putInt(iteraciones).put(salt).put(iv)
            .array()
        val cipher = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, derivarClave(contrasenia, salt, iteraciones), GCMParameterSpec(128, iv))
            updateAAD(encabezado)
        }
        return encabezado + cipher.doFinal(datos)
    }

    fun descifrar(archivo: ByteArray, contrasenia: CharArray): ByteArray {
        if (archivo.size <= LARGO_ENCABEZADO) throw RespaldoIlegibleException()
        val buffer = ByteBuffer.wrap(archivo)
        val magia = ByteArray(MAGIA.size).also(buffer::get)
        val version = buffer.get()
        val iteraciones = buffer.getInt()
        // Un archivo con miles de millones de iteraciones colgaría la app: se rechaza
        if (!magia.contentEquals(MAGIA) || version != VERSION || iteraciones !in 1..10_000_000) {
            throw RespaldoIlegibleException()
        }
        val salt = ByteArray(LARGO_SALT).also(buffer::get)
        val iv = ByteArray(LARGO_IV).also(buffer::get)
        return try {
            Cipher.getInstance(TRANSFORMACION).run {
                init(Cipher.DECRYPT_MODE, derivarClave(contrasenia, salt, iteraciones), GCMParameterSpec(128, iv))
                updateAAD(archivo, 0, LARGO_ENCABEZADO)
                doFinal(archivo, LARGO_ENCABEZADO, archivo.size - LARGO_ENCABEZADO)
            }
        } catch (e: AEADBadTagException) {
            throw RespaldoIlegibleException()
        }
    }

    private fun derivarClave(contrasenia: CharArray, salt: ByteArray, iteraciones: Int): SecretKeySpec {
        val spec = PBEKeySpec(contrasenia, salt, iteraciones, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMACION = "AES/GCM/NoPadding"
}
