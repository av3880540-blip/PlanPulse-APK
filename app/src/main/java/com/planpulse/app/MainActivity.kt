package com.planpulse.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.gms.ads.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var adView: AdView? = null
    private lateinit var adContainer: FrameLayout
    private val BANNER_AD_UNIT_ID = "ca-app-pub-7525892694932948/7375011720"

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PlanPulseCrash", throwable.stackTraceToString()))
            } catch (_: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#FAF6F0")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAF6F0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            addJavascriptInterface(object {
                @JavascriptInterface
                fun setProStatus(isPro: Boolean) {
                    runOnUiThread {
                        adContainer.visibility = if (isPro) View.GONE else View.VISIBLE
                    }
                }

                @JavascriptInterface
                fun copyText(text: String) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PlanPulse", text))
                }

                @JavascriptInterface
                fun scanRealRechargeSms(): String {
                    if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread {
                            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 101)
                        }
                        return "PERMISSION_DENIED"
                    }

                    val results = JSONArray()
                    try {
                        val uri = Uri.parse("content://sms/inbox")
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf("address", "body", "date"),
                            null,
                            null,
                            "date DESC"
                        )
                        val amountRegex = Regex("(?:Rs\\.?|INR|₹)\\s*(\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
                        val daysRegex = Regex("(\\d+)\\s*days?", RegexOption.IGNORE_CASE)

                        cursor?.use {
                            var count = 0
                            while (it.moveToNext() && count < 150) {
                                count++
                                val sender = it.getString(0) ?: ""
                                val body = it.getString(1) ?: ""
                                val dateMs = it.getLong(2)

                                val isTelecom = listOf(
                                    "JIO", "AIRTEL", "VI", "VODAFONE", "BSNL",
                                    "NETFLIX", "HOTSTAR", "SPOTIFY", "PRIME", "YOUTUBE"
                                ).any { key -> sender.uppercase().contains(key) || body.uppercase().contains(key) }

                                val isRecharge = body.contains("recharge", true) ||
                                        body.contains("plan", true) ||
                                        body.contains("valid", true) ||
                                        body.contains("subscription", true)

                                if (isTelecom && isRecharge) {
                                    val amt = amountRegex.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                                    val days = daysRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 28
                                    val obj = JSONObject().apply {
                                        put("sender", sender)
                                        put("amount", amt)
                                        put("validityDays", days)
                                        put("dateMs", dateMs)
                                        put("snippet", body.take(100))
                                    }
                                    results.put(obj)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                    return results.toString()
                }
            }, "AndroidAdBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.startsWith("https://planpulse.local/")) {
                        return try {
                            val inputStream = assets.open("index.html")
                            WebResourceResponse("text/html", "UTF-8", inputStream)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("https://planpulse.local/")) return false
                    if (url.startsWith("upi://") || url.startsWith("http://") || url.startsWith("https://")) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) { }
                        return true
                    }
                    return false
                }
            }
        }

        adContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(webView)
        rootLayout.addView(adContainer)
        setContentView(rootLayout)

        webView?.loadUrl("https://planpulse.local/index.html")

        window.decorView.postDelayed({
            try {
                MobileAds.initialize(this) {
                    loadBannerAd()
                }
            } catch (_: Throwable) { }
        }, 800)
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 101)
        }
    }

    private fun loadBannerAd() {
        try {
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)

            adView = AdView(this).apply {
                adUnitId = BANNER_AD_UNIT_ID
                setAdSize(adSize)
            }
            adContainer.removeAllViews()
            adContainer.addView(adView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            adView?.loadAd(AdRequest.Builder().build())
        } catch (_: Throwable) { }
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}
