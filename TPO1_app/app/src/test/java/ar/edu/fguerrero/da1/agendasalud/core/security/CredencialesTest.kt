package ar.edu.fguerrero.da1.agendasalud.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CredencialesTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun credenciales() = Credenciales(carpeta.root.resolve("credenciales"))

    @Test
    fun crearYVerificar() {
        val c = credenciales()
        assertFalse(c.existeUsuario())
        c.crear("Federico", "secreto123".toCharArray())
        assertTrue(c.existeUsuario())
        assertTrue(c.verificar(" federico ", "secreto123".toCharArray()))
        assertFalse(c.verificar("federico", "otra".toCharArray()))
        assertFalse(c.verificar("otro", "secreto123".toCharArray()))
    }

    @Test
    fun noSeGuardaLaContraseniaEnTextoPlano() {
        val c = credenciales()
        c.crear("federico", "secreto123".toCharArray())
        assertFalse(carpeta.root.resolve("credenciales").readText().contains("secreto123"))
    }
}
