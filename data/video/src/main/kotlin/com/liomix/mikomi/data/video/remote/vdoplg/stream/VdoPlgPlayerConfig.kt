package com.liomix.mikomi.data.video.remote.vdoplg.stream

import android.util.Base64
import java.net.URLDecoder
import org.json.JSONObject

/**
 * 站点播放器配置里的那串加密地址（macCMS 系的 `player_aaaa`）。
 *
 * 页面里长这样（实际抓到的 7sefun 播放页）：
 * ```
 * <script>var player_aaaa={"flag":"play","encrypt":2,"link":"\/vodplay\/...",
 *   "vod_data":{"vod_name":"..."},"url":"JTY4JTc0..."}</script>
 * ```
 * `encrypt` 决定怎么解：`2` 是 base64 之后再 urlencode，`1` 是纯 urlencode，其它当明文。
 *
 * **不能用一个正则吃掉这个对象**：它里面嵌了 `vod_data` 子对象，
 * `\{[^{}]*\}` 这种「单层」写法会在子对象开头就断掉，表现为「这条源解不出地址」——
 * 而页面里明明有。所以这里先定位起点，再做一次括号配对扫描（跳过字符串里的括号）。
 *
 * 解出来的东西**不一定**是视频地址。实测 7sefun 解出来是 `https://www.lmm85.com/play/...`，
 * 也就是「再跳一层页面」。所以调用方要把它当成「下一跳地址」处理。
 */
internal object VdoPlgPlayerConfig {

    /** 解出播放地址；页面里没有这个配置、或解不出合法地址时返回 null。 */
    fun decode(html: String): String? {
        val json = objectAt(html) ?: return null
        val config = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val raw = config.optString("url").trim()
        if (raw.isEmpty()) return null
        return decodeUrl(raw, config.optInt("encrypt", 0))?.takeIf { it.startsWith("http") }
    }

    /**
     * 从 `player_xxx = {` 开始，找配平的那个右括号。
     *
     * 手写扫描而不是正则：这串配置带一层嵌套，正则要么写得很脆（限制嵌套层数），
     * 要么一路贪婪吞掉后面的脚本。扫描只多十几行，但不会因为站点加个字段就崩。
     */
    private fun objectAt(html: String): String? {
        val start = START.find(html)?.range?.last ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun decodeUrl(raw: String, encrypt: Int): String? = runCatching {
        when (encrypt) {
            // base64 → urlencode。两层顺序不能反：站点是先 encode 再 base64 的。
            2 -> urlDecode(String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8))
            1 -> urlDecode(raw)
            else -> raw
        }
    }.getOrNull()

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, Charsets.UTF_8.name())

    /** 定位到左花括号为止；`range.last` 就是那个 `{` 的位置。 */
    private val START = Regex("""player_[A-Za-z0-9_]+\s*=\s*\{""")
}
