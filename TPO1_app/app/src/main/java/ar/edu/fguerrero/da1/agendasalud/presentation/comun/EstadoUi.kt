package ar.edu.fguerrero.da1.agendasalud.presentation.comun

/**
 * Estado de una pantalla que carga datos de la base. Es una jerarquía sellada para que el `when`
 * de la pantalla tenga que contemplar los tres casos: el compilador avisa si falta alguno.
 * Un error de lectura nunca se muestra como una lista vacía.
 */
sealed interface EstadoUi<out T> {
    data object Cargando : EstadoUi<Nothing>
    data object Error : EstadoUi<Nothing>
    data class Contenido<out T>(val datos: T) : EstadoUi<T>
}

/** Los datos si ya están cargados; null mientras carga o si hubo un error. */
fun <T> EstadoUi<T>.datosOrNull(): T? = (this as? EstadoUi.Contenido)?.datos
