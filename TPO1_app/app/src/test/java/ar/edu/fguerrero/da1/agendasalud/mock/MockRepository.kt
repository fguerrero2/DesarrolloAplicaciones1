package ar.edu.fguerrero.da1.agendasalud.mock

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoSesion
import ar.edu.fguerrero.da1.agendasalud.domain.model.Cobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import ar.edu.fguerrero.da1.agendasalud.domain.repository.BloqueoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PreferenciasRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.rules.ReglasAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.rules.ReglasCobro
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Repositorio en memoria que reemplaza a Room en los tests de unidad (JVM, sin emulador).
 *
 * Implementa las mismas interfaces del dominio que usan los ViewModels y casos de uso, y aplica las
 * mismas reglas que los repositorios reales (ReglasAgenda, ReglasCobro, idempotencia de completar).
 * Un único estado compartido hace que, como en la base real, completar un turno genere un cargo que
 * se ve enseguida en cobros y saldos.
 *
 * Para simular fallas:
 * - [fallarEscrituras]: toda escritura devuelve `Resultado.Fallo(ErrorDominio.ErrorGuardado)`.
 * - [fallarLecturas]: los Flow lanzan una excepción (como un error de SQLCipher al leer).
 *
 * [operaciones] registra cada escritura recibida, para verificar qué llamó la capa de arriba.
 */
class MockRepository {

    private data class Estado(
        val pacientes: Map<Long, Paciente> = emptyMap(),
        val turnos: Map<Long, Turno> = emptyMap(),
        val bloqueos: Map<Long, BloqueoAgenda> = emptyMap(),
        val cargos: Map<Long, CargoSesion> = emptyMap(),
        val cobros: Map<Long, Cobro> = emptyMap(),
        val ultimoId: Long = 0,
    )

    private val estado = MutableStateFlow(Estado())
    private val duracion = MutableStateFlow(60)
    private val importe = MutableStateFlow(0L)
    private val guia = MutableStateFlow(false)

    var fallarEscrituras = false
    var fallarLecturas = false
    val operaciones = mutableListOf<String>()

    // ---------- Carga rápida de datos para preparar un caso de prueba ----------

    fun agregarPaciente(nombre: String, telefono: String? = null, activo: Boolean = true): Long =
        insertar { id, e -> e.copy(pacientes = e.pacientes + (id to Paciente(id, nombre, telefono, activo))) }

    fun agregarTurno(turno: Turno): Long =
        insertar { id, e -> e.copy(turnos = e.turnos + (id to turno.copy(id = id))) }

    fun agregarBloqueo(bloqueo: BloqueoAgenda): Long =
        insertar { id, e -> e.copy(bloqueos = e.bloqueos + (id to bloqueo.copy(id = id))) }

    fun turno(id: Long): Turno? = estado.value.turnos[id]
    fun cobrosRegistrados(): List<Cobro> = estado.value.cobros.values.toList()
    fun cargosRegistrados(): List<CargoSesion> = estado.value.cargos.values.toList()
    fun bloqueosRegistrados(): List<BloqueoAgenda> = estado.value.bloqueos.values.toList()

    // ---------- Implementaciones de las interfaces del dominio ----------

    val pacientes: PacienteRepository = object : PacienteRepository {
        override fun observar(busqueda: String, incluirInactivos: Boolean): Flow<List<Paciente>> =
            leer { e ->
                e.pacientes.values
                    .filter { incluirInactivos || it.activo }
                    .filter { busqueda.isBlank() || it.nombre.contains(busqueda.trim(), ignoreCase = true) }
                    .sortedBy { it.nombre }
            }

        override suspend fun obtener(id: Long): Paciente? = estado.value.pacientes[id]

        override suspend fun guardar(paciente: Paciente): Resultado<Long> = escribir("pacientes.guardar") {
            if (paciente.nombre.isBlank()) return@escribir Resultado.Fallo(ErrorDominio.NombreInvalido)
            if (paciente.id == 0L) {
                Resultado.Exito(insertar { id, e -> e.copy(pacientes = e.pacientes + (id to paciente.copy(id = id))) })
            } else {
                if (paciente.id !in estado.value.pacientes) return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
                estado.update { it.copy(pacientes = it.pacientes + (paciente.id to paciente)) }
                Resultado.Exito(paciente.id)
            }
        }

        override suspend fun desactivar(id: Long): Resultado<Unit> = escribir("pacientes.desactivar") {
            val actual = estado.value.pacientes[id] ?: return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
            estado.update { it.copy(pacientes = it.pacientes + (id to actual.copy(activo = false))) }
            Resultado.Exito(Unit)
        }
    }

