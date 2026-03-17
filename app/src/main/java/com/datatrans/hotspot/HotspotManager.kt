package com.datatrans.hotspot

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

data class HotspotInfo(
    val ssid: String,
    val password: String,
    val ipAddress: String
)

class HotspotManager(context: Context) {

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start(onResult: (Result<HotspotInfo>) -> Unit) {
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res

                    val ssid: String
                    val pass: String

                    val softApConfig = res.softApConfiguration
                    if (softApConfig != null) {
                        ssid = softApConfig.ssid ?: "DataTrans"
                        pass = softApConfig.passphrase ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        val wifiConfig = res.wifiConfiguration
                        if (wifiConfig != null) {
                            @Suppress("DEPRECATION")
                            ssid = wifiConfig.SSID ?: "DataTrans"
                            @Suppress("DEPRECATION")
                            pass = wifiConfig.preSharedKey ?: ""
                        } else {
                            onResult(Result.failure(Exception("Failed to get hotspot config")))
                            return
                        }
                    }

                    // Wait for network interface to come up, then detect IP
                    Thread {
                        val ip = waitForHotspotIp()
                        Log.d("HotspotManager", "Hotspot IP: $ip, SSID: $ssid")
                        onResult(Result.success(HotspotInfo(ssid, pass, ip)))
                    }.start()
                }

                override fun onStopped() {
                    Log.d("HotspotManager", "Hotspot stopped")
                }

                override fun onFailed(reason: Int) {
                    onResult(Result.failure(Exception("Hotspot failed: reason=$reason")))
                }
            }, null)
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
    }

    private fun waitForHotspotIp(maxRetries: Int = 10, delayMs: Long = 500): String {
        // Hotspot interface takes a moment to come up, retry until we find it
        for (i in 0 until maxRetries) {
            val ip = detectHotspotIp()
            if (ip != null) return ip
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            Log.d("HotspotManager", "Waiting for hotspot IP... attempt ${i + 1}")
        }
        // Fallback: 192.168.49.1 is the standard IP for startLocalOnlyHotspot
        Log.w("HotspotManager", "Could not detect IP, using fallback 192.168.49.1")
        return "192.168.49.1"
    }

    private fun detectHotspotIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            // Known hotspot interface names
            val hotspotNames = listOf("swlan0", "wlan1", "ap0", "softap0")
            val candidates = mutableListOf<Pair<String, String>>() // iface name -> ip

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        Log.d("HotspotManager", "Found interface ${iface.name} with IP $ip")
                        candidates.add(iface.name to ip)
                    }
                }
            }

            // Prefer known hotspot interface names
            for (name in hotspotNames) {
                candidates.find { it.first == name }?.let { return it.second }
            }

            // Otherwise prefer 192.168.49.x (LocalOnlyHotspot range)
            candidates.find { it.second.startsWith("192.168.49.") }?.let { return it.second }

            // Then any 192.168.x.x
            candidates.find { it.second.startsWith("192.168.") }?.let { return it.second }

        } catch (e: Exception) {
            Log.e("HotspotManager", "Failed to detect IP", e)
        }
        return null
    }
}
