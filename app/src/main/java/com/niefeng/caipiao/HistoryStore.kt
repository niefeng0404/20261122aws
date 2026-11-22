package com.niefeng.caipiao

import android.content.Context
import java.io.File

/** 历史数据保存在平板上，每个彩种每年一个文件 */
class HistoryStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "history").apply { mkdirs() }

    private fun file(g: Game, y: Int) = File(dir, "${g.name}_$y.txt")
    private fun fullMark(g: Game, y: Int) = File(dir, "${g.name}_$y.full")

    @Synchronized
    fun read(g: Game, y: Int): List<Draw> {
        val f = file(g, y)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.trim().split(",")
            if (p.size != 2) null else Draw.of(p[0], p[1].map { it.toString() }, g)
        }.sortedByDescending { it.issue }
    }

    @Synchronized
    fun isFull(g: Game, y: Int): Boolean = fullMark(g, y).exists()

    @Synchronized
    private fun write(g: Game, y: Int, list: List<Draw>) {
        val tmp = File(dir, "${g.name}_$y.tmp")
        tmp.writeText(list.joinToString("\n") { it.issue + "," + it.numbers.joinToString("") })
        if (!tmp.renameTo(file(g, y))) {
            file(g, y).writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** 保存从新浪获取的整年数据（新浪为准），并保留本机已核对、新浪还没收录的最新期 */
    @Synchronized
    fun saveFull(g: Game, y: Int, fresh: List<Draw>): List<Draw> {
        val freshIssues = fresh.map { it.issue }.toHashSet()
        val merged = (fresh + read(g, y).filter { it.issue !in freshIssues }).sortedByDescending { it.issue }
        write(g, y, merged)
        fullMark(g, y).writeText("1")
        return merged
    }

    /** 最新开奖两家核对一致后，同步到历史数据 */
    @Synchronized
    fun merge(g: Game, d: Draw) {
        val y = d.issue.substring(0, 4).toIntOrNull() ?: return
        val cur = read(g, y)
        if (cur.any { it.issue == d.issue }) return
        write(g, y, (cur + d).sortedByDescending { it.issue })
    }
}
