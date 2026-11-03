package ar.edu.fguerrero.da1.agendasalud.presentation.respaldo

import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.security.Credenciales
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.repository.RespaldoRepository
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Aviso
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ViewModelConAvisos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate
import javax.inject.Inject

/** En qué paso del respaldo está el usuario; cada paso es un diálogo distinto. */
sealed interface PasoRespaldo {
    data object Inactivo : PasoRespaldo
    data object ClaveParaExportar : PasoRespaldo
    data object ConfirmarRestaurar : PasoRespaldo
    data class ClaveParaRestaurar(val archivo: String) : PasoRespaldo
    data object Procesando : PasoRespaldo
}

/** Pedido a la pantalla de abrir el selector de archivos de Android (evento de una sola vez). */
sealed interface SelectorArchivo {
    data class Guardar(val nombreSugerido: String) : SelectorArchivo
    data object Abrir : SelectorArchivo
}

/**
 * Exportar y restaurar el respaldo cifrado.
 * Exportar: contraseña → el usuario elige dónde guardar → se escribe el archivo.
 * Restaurar: confirmación ("reemplaza todo") → el usuario elige el archivo → contraseña → se restaura.
 * Las contraseñas no se guardan en SavedStateHandle a propósito: no deben quedar en disco.
 */
@HiltViewModel
class RespaldoViewModel @Inject constructor(
    private val respaldo: RespaldoRepository,
) : ViewModelConAvisos() {

    private val _paso = MutableStateFlow<PasoRespaldo>(PasoRespaldo.Inactivo)
    val paso: StateFlow<PasoRespaldo> = _paso.asStateFlow()

    private val selectores = Channel<SelectorArchivo>(Channel.BUFFERED)
    val selectorArchivo: Flow<SelectorArchivo> = selectores.receiveAsFlow()

    /** Contraseña elegida mientras el usuario está en el selector de archivos. */
    private var claveExportar: CharArray? = null

    fun pedirExportar() {
        _paso.value = PasoRespaldo.ClaveParaExportar
    }

    /** Devuelve el error a mostrar en el diálogo, o null si la contraseña es válida. */
    fun elegirClaveExportar(clave: String, confirmacion: String): Int? {
        if (clave.length < Credenciales.LARGO_MINIMO) return R.string.login_error_contrasenia_corta
        if (clave != confirmacion) return R.string.login_error_no_coinciden
        claveExportar = clave.toCharArray()
        _paso.value = PasoRespaldo.Inactivo
        selectores.trySend(SelectorArchivo.Guardar("agendasalud-${LocalDate.now()}.respaldo"))
        return null
    }

    /** Resultado del selector "Guardar como". [uri] es null si el usuario canceló. */
    fun destinoElegido(uri: String?) {
        val clave = claveExportar ?: return
        claveExportar = null
        if (uri == null) {
            clave.fill('\u0000')
            return
        }
        _paso.value = PasoRespaldo.Procesando
        ejecutar({ respaldo.exportar(uri, clave) }, alFallar = ::fallo) { resumen ->
            _paso.value = PasoRespaldo.Inactivo
            avisar(Aviso.Exito(R.string.respaldo_exportado, listOf(resumen.pacientes, resumen.turnos, resumen.cobros)))
        }
    }

    fun pedirRestaurar() {
        _paso.value = PasoRespaldo.ConfirmarRestaurar
    }

    fun confirmarRestaurar() {
        _paso.value = PasoRespaldo.Inactivo
        selectores.trySend(SelectorArchivo.Abrir)
    }

    /** Resultado del selector "Abrir". [uri] es null si el usuario canceló. */
    fun origenElegido(uri: String?) {
        if (uri != null) _paso.value = PasoRespaldo.ClaveParaRestaurar(uri)
    }

    /**
     * Si la contraseña es incorrecta, el diálogo queda abierto con el error ([alTerminar]) para
     * que el usuario pueda volver a escribirla sin elegir el archivo otra vez.
     */
    fun restaurar(clave: String, alTerminar: (ErrorDominio?) -> Unit) {
        val paso = _paso.value as? PasoRespaldo.ClaveParaRestaurar ?: return
        ejecutar({ respaldo.restaurar(paso.archivo, clave.toCharArray()) }, alFallar = alTerminar) { resumen ->
            _paso.value = PasoRespaldo.Inactivo
            alTerminar(null)
            avisar(Aviso.Exito(R.string.respaldo_restaurado, listOf(resumen.pacientes, resumen.turnos, resumen.cobros)))
        }
    }

    fun cancelar() {
        _paso.value = PasoRespaldo.Inactivo
    }

    private fun fallo(error: ErrorDominio) {
        _paso.value = PasoRespaldo.Inactivo
        avisar(Aviso.Error(error))
    }

    override fun onCleared() {
        claveExportar?.fill('\u0000')
    }
}
