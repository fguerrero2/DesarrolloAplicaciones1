package ar.edu.fguerrero.da1.agendasalud.data.repository

import androidx.room.withTransaction
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import kotlinx.coroutines.CancellationException

/** Error de negocio dentro de una transacción: aborta y deshace las escrituras previas. */
internal class FalloTransaccion(val error: ErrorDominio) : Exception()

/**
 * Ejecuta [bloque] en una transacción. Cualquier fallo deshace todo y se devuelve como
 * [Resultado.Fallo], nunca como éxito parcial (RNF07). No se registran datos del paciente.
 */
internal suspend fun <T> AgendaDatabase.transaccion(bloque: suspend () -> T): Resultado<T> =
    try {
        Resultado.Exito(withTransaction { bloque() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: FalloTransaccion) {
        Resultado.Fallo(e.error)
    } catch (e: Exception) {
        Resultado.Fallo(ErrorDominio.ErrorGuardado)
    }

internal fun fallar(error: ErrorDominio): Nothing = throw FalloTransaccion(error)
