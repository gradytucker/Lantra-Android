package app.lantra.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import kotlin.coroutines.resume

class ServerDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_lantra._tcp" // your Express server Bonjour type
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireMulticastLock() {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("LantraDiscoveryLock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }

    suspend fun findServer(timeoutMs: Long = 5000): Pair<InetAddress, Int>? =
        suspendCancellableCoroutine { cont ->

            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                Log.w("ServerDiscovery", "Wi-Fi is disabled, cannot start discovery")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            acquireMulticastLock()

            lateinit var listener: NsdManager.DiscoveryListener

            listener = object : NsdManager.DiscoveryListener {

                override fun onDiscoveryStarted(regType: String) {
                    Log.d("ServerDiscovery", "Discovery started for $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (!service.serviceType.contains(serviceType)) return
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            releaseMulticastLock()
                            try {
                                nsdManager.stopServiceDiscovery(listener)
                            } catch (_: Exception) {
                            }
                            if (!cont.isCompleted) cont.resume(Pair(resolved.host, resolved.port))
                        }

                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.w("ServerDiscovery", "Resolve failed: $errorCode")
                            releaseMulticastLock()
                            try {
                                nsdManager.stopServiceDiscovery(listener)
                            } catch (_: Exception) {
                            }
                            if (!cont.isCompleted) cont.resume(null)
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {
                    releaseMulticastLock()
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w("ServerDiscovery", "Discovery start failed: $errorCode")
                    releaseMulticastLock()
                    try {
                        nsdManager.stopServiceDiscovery(listener)
                    } catch (_: Exception) {
                    }
                    if (!cont.isCompleted) cont.resume(null)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w("ServerDiscovery", "Discovery stop failed: $errorCode")
                    releaseMulticastLock()
                    try {
                        nsdManager.stopServiceDiscovery(listener)
                    } catch (_: Exception) {
                    }
                    if (!cont.isCompleted) cont.resume(null)
                }
            }

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

            cont.invokeOnCancellation {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (_: Exception) {
                }
                releaseMulticastLock()
            }
        }
}