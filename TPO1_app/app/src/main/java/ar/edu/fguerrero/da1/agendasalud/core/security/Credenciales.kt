package ar.edu.fguerrero.da1.agendasalud.core.security

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Usuario y contraseña locales del profesional. Solo se guarda un hash PBKDF2 con salt,
 * nunca la contraseña. El archivo debe vivir en noBackupFilesDir.
 */
class Credenciales(private val archivo: File) {

    fun existeUsuario(): Boolean = archivo.exists()

    fun crear(usuario: String, contrasenia: CharArray) {
        check(!existeUsuario()) { "El usuario ya fue creado" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derivar(contrasenia, salt, ITERACIONES)
        val codificar = Base64.getEncoder()
        archivo.writeText(
            listOf(normalizar(usuario), codificar.encodeToString(salt), codificar.encodeToString(hash), ITERACIONES.toString())
                .joinToString("\n")
        )
    }

    fun verificar(usuario: String, contrasenia: CharArray): Boolean {
        if (!existeUsuario()) return false
        val (guardado, salt64, hash64, iteraciones) = archivo.readLines()
        val decodificar = Base64.getDecoder()
        val hash = derivar(contrasenia, decodificar.decode(salt64), iteraciones.toInt())
        // Comparación en tiempo constante; se evalúa el hash aunque el usuario no coincida
        val hashOk = MessageDigest.isEqual(hash, decodificar.decode(hash64))
        return hashOk && guardado == normalizar(usuario)
    }

    fun usuario(): String? = if (existeUsuario()) archivo.readLines().first() else null

    private fun normalizar(usuario: String) = usuario.trim().lowercase()

    private fun derivar(contrasenia: CharArray, salt: ByteArray, iteraciones: Int): ByteArray {
        val spec = PBEKeySpec(contrasenia, salt, iteraciones, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val LARGO_MINIMO = 6
        private const val ITERACIONES = 120_000
    }
}
