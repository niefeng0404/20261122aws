package com.niefeng.caipiao

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
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
import androidx.core.widget.TextViewCompat

/** 只使用福彩、体彩官方直播页面。官方改版时只需修改这里的地址。 */
object LiveConfig {
    const val FC_URL = "https://www.cwl.gov.cn/ygkj/kjzb/"
    const val FC_HOME = "https://www.cwl.gov.cn/"
    const val TC_URL = "https://www.lottery.gov.cn/xytc/index.html"
    const val TC_HOME = "https://www.lottery.gov.cn/"

    /** 只允许打开这些官方网站 */
    private val OFFICIAL = listOf("cwl.gov.cn", "zhcw.com", "lottery.gov.cn", "sporttery.cn")

    fun isOfficial(host: String): Boolean = OFFICIAL.any { host == it || host.endsWith(".$it") }
}

/**
 * 注入官方页面的脚本（已在官网实测）：
 * 福彩：直播时保留 #divVideoContainer（直播视频），直播前保留 #divVideoCover（海报 + 倒计时）
 * 体彩：直播时保留 #liveVideo（直播视频），平时保留 #picture（开奖视频画面）
 * 其他内容全部隐藏，画面按比例放大铺满。官网切换画面时每 1 秒自动跟着切换。
 */
object LiveJs {
    val ISOLATE = """
(function(){
  if (window.__qwIso) { window.__qwIso.run(); return; }
  var S = {keep:null, w:1, h:1, reported:false, blocked:false};
  var H = location.hostname;
  var CAND = H.indexOf('cwl.gov.cn')>=0 ? ['divVideoContainer','divVideoCover']
           : H.indexOf('lottery.gov.cn')>=0 ? ['liveVideo','picture1','divBlock','picture'] : [];
  function rect(e){ return e.getBoundingClientRect(); }
  function shown(e){ if(!e||!e.isConnected) return false; var cs=getComputedStyle(e); if(cs.display==='none'||cs.visibility==='hidden') return false; var r=rect(e); return r.width>40 && r.height>40; }
  function natural(e){ var cs=getComputedStyle(e); return cs.display!=='none'; }
  function target(){
    for (var i=0;i<CAND.length;i++){ var e=document.getElementById(CAND[i]); if(e && natural(e)) return e; }
    return null;
  }
  function vp(){ var v=window.visualViewport; return {w:(v&&v.width)||window.innerWidth, h:(v&&v.height)||window.innerHeight}; }
  function isCand(e){ return e.id && CAND.indexOf(e.id)>=0; }
  function hideOthers(k){
    var n=k;
    while(n && n.parentElement){
      var p=n.parentElement;
      for(var i=0;i<p.children.length;i++){
        var c=p.children[i]; if(c===n || isCand(c)) continue;
        var tg=c.tagName; if(tg==='HEAD'||tg==='SCRIPT'||tg==='STYLE'||tg==='LINK'||tg==='META') continue;
        if(!c.hasAttribute('data-qw-h')) c.setAttribute('data-qw-h','1');
      }
      if(p!==document.body && p!==document.documentElement){ p.setAttribute('data-qw-p','1'); }
      n=p;
    }
  }
  function unkeep(){
    var k=S.keep; if(!k) return;
    k.removeAttribute('data-qw-keep');
    ['position','left','top','width','height','margin','z-index','transform','transform-origin','background'].forEach(function(x){ k.style.removeProperty(x); });
    S.keep=null;
  }
  function fit(){
    var k=S.keep; if(!k) return;
    var V=vp(), s=Math.min(V.w/S.w, V.h/S.h), x=(V.w-S.w*s)/2, y=(V.h-S.h*s)/2;
    k.style.setProperty('transform','translate('+x+'px,'+y+'px) scale('+s+')','important');
  }
  function style(){
    var st=document.getElementById('qw-style');
    if(!st){ st=document.createElement('style'); st.id='qw-style'; (document.head||document.documentElement).appendChild(st); }
    st.textContent='html,body{background:#000 !important;overflow:hidden !important;margin:0 !important;min-width:0 !important;}'
      +'[data-qw-h]{display:none !important;}'
      +'[data-qw-p]{transform:none !important;filter:none !important;perspective:none !important;contain:none !important;background:transparent !important;overflow:visible !important;}'
      +'[data-qw-keep] video{object-fit:contain !important;background:#000 !important;width:100% !important;height:100% !important;}';
    var m=document.querySelector('meta[name=viewport]');
    if(!m){ m=document.createElement('meta'); m.name='viewport'; (document.head||document.documentElement).appendChild(m); }
    m.content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';
  }
  function iso(k){
    unkeep();
    var r=rect(k); S.w=Math.max(r.width,1); S.h=Math.max(r.height,1);
    style(); hideOthers(k);
    k.setAttribute('data-qw-keep','1');
    var set=function(a,b){ k.style.setProperty(a,b,'important'); };
    set('position','fixed'); set('left','0'); set('top','0'); set('margin','0');
    set('width',S.w+'px'); set('height',S.h+'px'); set('z-index','2147483647');
    set('transform-origin','0 0'); set('background','#000');
    S.keep=k; fit();
  }
  function checkBlocked(){
    var t=(document.body&&document.body.innerText)||'';
    if(/请求已被|安全策略拦截|访问行为存在异常|请稍后重试|统一错误页|内容不见了/.test(t+document.title) && !target()){
      if(!S.blocked){ S.blocked=true; if(window.QW){ try{QW.onBlocked();}catch(e){} } }
      return true;
    }
    return false;
  }
  function run(){
    try{
      if(!document.body) return;
      if(checkBlocked()) return;
      var t=target(); if(!t) return;
      if(S.keep!==t){
        if(!shown(t) && S.keep===null){ /* 等它显示出来 */ }
        iso(t);
      } else { hideOthers(t); fit(); }
      if(!S.reported && window.QW){ S.reported=true; try{ QW.onIsolated(); }catch(e){} }
    }catch(e){}
  }
  window.__qwIso={run:run, state:S};
  window.__qwFit=fit;
  window.addEventListener('resize', fit);
  if(window.visualViewport) window.visualViewport.addEventListener('resize', fit);
  setInterval(run, 1000);
  run();
})();
""".trimIndent()
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
class LiveScreen(private val a: MainActivity) : Screen {
    private val handler = Handler(Looper.getMainLooper())
    private val root = FrameLayout(a)
    private val content = Ui.vertical(a)
    private val topBar = Ui.horizontal(a)
    private val capsule = Ui.horizontal(a)
    private val fcTab: TextView
    private val tcTab: TextView
    private val webBox = FrameLayout(a)
    private val loading: TextView
    private val fsButton: TextView
    private val customBox = FrameLayout(a)
    private val customExit: TextView
    private var web: WebView? = null
    private var gen = 0
    private var isolated = false
    private var pendingLoad: Runnable? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreen = false
    private var showingFc = true
    override val view: View get() = root

