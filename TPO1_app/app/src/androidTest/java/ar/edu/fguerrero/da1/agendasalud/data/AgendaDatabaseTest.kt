package ar.edu.fguerrero.da1.agendasalud.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.repository.CobroRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.PacienteRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.TurnoRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AgendaDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clave = ByteArray(32) { it.toByte() }
    private lateinit var db: AgendaDatabase

    @Before
    fun abrir() {
        context.deleteDatabase(NOMBRE_TEST)
        db = AgendaDatabase.crear(context, clave, NOMBRE_TEST)
    }

    @After
    fun cerrar() {
        db.close()
        context.deleteDatabase(NOMBRE_TEST)
    }

    private suspend fun crearPaciente(nombre: String = "Juana Pérez"): Long =
        (PacienteRepositoryImpl(db).guardar(Paciente(nombre = nombre)) as Resultado.Exito).valor

    private fun turno(pacienteId: Long, inicio: LocalDateTime) =
        Turno(pacienteId = pacienteId, inicio = inicio, duracionMinutos = 50, importeCentavos = 20_000_00)

    @Test
    fun archivoDeBaseEstaCifrado() = runTest {
        crearPaciente("NombreMuyIdentificable")
        db.close()
        val bytes = context.getDatabasePath(NOMBRE_TEST).readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("NombreMuyIdentificable"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        db = AgendaDatabase.crear(context, clave, NOMBRE_TEST)
    }

    @Test
    fun turnoSuperpuestoSeRechaza() = runTest {
        val turnos = TurnoRepositoryImpl(db)
        val paciente = crearPaciente()
        val diez = LocalDateTime.of(2026, 10, 23, 10, 0)
        assertTrue(turnos.reservar(turno(paciente, diez)) is Resultado.Exito)
        assertEquals(Resultado.Fallo(ErrorDominio.HorarioOcupado), turnos.reservar(turno(paciente, diez.plusMinutes(30))))
        assertTrue(turnos.reservar(turno(paciente, diez.plusMinutes(50))) is Resultado.Exito)
    }

    @Test
    fun cobroParcialYTotalSegunRf04() = runTest {
        val turnos = TurnoRepositoryImpl(db)
        val cobros = CobroRepositoryImpl(db)
        val paciente = crearPaciente()
        val turnoId = (turnos.reservar(turno(paciente, LocalDateTime.of(2026, 10, 23, 10, 0))) as Resultado.Exito).valor
        val cargo = (turnos.completar(turnoId) as Resultado.Exito).valor

        // Reintentar completar no duplica el cargo (RNF07)
        assertEquals(cargo, (turnos.completar(turnoId) as Resultado.Exito).valor)

        val hoy = LocalDate.of(2026, 10, 23)
        assertTrue(cobros.registrar(cargo.id, 8_000_00, hoy, MedioCobro.EFECTIVO) is Resultado.Exito)
        assertEquals(12_000_00L, cobros.observarSaldoPaciente(paciente).first())
        assertEquals(
            Resultado.Fallo(ErrorDominio.MontoSuperaPendiente),
            cobros.registrar(cargo.id, 12_000_01, hoy, MedioCobro.MERCADO_PAGO),
        )
        assertTrue(cobros.registrar(cargo.id, 12_000_00, hoy, MedioCobro.MERCADO_PAGO) is Resultado.Exito)
        assertEquals(0L, cobros.observarSaldoPaciente(paciente).first())
        assertTrue(cobros.observarPendientes().first().isEmpty())
    }

    private companion object {
        const val NOMBRE_TEST = "agendasalud-test.db"
    }
}
