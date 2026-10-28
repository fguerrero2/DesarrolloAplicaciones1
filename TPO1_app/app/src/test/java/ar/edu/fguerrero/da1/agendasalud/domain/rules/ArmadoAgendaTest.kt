package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.ItemAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ArmadoAgendaTest {

    private val viernes = LocalDate.of(2026, 10, 23)

    private fun turno(h: Int, estado: EstadoTurno = EstadoTurno.PROGRAMADO) = TurnoConPaciente(
        Turno(id = h.toLong(), pacienteId = 1, inicio = viernes.atTime(h, 0), duracionMinutos = 60, importeCentavos = 0, estado = estado),
        "Ana Pérez",
    )

    private fun List<ItemAgenda>.horasLibres() = filterIsInstance<ItemAgenda.Libre>().map { it.inicio.hour }

    @Test
    fun diaDeSemanaVacioTieneNueveHuecos() {
        assertEquals((9..17).toList(), ArmadoAgenda.armar(viernes, emptyList(), emptyList()).horasLibres())
    }

    @Test
    fun sabadoTieneTresHuecosYDomingoNinguno() {
        assertEquals(listOf(9, 10, 11), ArmadoAgenda.armar(viernes.plusDays(1), emptyList(), emptyList()).horasLibres())
        assertTrue(ArmadoAgenda.armar(viernes.plusDays(2), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun turnosYBloqueosOcupanHuecosEnOrden() {
        val curso = BloqueoAgenda(inicio = viernes.atTime(12, 0), fin = viernes.atTime(13, 0), motivo = "Curso")
        val agenda = ArmadoAgenda.armar(viernes, listOf(turno(10)), listOf(curso))
        assertEquals(listOf(9, 11, 13, 14, 15, 16, 17), agenda.horasLibres())
        assertTrue(agenda[1] is ItemAgenda.Ocupado)
        assertEquals(LocalTime.of(12, 0), agenda[3].inicio.toLocalTime())
        assertTrue(agenda[3] is ItemAgenda.Bloqueado)
    }

    @Test
    fun turnoCanceladoSeMuestraPeroLiberaElHueco() {
        val agenda = ArmadoAgenda.armar(viernes, listOf(turno(10, EstadoTurno.CANCELADO)), emptyList())
        assertTrue(agenda.any { it is ItemAgenda.Ocupado })
        assertTrue(10 in agenda.horasLibres())
    }

    @Test
    fun huecosQueYaEmpezaronNoSeOfrecen() {
        val agenda = ArmadoAgenda.armar(viernes, emptyList(), emptyList(), ahora = viernes.atTime(14, 30))
        assertEquals(listOf(15, 16, 17), agenda.horasLibres())
    }
}
