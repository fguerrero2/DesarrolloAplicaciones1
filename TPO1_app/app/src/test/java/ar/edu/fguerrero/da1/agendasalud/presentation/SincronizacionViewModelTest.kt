package ar.edu.fguerrero.da1.agendasalud.presentation

import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoSincronizacion
import ar.edu.fguerrero.da1.agendasalud.domain.repository.SincronizacionRepository
import ar.edu.fguerrero.da1.agendasalud.mock.ReglaDispatcherPrincipal
import ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion.ResultadoSincronizacion
import ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion.SincronizacionViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Mensajes y estado que ve el usuario al pedir "Sincronizar ahora" (heurísticas de Nielsen). */
@OptIn(ExperimentalCoroutinesApi::class)
class SincronizacionViewModelTest {

    @get:Rule
    val main = ReglaDispatcherPrincipal()

    /** Repositorio falso: cada sincronización queda pendiente hasta que el test la termina. */
    private class SincronizacionFalsa : SincronizacionRepository {
        override val estado = MutableStateFlow<EstadoSincronizacion>(EstadoSincronizacion.NuncaSincronizado)
        var pedidos = 0
        var pendiente = CompletableDeferred<EstadoSincronizacion>()

        override suspend fun alAbrirLaApp() {}
        override suspend fun registrarCambiosLocales() = awaitCancellation()
        override suspend fun sincronizarAhora(): EstadoSincronizacion {
            pedidos++
            estado.value = EstadoSincronizacion.Sincronizando
            // Como el Sincronizador real: nunca deja el estado en Sincronizando al terminar
            return try {
                pendiente.await().also { estado.value = it }
            } catch (e: IllegalStateException) {
                estado.value = EstadoSincronizacion.Error
                throw e
            }
        }
    }

    private val repo = SincronizacionFalsa()

    private fun TestScope.crear(): SincronizacionViewModel {
        val vm = SincronizacionViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sincronizando.collect {} }
        return vm
    }

    @Test
    fun s01_muestraQueSincronizaYAvisaElExito() = runTest {
        val vm = crear()
        assertFalse(vm.sincronizando.value)

        vm.sincronizarAhora()
        assertTrue("Indicador visible mientras sincroniza", vm.sincronizando.value)

        repo.pendiente.complete(EstadoSincronizacion.AlDia(1L))
        assertFalse("El indicador desaparece al terminar", vm.sincronizando.value)
        assertEquals(ResultadoSincronizacion.Exito, vm.resultados.first())
    }

    @Test
    fun s02_sinConexionExplicaLaCausaYPermiteReintentar() = runTest {
        val vm = crear()
        vm.sincronizarAhora()
        repo.pendiente.complete(EstadoSincronizacion.SinConexion)
        assertEquals(ResultadoSincronizacion.Fallo(R.string.sync_fallo_sin_conexion, puedeReintentar = true), vm.resultados.first())
    }

    @Test
    fun s03_sesionVencidaNoOfreceReintentar() = runTest {
        val vm = crear()
        vm.sincronizarAhora()
        repo.pendiente.complete(EstadoSincronizacion.SinCuenta)
        assertEquals(ResultadoSincronizacion.Fallo(R.string.sync_fallo_sin_cuenta, puedeReintentar = false), vm.resultados.first())
    }

    @Test
    fun s04_errorDelServidorAvisaYPermiteReintentar() = runTest {
        val vm = crear()
        vm.sincronizarAhora()
        repo.pendiente.complete(EstadoSincronizacion.Error)
        assertEquals(ResultadoSincronizacion.Fallo(R.string.sync_fallo_error, puedeReintentar = true), vm.resultados.first())
    }

    @Test
    fun s05_excepcionInesperadaNoDejaElIndicadorColgado() = runTest {
        val vm = crear()
        vm.sincronizarAhora()
        repo.pendiente.completeExceptionally(IllegalStateException("falla"))
        assertFalse(vm.sincronizando.value)
        assertEquals(ResultadoSincronizacion.Fallo(R.string.sync_fallo_error, puedeReintentar = true), vm.resultados.first())
    }

    @Test
    fun s06_dobleToqueNoDuplicaElPedido() = runTest {
        val vm = crear()
        vm.sincronizarAhora()
        vm.sincronizarAhora()
        assertEquals(1, repo.pedidos)
    }

    @Test
    fun s07_reintentarDespuesDeUnFalloVuelveASincronizar() = runTest {
        val vm = crear()
        vm.sincronizarAhora()
        repo.pendiente.complete(EstadoSincronizacion.SinConexion)
        vm.resultados.first()

        repo.pendiente = CompletableDeferred()
        vm.sincronizarAhora()
        assertTrue(vm.sincronizando.value)
        repo.pendiente.complete(EstadoSincronizacion.AlDia(2L))
        assertEquals(2, repo.pedidos)
        assertEquals(ResultadoSincronizacion.Exito, vm.resultados.first())
    }

    @Test
    fun s08_sincronizacionAutomaticaMuestraElIndicadorSinMensajes() = runTest {
        val vm = crear()
        repo.estado.value = EstadoSincronizacion.Sincronizando
        assertTrue(vm.sincronizando.value)
        repo.estado.value = EstadoSincronizacion.AlDia(3L)
        assertFalse(vm.sincronizando.value)
        assertNull("Sin snackbar para las automáticas", withTimeoutOrNull(1_000) { vm.resultados.first() })
    }
}
