package com.lonerxone.siphon.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkState(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = true,
    val isUsableForDownload: Boolean = false, // True if usable Wi-Fi (or metered allowed)
    val ssid: String? = null
)

interface NetworkMonitor {
    val networkState: Flow<NetworkState>
    fun getCurrentState(): NetworkState
}

@Singleton
class NetworkMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val rawNetworkFlow = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(checkCurrentCapabilities(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(mapCapabilitiesToState(networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(NetworkState(isConnected = false))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Send initial state immediately
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            trySend(checkCurrentCapabilities(activeNetwork))
        } else {
            trySend(NetworkState(isConnected = false))
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    // 2-second debounce to handle Android's transient unvalidated flag on Wi-Fi connection
    @OptIn(FlowPreview::class)
    override val networkState: Flow<NetworkState> = combine(
        rawNetworkFlow.debounce(1500L),
        preferencesRepository.userPreferencesFlow
    ) { state, prefs ->
        val usable = state.isConnected &&
                state.isWifi &&
                state.isValidated &&
                (!state.isMetered || prefs.allowMeteredWifi)

        state.copy(isUsableForDownload = usable)
    }.distinctUntilChanged()

    private fun checkCurrentCapabilities(network: Network): NetworkState {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return mapCapabilitiesToState(capabilities)
    }

    private fun mapCapabilitiesToState(capabilities: NetworkCapabilities?): NetworkState {
        if (capabilities == null) return NetworkState(isConnected = false)

        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkState(
            isConnected = isConnected,
            isWifi = isWifi,
            isValidated = isValidated,
            isMetered = isMetered
        )
    }

    override fun getCurrentState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkState(isConnected = false)
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return mapCapabilitiesToState(capabilities)
    }
}
