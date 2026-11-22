package ar.edu.fguerrero.da1.agendasalud.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avisa si el celular tiene internet, para mostrar el modo sin conexión (Nielsen H1). Solo observa
 * mientras alguna pantalla está visible: el callback se registra al recolectar y se quita al dejar de hacerlo.
 *
 * Se lleva la cuenta de las redes con internet validado a partir de los propios callbacks. No se
 * consulta `activeNetwork` dentro de onLost: en ese momento todavía puede devolver la red que se está
 * perdiendo, y la app seguiría creyendo que hay conexión (por ejemplo, al activar el modo avión).
 */
@Singleton
class MonitorConexion @Inject constructor(@param:ApplicationContext private val context: Context) {

    val conectado: Flow<Boolean> = callbackFlow {
        val gestor = context.getSystemService(ConnectivityManager::class.java)
        val conInternet = mutableSetOf<Network>()

        fun tieneInternet(capacidades: NetworkCapabilities?) = capacidades != null &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                synchronized(conInternet) {
                    if (tieneInternet(capacidades)) conInternet.add(network) else conInternet.remove(network)
                    trySend(conInternet.isNotEmpty())
                }
            }

            override fun onLost(network: Network) {
                synchronized(conInternet) {
                    conInternet.remove(network)
                    trySend(conInternet.isNotEmpty())
                }
            }
        }
        // Valor inicial; después mandan los callbacks
        trySend(tieneInternet(gestor.activeNetwork?.let(gestor::getNetworkCapabilities)))
        val pedido = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        gestor.registerNetworkCallback(pedido, callback)
        awaitClose { gestor.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
