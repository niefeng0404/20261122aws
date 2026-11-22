package com.niefeng.caipiao

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 最新开奖：同时查新浪和网易，开奖时段每 10 秒刷新 */
class LatestScreen(private val a: MainActivity) : Screen {
    private val handler = Handler(Looper.getMainLooper())
    private val net = Executors.newCachedThreadPool()
    private val store = HistoryStore(a)
    private val cards = Game.values().map { GameCard(a, it) }
    private val hint = Ui.text(a, "", 24f, Ui.GRAY, false)
    private val root = Ui.vertical(a)
    private var running = false
    override val view: View get() = root

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            refreshAll()
            handler.postDelayed(this, if (Clock.isHot()) 10_000L else 60_000L)
        }
    }

    init {
        val top = Ui.horizontal(a)
        val p = Ui.dp(a, 16f)
        top.setPadding(p, p, p, p)
        top.addView(Ui.backButton(a) { a.show("home") })
        val title = Ui.text(a, "最新开奖", 44f)
        title.setPadding(Ui.dp(a, 24f), 0, 0, 0)
        top.addView(title)
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        hint.setPadding(Ui.dp(a, 28f), 0, Ui.dp(a, 28f), Ui.dp(a, 4f))
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))
        val box = Ui.vertical(a)
        val m = Ui.dp(a, 12f)
        for (c in cards) {
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(m, m, m, m)
            box.addView(c.view, lp)
        }
        val scroll = ScrollView(a)
        scroll.addView(box, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun onOrientation(landscape: Boolean) {
        cards.forEach { it.setLandscape(landscape) }
    }

    override fun onShow() {
        if (running) return
        running = true
        handler.post(tick)
    }

    override fun onHide() {
        running = false
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        onHide()
        net.shutdownNow()
    }

    private fun refreshAll() {
        hint.text = if (Clock.isHot()) "开奖时段：每 10 秒自动刷新"
        else "每 1 分钟自动刷新（每晚 21:12 起改为每 10 秒）"
        for (card in cards) {
            if (!card.busy.compareAndSet(false, true)) continue
            val g = card.game
            try {
                val fs = net.submit(Callable<Draw?> { fetch { SinaSource.latest(g) } })
                val fn = net.submit(Callable<Draw?> { fetch { NeteaseSource.latest(g) } })
                net.execute {
                    val s = try { fs.get() } catch (e: Exception) { null }
                    val n = try { fn.get() } catch (e: Exception) { null }
                    val st = decide(s, n)
                    if (st is CardState.Verified) {
                        try { store.merge(g, st.draw) } catch (e: Exception) { }
                    }
                    val at = Clock.now()
                    handler.post {
                        card.render(st, at)
                        card.busy.set(false)
                    }
                }
            } catch (e: Exception) {
                card.busy.set(false)
            }
        }
    }

    private fun fetch(block: () -> Draw?): Draw? = try { block() } catch (e: Exception) { null }
}

class GameCard(private val c: Context, val game: Game) {
    val busy = AtomicBoolean(false)
    val view = LinearLayout(c)
    private val color = if (game.isFc) Ui.FC_RED else Ui.TC_BLUE
    private val info = Ui.vertical(c)
    private val issueText = Ui.text(c, "", 34f)
    private val balls = Ui.horizontal(c)
    private val status = Ui.text(c, "正在获取…", 28f, Ui.GRAY)
    private val meta = Ui.text(c, "", 22f, Ui.GRAY, false)
    private var landscape = false
    private var shown: List<Int>? = null

    init {
        val p = Ui.dp(c, 20f)
        view.setPadding(p, p, p, p)
        view.background = Ui.rounded(Color.WHITE, Ui.dp(c, 20f).toFloat(), color, Ui.dp(c, 3f))
        view.gravity = Gravity.CENTER_VERTICAL
        info.addView(Ui.text(c, (if (game.isFc) "福彩 " else "体彩 ") + game.title, 44f, color))
        info.addView(Ui.text(c, "每天 ${game.drawTime} 开奖", 24f, Ui.GRAY, false))
        info.addView(issueText)
        info.addView(status)
        info.addView(meta)
        view.addView(info)
        view.addView(balls)
        setLandscape(false)
    }

    fun setLandscape(l: Boolean) {
        landscape = l
        view.orientation = if (l) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        info.layoutParams = if (l) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2)
        val blp = LinearLayout.LayoutParams(-2, -2)
        if (!l) blp.topMargin = Ui.dp(c, 8f)
        balls.layoutParams = blp
        shown?.let { drawBalls(it) }
    }

    private fun ballSize(): Float = if (landscape) 80f else 88f

    private fun drawBalls(n: List<Int>) {
        balls.removeAllViews()
        for (x in n) balls.addView(Ui.ball(c, x, color, ballSize()))
        shown = n
    }

    private fun clearBalls() {
        balls.removeAllViews()
        shown = null
    }

    private fun showDraw(d: Draw) {
        issueText.text = "第 ${d.issue} 期"
        if (d.numbers != shown) drawBalls(d.numbers)
    }

    private fun setStatus(s: String, col: Int) {
        status.text = s
        status.setTextColor(col)
    }

    fun render(st: CardState, at: String) {
        when (st) {
            is CardState.Verified -> {
                showDraw(st.draw)
                setStatus("✅ 新浪、网易 两家核对一致", Ui.GREEN)
                meta.text = "来源：新浪 + 网易　　获取时间 $at"
            }
            is CardState.Pending -> {
                showDraw(st.draw)
                setStatus("⏳ 待核对（目前只有${st.source}出了号码）", Ui.ORANGE)
                meta.text = "来源：${st.source}　　获取时间 $at"
            }
            is CardState.Conflict -> {
                issueText.text = "第 ${st.issue} 期"
                clearBalls()
                setStatus("⚠ 两个来源号码不一致，暂不显示，正在重新获取", Ui.FC_RED)
                meta.text = "获取时间 $at"
            }
            is CardState.Empty -> {
                issueText.text = ""
                clearBalls()
                setStatus("暂无数据，正在获取…", Ui.GRAY)
                meta.text = "尝试时间 $at"
            }
        }
    }
}
