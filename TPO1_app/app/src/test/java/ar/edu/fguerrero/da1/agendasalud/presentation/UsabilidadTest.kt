package ar.edu.fguerrero.da1.agendasalud.presentation

import androidx.lifecycle.SavedStateHandle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.CompletarSesion
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.RegistrarCobro
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.ReservarTurno
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.IMPORTE_SESION
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.LUNES
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.lunesA
import ar.edu.fguerrero.da1.agendasalud.mock.DatosPrueba.turno
import ar.edu.fguerrero.da1.agendasalud.mock.MockRepository
import ar.edu.fguerrero.da1.agendasalud.mock.ReglaDispatcherPrincipal
import ar.edu.fguerrero.da1.agendasalud.presentation.ayuda.AyudaViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.ayuda.ModoAyuda
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Aviso
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.datosOrNull
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.PedidosNavegacion
import ar.edu.fguerrero.da1.agendasalud.presentation.pacientes.PacientesViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.CobrosViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.AgendaDiaViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Mejoras de usabilidad según las heurísticas de Nielsen (U-01 a U-11): deshacer, anular cobros,
 * ayuda, primer paso sin pacientes. Usan el MockRepository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsabilidadTest {

    @get:Rule
    val main = ReglaDispatcherPrincipal()

    private lateinit var repo: MockRepository
    private var ana = 0L

    @Before
    fun preparar() {
        repo = MockRepository()
        ana = repo.agregarPaciente("Ana", "1155550000")
    }

    private fun agenda() = AgendaDiaViewModel(
        turnos = repo.turnos,
        bloqueos = repo.bloqueos,
        pacientes = repo.pacientes,
        cobros = repo.cobros,
        preferencias = repo.preferencias,
        reservarTurno = ReservarTurno(repo.pacientes, repo.turnos),
        completarSesion = CompletarSesion(repo.turnos),
        registrarCobro = RegistrarCobro(repo.cobros),
        estadoGuardado = SavedStateHandle(mapOf("fecha" to LUNES)),
    )

    private fun cobros() = CobrosViewModel(repo.cobros, RegistrarCobro(repo.cobros), SavedStateHandle())

    // ---------- H3: Control y libertad (deshacer) ----------

    @Test
    fun u01_completarPorErrorSeVuelveAProgramada() = runTest {
        val id = repo.agregarTurno(turno(ana))
        val vm = agenda()
        vm.completar(id)
        vm.avisos.first()
        vm.cerrarCobro()

        vm.deshacerCompletar(id)
        assertEquals(Aviso.Exito(R.string.aviso_sesion_reabierta), vm.avisos.first())
        assertEquals(EstadoTurno.PROGRAMADO, repo.turno(id)?.estado)
        assertTrue("Se borra el cargo generado", repo.cargosRegistrados().isEmpty())
    }

    @Test
    fun u02_noSeVuelveAProgramadaSiYaTieneCobros() = runTest {
        val id = repo.agregarTurno(turno(ana))
        val cargo = (repo.turnos.completar(id) as Resultado.Exito).valor
        repo.cobros.registrar(cargo.id, 1_000_00L, LUNES, MedioCobro.EFECTIVO)

        val vm = agenda()
        vm.deshacerCompletar(id)
        assertEquals(Aviso.Error(ErrorDominio.TieneCobros), vm.avisos.first())
        assertEquals(EstadoTurno.COMPLETADO, repo.turno(id)?.estado)
    }

    @Test
    fun u03_cancelarOfreceDeshacerYReactivaElTurno() = runTest {
        val id = repo.agregarTurno(turno(ana))
        val vm = agenda()
        vm.cancelar(id, "Enfermedad")

        val aviso = vm.avisos.first() as Aviso.Exito
        assertEquals(R.string.aviso_turno_cancelado, aviso.texto)
        assertNotNull("El aviso ofrece Deshacer", aviso.deshacer)

        aviso.deshacer!!.invoke()
        assertEquals(Aviso.Exito(R.string.aviso_turno_reactivado), vm.avisos.first())
        assertEquals(EstadoTurno.PROGRAMADO, repo.turno(id)?.estado)
        assertNull(repo.turno(id)?.motivoCancelacion)
    }

    @Test
    fun u04_noSeReactivaSiElHorarioSeOcupoMientrasTanto() = runTest {
        val id = repo.agregarTurno(turno(ana, inicio = lunesA(10)))
        repo.turnos.cancelar(id, null)
        repo.agregarTurno(turno(repo.agregarPaciente("Bruno"), inicio = lunesA(10)))
        assertEquals(Resultado.Fallo(ErrorDominio.HorarioOcupado), repo.turnos.reactivar(id))
    }

    @Test
    fun u05_cobroRegistradoOfreceDeshacerYLoAnula() = runTest {
        val id = repo.agregarTurno(turno(ana))
        val vm = agenda()
        vm.completar(id)
        vm.avisos.first()

        vm.registrarCobro(8_000_00L, LUNES, MedioCobro.EFECTIVO) {}
        val aviso = vm.avisos.first() as Aviso.Exito
        assertEquals(1, repo.cobrosRegistrados().size)

        aviso.deshacer!!.invoke()
        assertEquals(Aviso.Exito(R.string.aviso_cobro_anulado), vm.avisos.first())
        assertTrue(repo.cobrosRegistrados().isEmpty())
        assertEquals(IMPORTE_SESION, repo.cobros.observarSaldoPaciente(ana).first())
    }

    @Test
    fun u06_quitarBloqueoOfreceDeshacerYLoRestaura() = runTest {
        val bloqueo = BloqueoAgenda(inicio = lunesA(9), fin = lunesA(12), motivo = "Curso")
        val id = repo.agregarBloqueo(bloqueo)
        val vm = agenda()
        vm.eliminarBloqueo(bloqueo.copy(id = id))
        val aviso = vm.avisos.first() as Aviso.Exito
        assertTrue(repo.bloqueosRegistrados().isEmpty())

        aviso.deshacer!!.invoke()
        assertEquals(Aviso.Exito(R.string.aviso_bloqueo_restaurado), vm.avisos.first())
        assertEquals("Curso", repo.bloqueosRegistrados().single().motivo)
    }

    // ---------- H3 + H5: Anular un cobro mal cargado desde Cobros ----------

    @Test
    fun u07_anularPideConfirmacionConElUltimoCobro() = runTest {
        val cargo = (repo.turnos.completar(repo.agregarTurno(turno(ana))) as Resultado.Exito).valor
        repo.cobros.registrar(cargo.id, 5_000_00L, LUNES, MedioCobro.EFECTIVO)
        repo.cobros.registrar(cargo.id, 3_000_00L, LUNES, MedioCobro.MERCADO_PAGO)

        val vm = cobros()
        val estado = observar(vm.uiState)
        vm.pedirAnular(estado.value.datosOrNull()!!.cargos.single())

        val pedido = vm.anulacion.value!!
        assertEquals("Se anula el último", 3_000_00L, pedido.cobro.importeCentavos)
        assertEquals(MedioCobro.MERCADO_PAGO, pedido.cobro.medio)
        assertEquals("Nada se anula sin confirmar", 2, repo.cobrosRegistrados().size)

        vm.confirmarAnulacion()
        assertNull(vm.anulacion.value)
        assertEquals(Aviso.Exito(R.string.aviso_cobro_anulado), vm.avisos.first())
        assertEquals(IMPORTE_SESION - 5_000_00L, estado.value.datosOrNull()!!.totalPendienteCentavos)
    }

    @Test
    fun u08_volverEnLaConfirmacionNoAnulaNada() = runTest {
        val cargo = (repo.turnos.completar(repo.agregarTurno(turno(ana))) as Resultado.Exito).valor
        repo.cobros.registrar(cargo.id, 5_000_00L, LUNES, MedioCobro.EFECTIVO)
        val vm = cobros()
        val estado = observar(vm.uiState)
        vm.pedirAnular(estado.value.datosOrNull()!!.cargos.single())
        vm.cerrarAnulacion()
        assertNull(vm.anulacion.value)
        assertEquals(1, repo.cobrosRegistrados().size)
    }

    // ---------- H10: Ayuda y primer paso ----------

    @Test
    fun u09_laGuiaSeMuestraSoloLaPrimeraVez() = runTest {
        val vm = AyudaViewModel(repo.preferencias)
        val modo = observar(vm.modo)
        assertEquals(ModoAyuda.GUIA, modo.value)

        vm.cerrar()
        assertNull(modo.value)
        assertNull("Otra pantalla tampoco la vuelve a mostrar", observar(AyudaViewModel(repo.preferencias).modo).value)
    }

    @Test
    fun u10_laAyudaSeAbreDesdeElMenuCuandoSeQuiera() = runTest {
        repo.preferencias.marcarGuiaVista()
        val vm = AyudaViewModel(repo.preferencias)
        val modo = observar(vm.modo)
        assertNull(modo.value)
        vm.abrir()
        assertEquals(ModoAyuda.AYUDA, modo.value)
        vm.cerrar()
        assertNull(modo.value)
    }

    @Test
    fun u11_cargarPacienteDesdeOtraPantallaAbreElAlta() = runTest {
        val pedidos = PedidosNavegacion()
        val vm = PacientesViewModel(repo.pacientes, repo.turnos, repo.cobros, SavedStateHandle(), pedidos)
        assertFalse(vm.nuevoPacientePedido.value)

        pedidos.pedirNuevoPaciente()
        assertTrue(vm.nuevoPacientePedido.value)
        vm.nuevoPacienteAtendido()
        assertFalse("Se atiende una sola vez", vm.nuevoPacientePedido.value)
    }

    private fun <T> TestScope.observar(flujo: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flujo.collect {} }
        return flujo
    }
}
