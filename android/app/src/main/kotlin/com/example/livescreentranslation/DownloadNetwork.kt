package com.example.livescreentranslation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Only a coarse default-network snapshot; no identifiers, requests or logs. */
internal object DownloadNetwork {
    fun snapshot(context: Context): String = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        if (manager == null) "unknown"
        else if (network == null) "offline"
        else {
            val caps = manager.getNetworkCapabilities(network)
            when {
                caps == null -> "unknown"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "unknown"
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "unverified"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                else -> "other"
            }
        }
    } catch (_: Exception) {
        "unknown"
    }
}
