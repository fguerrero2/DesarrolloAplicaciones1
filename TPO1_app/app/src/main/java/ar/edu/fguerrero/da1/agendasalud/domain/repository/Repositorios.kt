package ar.edu.fguerrero.da1.agendasalud.domain.repository

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoSesion
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface PacienteRepository {
    fun observar(busqueda: String = "", incluirInactivos: Boolean = false): Flow<List<Paciente>>
    suspend fun obtener(id: Long): Paciente?
    suspend fun guardar(paciente: Paciente): Resultado<Long>
    suspend fun desactivar(id: Long): Resultado<Unit>
}

interface TurnoRepository {
    fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<TurnoConPaciente>>
    fun observarDePaciente(pacienteId: Long): Flow<List<Turno>>
    suspend fun obtener(id: Long): TurnoConPaciente?

    /** Verifica superposición y bloqueos en la misma transacción que inserta. */
    suspend fun reservar(turno: Turno): Resultado<Long>
    suspend fun reprogramar(turnoId: Long, nuevoInicio: LocalDateTime): Resultado<Unit>
    suspend fun cancelar(turnoId: Long, motivo: String?): Resultado<Unit>

    /** Marca el turno completado y crea su CargoSesion de forma atómica. */
    suspend fun completar(turnoId: Long): Resultado<CargoSesion>
    fun observarCancelacionesDelAnio(pacienteId: Long, anio: Int): Flow<Int>
}

interface BloqueoRepository {
    fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<BloqueoAgenda>>
    suspend fun crear(bloqueo: BloqueoAgenda): Resultado<Long>
    suspend fun eliminar(id: Long)
}

interface CobroRepository {
    fun observarPendientes(): Flow<List<CargoPendiente>>

    /** Todos los cargos, saldados o no, de la sesión más reciente a la más vieja. */
    fun observarCargos(): Flow<List<CargoPendiente>>

    /** Saldo pendiente por paciente (solo pacientes con algún cargo). */
    fun observarSaldos(): Flow<Map<Long, Long>>
    fun observarSaldoPaciente(pacienteId: Long): Flow<Long>
    suspend fun obtenerCargoDeTurno(turnoId: Long): CargoPendiente?

    /** Comprueba el pendiente e inserta el pago en una transacción. */
    suspend fun registrar(
        cargoId: Long,
        montoCentavos: Long,
        fecha: LocalDate,
        medio: MedioCobro,
    ): Resultado<Long>
}

/** Preferencias no sensibles (DataStore). */
interface PreferenciasRepository {
    val duracionPredeterminadaMinutos: Flow<Int>
    val importePredeterminadoCentavos: Flow<Long>
    suspend fun guardarDuracionPredeterminada(minutos: Int)
    suspend fun guardarImportePredeterminado(centavos: Long)
}
