package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.Intervalo
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ReglasAgendaTest {

    private fun hora(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 23, h, m)

    private fun turno(id: Long, h: Int, estado: EstadoTurno = EstadoTurno.PROGRAMADO) =
        Turno(id = id, pacienteId = 1, inicio = hora(h), duracionMinutos = 50, importeCentavos = 0, estado = estado)

    @Test
    fun horarioLibreEsValido() {
        assertNull(ReglasAgenda.validarHorario(Intervalo(hora(11), hora(12)), listOf(turno(1, 10)), emptyList()))
    }

    @Test
    fun turnoSuperpuestoSeRechaza() {
        val error = ReglasAgenda.validarHorario(Intervalo(hora(10, 30), hora(11, 20)), listOf(turno(1, 10)), emptyList())
        assertEquals(ErrorDominio.HorarioOcupado, error)
    }

    @Test
    fun turnosContiguosNoSeSuperponen() {
        // El turno existente termina 10:50
        assertNull(ReglasAgenda.validarHorario(Intervalo(hora(10, 50), hora(11, 40)), listOf(turno(1, 10)), emptyList()))
    }

    @Test
    fun turnoCanceladoLiberaHorario() {
        assertNull(
            ReglasAgenda.validarHorario(Intervalo(hora(10), hora(10, 50)), listOf(turno(1, 10, EstadoTurno.CANCELADO)), emptyList())
        )
    }

    @Test
    fun alReprogramarSeIgnoraElPropioTurno() {
        assertNull(
            ReglasAgenda.validarHorario(Intervalo(hora(10, 15), hora(11, 5)), listOf(turno(1, 10)), emptyList(), excluirTurnoId = 1)
        )
    }

    @Test
    fun periodoBloqueadoSeRechaza() {
        val bloqueo = BloqueoAgenda(inicio = hora(9), fin = hora(13), motivo = "Curso")
        assertEquals(
            ErrorDominio.PeriodoBloqueado,
            ReglasAgenda.validarHorario(Intervalo(hora(12), hora(12, 50)), emptyList(), listOf(bloqueo))
        )
    }

    @Test
    fun intervaloInvertidoSeRechaza() {
        assertEquals(ErrorDominio.IntervaloInvalido, ReglasAgenda.validarHorario(Intervalo(hora(12), hora(11)), emptyList(), emptyList()))
    }

    @Test
    fun domingoYFueraDeHorarioSeRechazan() {
        val domingo = LocalDateTime.of(2026, 10, 25, 10, 0)
        val sabado = LocalDateTime.of(2026, 10, 24, 11, 30)
        assertEquals(ErrorDominio.FueraDeHorario, ReglasAgenda.validarHorario(Intervalo(domingo, domingo.plusHours(1)), emptyList(), emptyList()))
        assertEquals(ErrorDominio.FueraDeHorario, ReglasAgenda.validarHorario(Intervalo(sabado, sabado.plusHours(1)), emptyList(), emptyList()))
        assertEquals(ErrorDominio.FueraDeHorario, ReglasAgenda.validarHorario(Intervalo(hora(17, 30), hora(18, 30)), emptyList(), emptyList()))
        assertNull(ReglasAgenda.validarHorario(Intervalo(sabado.minusMinutes(30), sabado.plusMinutes(30)), emptyList(), emptyList()))
    }

    @Test
    fun bloqueoSobreTurnoProgramadoSeRechaza() {
        assertEquals(ErrorDominio.TurnosEnPeriodo, ReglasAgenda.validarBloqueo(Intervalo(hora(8), hora(18)), listOf(turno(1, 10))))
    }

    @Test
    fun bloqueoSobreTurnoCompletadoSePermite() {
        assertNull(ReglasAgenda.validarBloqueo(Intervalo(hora(8), hora(18)), listOf(turno(1, 10, EstadoTurno.COMPLETADO))))
    }
}
