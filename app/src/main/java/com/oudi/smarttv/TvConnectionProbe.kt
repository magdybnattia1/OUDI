package com.oudi.smarttv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Performs a TCP reachability check only.
 * It does not start TLS, send protocol data, or pair with the television.
 */
object TvConnectionProbe {
    suspend fun isPairingPortReachable(
        host: String,
        port: Int,
        timeoutMs: Int = 2500
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
