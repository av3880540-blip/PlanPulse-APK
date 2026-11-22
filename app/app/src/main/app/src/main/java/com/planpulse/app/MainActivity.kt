package com.planpulse.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var adView: AdView? = null
    private lateinit var adContainer: FrameLayout

    // Official Google Test Banner ID (Replace with your real Banner Unit ID before publishing)
    private val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout: WebView on top, AdMob Banner docked cleanly at bottom
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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

            // Bridge so JS can hide banner when Pro is unlocked
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

        // Initialize AdMob SDK & Load Adaptive Banner
        MobileAds.initialize(this) {
            loadBannerAd()
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun loadBannerAd() {
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
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}
