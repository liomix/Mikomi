package com.liomix.mikomi.core.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3 ExoPlayer 封装，供播放器 feature 使用。
 *
 * 这一层只做「把地址变成能播的东西」，不认识番剧、不认识插件源：
 * 传进来的是 `https://…/index.m3u8` 加一组请求头，出去的是解码后的画面。
 *
 * 三个必须自己接手的点（缺一个就黑屏或没声音）：
 * 1. **HLS**：插件源给的几乎都是 m3u8，`DefaultMediaSourceFactory` 需要 classpath 上有
 *    `media3-exoplayer-hls`；同时我们按已知类型显式指定 mimeType，避免靠后缀猜。
 * 2. **请求头**：这类站点普遍校验 `Referer` / `User-Agent`，部分还要求页面阶段拿到的 Cookie。
 *    Media3 默认的 `DefaultHttpDataSource` 不带这些，会直接 403。
 * 3. **跨协议重定向**：站点常用 http→https 跳转，默认策略会拒绝，表现为「解析到地址但拉不动」。
 *
 * 播放器实例必须建在主线程（ExoPlayer 内部要绑 Looper），所以工厂由 ViewModel 在主线程里取。
 */
class MikomiPlayer(context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val appContext: Context = context.applicationContext

    /**
     * 装载并开始缓冲一个流。
     *
     * 每次出流都重建数据源工厂：请求头（尤其 Cookie/Referer）是按这一条流算的，
     * 复用上一次的工厂会把上一集的鉴权带过来，表现为「换集后 403」。
     *
     * @param mimeType 已知类型（如 `application/x-mpegURL`）；不确定时传 null 交给 ExoPlayer 推断。
     * @param headers 请求头：User-Agent / Referer / Cookie，来自出流层。
     * @param startPositionMillis 从这个位置续播（切源、换线路时保持进度）。
     */
    fun play(
        url: String,
        mimeType: String? = null,
        headers: Map<String, String> = emptyMap(),
        startPositionMillis: Long = 0L,
    ) {
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { mimeType?.let(::setMimeType) }
            .build()
        exoPlayer.setMediaSource(mediaSource(item, headers))
        exoPlayer.prepare()
        if (startPositionMillis > 0L) exoPlayer.seekTo(startPositionMillis)
        exoPlayer.playWhenReady = true
    }

    /**
     * 换一个流但**不留播放位置**（切集用）。
     *
     * 与 [play] 的差别只在 `startPositionMillis`：换集从 0 开始，续播才带位置。
     */
    fun reset() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    fun release() {
        exoPlayer.release()
    }

    /** 数据源按这一条流的请求头现建；用 `DefaultDataSource` 包一层是为了兼容本地/asset 地址。 */
    private fun mediaSource(item: MediaItem, headers: Map<String, String>): MediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            // 不从 setUserAgent 走：UA 和 Referer 一起来自出流层，统一放请求头里，
            // 免得两处都设、结果取决于谁覆盖谁。
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(TIMEOUT_MILLIS)
            .setReadTimeoutMs(TIMEOUT_MILLIS)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
    }

    companion object {
        /** 流地址普遍在墙外，给足 20 秒；再久用户也在等首帧，不如早点报错。 */
        const val TIMEOUT_MILLIS = 20_000

        /** HLS 播放列表的 mimeType，与出流层的 `VdoStreamKind.Hls` 对应。 */
        const val MIME_HLS = MimeTypes.APPLICATION_M3U8
    }
}

/**
 * 播放器工厂。
 *
 * ExoPlayer 必须绑在主线程的 Looper 上，因此不在 DI 里直接提供实例，
 * 而是由持有页面的 ViewModel 在自己的作用域里建一个——生命周期跟着播放页走。
 */
@Singleton
class MikomiPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(): MikomiPlayer = MikomiPlayer(context)
}