    private fun dp(v: Float) = Ui.dp(a, v)

    init {
        // ===== 顶部：⬅ 返回 | 福彩直播 · 体彩直播 胶囊 | ⟳ 刷新 =====
        val p = dp(12f)
        topBar.setPadding(p, p, p, p)
        topBar.setBackgroundColor(Ui.BG)
        topBar.addView(Ui.backButton(a) { a.show("home") }, LinearLayout.LayoutParams(-2, -2))

        capsule.background = Ui.rounded(Color.WHITE, dp(60f).toFloat(), Color.parseColor("#D0D0D0"), dp(2f))
        capsule.elevation = dp(6f).toFloat()
        val cp = dp(6f)
        capsule.setPadding(cp, cp, cp, cp)
        fcTab = tab("福彩直播") { requestLoad(true, false) }
        tcTab = tab("体彩直播") { requestLoad(false, false) }
        capsule.addView(fcTab, LinearLayout.LayoutParams(0, -2, 1f))
        capsule.addView(tcTab, LinearLayout.LayoutParams(0, -2, 1f))
        val capWrap = FrameLayout(a)
        capWrap.setPadding(dp(14f), dp(4f), dp(14f), dp(8f))
        capWrap.clipToPadding = false
        capWrap.addView(capsule, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        topBar.addView(capWrap, LinearLayout.LayoutParams(0, -2, 1f))

        val refresh = Ui.bigButton(a, "⟳ 刷新", Ui.GREEN, 32f) { requestLoad(showingFc, true) }
        refresh.setPadding(dp(24f), dp(14f), dp(24f), dp(14f))
        topBar.addView(refresh, LinearLayout.LayoutParams(-2, -2))
        content.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // ===== 直播画面 =====
        webBox.setBackgroundColor(Color.BLACK)
        loading = Ui.text(a, "", 34f, Color.WHITE)
        loading.gravity = Gravity.CENTER
        loading.setBackgroundColor(Color.BLACK)
        webBox.addView(loading, FrameLayout.LayoutParams(-1, -1))

        fsButton = Ui.bigButton(a, "⛶ 全屏", Color.parseColor("#B3000000"), 42f) { setFullscreen(!fullscreen) }
        fsButton.setPadding(dp(44f), dp(24f), dp(44f), dp(24f))
        val fsLp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
        fsLp.setMargins(dp(24f), dp(24f), dp(24f), dp(24f))
        webBox.addView(fsButton, fsLp)
        content.addView(webBox, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // 播放器自带的全屏
        customBox.setBackgroundColor(Color.BLACK)
        customBox.visibility = View.GONE
        customExit = Ui.bigButton(a, "退出全屏", Color.parseColor("#B3000000"), 42f) { hideCustom() }
        customExit.setPadding(dp(44f), dp(24f), dp(44f), dp(24f))
        root.addView(customBox, FrameLayout.LayoutParams(-1, -1))

        updateTabs()
    }

    private fun tab(label: String, onClick: () -> Unit): TextView {
        val t = Ui.text(a, label, 30f)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(dp(10f), dp(14f), dp(10f), dp(14f))
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(t, 18, 32, 1, TypedValue.COMPLEX_UNIT_SP)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun updateTabs() {
        val r = dp(60f).toFloat()
        fcTab.background = if (showingFc) Ui.rounded(Ui.FC_RED, r) else null
        fcTab.setTextColor(if (showingFc) Color.WHITE else Color.parseColor("#9E9E9E"))
        fcTab.elevation = if (showingFc) dp(3f).toFloat() else 0f
        tcTab.background = if (!showingFc) Ui.rounded(Ui.TC_BLUE, r) else null
        tcTab.setTextColor(if (!showingFc) Color.WHITE else Color.parseColor("#9E9E9E"))
        tcTab.elevation = if (!showingFc) dp(3f).toFloat() else 0f
    }

    private fun showLoading() {
        loading.text = "正在打开" + (if (showingFc) "福彩" else "体彩") + "官方直播…"
        loading.visibility = View.VISIBLE
        loading.bringToFront()
        fsButton.bringToFront()
    }

    /**
     * 切换 / 刷新：先立刻彻底关闭旧画面（释放视频解码器），
     * 等 0.4 秒内不再点击才打开新画面。快速来回点也不会卡住。
     */
    private fun requestLoad(fc: Boolean, force: Boolean) {
        if (!force && fc == showingFc && (web != null || pendingLoad != null)) return
        showingFc = fc
        updateTabs()
        destroyWeb()
        showLoading()
        pendingLoad?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            pendingLoad = null
            createAndLoad()
        }
        pendingLoad = r
        handler.postDelayed(r, 400)
    }

    private fun destroyWeb() {
        hideCustom()
        gen += 1
        isolated = false
        val w = web ?: return
        web = null
        try {
            w.stopLoading()
            w.loadUrl("about:blank")
            w.onPause()
            webBox.removeView(w)
            w.removeJavascriptInterface("QW")
            w.destroy()
        } catch (e: Exception) { }
    }

    private fun createAndLoad() {
        gen += 1
        val g = gen
        val w = WebView(a)
        setupWeb(w, g)
        webBox.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        web = w
        w.loadUrl(if (showingFc) LiveConfig.FC_URL else LiveConfig.TC_URL)
        // 页面较慢时，多次尝试识别直播画面（只在本机执行，不会多访问官网）
        for (t in longArrayOf(1500, 3000, 5000, 8000, 12000)) {
            handler.postDelayed({ if (g == gen) inject(w) }, t)
        }
        handler.postDelayed({
            if (g == gen && !isolated) {
                loading.text = "直播画面还没打开\n请检查网络后点右上角“⟳ 刷新”"
            }
        }, 20000)
    }

    private fun inject(w: WebView) {
        if (w !== web) return
        try { w.evaluateJavascript(LiveJs.ISOLATE, null) } catch (e: Exception) { }
    }

    private fun fitVideo() {
        web?.let { w -> w.post { try { w.evaluateJavascript("window.__qwFit&&window.__qwFit()", null) } catch (e: Exception) { } } }
    }

    inner class Bridge(private val g: Int) {
        @JavascriptInterface
        fun onIsolated() {
            handler.post {
                if (g == gen) {
                    isolated = true
                    loading.visibility = View.GONE
                }
            }
        }

        @JavascriptInterface
        fun onBlocked() {
            handler.post {
                if (g == gen) {
                    loading.text = "官网暂时拦截了访问\n请过几分钟再点右上角“⟳ 刷新”"
                    loading.visibility = View.VISIBLE
                    loading.bringToFront()
                    fsButton.bringToFront()
                }
            }
        }
    }

    private fun setupWeb(w: WebView, g: Int) {
        w.setBackgroundColor(Color.BLACK)
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // 使用电脑版页面：结构固定，已实测能准确找到直播画面
        s.userAgentString = DESKTOP_UA
        w.addJavascriptInterface(Bridge(g), "QW")
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: ""
                val host = uri.host ?: ""
                if ((scheme == "http" || scheme == "https") && LiveConfig.isOfficial(host)) return false
                Toast.makeText(a, "只能打开福彩、体彩官方页面", Toast.LENGTH_LONG).show()
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                inject(view)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode == 404) {
                    val home = if (showingFc) LiveConfig.FC_HOME else LiveConfig.TC_HOME
                    if (request.url.toString() != home) view.loadUrl(home)
                }
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress >= 80) inject(view)
            }

            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = v
                customCallback = callback
                customBox.addView(v, FrameLayout.LayoutParams(-1, -1))
                val lp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
                val m = dp(24f)
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

    /** 全屏：只剩直播视频铺满整个屏幕 */
    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        topBar.visibility = if (on) View.GONE else View.VISIBLE
        fsButton.text = if (on) "退出全屏" else "⛶ 全屏"
        fsButton.alpha = if (on) 0.75f else 1f
        setSystemBars(!on)
        fitVideo()
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

    override fun onOrientation(landscape: Boolean) {
        val lp = capsule.layoutParams as FrameLayout.LayoutParams
        lp.width = if (landscape) dp(560f) else FrameLayout.LayoutParams.MATCH_PARENT
        lp.gravity = Gravity.CENTER
        capsule.layoutParams = lp
        fitVideo()
    }

    override fun onShow() {
        a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (web == null && pendingLoad == null) requestLoad(showingFc, true)
    }

    /** 离开直播页时彻底关闭画面，不在后台播放声音 */
    override fun onHide() {
        a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pendingLoad?.let { handler.removeCallbacks(it) }
        pendingLoad = null
        if (fullscreen) setFullscreen(false)
        destroyWeb()
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
        onHide()
    }
}
