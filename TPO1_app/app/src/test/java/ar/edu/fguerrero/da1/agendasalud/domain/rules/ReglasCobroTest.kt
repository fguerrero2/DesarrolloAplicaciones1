package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReglasCobroTest {

    // Criterio de aceptación de RF04, en centavos
    private val cargo = 20_000_00L

    @Test
    fun cobroParcialDejaPendiente() {
        assertEquals(12_000_00L, ReglasCobro.pendiente(cargo, listOf(8_000_00L)))
    }

    @Test
    fun segundoCobroSaldaLaDeuda() {
        assertEquals(0L, ReglasCobro.pendiente(cargo, listOf(8_000_00L, 12_000_00L)))
    }

    @Test
    fun cobroIgualAlPendienteEsValido() {
        assertNull(ReglasCobro.validarCobro(12_000_00L, 12_000_00L))
    }

    @Test
    fun montoCeroONegativoSeRechaza() {
        assertEquals(ErrorDominio.MontoNoPositivo, ReglasCobro.validarCobro(0, 12_000_00L))
        assertEquals(ErrorDominio.MontoNoPositivo, ReglasCobro.validarCobro(-1, 12_000_00L))
    }

    @Test
    fun montoMayorAlPendienteSeRechaza() {
        assertEquals(ErrorDominio.MontoSuperaPendiente, ReglasCobro.validarCobro(12_000_01L, 12_000_00L))
    }
}
