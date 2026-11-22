package com.niefeng.caipiao

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

/** 历史数据：近 3 年，来源新浪彩票，保存在平板上，今年的数据每次打开都同步最新 */
class HistoryScreen(private val a: MainActivity) : Screen {
    private val root = Ui.vertical(a)
    override val view: View get() = root
    private val store = HistoryStore(a)
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val years: List<Int> = Clock.year().let { listOf(it, it - 1, it - 2) }
    private var game = Game.FC3D
    private var year = years[0]
    private var token = 0
    private val tabsBox = Ui.vertical(a)
    private val gameTabs = Ui.horizontal(a)
    private val yearTabs = Ui.horizontal(a)
    private val status = Ui.text(a, "", 26f, Ui.GRAY)
    private val list = ListView(a)
    private val adapter = DrawAdapter()

    init {
        val top = Ui.horizontal(a)
        val p = Ui.dp(a, 16f)
        top.setPadding(p, p, p, p)
        top.addView(Ui.backButton(a) { a.show("home") })
        val title = Ui.text(a, "历史数据", 44f)
        title.setPadding(Ui.dp(a, 24f), 0, 0, 0)
        top.addView(title)
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        tabsBox.addView(gameTabs)
        tabsBox.addView(yearTabs)
        tabsBox.setPadding(Ui.dp(a, 12f), 0, Ui.dp(a, 12f), 0)
        root.addView(tabsBox, LinearLayout.LayoutParams(-1, -2))
        status.setPadding(Ui.dp(a, 24f), Ui.dp(a, 8f), Ui.dp(a, 24f), Ui.dp(a, 8f))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        list.adapter = adapter
        list.divider = null
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        status.setOnClickListener { load() }
    }

    override fun onOrientation(landscape: Boolean) {
        tabsBox.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
    }

    override fun onShow() {
        rebuildTabs()
        load()
    }

    override fun onDestroy() {
        io.shutdownNow()
    }

    private fun tab(label: String, selected: Boolean, col: Int, onClick: () -> Unit): TextView {
        val t = Ui.text(a, label, 32f, if (selected) Color.WHITE else col)
        t.gravity = Gravity.CENTER
        t.background = Ui.rounded(if (selected) col else Color.WHITE, Ui.dp(a, 40f).toFloat(), col, Ui.dp(a, 3f))
        val ph = Ui.dp(a, 24f)
        val pv = Ui.dp(a, 12f)
        t.setPadding(ph, pv, ph, pv)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun rebuildTabs() {
        gameTabs.removeAllViews()
        yearTabs.removeAllViews()
        val m = Ui.dp(a, 8f)
        for (g in Game.values()) {
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(m, m, m, m)
            gameTabs.addView(tab(g.title, g == game, if (g.isFc) Ui.FC_RED else Ui.TC_BLUE) {
                game = g
                rebuildTabs()
                load()
            }, lp)
        }
        for (y in years) {
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(m, m, m, m)
            yearTabs.addView(tab("${y}年", y == year, Ui.DARK) {
                year = y
                rebuildTabs()
                load()
            }, lp)
        }
    }

    private fun summary(l: List<Draw>, extra: String): String =
        "来源：新浪彩票　共 ${l.size} 期　最新到第 ${l.first().issue} 期$extra"

    private fun load() {
        token += 1
        val t = token
        val g = game
        val y = year
        status.text = "正在加载…"
        adapter.set(emptyList(), g)
        io.execute {
            val cached = try { store.read(g, y) } catch (e: Exception) { emptyList<Draw>() }
            if (cached.isNotEmpty()) {
                handler.post {
                    if (t == token) {
                        adapter.set(cached, g)
                        status.text = summary(cached, "（正在同步最新数据…）")
                    }
                }
            }
            val needFetch = y >= Clock.year() || !store.isFull(g, y) || cached.isEmpty()
            if (!needFetch) {
                handler.post { if (t == token) status.text = summary(cached, "") }
                return@execute
            }
            val fresh = try { SinaSource.history(g, y) } catch (e: Exception) { null }
            if (fresh.isNullOrEmpty()) {
                handler.post {
                    if (t == token) {
                        status.text = if (cached.isEmpty()) "暂无数据：获取失败，请检查网络后点这里重试"
                        else summary(cached, "（同步失败，显示的是之前保存的数据，点这里重试）")
                    }
                }
                return@execute
            }
            val merged = store.saveFull(g, y, fresh)
            handler.post {
                if (t == token) {
                    adapter.set(merged, g)
                    status.text = summary(merged, "")
                }
            }
        }
    }

    private inner class DrawAdapter : BaseAdapter() {
        private var items: List<Draw> = emptyList()
        private var g: Game = Game.FC3D

        fun set(l: List<Draw>, game: Game) {
            items = l
            g = game
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: Ui.horizontal(a).also { r ->
                val p = Ui.dp(a, 10f)
                r.setPadding(Ui.dp(a, 24f), p, p, p)
                val issue = Ui.text(a, "", 32f)
                issue.minWidth = Ui.dp(a, 300f)
                r.addView(issue, LinearLayout.LayoutParams(-2, -2))
                r.addView(Ui.horizontal(a), LinearLayout.LayoutParams(-2, -2))
            }
            val d = items[position]
            (row.getChildAt(0) as TextView).text = "第 ${d.issue} 期"
            val box = row.getChildAt(1) as LinearLayout
            box.removeAllViews()
            val col = if (g.isFc) Ui.FC_RED else Ui.TC_BLUE
            for (n in d.numbers) box.addView(Ui.ball(a, n, col, 64f))
            row.setBackgroundColor(if (position % 2 == 0) Color.WHITE else Color.parseColor("#FFF3D6"))
            return row
        }
    }
}
