package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive network connectivity observer backed by [ConnectivityManager.NetworkCallback].
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val activeNetworks = mutableSetOf<Network>()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network)
            val validated = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            synchronized(activeNetworks) {
                if (validated) {
                    activeNetworks.add(network)
                    _isOnline.value = true
                }
            }
        }

        override fun onLost(network: Network) {
            synchronized(activeNetworks) {
                activeNetworks.remove(network)
                _isOnline.value = activeNetworks.isNotEmpty() || checkCurrentlyOnline()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            synchronized(activeNetworks) {
                if (validated) activeNetworks.add(network) else activeNetworks.remove(network)
                _isOnline.value = activeNetworks.isNotEmpty() || checkCurrentlyOnline()
            }
        }
    }

    private fun checkCurrentlyOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun start() {
        if (registered) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, callback)
            registered = true
            _isOnline.value = checkCurrentlyOnline()
        }
    }

    fun stop() {
        if (!registered) return
        val cm = connectivityManager ?: return
        runCatching {
            cm.unregisterNetworkCallback(callback)
        }
        synchronized(activeNetworks) {
            activeNetworks.clear()
        }
        registered = false
    }
}
