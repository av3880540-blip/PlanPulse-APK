package com.planpulse.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.gms.ads.*

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var adView: AdView? = null
    private lateinit var adContainer: FrameLayout
    private val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Global crash catcher: copies stacktrace to clipboard if anything fails
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PlanPulseCrash", throwable.stackTraceToString()))
            } catch (_: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        super.onCreate(savedInstanceState)

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
            settings.allowFileAccess = true

            addJavascriptInterface(object {
                @JavascriptInterface
                fun setProStatus(isPro: Boolean) {
                    runOnUiThread {
                        adContainer.visibility = if (isPro) View.GONE else View.VISIBLE
                    }
                }
            }, "AndroidAdBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
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

        webView?.loadUrl("file:///android_asset/index.html")

        // Initialize AdMob safely after UI is visible
        window.decorView.postDelayed({
            try {
                MobileAds.initialize(this) {
                    loadBannerAd()
                }
            } catch (e: Throwable) {
                Toast.makeText(this, "Ad init skipped: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, 800)
    }

    private fun loadBannerAd() {
        try {
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)

            adView = AdView(this).apply {
                adUnitId = TEST_BANNER_ID
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
