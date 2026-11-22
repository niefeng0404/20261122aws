package com.niefeng.caipiao

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** 只使用福彩、体彩官方直播页面。官方改版时只需修改这里的地址。 */
object LiveConfig {
    const val FC_URL = "https://www.cwl.gov.cn/ygkj/kjzb/"
    const val FC_HOME = "https://www.cwl.gov.cn/"
    const val TC_URL = "https://www.lottery.gov.cn/kj/kjzb.html"
    const val TC_HOME = "https://www.lottery.gov.cn/"

    /** 只允许打开这些官方网站 */
    private val OFFICIAL = listOf("cwl.gov.cn", "zhcw.com", "lottery.gov.cn", "sporttery.cn")

    fun isOfficial(host: String): Boolean = OFFICIAL.any { host == it || host.endsWith(".$it") }
}

@SuppressLint("SetJavaScriptEnabled")
class LiveScreen(private val a: MainActivity) : Screen {
    private val root = FrameLayout(a)
    private val content = Ui.vertical(a)
    private val topBar = Ui.horizontal(a)
    private val capsule = Ui.horizontal(a)
    private val fcTab: TextView
    private val tcTab: TextView
    private val web = WebView(a)
    private val fsButton: TextView
    private val customBox = FrameLayout(a)
    private val customExit: TextView
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreen = false
    private var showingFc = true
    private var loaded = false
    override val view: View get() = root

    init {
        // 顶部：返回按钮 + 福彩/体彩 胶囊切换
        val p = Ui.dp(a, 14f)
        topBar.setPadding(p, p, p, p)
        topBar.setBackgroundColor(Ui.BG)
        topBar.addView(Ui.backButton(a) { a.show("home") })
        fcTab = tab("福彩直播") { switchTo(true) }
        tcTab = tab("体彩直播") { switchTo(false) }
        capsule.background = Ui.rounded(Color.WHITE, Ui.dp(a, 50f).toFloat(), Ui.DARK, Ui.dp(a, 3f))
        val cp = Ui.dp(a, 5f)
        capsule.setPadding(cp, cp, cp, cp)
        capsule.addView(fcTab)
        capsule.addView(tcTab)
        val capWrap = FrameLayout(a)
        capWrap.addView(capsule, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        topBar.addView(capWrap, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // 直播画面 + 超大全屏按钮
        val webBox = FrameLayout(a)
        webBox.setBackgroundColor(Color.BLACK)
        webBox.addView(web, FrameLayout.LayoutParams(-1, -1))
        fsButton = Ui.bigButton(a, "全屏", Color.parseColor("#CC000000"), 40f) { setFullscreen(!fullscreen) }
        val fp = Ui.dp(a, 26f)
        fsButton.setPadding(fp * 2, fp, fp * 2, fp)
        val fsLp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
        val fm = Ui.dp(a, 24f)
        fsLp.setMargins(fm, fm, fm, fm)
        webBox.addView(fsButton, fsLp)
        content.addView(webBox, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // 网页自带的视频全屏
        customBox.setBackgroundColor(Color.BLACK)
        customBox.visibility = View.GONE
        customExit = Ui.bigButton(a, "退出全屏", Color.parseColor("#CC000000"), 40f) { hideCustom() }
        customExit.setPadding(fp * 2, fp, fp * 2, fp)
        root.addView(customBox, FrameLayout.LayoutParams(-1, -1))

        setupWeb()
        updateTabs()
    }

    private fun tab(label: String, onClick: () -> Unit): TextView {
        val t = Ui.text(a, label, 32f)
        t.gravity = Gravity.CENTER
        val ph = Ui.dp(a, 34f)
        val pv = Ui.dp(a, 12f)
        t.setPadding(ph, pv, ph, pv)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun updateTabs() {
        val r = Ui.dp(a, 50f).toFloat()
        fcTab.background = if (showingFc) Ui.rounded(Ui.FC_RED, r) else null
        fcTab.setTextColor(if (showingFc) Color.WHITE else Ui.FC_RED)
        tcTab.background = if (!showingFc) Ui.rounded(Ui.TC_BLUE, r) else null
        tcTab.setTextColor(if (!showingFc) Color.WHITE else Ui.TC_BLUE)
    }

    private fun switchTo(fc: Boolean) {
        if (fc == showingFc && loaded) return
        showingFc = fc
        updateTabs()
        loaded = true
        web.loadUrl(if (fc) LiveConfig.FC_URL else LiveConfig.TC_URL)
    }

    private fun setupWeb() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: ""
                val host = uri.host ?: ""
                if ((scheme == "http" || scheme == "https") && LiveConfig.isOfficial(host)) return false
                Toast.makeText(a, "只能打开福彩、体彩官方页面", Toast.LENGTH_LONG).show()
                return true
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                // 直播页地址失效时，改为打开官方首页
                if (request.isForMainFrame && errorResponse.statusCode == 404) {
                    val home = if (showingFc) LiveConfig.FC_HOME else LiveConfig.TC_HOME
                    if (request.url.toString() != home) view.loadUrl(home)
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = v
                customCallback = callback
                customBox.addView(v, FrameLayout.LayoutParams(-1, -1))
                val lp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
                val m = Ui.dp(a, 24f)
                lp.setMargins(m, m, m, m)
                customBox.addView(customExit, lp)
                customBox.visibility = View.VISIBLE
                setSystemBars(false)
            }

            override fun onHideCustomView() {
                hideCustom()
            }
        }
    }

    private fun hideCustom() {
        if (customView == null) return
        customView = null
        customBox.removeAllViews()
        customBox.visibility = View.GONE
        val cb = customCallback
        customCallback = null
        try { cb?.onCustomViewHidden() } catch (e: Exception) { }
        setSystemBars(!fullscreen)
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        topBar.visibility = if (on) View.GONE else View.VISIBLE
        fsButton.text = if (on) "退出全屏" else "全屏"
        setSystemBars(!on)
    }

    private fun setSystemBars(visible: Boolean) {
        val w = a.window
        if (Build.VERSION.SDK_INT >= 30) {
            val c = w.insetsController ?: return
            if (visible) {
                c.show(WindowInsets.Type.systemBars())
            } else {
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = if (visible) View.SYSTEM_UI_FLAG_VISIBLE else
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onShow() {
        a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web.onResume()
        if (!loaded) switchTo(showingFc)
    }

    override fun onHide() {
        a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideCustom()
        if (fullscreen) setFullscreen(false)
        web.onPause()
    }

    override fun onBack(): Boolean {
        if (customView != null) {
            hideCustom()
            return true
        }
        if (fullscreen) {
            setFullscreen(false)
            return true
        }
        return false
    }

    override fun onDestroy() {
        web.destroy()
    }
}
