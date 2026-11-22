package com.niefeng.caipiao

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** 更新来源：本仓库的 GitHub Releases */
object UpdateConfig {
    const val REPO = "niefeng0404/20261122aws"
    const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val APK_NAME = "caipiao-helper.apk"
}

class ReleaseInfo(val code: Long, val name: String, val notes: String, val url: String, val size: Long)

object Updater {
    fun currentCode(c: Context): Long {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    fun currentName(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"

    /** 读取最新发布版本。标签格式 v1.0.N，N 就是版本号 */
    fun latest(): ReleaseInfo? {
        val c = URL(UpdateConfig.API).openConnection() as HttpURLConnection
        val text = try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.useCaches = false
            c.setRequestProperty("User-Agent", "CaipiaoHelper")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
        val j = JSONObject(text)
        val tag = j.optString("tag_name")
        if (!Regex("v\\d+\\.\\d+\\.\\d+").matches(tag)) return null
        val code = tag.substringAfterLast('.').toLongOrNull() ?: return null
        val assets = j.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == UpdateConfig.APK_NAME) {
                return ReleaseInfo(
                    code, tag.removePrefix("v"), j.optString("body"),
                    a.optString("browser_download_url"), a.optLong("size", -1)
                )
            }
        }
        return null
    }

    fun download(c: Context, info: ReleaseInfo, progress: (Int) -> Unit): File {
        val dir = File(c.cacheDir, "update")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "update-${info.code}.apk")
        var url = info.url
        var conn: HttpURLConnection? = null
        // 手动跟随跳转（GitHub 会跳转到下载服务器）
        for (i in 0 until 5) {
            val k = URL(url).openConnection() as HttpURLConnection
            k.instanceFollowRedirects = false
            k.connectTimeout = 15000
            k.readTimeout = 30000
            k.setRequestProperty("User-Agent", "CaipiaoHelper")
            val code = k.responseCode
            if (code in 300..399) {
                url = k.getHeaderField("Location") ?: throw IOException("跳转地址为空")
                k.disconnect()
                continue
            }
            if (code != 200) {
                k.disconnect()
                throw IOException("HTTP $code")
            }
            conn = k
            break
        }
        val k = conn ?: throw IOException("跳转次数过多")
        try {
            val total = if (info.size > 0) info.size else k.contentLengthLong
            var done = 0L
            var last = -1
            k.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; progress(p) }
                        }
                    }
                }
            }
            if (info.size > 0 && out.length() != info.size) {
                out.delete()
                throw IOException("下载不完整")
            }
        } finally {
            k.disconnect()
        }
        return out
    }

    fun canInstall(c: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    fun install(c: Context, f: File) {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
    }
}

/** 更新界面：大字体对话框 */
class UpdateUi(private val a: MainActivity) {
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var pending: File? = null
    var onStatus: ((String) -> Unit)? = null

    fun check(manual: Boolean) {
        if (busy) return
        busy = true
        if (manual) onStatus?.invoke("正在检查更新…")
        io.execute {
            val info = try { Updater.latest() } catch (e: Exception) { null }
            val cur = Updater.currentCode(a)
            handler.post {
                busy = false
                if (a.isFinishing) return@post
                when {
                    info == null -> {
                        onStatus?.invoke("当前版本 ${Updater.currentName(a)}")
                        if (manual) message("检查更新失败", "连不上 GitHub，请检查网络后再试。")
                    }
                    info.code > cur -> {
                        onStatus?.invoke("有新版本 ${info.name}")
                        askUpdate(info)
                    }
                    else -> {
                        onStatus?.invoke("当前版本 ${Updater.currentName(a)}（已是最新）")
                        if (manual) message("已是最新版本", "当前版本 ${Updater.currentName(a)}，不需要更新。")
                    }
                }
            }
        }
    }

    private fun bigText(s: String, sp: Float): TextView {
        val t = Ui.text(a, s, sp)
        val p = Ui.dp(a, 24f)
        t.setPadding(p, p, p, p)
        return t
    }

    private fun styleButtons(d: AlertDialog) {
        for (w in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
            d.getButton(w)?.let { it.textSize = 30f; it.setPadding(40, 24, 40, 24) }
        }
    }

    private fun message(title: String, body: String) {
        val d = AlertDialog.Builder(a)
            .setCustomTitle(bigText(title, 38f))
            .setView(bigText(body, 30f))
            .setPositiveButton("知道了", null)
            .create()
        d.show()
        styleButtons(d)
    }

    private fun askUpdate(info: ReleaseInfo) {
        val notes = info.notes.trim().take(300)
        val body = "当前版本：${Updater.currentName(a)}\n新版本：${info.name}" +
            (if (notes.isNotEmpty()) "\n\n更新内容：$notes" else "")
        val d = AlertDialog.Builder(a)
            .setCustomTitle(bigText("发现新版本", 40f))
            .setView(bigText(body, 30f))
            .setPositiveButton("立即更新") { _, _ -> download(info) }
            .setNegativeButton("以后再说", null)
            .create()
        d.show()
        styleButtons(d)
    }

    private fun download(info: ReleaseInfo) {
        val t = bigText("正在下载新版本… 0%", 32f)
        t.gravity = Gravity.CENTER
        val d = AlertDialog.Builder(a).setView(t).setCancelable(false).create()
        d.show()
        io.execute {
            val f = try {
                Updater.download(a, info) { p -> handler.post { t.text = "正在下载新版本… $p%" } }
            } catch (e: Exception) { null }
            handler.post {
                d.dismiss()
                if (f == null) {
                    message("下载失败", "请检查网络后，在首页点“检查更新”再试一次。")
                } else {
                    installOrAsk(f)
                }
            }
        }
    }

    private fun installOrAsk(f: File) {
        if (Updater.canInstall(a)) {
            Updater.install(a, f)
            return
        }
        pending = f
        val d = AlertDialog.Builder(a)
            .setCustomTitle(bigText("需要允许安装", 38f))
            .setView(bigText("第一次更新需要打开“允许安装应用”的开关。\n\n点“去设置”，打开开关后按返回键回到本 App，会自动继续安装。", 30f))
            .setPositiveButton("去设置") { _, _ ->
                if (Build.VERSION.SDK_INT >= 26) {
                    a.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))
                    )
                }
            }
            .setNegativeButton("取消") { _, _ -> pending = null }
            .create()
        d.show()
        styleButtons(d)
    }

    /** 从设置页返回时继续安装 */
    fun onResume() {
        val f = pending ?: return
        if (Updater.canInstall(a) && f.exists()) {
            pending = null
            Updater.install(a, f)
        }
    }
}
