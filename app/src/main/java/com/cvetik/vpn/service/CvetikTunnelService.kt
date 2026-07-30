package com.cvetik.vpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Background tunnel management service
 * Handles tunnel state, auto-reconnect, etc.
 */
class CvetikTunnelService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