    val turnos: TurnoRepository = object : TurnoRepository {
        override fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<TurnoConPaciente>> =
            leer { e ->
                e.turnos.values
                    .filter { !it.inicio.isBefore(desde) && it.inicio.isBefore(hasta) }
                    .sortedBy { it.inicio }
                    .map { TurnoConPaciente(it, e.pacientes[it.pacienteId]?.nombre.orEmpty()) }
            }

        override fun observarDePaciente(pacienteId: Long): Flow<List<Turno>> =
            leer { e -> e.turnos.values.filter { it.pacienteId == pacienteId }.sortedByDescending { it.inicio } }

        override suspend fun obtener(id: Long): TurnoConPaciente? {
            val e = estado.value
            val turno = e.turnos[id] ?: return null
            return TurnoConPaciente(turno, e.pacientes[turno.pacienteId]?.nombre.orEmpty())
        }

        override suspend fun reservar(turno: Turno): Resultado<Long> = escribir("turnos.reservar") {
            validarHorario(turno, excluirTurnoId = null)?.let { return@escribir Resultado.Fallo(it) }
            Resultado.Exito(agregarTurno(turno.copy(id = 0, estado = EstadoTurno.PROGRAMADO)))
        }

        override suspend fun reprogramar(turnoId: Long, nuevoInicio: LocalDateTime): Resultado<Unit> =
            escribir("turnos.reprogramar") {
                val actual = programado(turnoId) ?: return@escribir falloEstado(turnoId)
                val nuevo = actual.copy(inicio = nuevoInicio, fechaOriginal = actual.fechaOriginal ?: actual.inicio)
                validarHorario(nuevo, excluirTurnoId = turnoId)?.let { return@escribir Resultado.Fallo(it) }
                guardarTurno(nuevo)
                Resultado.Exito(Unit)
            }

        override suspend fun cancelar(turnoId: Long, motivo: String?): Resultado<Unit> = escribir("turnos.cancelar") {
            val actual = programado(turnoId) ?: return@escribir falloEstado(turnoId)
            guardarTurno(actual.copy(estado = EstadoTurno.CANCELADO, motivoCancelacion = motivo?.trim()?.ifEmpty { null }))
            Resultado.Exito(Unit)
        }

        override suspend fun completar(turnoId: Long): Resultado<CargoSesion> = escribir("turnos.completar") {
            val actual = estado.value.turnos[turnoId] ?: return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
            // Igual que el repositorio real: reintentar no duplica el cargo
            estado.value.cargos.values.firstOrNull { it.turnoId == turnoId }?.let { return@escribir Resultado.Exito(it) }
            if (actual.estado != EstadoTurno.PROGRAMADO) return@escribir Resultado.Fallo(ErrorDominio.EstadoNoPermitido)
            guardarTurno(actual.copy(estado = EstadoTurno.COMPLETADO))
            var cargo: CargoSesion? = null
            insertar { id, e ->
                cargo = CargoSesion(id, turnoId, actual.importeCentavos)
                e.copy(cargos = e.cargos + (id to cargo!!))
            }
            Resultado.Exito(cargo!!)
        }

        override suspend fun deshacerCompletar(turnoId: Long): Resultado<Unit> = escribir("turnos.deshacerCompletar") {
            val actual = estado.value.turnos[turnoId] ?: return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
            if (actual.estado != EstadoTurno.COMPLETADO) return@escribir Resultado.Fallo(ErrorDominio.EstadoNoPermitido)
            val cargo = estado.value.cargos.values.firstOrNull { it.turnoId == turnoId }
            if (cargo != null && estado.value.cobros.values.any { it.cargoId == cargo.id }) {
                return@escribir Resultado.Fallo(ErrorDominio.TieneCobros)
            }
            estado.update { e ->
                e.copy(
                    turnos = e.turnos + (turnoId to actual.copy(estado = EstadoTurno.PROGRAMADO)),
                    cargos = if (cargo != null) e.cargos - cargo.id else e.cargos,
                )
            }
            Resultado.Exito(Unit)
        }

        override suspend fun reactivar(turnoId: Long): Resultado<Unit> = escribir("turnos.reactivar") {
            val actual = estado.value.turnos[turnoId] ?: return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
            if (actual.estado != EstadoTurno.CANCELADO) return@escribir Resultado.Fallo(ErrorDominio.EstadoNoPermitido)
            validarHorario(actual, excluirTurnoId = turnoId)?.let { return@escribir Resultado.Fallo(it) }
            guardarTurno(actual.copy(estado = EstadoTurno.PROGRAMADO, motivoCancelacion = null))
            Resultado.Exito(Unit)
        }

        override fun observarCancelacionesDelAnio(pacienteId: Long, anio: Int): Flow<Int> =
            leer { e ->
                e.turnos.values.count {
                    it.pacienteId == pacienteId && it.estado == EstadoTurno.CANCELADO && it.inicio.year == anio
                }
            }

        private fun programado(id: Long): Turno? =
            estado.value.turnos[id]?.takeIf { it.estado == EstadoTurno.PROGRAMADO }

        private fun falloEstado(id: Long): Resultado.Fallo =
            Resultado.Fallo(if (id in estado.value.turnos) ErrorDominio.EstadoNoPermitido else ErrorDominio.NoEncontrado)

        private fun guardarTurno(turno: Turno) = estado.update { it.copy(turnos = it.turnos + (turno.id to turno)) }

        private fun validarHorario(turno: Turno, excluirTurnoId: Long?): ErrorDominio? {
            val e = estado.value
            return ReglasAgenda.validarHorario(turno.intervalo, e.turnos.values.toList(), e.bloqueos.values.toList(), excluirTurnoId)
        }
    }

