package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

object SystemUtils {
    // [A-Z, 2-9] excluding 0, O, 1, I
    private const val TRACKING_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    /**
     * AI 约束：generateTrackingId() 方法只能使用 [A-Z, 2-9]（剔除 0, O, 1, I），生成 6 位随机字符串。
     */
    fun generateTrackingId(): String {
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            val idx = random.nextInt(TRACKING_CHARS.length)
            sb.append(TRACKING_CHARS[idx])
        }
        return sb.toString()
    }

    fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "os" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE
        )
    }

    /**
     * 底噪收集：使用 ConnectivityManager.getNetworkCapabilities() 判断是否开启代理 hasTransport(NetworkCapabilities.TRANSPORT_VPN)。
     */
    fun getNetworkEnv(context: Context): Map<String, Any> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var isVpn = false
        var netType = "Unknown"

        if (cm != null) {
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // 判断是否开启代理或VPN
                isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

                netType = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Other"
                }
            }
        }

        val localIp = getLocalIpAddress()

        return mapOf(
            "type" to netType,
            "local_ip" to localIp,
            "is_vpn" to isVpn
        )
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }
}
