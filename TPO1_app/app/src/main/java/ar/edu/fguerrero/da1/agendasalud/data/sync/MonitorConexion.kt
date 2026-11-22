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
 */
@Singleton
class MonitorConexion @Inject constructor(@param:ApplicationContext private val context: Context) {

    val conectado: Flow<Boolean> = callbackFlow {
        val gestor = context.getSystemService(ConnectivityManager::class.java)
        fun hayInternet(): Boolean = gestor.activeNetwork
            ?.let(gestor::getNetworkCapabilities)
            ?.run { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
            ?: false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                trySend(hayInternet())
            }

            override fun onLost(network: Network) {
                trySend(hayInternet())
            }
        }
        trySend(hayInternet())
        val pedido = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        gestor.registerNetworkCallback(pedido, callback)
        awaitClose { gestor.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
