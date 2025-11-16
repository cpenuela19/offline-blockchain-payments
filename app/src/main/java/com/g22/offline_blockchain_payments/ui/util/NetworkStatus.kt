package com.g22.offline_blockchain_payments.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observa el estado de la conexión a internet
 */
@Composable
fun rememberNetworkStatus(): Boolean {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(checkInitialNetworkStatus(context)) }
    
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }
            
            override fun onLost(network: Network) {
                isOnline = false
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                 networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                isOnline = hasInternet
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
    
    return isOnline
}

/**
 * Verifica el estado inicial de la red
 */
private fun checkInitialNetworkStatus(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
           capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Flow para observar cambios de red
 */
fun Context.observeNetworkStatus(): Flow<Boolean> = callbackFlow {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }
        
        override fun onLost(network: Network) {
            trySend(false)
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                             networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            trySend(hasInternet)
        }
    }
    
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    
    connectivityManager.registerNetworkCallback(request, callback)
    
    // Enviar estado inicial
    trySend(checkInitialNetworkStatus(this@observeNetworkStatus))
    
    awaitClose {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}

