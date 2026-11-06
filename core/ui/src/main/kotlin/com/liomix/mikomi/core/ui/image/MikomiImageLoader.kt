package com.liomix.mikomi.core.ui.image

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/**
 * 全站唯一的图片加载入口。
 *
 * 设计与 Telegram 的 `ImageLoader` 对齐：**图片有自己的连接池、自己的两级缓存上限**，
 * 不与接口请求共用。两个原因：
 *
 * 1. 失败语义不同。接口要长超时（弱网下等一次重传值回来），图片要短超时 ——
 *    一张封面卡 30 秒不如立刻给占位色块。共用一个 client 就得二选一。
 * 2. 量级不同。列表滚动时图片请求是接口的几十倍，共用会挤占接口的并发名额。
 *
 * 缓存上限必须显式给：Coil 默认按「可用内存 / 磁盘的百分比」推，
 * 在低内存机上会退得极小（反复解码），在大内存机上又会留一大块永不释放。
 * 这里给的是「绝对上限 + 比例」里更保守的那个思路 —— 直接写死绝对值，
 * 让缓存行为在两种机器上一致，便于定位「图片为什么重新下载」。
 */
object MikomiImageLoader {

    /** 磁盘缓存目录，放在 `cacheDir` 下，系统清缓存时一起清掉。 */
    private const val DISK_CACHE_DIR = "image_cache"

    /** 磁盘缓存上限：256 MB。约等于 2000 张 300px 宽的封面。 */
    private const val DISK_CACHE_BYTES = 256L * 1024 * 1024

    /** 内存缓存占比：可用内存的 20%。列表一屏 10 张封面 × 若干页，够回滚不重解码。 */
    private const val MEMORY_CACHE_FRACTION = 0.20

    /**
     * 图片专用连接池。
     *
     * 超时比接口短一档：连接 10s / 读 15s。图片挂了有占位块兜着，
     * 不值得让用户盯着转圈等满 30 秒。
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 装到 Coil 的单例上。`Application.onCreate` 里调一次即可。
     *
     * **必须早于第一个图片请求**：Coil 的单例一旦被读取就定型，之后的 `setSafe` 静默失效，
     * 表现为「缓存上限没生效、UA 也没带上」却不报任何错。
     *
     * 之所以由这里而不是 `Application` 直接调 `SingletonImageLoader`：
     * 让 app 模块不必依赖 coil —— 换图片库时只改 core:ui 一个文件。
     */
    fun install(context: Context) {
        SingletonImageLoader.setSafe(object : SingletonImageLoader.Factory {
            override fun newImageLoader(context: Context): ImageLoader = new(context)
        })
    }

    /** 构造 ImageLoader。只在 `Application` 里调一次，交给 `SingletonImageLoader` 持有。 */
    fun new(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(DISK_CACHE_DIR).toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .build()
}
