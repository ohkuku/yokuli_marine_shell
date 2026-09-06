package com.yokuli.marine.data.android.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkAvailabilityPort {
    val available: StateFlow<Boolean>
}

class AndroidNetworkAvailability(context: Context) : NetworkAvailabilityPort, Closeable {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val mutableAvailable = MutableStateFlow(connectivity.hasUsableNetwork())
    override val available: StateFlow<Boolean> = mutableAvailable.asStateFlow()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mutableAvailable.value = connectivity.hasUsableNetwork()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            mutableAvailable.value = connectivity.hasUsableNetwork()
        }

        override fun onLost(network: Network) {
            mutableAvailable.value = connectivity.hasUsableNetwork()
        }

        override fun onUnavailable() {
            mutableAvailable.value = false
        }
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    private fun ConnectivityManager.hasUsableNetwork(): Boolean {
        val active = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(active) ?: return false
        // Boat Wi-Fi often has no Internet. Local TCP/UDP only needs an active, unsuspended
        // network transport, not Android's validated Internet capability.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) &&
            (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                )
    }
}
