package ar.edu.fguerrero.da1.agendasalud.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import javax.crypto.AEADBadTagException

class ClavesCuentaTest {

    // Pocas iteraciones para que el test sea rápido
    private val it = 1_000

    @Test
    fun `otro celular recupera la clave de datos con la misma contraseña`() {
        val salt = ClavesCuenta.aleatorio(16)
        val dek = ClavesCuenta.aleatorio(32)
        val sobre = ClavesCuenta.envolver(ClavesCuenta.kek("secreto12".toCharArray(), salt, it), dek)
        // En el celular nuevo solo hay contraseña + lo que devuelve el login (sobre y salt)
        assertArrayEquals(dek, ClavesCuenta.desenvolver(ClavesCuenta.kek("secreto12".toCharArray(), salt, it), sobre))
    }

    @Test(expected = AEADBadTagException::class)
    fun `con otra contraseña no se puede desenvolver`() {
        val salt = ClavesCuenta.aleatorio(16)
        val sobre = ClavesCuenta.envolver(ClavesCuenta.kek("secreto12".toCharArray(), salt, it), ClavesCuenta.aleatorio(32))
        ClavesCuenta.desenvolver(ClavesCuenta.kek("otraClave1".toCharArray(), salt, it), sobre)
    }

    @Test
    fun `la credencial no es la contraseña y no sirve como KEK`() {
        val credencial = ClavesCuenta.credencial("Ana@Mail.com", "secreto12".toCharArray(), it)
        assertFalse(credencial.contains("secreto12"))
        assertEquals(credencial, ClavesCuenta.credencial(" ana@mail.com ", "secreto12".toCharArray(), it))
        assertNotEquals(credencial, ClavesCuenta.credencial("beto@mail.com", "secreto12".toCharArray(), it))
    }

    @Test
    fun `los datos se cifran con tag separado como espera el backend`() {
        val dek = ClavesCuenta.aleatorio(32)
        val json = """{"pacientes":[{"nombre":"Ana Pérez"}]}"""
        val cifrado = ClavesCuenta.cifrarDatos(dek, json)
        assertEquals(16, ClavesCuenta.deB64(cifrado.authTag).size)
        assertEquals(12, ClavesCuenta.deB64(cifrado.iv).size)
        assertFalse(ClavesCuenta.deB64(cifrado.ciphertext).decodeToString(throwOnInvalidSequence = false).contains("Ana"))
        assertEquals(json, ClavesCuenta.descifrarDatos(dek, cifrado))
    }

    @Test(expected = AEADBadTagException::class)
    fun `datos modificados en el servidor se rechazan`() {
        val dek = ClavesCuenta.aleatorio(32)
        val cifrado = ClavesCuenta.cifrarDatos(dek, "{}")
        val otroTag = ClavesCuenta.b64(ByteArray(16))
        ClavesCuenta.descifrarDatos(dek, cifrado.copy(authTag = otroTag))
    }
}
