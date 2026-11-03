package ar.edu.fguerrero.da1.agendasalud.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Datos generados con `CryptoService` del backend (TPO1_backend/app/services/crypto_service.py):
 *   salt = bytes(range(16)); dek = bytes(range(100, 132)); kek = derive_key("secreto12", salt)
 *   wrap_key(kek, dek) y encrypt_payload(dek, '{"pacientes": [{"nombre": "Ana"}]}')
 * Si este test falla, la app y el backend dejaron de cifrar igual.
 */
class CompatibilidadBackendTest {

    private val salt = "AAECAwQFBgcICQoLDA0ODw=="
    private val dekEsperada = ByteArray(32) { (100 + it).toByte() }

    @Test
    fun `la app abre el sobre de la clave de datos que arma el backend`() {
        val kek = ClavesCuenta.kek("secreto12".toCharArray(), ClavesCuenta.deB64(salt))
        // wrap_key devuelve ciphertext y tag por separado; la app los guarda juntos en encrypted_dek
        val encryptedDek = ClavesCuenta.b64(
            ClavesCuenta.deB64("27tmY6Ov7JpA28xsl6yV1+LPVMbzsP+WBFIGL+avmYE=") + ClavesCuenta.deB64("arygVF4DMwe2setAp4lzmQ==")
        )
        assertArrayEquals(dekEsperada, ClavesCuenta.desenvolver(kek, SobreClave(encryptedDek, "aYhl3gltDwhRaOqT")))
    }

    @Test
    fun `la app descifra una copia cifrada por el backend`() {
        val datos = DatosCifrados(
            ciphertext = "Y1gL3958/TKW8j2o2MzcCqVHvxoBYRx+WKOdonU5pz3mPg==",
            iv = "I4357hQSVozUFfTO",
            authTag = "PfwFdtl9Rj3PXzGggw6g1A==",
        )
        assertEquals("""{"pacientes": [{"nombre": "Ana"}]}""", ClavesCuenta.descifrarDatos(dekEsperada, datos))
    }
}
