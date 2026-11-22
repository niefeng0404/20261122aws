package com.niefeng.caipiao

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** 统一的大字体、高对比样式，方便老年人看 */
object Ui {
    val BG = Color.parseColor("#FFF8E7")
    val TEXT = Color.parseColor("#1A1A1A")
    val FC_RED = Color.parseColor("#C62828")
    val TC_BLUE = Color.parseColor("#1565C0")
    val GREEN = Color.parseColor("#2E7D32")
    val ORANGE = Color.parseColor("#E65100")
    val GRAY = Color.parseColor("#555555")
    val DARK = Color.parseColor("#424242")

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun text(c: Context, s: String, sp: Float, color: Int = TEXT, bold: Boolean = true): TextView {
        val t = TextView(c)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        t.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return t
    }

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radiusPx
        if (strokeColor != null) d.setStroke(strokePx, strokeColor)
        return d
    }

    fun bigButton(c: Context, label: String, color: Int, sp: Float = 40f, onClick: () -> Unit): TextView {
        val b = text(c, label, sp, Color.WHITE)
        b.gravity = Gravity.CENTER
        b.background = rounded(color, dp(c, 24f).toFloat())
        val p = dp(c, 20f)
        b.setPadding(p, p, p, p)
        b.isClickable = true
        b.isFocusable = true
        b.setOnClickListener { onClick() }
        return b
    }

    fun backButton(c: Context, onClick: () -> Unit): TextView {
        val b = bigButton(c, "⬅ 返回", DARK, 32f, onClick)
        val ph = dp(c, 28f)
        val pv = dp(c, 14f)
        b.setPadding(ph, pv, ph, pv)
        return b
    }

    fun ball(c: Context, n: Int, color: Int, sizeDp: Float): TextView {
        val t = text(c, n.toString(), sizeDp * 0.5f, Color.WHITE)
        t.gravity = Gravity.CENTER
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        t.background = d
        val s = dp(c, sizeDp)
        val lp = LinearLayout.LayoutParams(s, s)
        val m = dp(c, 6f)
        lp.setMargins(m, m, m, m)
        t.layoutParams = lp
        return t
    }

    fun vertical(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    fun horizontal(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }
}
