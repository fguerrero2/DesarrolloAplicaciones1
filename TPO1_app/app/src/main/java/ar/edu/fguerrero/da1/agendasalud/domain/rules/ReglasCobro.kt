package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio

/** Reglas de saldo (RF04). Kotlin puro, sin Android. */
object ReglasCobro {

    fun pendiente(importeCargoCentavos: Long, cobrosCentavos: List<Long>): Long =
        importeCargoCentavos - cobrosCentavos.sum()

    /** Rechaza montos cero, negativos o mayores al pendiente. */
    fun validarCobro(montoCentavos: Long, pendienteCentavos: Long): ErrorDominio? = when {
        montoCentavos <= 0 -> ErrorDominio.MontoNoPositivo
        montoCentavos > pendienteCentavos -> ErrorDominio.MontoSuperaPendiente
        else -> null
    }
}
