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
                    val config = res.wifiConfiguration
                        ?: res.softApConfiguration?.let { sac ->
                            val ssid = sac.ssid ?: "DataTrans"
                            val pass = sac.passphrase ?: ""
                            onResult(Result.success(HotspotInfo(ssid, pass, getLocalIpAddress())))
                            return
                        }
                        ?: run {
                            onResult(Result.failure(Exception("Failed to get hotspot config")))
                            return
                        }

                    @Suppress("DEPRECATION")
                    val ssid = config.SSID ?: "DataTrans"
                    @Suppress("DEPRECATION")
                    val pass = config.preSharedKey ?: ""
                    onResult(Result.success(HotspotInfo(ssid, pass, getLocalIpAddress())))
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

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                // Look for the hotspot interface (commonly swlan0, wlan1, ap0, etc.)
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        // Prefer 192.168.x.x addresses typical for hotspot
                        if (ip.startsWith("192.168.")) return ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HotspotManager", "Failed to get IP", e)
        }
        return "192.168.43.1" // Common default for Android hotspot
    }
}
