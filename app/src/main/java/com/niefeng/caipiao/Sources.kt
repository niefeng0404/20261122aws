package com.niefeng.caipiao

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun get(url: String, referer: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.useCaches = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Referer", referer)
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("Pragma", "no-cache")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

class SinaLatest(val draw: Draw, val createTime: Long?, val nextOpen: Long?)

/** 新浪彩票 */
object SinaSource {
    const val NAME = "新浪"

    fun latest(game: Game): Draw? = latestInfo(game)?.draw

    /** 最新一期 + 新浪首次收录时间（createTime）+ 下一期开奖时间（nextOpenTime） */
    fun latestInfo(game: Game): SinaLatest? {
        val url = "https://mix.lottery.sina.com.cn/gateway/index/entry?format=json&__caller__=web" +
            "&__version__=1&__verno__=1&cat1=gameOpenInfo&lottoType=${game.sinaType}" +
            "&_t=${System.currentTimeMillis()}"
        val result = JSONObject(Http.get(url, "https://lottery.sina.com.cn/")).getJSONObject("result")
        if (result.getJSONObject("status").optInt("code", -1) != 0) return null
        val data = result.getJSONObject("data")
        if (data.optString("lottoType") != game.sinaType) return null
        val issue = Issue.normalize(data.optString("issueNo")) ?: return null
        val arr = data.optJSONArray("openResults") ?: return null
        val nums = (0 until arr.length()).map { arr.optString(it) }
        val draw = Draw.of(issue, nums, game) ?: return null
        return SinaLatest(draw, Clock.parse(data.optString("createTime")), Clock.parse(data.optString("nextOpenTime")))
    }

    /** 按年份获取历史开奖（新浪走势图页面），每行再与"奖号"列自我核对 */
    fun history(game: Game, year: Int): List<Draw> {
        val html = Http.get(
            "https://view.lottery.sina.com.cn/lotto/pc_zst/index?lottoType=${game.sinaChart}" +
                "&actionType=chzs&year=$year&dpc=1",
            "https://view.lottery.sina.com.cn/"
        )
        val rowRe = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
        val issueRe = Regex("^\\s*<td>\\s*(\\d{7})\\s*</td>")
        val ballRe = Regex("<td[^>]*class=\"chartball\\d*[^\"]*\"[^>]*>\\s*(\\d)\\s*</td>")
        val prizeRe = Regex(">\\s*(\\d{3})\\s*<")
        val out = LinkedHashMap<String, Draw>()
        for (m in rowRe.findAll(html)) {
            val row = m.groupValues[1]
            val issue = issueRe.find(row)?.groupValues?.get(1) ?: continue
            if (!issue.startsWith(year.toString())) continue
            val balls = ballRe.findAll(row).take(game.digits).map { it.groupValues[1] }.toList()
            if (game.digits == 3) {
                val prize = prizeRe.find(row)?.groupValues?.get(1)
                if (prize != null && prize != balls.joinToString("")) continue
            }
            val d = Draw.of(issue, balls, game) ?: continue
            out[issue] = d
        }
        return out.values.sortedByDescending { it.issue }
    }
}

/** 网易彩票 */
object NeteaseSource {
    const val NAME = "网易"

    fun latest(game: Game): Draw? {
        val url = "https://sports.163.com/caipiao/api/web/numGame/bet/page/info?typeId=${game.neteaseType}" +
            "&_t=${System.currentTimeMillis()}"
        val root = JSONObject(Http.get(url, "https://sports.163.com/caipiao/"))
        if (root.optInt("code") != 200) return null
        val list = root.getJSONObject("data").optJSONArray("openList") ?: return null
        if (list.length() == 0) return null
        val o = list.getJSONObject(0)
        // 网易有缓存，曾实测返回别的彩种数据，所以必须核对彩种编号
        if (o.optInt("gameType") != game.neteaseType.toInt()) return null
        val issue = Issue.normalize(o.optString("degree")) ?: return null
        val nums = o.optString("red").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return Draw.of(issue, nums, game)
    }
}
