package com.cvetik.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "CvetikVpn")
class CvetikVpnPlugin : Plugin() {
    private val VPN_REQUEST_CODE = 1001
    private var pendingCall: PluginCall? = null

    @PluginMethod
    fun connect(call: PluginCall) {
        val endpoint = call.getString("endpoint", "") ?: ""
        val name = call.getString("name", "CVETIK-VPN") ?: "CVETIK-VPN"
        if (endpoint.isEmpty()) { call.reject("No endpoint"); return }
        val intent = VpnService.prepare(context)
        if (intent != null) {
            pendingCall = call
            startActivityForResult(call, intent, VPN_REQUEST_CODE)
        } else {
            startVpnService(endpoint, name)
            val ret = JSObject(); ret.put("status", "connected"); call.resolve(ret)
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        val intent = Intent(context, CvetikVpnService::class.java)
        intent.action = CvetikVpnService.ACTION_DISCONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
        val ret = JSObject(); ret.put("status", "disconnected"); call.resolve(ret)
    }

    @PluginMethod
    fun getStats(call: PluginCall) {
        val ret = JSObject()
        ret.put("rx", CvetikVpnService.totalRx)
        ret.put("tx", CvetikVpnService.totalTx)
        ret.put("connected", CvetikVpnService.isRunning)
        call.resolve(ret)
    }

    private fun startVpnService(endpoint: String, name: String) {
        val intent = Intent(context, CvetikVpnService::class.java)
        intent.action = CvetikVpnService.ACTION_CONNECT
        intent.putExtra("endpoint", endpoint)
        intent.putExtra("name", name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }

    override fun handleOnActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.handleOnActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            val call = pendingCall; pendingCall = null
            if (resultCode == Activity.RESULT_OK) {
                val endpoint = call?.getString("endpoint", "") ?: ""
                val name = call?.getString("name", "CVETIK-VPN") ?: "CVETIK-VPN"
                startVpnService(endpoint, name)
                val ret = JSObject(); ret.put("status", "connected"); call?.resolve(ret)
            } else { call?.reject("VPN permission denied") }
        }
    }
}
