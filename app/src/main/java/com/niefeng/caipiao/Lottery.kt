package com.niefeng.caipiao

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 三种彩票。数据接口编号均已实测核对。 */
enum class Game(
    val title: String,
    val digits: Int,
    val sinaType: String,
    val neteaseType: String,
    val sinaChart: String,
    val drawTime: String,
    val isFc: Boolean
) {
    FC3D("福彩3D", 3, "102", "102", "3d", "21:15", true),
    PL3("排列三", 3, "202", "104", "p3", "21:25", false),
    PL5("排列五", 5, "203", "105", "p5", "21:25", false)
}

/** 一期开奖。只能通过 Draw.of 创建，格式不对一律丢弃，绝不补造。 */
class Draw private constructor(val issue: String, val numbers: List<Int>) {
    companion object {
        fun of(issue: String, raw: List<String>, game: Game): Draw? {
            if (!Regex("\\d{7}").matches(issue)) return null
            if (raw.size != game.digits) return null
            val nums = ArrayList<Int>()
            for (r in raw) {
                val n = r.trim().toIntOrNull() ?: return null
                if (n !in 0..9) return null
                nums.add(n)
            }
            return Draw(issue, nums)
        }
    }
}

/** 期号统一成 7 位，例如 网易"26311期" / 新浪"26311" -> 2026311 */
object Issue {
    fun normalize(raw: String): String? {
        val d = raw.filter { it.isDigit() }
        return when (d.length) {
            7 -> d
            5 -> "20$d"
            else -> null
        }
    }
}

sealed class CardState {
    object Empty : CardState()
    class Verified(val draw: Draw) : CardState()
    class Pending(val draw: Draw, val source: String) : CardState()
    class Conflict(val issue: String) : CardState()
}

/**
 * 核对规则：
 * 两家同一期且号码相同 -> 已核对；同一期号码不同 -> 不显示；
 * 只有一家出了最新一期 -> 先显示，标"待核对"；两家都没有 -> 暂无数据。
 */
fun decide(sina: Draw?, netease: Draw?): CardState {
    if (sina == null && netease == null) return CardState.Empty
    if (sina == null) return CardState.Pending(netease!!, NeteaseSource.NAME)
    if (netease == null) return CardState.Pending(sina, SinaSource.NAME)
    if (sina.issue == netease.issue) {
        return if (sina.numbers == netease.numbers) CardState.Verified(sina)
        else CardState.Conflict(sina.issue)
    }
    return if (sina.issue > netease.issue) CardState.Pending(sina, SinaSource.NAME)
    else CardState.Pending(netease, NeteaseSource.NAME)
}

object Clock {
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /** 每晚 21:12 - 22:30 为开奖时段，每 10 秒刷新 */
    fun isHot(): Boolean {
        val c = Calendar.getInstance(tz)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return m in (21 * 60 + 12)..(22 * 60 + 30)
    }

    fun now(): String {
        val f = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        f.timeZone = tz
        return f.format(Date())
    }

    fun year(): Int = Calendar.getInstance(tz).get(Calendar.YEAR)

    /** 解析 "2026-11-21 21:16:46"（北京时间），无效返回 null */
    fun parse(s: String?): Long? {
        if (s.isNullOrBlank() || s.startsWith("0000")) return null
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            f.timeZone = tz
            f.isLenient = false
            f.parse(s)?.time
        } catch (e: Exception) {
            null
        }
    }

    /** 例如 "11月21日 21:16:46" */
    fun fmtFull(ms: Long): String {
        val f = SimpleDateFormat("M月d日 HH:mm:ss", Locale.CHINA)
        f.timeZone = tz
        return f.format(Date(ms))
    }

    /** 例如 "21:15" */
    fun fmtHm(ms: Long): String {
        val f = SimpleDateFormat("HH:mm", Locale.CHINA)
        f.timeZone = tz
        return f.format(Date(ms))
    }
}
