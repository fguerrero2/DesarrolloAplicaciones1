package ar.edu.fguerrero.da1.agendasalud.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatosTest {

    @Test
    fun parsearPesosAceptaFormatosComunes() {
        assertEquals(2_000_000L, parsearPesos("20000"))
        assertEquals(2_000_000L, parsearPesos("20.000"))
        assertEquals(2_000_000L, parsearPesos("$ 20.000"))
        assertEquals(2_000_050L, parsearPesos("20000,50"))
        assertEquals(-100L, parsearPesos("-1"))
    }

    @Test
    fun parsearPesosRechazaTextoInvalido() {
        assertNull(parsearPesos(""))
        assertNull(parsearPesos("abc"))
        assertNull(parsearPesos("1,234"))
    }

    @Test
    fun pesosParaEditarIdaYVuelta() {
        assertEquals("20000", pesosParaEditar(2_000_000))
        assertEquals("20000,50", pesosParaEditar(2_000_050))
        assertEquals(2_000_050L, parsearPesos(pesosParaEditar(2_000_050)))
    }
}
