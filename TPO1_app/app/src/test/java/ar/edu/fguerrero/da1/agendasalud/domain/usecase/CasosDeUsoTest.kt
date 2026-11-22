package ar.edu.fguerrero.da1.agendasalud.domain.usecase

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.DOMINGO
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.IMPORTE_SESION
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.LUNES
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.SABADO
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.lunesA
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.turno
import ar.edu.fguerrero.da1.agendasalud.mock.MockRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Casos de uso del dominio contra el MockRepository. Si alguien cambia una regla de negocio
 * (horario, superposición, saldos), estos tests lo detectan sin necesidad de emulador.
 */
class CasosDeUsoTest {

    private lateinit var repo: MockRepository
    private lateinit var reservar: ReservarTurno
    private lateinit var completar: CompletarSesion
    private lateinit var cobrar: RegistrarCobro
    private var ana = 0L

    @Before
    fun preparar() {
        repo = MockRepository()
        reservar = ReservarTurno(repo.pacientes, repo.turnos)
        completar = CompletarSesion(repo.turnos)
        cobrar = RegistrarCobro(repo.cobros)
        ana = repo.agregarPaciente("Ana", "1155550000")
    }

    // ---------- CP-01 a CP-08: Reservar turno (RF02) ----------

    @Test
    fun cp01_reservaEnHorarioLibreQuedaProgramada() = runTest {
        val id = exito(reservar(turno(ana)))
        assertEquals(EstadoTurno.PROGRAMADO, repo.turno(id)?.estado)
    }

    @Test
    fun cp02_pacienteInexistenteSeRechaza() = runTest {
        assertFallo(ErrorDominio.NoEncontrado, reservar(turno(pacienteId = 999)))
    }

    @Test
    fun cp03_pacienteInactivoSeRechaza() = runTest {
        val inactivo = repo.agregarPaciente("Beto", activo = false)
        assertFallo(ErrorDominio.PacienteInactivo, reservar(turno(inactivo)))
    }

    @Test
    fun cp04_duracionCeroOImporteNegativoSeRechazan() = runTest {
        assertFallo(ErrorDominio.IntervaloInvalido, reservar(turno(ana, duracionMinutos = 0)))
        assertFallo(ErrorDominio.MontoNoPositivo, reservar(turno(ana, importeCentavos = -1)))
        assertTrue("No debe llegar al repositorio", "turnos.reservar" !in repo.operaciones)
    }

    @Test
    fun cp05_horarioSuperpuestoSeRechaza() = runTest {
        exito(reservar(turno(ana, inicio = lunesA(10))))
        assertFallo(ErrorDominio.HorarioOcupado, reservar(turno(ana, inicio = lunesA(10, 30))))
    }

    @Test
    fun cp06_turnosContiguosNoSeSuperponen() = runTest {
        exito(reservar(turno(ana, inicio = lunesA(10))))
        exito(reservar(turno(ana, inicio = lunesA(11))))
    }

    @Test
    fun cp07_fueraDeHorarioDeAtencionSeRechaza() = runTest {
        assertFallo(ErrorDominio.FueraDeHorario, reservar(turno(ana, inicio = lunesA(8))))
        assertFallo(ErrorDominio.FueraDeHorario, reservar(turno(ana, inicio = lunesA(17, 30))))
        assertFallo(ErrorDominio.FueraDeHorario, reservar(turno(ana, inicio = SABADO.atTime(11, 30))))
        assertFallo(ErrorDominio.FueraDeHorario, reservar(turno(ana, inicio = DOMINGO.atTime(10, 0))))
    }

    @Test
    fun cp08_periodoBloqueadoSeRechaza() = runTest {
        repo.agregarBloqueo(BloqueoAgenda(inicio = lunesA(9), fin = lunesA(13), motivo = "Curso"))
        assertFallo(ErrorDominio.PeriodoBloqueado, reservar(turno(ana, inicio = lunesA(12))))
        exito(reservar(turno(ana, inicio = lunesA(13))))
    }

    // ---------- CP-09 a CP-12: Cancelar, reprogramar y bloquear (RF03, RF05) ----------

    @Test
    fun cp09_turnoCanceladoLiberaElHorario() = runTest {
        val id = exito(reservar(turno(ana, inicio = lunesA(10))))
        exito(repo.turnos.cancelar(id, "  "))
        assertEquals(null, repo.turno(id)?.motivoCancelacion)
        exito(reservar(turno(ana, inicio = lunesA(10))))
    }

    @Test
    fun cp10_reprogramarConservaLaFechaOriginal() = runTest {
        val id = exito(reservar(turno(ana, inicio = lunesA(10))))
        exito(repo.turnos.reprogramar(id, lunesA(15)))
        exito(repo.turnos.reprogramar(id, lunesA(16)))
        assertEquals(lunesA(16), repo.turno(id)?.inicio)
        assertEquals(lunesA(10), repo.turno(id)?.fechaOriginal)
    }

