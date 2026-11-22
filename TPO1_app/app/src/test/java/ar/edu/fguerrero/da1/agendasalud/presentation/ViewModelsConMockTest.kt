package ar.edu.fguerrero.da1.agendasalud.presentation

import androidx.lifecycle.SavedStateHandle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.ItemAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.CompletarSesion
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.RegistrarCobro
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.ReservarTurno
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.DOMINGO
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.IMPORTE_SESION
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.LUNES
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.lunesA
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.turno
import ar.edu.fguerrero.da1.agendasalud.mock.MockRepository
import ar.edu.fguerrero.da1.agendasalud.mock.ReglaDispatcherPrincipal
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Aviso
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstadoUi
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.datosOrNull
import ar.edu.fguerrero.da1.agendasalud.presentation.pacientes.PacientesViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.CobrosViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.AgendaDiaViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * ViewModels de las pantallas Agenda/Hoy, Pacientes y Cobros con el MockRepository en lugar de Room.
 * Verifican lo que ve la pantalla (EstadoUi, avisos) ante cada acción del usuario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsConMockTest {

    @get:Rule
    val main = ReglaDispatcherPrincipal()

    private lateinit var repo: MockRepository
    private var ana = 0L

    @Before
    fun preparar() {
        repo = MockRepository()
        ana = repo.agregarPaciente("Ana", "1155550000")
    }

    // ---------- Agenda del día ----------

    private fun agenda(dia: LocalDate = LUNES) = AgendaDiaViewModel(
        turnos = repo.turnos,
        bloqueos = repo.bloqueos,
        pacientes = repo.pacientes,
        cobros = repo.cobros,
        preferencias = repo.preferencias,
        reservarTurno = ReservarTurno(repo.pacientes, repo.turnos),
        completarSesion = CompletarSesion(repo.turnos),
        registrarCobro = RegistrarCobro(repo.cobros),
        estadoGuardado = SavedStateHandle(mapOf("fecha" to dia)),
    )

    @Test
    fun vm01_agendaMuestraElTurnoReservadoEnElDia() = runTest {
        val vm = agenda()
        val estado = observar(vm.uiState)
        var error: ErrorDominio? = ErrorDominio.ErrorServidor

        vm.reservar(ana, lunesA(10), 60, IMPORTE_SESION) { error = it }

        assertNull(error)
        val items = estado.value.datosOrNull()!!.items
        assertTrue(items.any { it is ItemAgenda.Ocupado && it.turno.nombrePaciente == "Ana" })
        assertEquals(Aviso.Exito(R.string.aviso_turno_guardado), vm.avisos.first())
    }

    @Test
    fun vm02_reservarGuardaLosValoresPredeterminados() = runTest {
        val vm = agenda()
        observar(vm.uiState)
        vm.reservar(ana, lunesA(10), 45, 15_000_00L) {}
        val datos = vm.uiState.value.datosOrNull()!!
        assertEquals(45, datos.duracionPredeterminada)
        assertEquals(15_000_00L, datos.importePredeterminado)
    }

    @Test
    fun vm03_horarioOcupadoSeInformaAlDialogo() = runTest {
        repo.agregarTurno(turno(ana, inicio = lunesA(10)))
        val vm = agenda()
        var error: ErrorDominio? = null
        vm.reservar(ana, lunesA(10, 30), 60, IMPORTE_SESION) { error = it }
        assertEquals(ErrorDominio.HorarioOcupado, error)
    }

    @Test
    fun vm04_completarAbreElCobroYCobrarParcialAvisaElRestante() = runTest {
        val id = repo.agregarTurno(turno(ana))
        val vm = agenda()
        observar(vm.uiState)

        vm.completar(id)
        assertEquals(Aviso.Exito(R.string.aviso_sesion_completada), vm.avisos.first())
        assertEquals(IMPORTE_SESION, vm.cobroEnCurso.value?.pendienteCentavos)

        vm.registrarCobro(8_000_00L, LUNES, MedioCobro.EFECTIVO) {}
        assertNull(vm.cobroEnCurso.value)
        assertEquals(AgendaDiaViewModel.avisoCobro(12_000_00L), vm.avisos.first())
        assertEquals(12_000_00L, vm.uiState.value.datosOrNull()!!.saldosDelDia.single().saldoCentavos)
    }

    @Test
    fun vm05_reprogramarArmaElBorradorDeWhatsApp() = runTest {
        val id = repo.agregarTurno(turno(ana, inicio = lunesA(10)))
        val vm = agenda()
        vm.reprogramar(id, lunesA(15)) {}
        val borrador = vm.borradorWhatsApp.value!!
        assertEquals("1155550000", borrador.telefono)
        assertTrue(borrador.texto.contains("Ana") && borrador.texto.contains("15:00"))
    }

    @Test
    fun vm06_domingoSeMarcaComoDiaSinAtencion() = runTest {
        val vm = agenda(DOMINGO)
        val estado = observar(vm.uiState)
        assertFalse(estado.value.datosOrNull()!!.diaDeAtencion)
    }

    @Test
    fun vm07_fallaDeLecturaMuestraErrorYReintentarRecupera() = runTest {
        repo.fallarLecturas = true
        val vm = agenda()
        val estado = observar(vm.uiState)
        assertEquals(EstadoUi.Error, estado.value)

        repo.fallarLecturas = false
        vm.reintentar()
        assertTrue(estado.value is EstadoUi.Contenido)
    }

    @Test
    fun vm08_fallaDeGuardadoSeMuestraEnElSnackbar() = runTest {
        val id = repo.agregarTurno(turno(ana))
        repo.fallarEscrituras = true
        val vm = agenda()
        vm.cancelar(id, null)
        assertEquals(Aviso.Error(ErrorDominio.ErrorGuardado), vm.avisos.first())
    }

    // ---------- Pacientes ----------

    private fun pacientes() = PacientesViewModel(repo.pacientes, repo.turnos, repo.cobros, SavedStateHandle())

    @Test
    fun vm09_busquedaFiltraDespuesDelDebounce() = runTest {
        repo.agregarPaciente("Bruno")
        val vm = pacientes()
        val estado = observar(vm.uiState)
        assertEquals(2, estado.value.datosOrNull()!!.size)

        vm.buscar("bru")
        advanceTimeBy(100)
        assertEquals("Todavía no consulta", 2, estado.value.datosOrNull()!!.size)
        advanceUntilIdle()
        assertEquals(listOf("Bruno"), estado.value.datosOrNull()!!.map { it.paciente.nombre })
    }

    @Test
    fun vm10_listaMuestraElSaldoPendienteDeCadaPaciente() = runTest {
        val turnoId = repo.agregarTurno(turno(ana))
        repo.turnos.completar(turnoId)
        val vm = pacientes()
        val estado = observar(vm.uiState)
        assertEquals(IMPORTE_SESION, estado.value.datosOrNull()!!.single().saldoCentavos)
    }

    @Test
    fun vm11_desactivarCierraLaFichaYLoSacaDeLaLista() = runTest {
        val vm = pacientes()
        val estado = observar(vm.uiState)
        vm.abrirFicha(ana)
        vm.desactivar(ana)
        assertNull(vm.fichaAbierta.value)
        assertTrue(estado.value.datosOrNull()!!.isEmpty())
        assertEquals(Aviso.Exito(R.string.aviso_paciente_desactivado), vm.avisos.first())
    }

    @Test
    fun vm12_guardarPacienteSinNombreDevuelveElError() = runTest {
        val vm = pacientes()
        var error: ErrorDominio? = null
        vm.guardar(Paciente(nombre = "")) { error = it }
        assertEquals(ErrorDominio.NombreInvalido, error)
    }

    // ---------- Cobros ----------

    private fun cobros() = CobrosViewModel(repo.cobros, RegistrarCobro(repo.cobros), SavedStateHandle())

    @Test
    fun vm13_filtroPendientesOcultaLosCargosSaldados() = runTest {
        val saldado = repo.turnos.completar(repo.agregarTurno(turno(ana, inicio = lunesA(10))))
        repo.turnos.completar(repo.agregarTurno(turno(ana, inicio = lunesA(11))))
        val cargo = (saldado as Resultado.Exito).valor
        repo.cobros.registrar(cargo.id, IMPORTE_SESION, LUNES, MedioCobro.EFECTIVO)

        val vm = cobros()
        val estado = observar(vm.uiState)
        assertEquals(1, estado.value.datosOrNull()!!.cargos.size)
        assertEquals(IMPORTE_SESION, estado.value.datosOrNull()!!.totalPendienteCentavos)

        vm.filtrarPendientes(false)
        assertEquals(2, estado.value.datosOrNull()!!.cargos.size)
    }

    @Test
    fun vm14_cobroMayorAlPendienteNoCierraElDialogo() = runTest {
        repo.turnos.completar(repo.agregarTurno(turno(ana)))
        val vm = cobros()
        val estado = observar(vm.uiState)
        vm.abrirCobro(estado.value.datosOrNull()!!.cargos.single())

        var error: ErrorDominio? = null
        vm.registrarCobro(IMPORTE_SESION * 2, LUNES, MedioCobro.EFECTIVO) { error = it }

        assertEquals(ErrorDominio.MontoSuperaPendiente, error)
        assertTrue("El diálogo sigue abierto", vm.cobroEnCurso.value != null)
    }

    /** Suscribe la pantalla al StateFlow (WhileSubscribed solo consulta mientras alguien observa). */
    private fun <T> TestScope.observar(flujo: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flujo.collect {} }
        return flujo
    }
}
