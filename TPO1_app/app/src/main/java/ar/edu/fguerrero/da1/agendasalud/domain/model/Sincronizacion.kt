package ar.edu.fguerrero.da1.agendasalud.domain.model

/** Estado de la sincronización con el servidor, para mostrarlo en el menú. */
sealed interface EstadoSincronizacion {
    data object NuncaSincronizado : EstadoSincronizacion
    data object Sincronizando : EstadoSincronizacion
    data class AlDia(val momentoMs: Long) : EstadoSincronizacion
    data object SinConexion : EstadoSincronizacion

    /** No hay sesión con el servidor (por ejemplo, se ingresó con huella y el token venció). */
    data object SinCuenta : EstadoSincronizacion
    data object Error : EstadoSincronizacion
}
