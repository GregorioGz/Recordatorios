package com.example.recordatorios.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observa la conectividad real del dispositivo con ConnectivityManager.NetworkCallback
 * y la expone como Flow<Boolean> (true = hay red con acceso a internet).
 */
class MonitorDeConectividad(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val hayConexion: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(estadoActual())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun estadoActual(): Boolean {
        val red = connectivityManager.activeNetwork ?: return false
        val capacidades = connectivityManager.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
