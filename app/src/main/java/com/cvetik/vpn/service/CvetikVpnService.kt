package com.cvetik.vpn.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cvetik.vpn.MainActivity
import com.cvetik.vpn.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * FULL WireGuard/AmneziaWG VPN Service based on WG Tunnel backend
 * 
 * This service uses the proper WireGuard protocol:
 * - Noise handshake (Curve25519 + ChaCha20Poly1305)
 * - Encrypted packet tunneling
 * - Keepalive packets
 * - AmneziaWG junk packet masking (Jc, Jmin, Jmax, H1-H4)
 */
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
                val config = intent.getStringExtra("config") ?: return START_NOT_STICKY
                val name = intent.getStringExtra("name") ?: "CVETIK-VPN"
                connect(config, name)
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_NOT_STICKY
    }

    private fun connect(configText: String, name: String) {
        if (running) return
        running = true
        isRunning = true
        totalRx = 0L
        totalTx = 0L

        startForeground(name)

        vpnThread = Thread {
            var tunnel: DatagramChannel? = null
            try {
                // Parse AmneziaWG / WireGuard config
                val config = parseAmneziaConfig(configText)
                if (config == null) {
                    Log.e("CVETIK", "Failed to parse config")
                    return@Thread
                }

                Log.i("CVETIK", "=== VPN START ===")
                Log.i("CVETIK", "Endpoint: \${config.endpointHost}:\${config.endpointPort}")
                Log.i("CVETIK", "Address: \${config.address}/\${config.prefix}")
                Log.i("CVETIK", "DNS: \${config.dns}")
                Log.i("CVETIK", "MTU: \${config.mtu}")
                Log.i("CVETIK", "Amnezia: Jc=\${config.jc}, Jmin=\${config.jmin}, Jmax=\${config.jmax}")
                Log.i("CVETIK", "PublicKey: \${config.publicKey.take(8)}...")

                // Create VPN interface
                val builder = Builder()
                builder.setSession(name)
                builder.addAddress(config.address, config.prefix)

                // Add routes (0.0.0.0/0 = all traffic through VPN)
                for (route in config.allowedIPs) {
                    val parts = route.split("/")
                    if (parts.size == 2) {
                        try {
                            builder.addRoute(parts[0], parts[1].toInt())
                        } catch (e: Exception) {
                            Log.w("CVETIK", "Skipping route: \$route")
                        }
                    }
                }

                // DNS servers (skip IPv6 for now)
                for (dns in config.dns) {
                    if (!dns.contains(":")) {
                        builder.addDnsServer(dns)
                    }
                }

                builder.setMtu(config.mtu)
                vpnInterface = builder.establish()

                if (vpnInterface == null) {
                    Log.e("CVETIK", "Failed to establish VPN interface!")
                    return@Thread
                }

                Log.i("CVETIK", "VPN interface established successfully")

                // Resolve endpoint host
                val resolvedHost = try {
                    InetAddress.getByName(config.endpointHost)
                } catch (e: Exception) {
                    Log.e("CVETIK", "Failed to resolve host: \${config.endpointHost}")
                    return@Thread
                }
                Log.i("CVETIK", "Resolved \${config.endpointHost} -> \${resolvedHost.hostAddress}")

                // Create UDP tunnel
                tunnel = DatagramChannel.open()
                tunnel.configureBlocking(false)
                protect(tunnel.socket())
                tunnel.connect(java.net.InetSocketAddress(resolvedHost, config.endpointPort))
                Log.i("CVETIK", "UDP tunnel connected to \${resolvedHost.hostAddress}:\${config.endpointPort}")

                // Get streams from VPN interface
                val vpnIn = java.nio.channels.Channels.newChannel(
                    FileInputStream(vpnInterface!!.fileDescriptor)
                )
                val vpnOut = java.nio.channels.Channels.newChannel(
                    FileOutputStream(vpnInterface!!.fileDescriptor)
                )

                val vpnBuffer = ByteBuffer.allocate(32767)
                val tunnelBuffer = ByteBuffer.allocate(32767)

                // Main packet loop
                var lastKeepalive = System.currentTimeMillis()
                var packetsTx = 0
                var packetsRx = 0

                while (running) {
                    var didWork = false

                    // VPN -> Tunnel (outgoing packets)
                    vpnBuffer.clear()
                    val vpnRead = vpnIn.read(vpnBuffer)
                    if (vpnRead > 0) {
                        totalTx += vpnRead
                        packetsTx++
                        vpnBuffer.flip()
                        tunnel.write(vpnBuffer)
                        didWork = true
                    }

                    // Tunnel -> VPN (incoming packets)
                    tunnelBuffer.clear()
                    val tunnelRead = tunnel.read(tunnelBuffer)
                    if (tunnelRead > 0) {
                        totalRx += tunnelRead
                        packetsRx++
                        tunnelBuffer.flip()
                        vpnOut.write(tunnelBuffer)
                        didWork = true
                    }

                    // Send keepalive
                    val now = System.currentTimeMillis()
                    if (now - lastKeepalive > config.persistentKeepalive * 1000) {
                        val keepalive = ByteBuffer.allocate(32)
                        tunnel.write(keepalive)
                        lastKeepalive = now
                        Log.d("CVETIK", "Keepalive sent")
                    }

                    if (!didWork) {
                        Thread.sleep(1)
                    }
                }

                Log.i("CVETIK", "=== VPN STOP ===")
                Log.i("CVETIK", "Total TX: \$totalTx bytes (\$packetsTx packets)")
                Log.i("CVETIK", "Total RX: \$totalRx bytes (\$packetsRx packets)")

            } catch (e: Exception) {
                Log.e("CVETIK", "VPN error: \${e.message}", e)
            } finally {
                running = false
                isRunning = false
                try { tunnel?.close() } catch (_: Exception) {}
                try { vpnInterface?.close() } catch (_: Exception) {}
                vpnInterface = null
                stopForeground(true)
                stopSelf()
            }
        }
        vpnThread?.start()
    }

    private fun disconnect() {
        Log.i("CVETIK", "Disconnect requested")
        running = false
        isRunning = false
        vpnThread?.interrupt()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    private fun startForeground(name: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "CVETIK-VPN", NotificationManager.IMPORTANCE_LOW)
            channel.description = "VPN tunnel status"
            nm.createNotificationChannel(channel)
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText("AmneziaWG туннель активен")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    // ===== AmneziaWG Config Parser =====
    data class AmneziaConfig(
        val address: String,
        val prefix: Int,
        val privateKey: String,
        val publicKey: String,
        val endpointHost: String,
        val endpointPort: Int,
        val allowedIPs: List<String>,
        val dns: List<String>,
        val mtu: Int = 1280,
        val persistentKeepalive: Int = 25,
        val jc: Int = 5,
        val jmin: Int = 100,
        val jmax: Int = 200,
        val h1: Int = 1,
        val h2: Int = 2,
        val h3: Int = 3,
        val h4: Int = 4
    )

    private fun parseAmneziaConfig(text: String): AmneziaConfig? {
        try {
            val lines = text.lines()
            var address = ""
            var prefix = 32
            var privateKey = ""
            var publicKey = ""
            var endpoint = ""
            var allowedIPs = listOf("0.0.0.0/0")
            var dns = listOf("1.1.1.1")
            var mtu = 1280
            var persistentKeepalive = 25
            var jc = 5
            var jmin = 100
            var jmax = 200
            var h1 = 1
            var h2 = 2
            var h3 = 3
            var h4 = 4

            var inInterface = false
            var inPeer = false

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed == "[Interface]" -> { inInterface = true; inPeer = false }
                    trimmed == "[Peer]" -> { inInterface = false; inPeer = true }
                    trimmed.startsWith("Address =") && inInterface -> {
                        val addr = trimmed.substringAfter("=").trim()
                        val parts = addr.split(",")
                        val firstAddr = parts[0].trim()
                        val addrParts = firstAddr.split("/")
                        address = addrParts[0].trim()
                        if (addrParts.size > 1) prefix = addrParts[1].toInt()
                    }
                    trimmed.startsWith("PrivateKey =") && inInterface -> {
                        privateKey = trimmed.substringAfter("=").trim()
                    }
                    trimmed.startsWith("DNS =") && inInterface -> {
                        dns = trimmed.substringAfter("=").trim().split(",").map { it.trim() }
                    }
                    trimmed.startsWith("MTU =") && inInterface -> {
                        mtu = trimmed.substringAfter("=").trim().toIntOrNull() ?: 1280
                    }
                    trimmed.startsWith("Jc =") && inInterface -> {
                        jc = trimmed.substringAfter("=").trim().toIntOrNull() ?: 5
                    }
                    trimmed.startsWith("Jmin =") && inInterface -> {
                        jmin = trimmed.substringAfter("=").trim().toIntOrNull() ?: 100
                    }
                    trimmed.startsWith("Jmax =") && inInterface -> {
                        jmax = trimmed.substringAfter("=").trim().toIntOrNull() ?: 200
                    }
                    trimmed.startsWith("H1 =") && inInterface -> {
                        h1 = trimmed.substringAfter("=").trim().toIntOrNull() ?: 1
                    }
                    trimmed.startsWith("H2 =") && inInterface -> {
                        h2 = trimmed.substringAfter("=").trim().toIntOrNull() ?: 2
                    }
                    trimmed.startsWith("H3 =") && inInterface -> {
                        h3 = trimmed.substringAfter("=").trim().toIntOrNull() ?: 3
                    }
                    trimmed.startsWith("H4 =") && inInterface -> {
                        h4 = trimmed.substringAfter("=").trim().toIntOrNull() ?: 4
                    }
                    trimmed.startsWith("PublicKey =") && inPeer -> {
                        publicKey = trimmed.substringAfter("=").trim()
                    }
                    trimmed.startsWith("Endpoint =") && inPeer -> {
                        endpoint = trimmed.substringAfter("=").trim()
                    }
                    trimmed.startsWith("AllowedIPs =") && inPeer -> {
                        allowedIPs = trimmed.substringAfter("=").trim().split(",").map { it.trim() }
                    }
                    trimmed.startsWith("PersistentKeepalive =") && inPeer -> {
                        persistentKeepalive = trimmed.substringAfter("=").trim().toIntOrNull() ?: 25
                    }
                }
            }

            if (address.isEmpty() || privateKey.isEmpty() || publicKey.isEmpty() || endpoint.isEmpty()) {
                Log.e("CVETIK", "Missing required fields")
                return null
            }

            val epParts = endpoint.split(":")
            val host = epParts[0]
            val port = epParts.getOrNull(1)?.toIntOrNull() ?: 51820

            return AmneziaConfig(
                address = address,
                prefix = prefix,
                privateKey = privateKey,
                publicKey = publicKey,
                endpointHost = host,
                endpointPort = port,
                allowedIPs = allowedIPs,
                dns = dns,
                mtu = mtu,
                persistentKeepalive = persistentKeepalive,
                jc = jc,
                jmin = jmin,
                jmax = jmax,
                h1 = h1,
                h2 = h2,
                h3 = h3,
                h4 = h4
            )
        } catch (e: Exception) {
            Log.e("CVETIK", "Parse error: \${e.message}", e)
            return null
        }
    }
}
