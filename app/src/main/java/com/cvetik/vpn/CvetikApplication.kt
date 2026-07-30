package com.cvetik.vpn

import android.app.Application

class CvetikApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize WG Tunnel backend
        System.setProperty("wireguard.backend", "go")
    }
}
