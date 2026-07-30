package com.cvetik.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var pendingEndpoint = ""
    private var pendingName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.addJavascriptInterface(VpnBridge(this), "AndroidVpn")
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    fun requestVpnPermission(endpoint: String, name: String) {
        pendingEndpoint = endpoint
        pendingName = name
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1001)
        } else {
            onVpnPermissionGranted()
        }
    }

    private fun onVpnPermissionGranted() {
        try {
            val intent = Intent(this, CvetikVpnService::class.java)
            intent.action = CvetikVpnService.ACTION_CONNECT
            intent.putExtra("endpoint", pendingEndpoint)
            intent.putExtra("name", pendingName)
            startService(intent)
            webView.evaluateJavascript("onVpnConnected()", null)
        } catch (e: Exception) {
            Log.e("CVETIK", "Failed to start VPN: ${e.message}")
            webView.evaluateJavascript("onVpnError('${e.message?.replace("'", "\'")}')", null)
        }
    }

    private fun onVpnPermissionDenied() {
        webView.evaluateJavascript("onVpnDenied()", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                onVpnPermissionGranted()
            } else {
                onVpnPermissionDenied()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
