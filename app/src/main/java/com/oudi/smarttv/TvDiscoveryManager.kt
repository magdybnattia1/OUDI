package com.oudi.smarttv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Discovers Android TV Remote v2 services on the current Wi-Fi network. */
data class TvDevice(
    val name: String,
    val host: String,
    val port: Int
)

class TvDiscoveryManager(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val foundDevices = linkedMapOf<String, TvDevice>()
    private val resolvingNames = mutableSetOf<String>()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isDiscovering = false
    private var devicesCallback: ((List<TvDevice>) -> Unit)? = null
    private var statusCallback: ((String) -> Unit)? = null

    @Suppress("DEPRECATION")
    fun startDiscovery(
        onDevicesChanged: (List<TvDevice>) -> Unit,
        onStatusChanged: (String) -> Unit
    ) {
        stopDiscovery()
        foundDevices.clear()
        resolvingNames.clear()
        devicesCallback = onDevicesChanged
        statusCallback = onStatusChanged
        postDevices()
        postStatus("جاري البحث عن تلفزيونات Android TV على شبكة Wi-Fi…")

        acquireMulticastLockIfNeeded()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                postStatus("البحث شغال… تأكد إن الموبايل والتلفزيون على نفس شبكة Wi-Fi.")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val type = serviceInfo.serviceType?.trimEnd('.')
                if (type != SERVICE_TYPE.trimEnd('.')) return

                val name = serviceInfo.serviceName ?: return
                if (!resolvingNames.add(name)) return

                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolvingNames.remove(name)
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        resolvingNames.remove(name)
                        if (!isDiscovering) return

                        val address = info.host?.hostAddress ?: return
                        val device = TvDevice(
                            name = info.serviceName ?: name,
                            host = address,
                            port = info.port
                        )
                        foundDevices[name] = device
                        postDevices()
                        postStatus("تم العثور على تلفزيون/خدمة Android TV. لسه ما حصلش اقتران أو تحكم.")
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                foundDevices.remove(name)
                resolvingNames.remove(name)
                postDevices()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
                releaseMulticastLock()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
                postStatus("فشل بدء البحث (رمز $errorCode). جرّب إيقاف البحث ثم تشغيله.")
                safelyStop(this)
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
                postStatus("تعذر إيقاف البحث بشكل طبيعي (رمز $errorCode).")
                releaseMulticastLock()
            }
        }

        discoveryListener = listener
        isDiscovering = true
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            isDiscovering = false
            releaseMulticastLock()
            postStatus("تعذر البحث عن التلفزيون: ${error.message ?: "خطأ غير معروف"}")
        }
    }

    fun stopDiscovery() {
        val listener = discoveryListener
        if (listener != null && isDiscovering) safelyStop(listener)
        isDiscovering = false
        discoveryListener = null
        releaseMulticastLock()
    }

    private fun safelyStop(listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
            // The system may already have stopped this listener after an NSD error.
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireMulticastLockIfNeeded() {
        // Android 12 and earlier need an explicit lock to receive mDNS multicast packets.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("OUDI-mdns-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
            // Ignore a lock that was already released during a system callback.
        } finally {
            multicastLock = null
        }
    }

    private fun postDevices() {
        val snapshot = foundDevices.values.toList()
        mainHandler.post { devicesCallback?.invoke(snapshot) }
    }

    private fun postStatus(message: String) {
        mainHandler.post { statusCallback?.invoke(message) }
    }

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