    val bloqueos: BloqueoRepository = object : BloqueoRepository {
        override fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<BloqueoAgenda>> =
            leer { e -> e.bloqueos.values.filter { it.inicio.isBefore(hasta) && desde.isBefore(it.fin) }.sortedBy { it.inicio } }

        override suspend fun crear(bloqueo: BloqueoAgenda): Resultado<Long> = escribir("bloqueos.crear") {
            ReglasAgenda.validarBloqueo(bloqueo.intervalo, estado.value.turnos.values.toList())
                ?.let { return@escribir Resultado.Fallo(it) }
            Resultado.Exito(agregarBloqueo(bloqueo.copy(id = 0)))
        }

        override suspend fun eliminar(id: Long) {
            operaciones += "bloqueos.eliminar"
            if (fallarEscrituras) throw IllegalStateException("Falla simulada")
            estado.update { it.copy(bloqueos = it.bloqueos - id) }
        }
    }

    val cobros: CobroRepository = object : CobroRepository {
        override fun observarPendientes(): Flow<List<CargoPendiente>> =
            observarCargos().map { lista -> lista.filter { it.pendienteCentavos > 0 }.sortedBy { it.fechaSesion } }

        override fun observarCargos(): Flow<List<CargoPendiente>> =
            leer { e -> e.cargos.values.mapNotNull { cargoPendiente(e, it) }.sortedByDescending { it.fechaSesion } }

        override fun observarSaldos(): Flow<Map<Long, Long>> =
            observarCargos().map { lista ->
                lista.groupBy { it.pacienteId }.mapValues { (_, cargos) -> cargos.sumOf { it.pendienteCentavos } }
            }

        override fun observarSaldoPaciente(pacienteId: Long): Flow<Long> =
            observarSaldos().map { it[pacienteId] ?: 0L }

        override suspend fun obtenerCargoDeTurno(turnoId: Long): CargoPendiente? {
            val e = estado.value
            return e.cargos.values.firstOrNull { it.turnoId == turnoId }?.let { cargoPendiente(e, it) }
        }

        override suspend fun ultimoCobro(cargoId: Long): Cobro? =
            estado.value.cobros.values.filter { it.cargoId == cargoId }.maxByOrNull { it.id }

        override suspend fun anular(cobroId: Long): Resultado<Unit> = escribir("cobros.anular") {
            if (cobroId !in estado.value.cobros) return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
            estado.update { it.copy(cobros = it.cobros - cobroId) }
            Resultado.Exito(Unit)
        }

        override suspend fun registrar(
            cargoId: Long,
            montoCentavos: Long,
            fecha: LocalDate,
            medio: MedioCobro,
        ): Resultado<Long> = escribir("cobros.registrar") {
            val e = estado.value
            val cargo = e.cargos[cargoId] ?: return@escribir Resultado.Fallo(ErrorDominio.NoEncontrado)
            val pendiente = ReglasCobro.pendiente(
                cargo.importeCentavos,
                e.cobros.values.filter { it.cargoId == cargoId }.map { it.importeCentavos },
            )
            ReglasCobro.validarCobro(montoCentavos, pendiente)?.let { return@escribir Resultado.Fallo(it) }
            Resultado.Exito(insertar { id, s -> s.copy(cobros = s.cobros + (id to Cobro(id, cargoId, montoCentavos, fecha, medio))) })
        }

        private fun cargoPendiente(e: Estado, cargo: CargoSesion): CargoPendiente? {
            val turno = e.turnos[cargo.turnoId] ?: return null
            return CargoPendiente(
                cargoId = cargo.id,
                turnoId = turno.id,
                pacienteId = turno.pacienteId,
                nombrePaciente = e.pacientes[turno.pacienteId]?.nombre.orEmpty(),
                fechaSesion = turno.inicio,
                importeCentavos = cargo.importeCentavos,
                cobradoCentavos = e.cobros.values.filter { it.cargoId == cargo.id }.sumOf { it.importeCentavos },
            )
        }
    }

