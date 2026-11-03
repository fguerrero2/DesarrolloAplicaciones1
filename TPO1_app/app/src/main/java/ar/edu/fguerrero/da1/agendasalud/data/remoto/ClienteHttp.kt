package ar.edu.fguerrero.da1.agendasalud.data.remoto

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Agrega el JWT a los pedidos de sincronización. Los de /auth no lo llevan.
 * Si el token venció (401), el Sincronizador vuelve a iniciar sesión con la credencial en memoria.
 */
class InterceptorAutenticacion(private val almacen: AlmacenCuenta) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        val token = almacen.sesion()?.accessToken
        if (token == null || pedido.url.encodedPath.contains("/auth/")) return chain.proceed(pedido)
        return chain.proceed(pedido.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
