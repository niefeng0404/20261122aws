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
    const val TC_URL = "https://www.lottery.gov.cn/kj/kjzb.html"
    const val TC_HOME = "https://www.lottery.gov.cn/"

    /** 只允许打开这些官方网站 */
    private val OFFICIAL = listOf("cwl.gov.cn", "zhcw.com", "lottery.gov.cn", "sporttery.cn")

    fun isOfficial(host: String): Boolean = OFFICIAL.any { host == it || host.endsWith(".$it") }
}

/**
 * 注入官方页面的脚本：自动找到直播画面（视频 / 播放器 / 直播倒计时海报），
 * 把它放大铺满，页面上其他内容全部隐藏。页面变化时每 1.5 秒自动重新检查。
 */
object LiveJs {
    val ISOLATE = """
(function(){
  if (window.__qwIso) { window.__qwIso.run(); return; }
  var S = {keep:null, w:1, h:1};
  function rect(e){ return e.getBoundingClientRect(); }
  function all(sel){ return Array.prototype.slice.call(document.querySelectorAll(sel)); }
  function vis(e){ var r=rect(e); if(r.width<2||r.height<2) return false; var cs=getComputedStyle(e); return cs.display!=='none' && cs.visibility!=='hidden'; }
  function vw(){ return document.documentElement.clientWidth || window.innerWidth || 1; }
  function okSize(e){ var r=rect(e), W=vw(); return r.width>=W*0.45 && r.height>=90 && r.width<=W*1.05+4 && r.height<=Math.max(window.innerHeight*1.3,500); }
  function best(list){ var b=null, ba=0; for (var i=0;i<list.length;i++){ var e=list[i]; if(!vis(e)||!okSize(e)) continue; var r=rect(e), a=r.width*r.height; if(a>ba){ ba=a; b=e; } } return b; }
  function byText(re){
    if(!document.body) return null;
    var w=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null), n;
    while((n=w.nextNode())){ if(re.test(n.nodeValue)){ var e=n.parentElement; while(e && e!==document.body){ if(vis(e)&&okSize(e)) return e; e=e.parentElement; } } }
    return null;
  }
  function pick(){
    var v=best(all('video')); if(v) return v;
    var f=best(all('iframe')); if(f) return f;
    var t=byText(/距离直播|直播倒计时|即将开始/); if(t) return t;
    var re=/video|player|live|zhibo|kjzb|poster|countdown/i;
    return best(all('div,section,img,canvas,a').filter(function(e){ var c=(typeof e.className==='string')?e.className:''; return re.test((e.id||'')+' '+c); }));
  }
  function grow(e){
    var r=rect(e), cur=e;
    while(cur.parentElement && cur.parentElement!==document.body && cur.parentElement!==document.documentElement){
      var pr=rect(cur.parentElement);
      if(pr.height<=r.height*1.08+12 && pr.width<=r.width*1.1+24) cur=cur.parentElement; else break;
    }
    return cur;
  }
  function findTitle(k){
    var kr=rect(k), bestEl=null, bestD=1e9;
    all('h1,h2,h3,h4,h5,p,span,div').forEach(function(e){
      if(k.contains(e) || e.contains(k) || !vis(e)) return;
      var t=(e.innerText||'').trim();
      if(t.length<4 || t.length>60 || !/\d{4}[-年.\/]\d{1,2}/.test(t)) return;
      if(e.children.length>3) return;
      var r=rect(e), d=kr.top-r.bottom;
      if(d<-6 || d>160) return;
      if(d<bestD){ bestD=d; bestEl=e; }
    });
    return bestEl ? (bestEl.innerText||'').trim() : '';
  }
  function hideOthers(k){
    var n=k;
    while(n && n.parentElement){
      var p=n.parentElement;
      for(var i=0;i<p.children.length;i++){
        var c=p.children[i]; if(c===n) continue;
        var tg=c.tagName; if(tg==='HEAD'||tg==='SCRIPT'||tg==='STYLE'||tg==='LINK'||tg==='META') continue;
        if(!c.hasAttribute('data-qw-h')) c.setAttribute('data-qw-h', c.style.getPropertyValue('display')||'');
        c.style.setProperty('display','none','important');
      }
      if(p!==document.body && p!==document.documentElement){
        ['transform','filter','perspective','contain'].forEach(function(x){ p.style.setProperty(x,'none','important'); });
        p.style.setProperty('background','transparent','important');
      }
      n=p;
    }
  }
  function restore(){
    all('[data-qw-h]').forEach(function(c){ var d=c.getAttribute('data-qw-h'); c.removeAttribute('data-qw-h'); if(d) c.style.setProperty('display',d); else c.style.removeProperty('display'); });
    if(S.keep){ S.keep.removeAttribute('data-qw-keep'); ['position','left','top','width','height','margin','z-index','transform','transform-origin','background'].forEach(function(x){ S.keep.style.removeProperty(x); }); }
    S.keep=null;
  }
  function fit(){
    var k=S.keep; if(!k) return;
    var W=window.innerWidth, H=window.innerHeight;
    var s=Math.min(W/S.w, H/S.h), x=(W-S.w*s)/2, y=(H-S.h*s)/2;
    k.style.setProperty('transform','translate('+x+'px,'+y+'px) scale('+s+')','important');
  }
  function iso(k){
    var r=rect(k); S.w=Math.max(r.width,1); S.h=Math.max(r.height,1);
    var st=document.getElementById('qw-style');
    if(!st){ st=document.createElement('style'); st.id='qw-style'; (document.head||document.documentElement).appendChild(st); }
    st.textContent='html,body{background:#000 !important;overflow:hidden !important;}[data-qw-keep] video{object-fit:contain !important;background:#000 !important;}';
    hideOthers(k);
    k.setAttribute('data-qw-keep','1');
    var set=function(a,b){ k.style.setProperty(a,b,'important'); };
    set('position','fixed'); set('left','0'); set('top','0'); set('margin','0');
    set('width',S.w+'px'); set('height',S.h+'px'); set('z-index','2147483647');
    set('transform-origin','0 0'); set('background','#000');
    S.keep=k; fit();
  }
  function run(){
    try{
      if(S.keep && !S.keep.isConnected) restore();
      if(S.keep){
        var hasV = S.keep.tagName==='VIDEO' || S.keep.tagName==='IFRAME' || S.keep.querySelector('video,iframe');
        if(!hasV){
          var fresh=all('video').filter(function(v){ return !S.keep.contains(v) && !v.__qwSeen; });
          if(fresh.length){ fresh.forEach(function(v){ v.__qwSeen=true; }); restore(); }
        }
      }
      if(S.keep){ hideOthers(S.keep); fit(); return; }
      var c=pick(); if(!c) return;
      all('video').forEach(function(v){ v.__qwSeen=true; });
      var k=grow(c), t=findTitle(k);
      iso(k);
      if(window.QW){ try{ QW.onTitle(t); QW.onIsolated(); }catch(e){} }
    }catch(e){}
  }
  window.__qwIso={run:run};
  window.__qwFit=fit;
  window.addEventListener('resize', fit);
  setInterval(run, 1500);
  run();
})();
""".trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
class LiveScreen(private val a: MainActivity) : Screen {
    private val handler = Handler(Looper.getMainLooper())
    private val root = FrameLayout(a)
    private val content = Ui.vertical(a)
    private val topBar = Ui.horizontal(a)
    private val capsule = Ui.horizontal(a)
    private val fcTab: TextView
    private val tcTab: TextView
    private val titleText: TextView
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

