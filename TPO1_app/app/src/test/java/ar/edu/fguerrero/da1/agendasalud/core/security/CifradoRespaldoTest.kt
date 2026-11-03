package ar.edu.fguerrero.da1.agendasalud.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CifradoRespaldoTest {

    // Pocas iteraciones para que el test sea rápido; el formato guarda cuántas se usaron
    private val iteraciones = 1_000
    private val datos = """{"pacientes":[{"id":1,"nombre":"Ana"}]}""".encodeToByteArray()

    @Test
    fun `con la misma contraseña se recuperan los datos`() {
        val archivo = CifradoRespaldo.cifrar(datos, "secreto1".toCharArray(), iteraciones)
        assertArrayEquals(datos, CifradoRespaldo.descifrar(archivo, "secreto1".toCharArray()))
    }

    @Test
    fun `el archivo no contiene los datos en claro`() {
        val archivo = CifradoRespaldo.cifrar(datos, "secreto1".toCharArray(), iteraciones)
        assertFalse(archivo.decodeToString(throwOnInvalidSequence = false).contains("Ana"))
    }

    @Test(expected = CifradoRespaldo.RespaldoIlegibleException::class)
    fun `con otra contraseña se rechaza`() {
        val archivo = CifradoRespaldo.cifrar(datos, "secreto1".toCharArray(), iteraciones)
        CifradoRespaldo.descifrar(archivo, "secreto2".toCharArray())
    }

    @Test(expected = CifradoRespaldo.RespaldoIlegibleException::class)
    fun `un archivo modificado se rechaza`() {
        val archivo = CifradoRespaldo.cifrar(datos, "secreto1".toCharArray(), iteraciones)
        archivo[archivo.size - 1] = (archivo.last() + 1).toByte()
        CifradoRespaldo.descifrar(archivo, "secreto1".toCharArray())
    }

    @Test(expected = CifradoRespaldo.RespaldoIlegibleException::class)
    fun `un archivo que no es un respaldo se rechaza`() {
        CifradoRespaldo.descifrar("esto es un PDF cualquiera, no un respaldo".encodeToByteArray(), "secreto1".toCharArray())
    }
}