    val preferencias: PreferenciasRepository = object : PreferenciasRepository {
        override val duracionPredeterminadaMinutos: Flow<Int> = duracion
        override val importePredeterminadoCentavos: Flow<Long> = importe
        override suspend fun guardarDuracionPredeterminada(minutos: Int) {
            duracion.value = minutos
        }

        override suspend fun guardarImportePredeterminado(centavos: Long) {
            importe.value = centavos
        }

        override val guiaVista: Flow<Boolean> = guia

        override suspend fun marcarGuiaVista() {
            guia.value = true
        }
    }

    // ---------- Utilidades internas ----------

    /** Reserva un id nuevo y aplica el cambio en un único paso (como una transacción). */
    private fun insertar(cambio: (Long, Estado) -> Estado): Long {
        var id = 0L
        estado.update { e ->
            id = e.ultimoId + 1
            cambio(id, e).copy(ultimoId = id)
        }
        return id
    }

    private inline fun <T> escribir(nombre: String, bloque: () -> Resultado<T>): Resultado<T> {
        operaciones += nombre
        if (fallarEscrituras) return Resultado.Fallo(ErrorDominio.ErrorGuardado)
        return bloque()
    }

    /** Como un Flow de Room: emite el valor actual y vuelve a emitir con cada cambio. */
    private fun <T> leer(consulta: (Estado) -> T): Flow<T> = flow {
        if (fallarLecturas) throw IllegalStateException("Falla de lectura simulada")
        emitAll(estado.map(consulta).distinctUntilChanged())
    }
}
