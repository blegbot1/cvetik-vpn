package com.cvetik.vpn

import android.content.Intent
import android.webkit.JavascriptInterface

class VpnBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun requestVpnPermission(endpoint: String, name: String) {
        activity.runOnUiThread {
            activity.requestVpnPermission(endpoint, name)
        }
    }

    @JavascriptInterface
    fun disconnect() {
        val intent = Intent(activity, CvetikVpnService::class.java)
        intent.action = CvetikVpnService.ACTION_DISCONNECT
        activity.startService(intent)
    }

    @JavascriptInterface
    fun getStats(): String {
        return "{"rx":" + CvetikVpnService.totalRx + ","tx":" + CvetikVpnService.totalTx + ","connected":" + CvetikVpnService.isRunning + "}"
    }

    @JavascriptInterface
    fun ping(host: String): String {
        return try {
            val start = System.currentTimeMillis()
            val reachable = java.net.InetAddress.getByName(host).isReachable(3000)
            val ms = if (reachable) System.currentTimeMillis() - start else 999
            "{"ms":" + ms + ","reachable":" + reachable + "}"
        } catch (e: Exception) {
            "{"ms":999,"reachable":false}"
        }
    }
}
