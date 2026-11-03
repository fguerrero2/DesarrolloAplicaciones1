package ar.edu.fguerrero.da1.agendasalud.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía de la cuenta, compatible con `CryptoService` del backend (TPO1_backend/app/services/crypto_service.py):
 * PBKDF2-HMAC-SHA256 de 120.000 iteraciones y AES-256-GCM con IV de 12 bytes.
 *
 *  contraseña ─PBKDF2(dek_salt aleatorio)─▶ KEK ──envuelve──▶ DEK (clave de datos, aleatoria)
 *  contraseña ─PBKDF2(salt fijo + email)──▶ credencial ─▶ se manda como "password" al servidor
 *  DEK ──cifra──▶ la copia de los datos que se sube (push)
 *
 * La contraseña real no se manda: el backend la guardaría con bcrypt, pero con ella y el dek_salt
 * (que también tiene) podría derivar la KEK y leer los datos. Con la credencial derivada el login
 * funciona igual y el servidor queda realmente "zero-knowledge".
 *
 * Kotlin/JVM puro para poder probarlo sin Android.
 */
object ClavesCuenta {
    const val ITERACIONES = 120_000
    private const val LARGO_IV = 12
    private const val LARGO_TAG = 16

    /** Lo que la app manda como "password" en register, login y reset-password. */
    fun credencial(email: String, contrasenia: CharArray, iteraciones: Int = ITERACIONES): String =
        Base64.getEncoder().encodeToString(pbkdf2(contrasenia, "agendasalud-autenticacion|${normalizar(email)}".toByteArray(), iteraciones))

    /** KEK: igual que CryptoService.derive_key(password, salt). */
    fun kek(contrasenia: CharArray, salt: ByteArray, iteraciones: Int = ITERACIONES): ByteArray = pbkdf2(contrasenia, salt, iteraciones)

    fun aleatorio(largo: Int): ByteArray = ByteArray(largo).also(SecureRandom()::nextBytes)

    /**
     * Envuelve la DEK con la KEK (CryptoService.wrap_key). El backend guarda `encrypted_dek` y
     * `dek_iv` pero no tiene un campo para el tag de GCM: por eso `encrypted_dek` lleva
     * ciphertext + tag juntos (es lo que devuelve Java y lo que AESGCM.decrypt de Python espera).
     */
    fun envolver(kek: ByteArray, dek: ByteArray): SobreClave {
        val iv = aleatorio(LARGO_IV)
        val cifrado = aesGcm(Cipher.ENCRYPT_MODE, kek, iv).doFinal(dek)
        return SobreClave(b64(cifrado), b64(iv))
    }

    fun desenvolver(kek: ByteArray, sobre: SobreClave): ByteArray =
        aesGcm(Cipher.DECRYPT_MODE, kek, deB64(sobre.iv)).doFinal(deB64(sobre.encryptedDek))

    /** Cifra la copia de los datos (CryptoService.encrypt_payload): ciphertext, iv y auth_tag por separado. */
    fun cifrarDatos(dek: ByteArray, json: String): DatosCifrados {
        val iv = aleatorio(LARGO_IV)
        val salida = aesGcm(Cipher.ENCRYPT_MODE, dek, iv).doFinal(json.toByteArray())
        return DatosCifrados(
            ciphertext = b64(salida.copyOfRange(0, salida.size - LARGO_TAG)),
            iv = b64(iv),
            authTag = b64(salida.copyOfRange(salida.size - LARGO_TAG, salida.size)),
        )
    }

    fun descifrarDatos(dek: ByteArray, datos: DatosCifrados): String =
        aesGcm(Cipher.DECRYPT_MODE, dek, deB64(datos.iv)).doFinal(deB64(datos.ciphertext) + deB64(datos.authTag)).decodeToString()

    fun normalizar(email: String) = email.trim().lowercase()

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun deB64(texto: String): ByteArray = Base64.getDecoder().decode(texto)

    private fun aesGcm(modo: Int, clave: ByteArray, iv: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(modo, SecretKeySpec(clave, "AES"), GCMParameterSpec(LARGO_TAG * 8, iv)) }

    private fun pbkdf2(contrasenia: CharArray, salt: ByteArray, iteraciones: Int): ByteArray {
        val spec = PBEKeySpec(contrasenia, salt, iteraciones, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Sobre de la clave de datos tal como lo guarda el backend (`encrypted_dek`, `dek_iv`). */
data class SobreClave(val encryptedDek: String, val iv: String)

/** Datos cifrados tal como los recibe el backend en `/sync/push` (`ciphertext`, `iv`, `auth_tag`). */
data class DatosCifrados(val ciphertext: String, val iv: String, val authTag: String)