        // 体彩开奖日期标题（从官方页面取出，单独大字显示）
        titleText = Ui.text(a, "", 30f, Color.WHITE)
        titleText.gravity = Gravity.CENTER
        titleText.setBackgroundColor(Color.BLACK)
        titleText.setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        titleText.maxLines = 1
        titleText.visibility = View.GONE
        content.addView(titleText, LinearLayout.LayoutParams(-1, -2))

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
        titleText.visibility = View.GONE
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
        // 页面较慢时，多次尝试识别直播画面
        for (t in longArrayOf(2000, 4000, 6000, 9000, 12000)) {
            handler.postDelayed({ if (g == gen) inject(w) }, t)
        }
        handler.postDelayed({
            if (g == gen && !isolated) {
                loading.visibility = View.GONE
                Toast.makeText(a, "没有识别到直播画面，先显示官方完整页面", Toast.LENGTH_LONG).show()
            }
        }, 15000)
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
        fun onTitle(t: String?) {
            handler.post {
                if (g == gen) {
                    val s = (t ?: "").trim().take(40)
                    titleText.text = s
                    titleText.visibility = if (s.isEmpty() || fullscreen) View.GONE else View.VISIBLE
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
        titleText.visibility = if (on || titleText.text.isNullOrEmpty()) View.GONE else View.VISIBLE
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
