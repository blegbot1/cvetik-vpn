package com.cvetik.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class CvetikVpnService : VpnService() {
    companion object {
        const val ACTION_CONNECT = "com.cvetik.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.cvetik.vpn.DISCONNECT"
        const val CHANNEL_ID = "cvetik_vpn_channel"
        const val NOTIFICATION_ID = 1337
        @JvmStatic var totalRx = 0L
        @JvmStatic var totalTx = 0L
        @JvmStatic @Volatile var isRunning = false
    }
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val endpoint = intent.getStringExtra("endpoint") ?: return START_NOT_STICKY
                val name = intent.getStringExtra("name") ?: "CVETIK-VPN"
                connect(endpoint, name)
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_NOT_STICKY
    }

    private fun connect(endpoint: String, name: String) {
        if (running) return
        running = true; isRunning = true; totalRx = 0L; totalTx = 0L
        startForeground(name)
        val parts = endpoint.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 4500

        vpnThread = Thread {
            var socket: Socket? = null
            try {
                val builder = Builder()
                builder.setSession(name)
                builder.addAddress("10.200.200.2", 24)
                builder.addRoute("0.0.0.0", 0)
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
                builder.setMtu(1400)
                builder.setBlocking(true)
                vpnInterface = builder.establish()
                if (vpnInterface == null) { running = false; isRunning = false; return@Thread }

                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 15000)
                protect(socket)

                val vpnIn = FileInputStream(vpnInterface!!.fileDescriptor)
                val vpnOut = FileOutputStream(vpnInterface!!.fileDescriptor)
                val sockIn = socket.getInputStream()
                val sockOut = socket.getOutputStream()

                val t1 = Thread {
                    val buf = ByteArray(32767)
                    try { while (running) {
                        val n = vpnIn.read(buf)
                        if (n > 0) { totalTx += n; sockOut.write(buf, 0, n); sockOut.flush() }
                        else if (n < 0) break
                    }} catch (e: Exception) {}
                }
                val t2 = Thread {
                    val buf = ByteArray(32767)
                    try { while (running) {
                        val n = sockIn.read(buf)
                        if (n > 0) { totalRx += n; vpnOut.write(buf, 0, n); vpnOut.flush() }
                        else if (n < 0) break
                    }} catch (e: Exception) {}
                }
                t1.start(); t2.start(); t1.join(); t2.join()
            } catch (e: Exception) { Log.e("CVETIK", "VPN error: ${e.message}")
            } finally {
                running = false; isRunning = false
                try { socket?.close() } catch (_: Exception) {}
                try { vpnInterface?.close() } catch (_: Exception) {}
                vpnInterface = null; stopForeground(true); stopSelf()
            }
        }
        vpnThread?.start()
    }

    private fun disconnect() {
        running = false; isRunning = false
        vpnThread?.interrupt()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null; stopForeground(true); stopSelf()
    }

    private fun startForeground(name: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "CVETIK-VPN", NotificationManager.IMPORTANCE_LOW)
            channel.description = "VPN connection status"
            nm.createNotificationChannel(channel)
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText("VPN активен • Трафик шифруется")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() { disconnect(); super.onDestroy() }
}
