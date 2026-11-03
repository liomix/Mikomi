package com.liomix.mikomi.data.video.remote

import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 内存 CookieJar：让「页面阶段拿到的 Cookie」能带到「流地址」那一步。
 *
 * 为什么非要有：这类站点普遍在搜索/播放页下发一个短期 Cookie（防盗链、防采集、
 * 简单的会话校验），随后请求 m3u8 时校验它。出流在数据层用 OkHttp，播放却在 Media3，
 * 两边不共享 cookie 存储——不手动搬一次，结果就是「测试面板说地址有效，播放器却 403」。
 *
 * 不落盘、不跨进程：这些 Cookie 只在一次播放会话里有意义，过期了重新走一遍页面即可。
 * 容量上限按站点数算（几十条），不需要淘汰策略。
 */
internal class VdoCookieJar : CookieJar {

    private val jar = CopyOnWriteArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        cookies.forEach { incoming ->
            // 同名同域同路径的新值覆盖旧值：站点续期会话时就是靠这个更新的。
            jar.removeAll {
                it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
            }
            if (incoming.expiresAt > now) jar += incoming
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        // 顺手清过期的：数量很小，每次读列表时扫一遍比记时器简单。
        jar.removeAll { it.expiresAt <= now }
        // `matches` 自己会判域、路径、secure、过期，不用我们再算一遍。
        return jar.filter { it.matches(url) }
    }

    /**
     * 给 Media3 用的 `Cookie` 请求头。
     *
     * @return 形如 `a=1; b=2`；没有可用 Cookie 或地址不合法时为 null。
     */
    fun headerFor(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val cookies = loadForRequest(httpUrl)
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }
}
