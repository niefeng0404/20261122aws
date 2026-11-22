package com.niefeng.caipiao

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

interface Screen {
    val view: View
    fun onShow() {}
    fun onHide() {}
    fun onOrientation(landscape: Boolean) {}
    /** 返回 true 表示已处理返回键 */
    fun onBack(): Boolean = false
    fun onDestroy() {}
}

/**
 * 只有一个页面容器，各功能页面在里面切换。
 * 旋转屏幕时页面不重建（见 AndroidManifest 的 configChanges），只调整排列方式。
 */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var current: Screen? = null
    private val screens = HashMap<String, Screen>()
    lateinit var updater: UpdateUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        setContentView(root)
        updater = UpdateUi(this)
        show("home")
        // 打开 App 时自动检查一次更新（没有新版本时不打扰）
        updater.check(manual = false)
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun show(name: String) {
        current?.onHide()
        root.removeAllViews()
        val s = screens.getOrPut(name) {
            when (name) {
                "latest" -> LatestScreen(this)
                "live" -> LiveScreen(this)
                "history" -> HistoryScreen(this)
                else -> HomeScreen(this)
            }
        }
        current = s
        root.addView(s.view, FrameLayout.LayoutParams(-1, -1))
        s.onOrientation(isLandscape())
        s.onShow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        current?.onOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val s = current
        if (s != null && s.onBack()) return
        if (s != null && s !is HomeScreen) {
            show("home")
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        current?.onShow()
        if (::updater.isInitialized) updater.onResume()
    }

    override fun onPause() {
        current?.onHide()
        super.onPause()
    }

    override fun onDestroy() {
        screens.values.forEach { it.onDestroy() }
        super.onDestroy()
    }
}

class HomeScreen(private val a: MainActivity) : Screen {
    private val root = Ui.vertical(a)
    private val buttons = Ui.vertical(a)
    override val view: View get() = root

    init {
        root.gravity = Gravity.CENTER
        val p = Ui.dp(a, 32f)
        root.setPadding(p, p, p, p)
        val title = Ui.text(a, "福彩体彩助手", 56f)
        title.gravity = Gravity.CENTER
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        val sub = Ui.text(a, "福彩3D · 排列三 · 排列五", 30f, Ui.GRAY)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, Ui.dp(a, 8f), 0, Ui.dp(a, 24f))
        root.addView(sub, LinearLayout.LayoutParams(-1, -2))
        buttons.addView(Ui.bigButton(a, "最新开奖", Ui.FC_RED, 52f) { a.show("latest") })
        buttons.addView(Ui.bigButton(a, "开奖直播", Ui.ORANGE, 52f) { a.show("live") })
        buttons.addView(Ui.bigButton(a, "历史数据", Ui.TC_BLUE, 52f) { a.show("history") })
        root.addView(buttons, LinearLayout.LayoutParams(-1, 0, 1f))

        // 底部：版本号 + 检查更新
        val bottom = Ui.horizontal(a)
        bottom.gravity = Gravity.CENTER
        bottom.setPadding(0, Ui.dp(a, 12f), 0, 0)
        val ver = Ui.text(a, "当前版本 ${Updater.currentName(a)}", 28f, Ui.GRAY)
        ver.setPadding(0, 0, Ui.dp(a, 24f), 0)
        bottom.addView(ver)
        val upd = Ui.bigButton(a, "检查更新", Ui.GREEN, 30f) { a.updater.check(manual = true) }
        val up = Ui.dp(a, 14f)
        upd.setPadding(up * 2, up, up * 2, up)
        bottom.addView(upd)
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        a.updater.onStatus = { s -> ver.text = s }
    }

    override fun onOrientation(landscape: Boolean) {
        buttons.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val m = Ui.dp(a, 14f)
        for (i in 0 until buttons.childCount) {
            val lp = if (landscape) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f)
            lp.setMargins(m, m, m, m)
            buttons.getChildAt(i).layoutParams = lp
        }
    }
}