    @Test
    fun cp11_reprogramarSobreSuPropioHorarioEsValido() = runTest {
        val id = exito(reservar(turno(ana, inicio = lunesA(10))))
        exito(repo.turnos.reprogramar(id, lunesA(10, 30)))
    }

    @Test
    fun cp12_bloqueoNoPuedePisarTurnosProgramados() = runTest {
        val id = exito(reservar(turno(ana, inicio = lunesA(10))))
        val bloqueo = BloqueoAgenda(inicio = lunesA(9), fin = lunesA(12), motivo = "Trámite")
        assertFallo(ErrorDominio.TurnosEnPeriodo, repo.bloqueos.crear(bloqueo))
        exito(repo.turnos.cancelar(id, null))
        exito(repo.bloqueos.crear(bloqueo))
    }

    // ---------- CP-13 a CP-18: Completar sesión y cobrar (RF04) ----------

    @Test
    fun cp13_completarGeneraUnCargoPorElImporteDelTurno() = runTest {
        val id = exito(reservar(turno(ana)))
        val cargo = exito(completar(id))
        assertEquals(IMPORTE_SESION, cargo.importeCentavos)
        assertEquals(EstadoTurno.COMPLETADO, repo.turno(id)?.estado)
        assertEquals(IMPORTE_SESION, repo.cobros.observarSaldoPaciente(ana).first())
    }

    @Test
    fun cp14_completarDosVecesNoDuplicaElCargo() = runTest {
        val id = exito(reservar(turno(ana)))
        val primero = exito(completar(id))
        val segundo = exito(completar(id))
        assertEquals(primero, segundo)
        assertEquals(1, repo.cargosRegistrados().size)
    }

    @Test
    fun cp15_noSePuedeCompletarUnTurnoCancelado() = runTest {
        val id = exito(reservar(turno(ana)))
        exito(repo.turnos.cancelar(id, "Enfermedad"))
        assertFallo(ErrorDominio.EstadoNoPermitido, completar(id))
    }

    @Test
    fun cp16_cobroParcialYLuegoTotalSaldanLaDeuda() = runTest {
        val cargo = exito(completar(exito(reservar(turno(ana)))))
        exito(cobrar(cargo.id, 8_000_00L, MedioCobro.EFECTIVO, LUNES))
        assertEquals(12_000_00L, repo.cobros.observarSaldoPaciente(ana).first())
        exito(cobrar(cargo.id, 12_000_00L, MedioCobro.MERCADO_PAGO, LUNES))
        assertEquals(0L, repo.cobros.observarSaldoPaciente(ana).first())
        assertTrue(repo.cobros.observarPendientes().first().isEmpty())
    }

    @Test
    fun cp17_cobroMayorAlPendienteSeRechaza() = runTest {
        val cargo = exito(completar(exito(reservar(turno(ana)))))
        assertFallo(ErrorDominio.MontoSuperaPendiente, cobrar(cargo.id, IMPORTE_SESION + 1, MedioCobro.EFECTIVO))
        assertTrue(repo.cobrosRegistrados().isEmpty())
    }

    @Test
    fun cp18_montoCeroSeRechazaSinLlegarAlRepositorio() = runTest {
        val cargo = exito(completar(exito(reservar(turno(ana)))))
        assertFallo(ErrorDominio.MontoNoPositivo, cobrar(cargo.id, 0, MedioCobro.EFECTIVO))
        assertTrue("cobros.registrar" !in repo.operaciones)
    }

    // ---------- CP-19 y CP-20: Pacientes ----------

    @Test
    fun cp19_pacienteSinNombreSeRechaza() = runTest {
        assertFallo(ErrorDominio.NombreInvalido, repo.pacientes.guardar(Paciente(nombre = "  ")))
    }

    @Test
    fun cp20_pacienteDesactivadoNoApareceEnLaListaDeActivos() = runTest {
        exito(repo.pacientes.desactivar(ana))
        assertTrue(repo.pacientes.observar().first().isEmpty())
        assertEquals(1, repo.pacientes.observar(incluirInactivos = true).first().size)
    }

    // ---------- CP-21: Falla de guardado ----------

    @Test
    fun cp21_fallaDeGuardadoSePropagaComoErrorDeDominio() = runTest {
        repo.fallarEscrituras = true
        assertFallo(ErrorDominio.ErrorGuardado, reservar(turno(ana)))
    }

    private fun <T> exito(resultado: Resultado<T>): T {
        assertTrue("Se esperaba éxito y llegó $resultado", resultado is Resultado.Exito)
        return (resultado as Resultado.Exito).valor
    }

    private fun assertFallo(esperado: ErrorDominio, resultado: Resultado<*>) {
        assertEquals(Resultado.Fallo(esperado), resultado)
    }
}
