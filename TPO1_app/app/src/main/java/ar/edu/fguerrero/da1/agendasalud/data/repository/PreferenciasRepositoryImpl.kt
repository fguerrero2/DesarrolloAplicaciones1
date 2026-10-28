package ar.edu.fguerrero.da1.agendasalud.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PreferenciasRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencias by preferencesDataStore(name = "preferencias")

/** Solo preferencias no sensibles; nunca datos de pacientes ni claves. */
class PreferenciasRepositoryImpl(private val context: Context) : PreferenciasRepository {

    override val duracionPredeterminadaMinutos: Flow<Int> =
        context.preferencias.data.map { it[DURACION] ?: DURACION_DEFECTO }

    override val importePredeterminadoCentavos: Flow<Long> =
        context.preferencias.data.map { it[IMPORTE] ?: 0L }

    override suspend fun guardarDuracionPredeterminada(minutos: Int) {
        context.preferencias.edit { it[DURACION] = minutos }
    }

    override suspend fun guardarImportePredeterminado(centavos: Long) {
        context.preferencias.edit { it[IMPORTE] = centavos }
    }

    private companion object {
        val DURACION = intPreferencesKey("duracion_minutos")
        val IMPORTE = longPreferencesKey("importe_centavos")
        const val DURACION_DEFECTO = 60
    }
}
