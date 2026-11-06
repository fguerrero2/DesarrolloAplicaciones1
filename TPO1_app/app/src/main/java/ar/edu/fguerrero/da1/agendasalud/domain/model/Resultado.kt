package ar.edu.fguerrero.da1.agendasalud.domain.model

/** Errores de negocio que la UI traduce a mensajes. */
sealed interface ErrorDominio {
    data object HorarioOcupado : ErrorDominio
    data object TurnosEnPeriodo : ErrorDominio
    data object PeriodoBloqueado : ErrorDominio
    data object FueraDeHorario : ErrorDominio
    data object IntervaloInvalido : ErrorDominio
    data object PacienteInactivo : ErrorDominio
    data object NombreInvalido : ErrorDominio
    data object MontoNoPositivo : ErrorDominio
    data object MontoSuperaPendiente : ErrorDominio
    data object EstadoNoPermitido : ErrorDominio
    data object NoEncontrado : ErrorDominio
    data object ErrorGuardado : ErrorDominio
    data object ErrorLectura : ErrorDominio

    /** El archivo de respaldo no se pudo leer: contraseña incorrecta, archivo dañado o de otra app. */
    data object RespaldoInvalido : ErrorDominio

    /** No se pudo abrir o escribir el archivo elegido (por ejemplo, Drive sin conexión). */
    data object ErrorArchivo : ErrorDominio

    // Cuenta en el servidor
    data object SinConexion : ErrorDominio
    data object CredencialesInvalidas : ErrorDominio
    data object UsuarioExistente : ErrorDominio
    data object EmailInvalido : ErrorDominio
    data object EnlaceVencido : ErrorDominio
    data object ErrorServidor : ErrorDominio
}

sealed interface Resultado<out T> {
    data class Exito<out T>(val valor: T) : Resultado<T>
    data class Fallo(val error: ErrorDominio) : Resultado<Nothing>
}
