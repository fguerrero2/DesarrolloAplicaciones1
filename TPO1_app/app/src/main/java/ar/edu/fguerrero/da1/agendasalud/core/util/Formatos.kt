package ar.edu.fguerrero.da1.agendasalud.core.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

private val localeAr = Locale.forLanguageTag("es-AR")
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm", localeAr)
private val formatoFechaCorta = DateTimeFormatter.ofPattern("dd/MM", localeAr)
private val formatoFechaNumerica = DateTimeFormatter.ofPattern("dd/MM/yyyy", localeAr)
private val formatoFechaLarga = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", localeAr)

/** 2000000 centavos → "$ 20.000" (sin decimales si son cero). */
fun formatearPesos(centavos: Long): String {
    val formato = NumberFormat.getCurrencyInstance(localeAr).apply {
        maximumFractionDigits = if (centavos % 100 == 0L) 0 else 2
        minimumFractionDigits = maximumFractionDigits
    }
    return formato.format(centavos / 100.0)
}

/** Texto editable de un importe: 2000000 → "20000", 2000050 → "20000,50". */
fun pesosParaEditar(centavos: Long): String =
    if (centavos % 100 == 0L) "${centavos / 100}" else "%d,%02d".format(centavos / 100, centavos % 100)

/** "20.000", "20000" o "20000,5" → centavos. Devuelve null si el texto no es un número válido. */
fun parsearPesos(texto: String): Long? {
    val limpio = texto.trim().removePrefix("$").trim().replace(".", "").replace(',', '.')
    if (limpio.isEmpty()) return null
    return runCatching { BigDecimal(limpio).movePointRight(2).longValueExact() }.getOrNull()
}

fun formatearHora(valor: TemporalAccessor): String = formatoHora.format(valor)

/** 02/10 */
fun formatearFechaCorta(valor: TemporalAccessor): String = formatoFechaCorta.format(valor)

/** 02/10/2026 */
fun formatearFechaNumerica(valor: TemporalAccessor): String = formatoFechaNumerica.format(valor)

/** Viernes 2 de octubre de 2026 */
fun formatearFechaLarga(valor: TemporalAccessor): String =
    formatoFechaLarga.format(valor).replaceFirstChar { it.titlecase(localeAr) }
