package com.liomix.mikomi.feature.player.component

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * WebView 形态下的「声音」两件事：**抢音频焦点** + **把页面播放器的音量顶回 100%**。
 *
 * 为什么需要它：WebView 里播的是站点自己的播放器，音量不由我们管，于是有两种典型的
 * 「能播但声音很小」：
 *
 * 1. **站点播放器自己把 `volume` 设成 0.2~0.5**。桌面版播放器尤其常见（它对大屏留了余量），
 *    而我们给 WebView 的正是桌面 UA。这种只能进页面里改 `<video>` 的 `volume`。
 * 2. **音频焦点在别人手里**。别的 App 持有焦点时系统会把我们 duck 掉，
 *    听起来就是"有声音但一直很小"，而且按音量键也只能在这个被压低的基线上调。
 *    播视频应该拿 [AudioManager.AUDIOFOCUS_GAIN]（让对方停，而不是让我们缩）。
 *
 * 生命周期跟着 WebView 走：`acquire()` 在进入 Web 形态时调用一次，`release()` 在离开时调用，
 * 否则退回详情页后别的 App 永远起不来声音。
 */
internal class WebAudioController(context: Context) {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var holding = false

    /**
     * 拿焦点。
     *
     * 焦点**变化**不在这里处理：WebView 自己会响应（来电时 Chromium 会把媒体暂停），
     * 我们只在外面多要一次 GAIN，把「被别的 App 压着」这个前提条件去掉。
     */
    fun acquire() {
        val manager = audioManager ?: return
        if (holding) return
        holding = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            val listener = AudioManager.OnAudioFocusChangeListener { }
            legacyFocusListener = listener
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    /** 放焦点。必须成对调用，否则用户退回列表页后别的 App 拿不到声音。 */
    fun release() {
        val manager = audioManager ?: return
        if (!holding) return
        holding = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let { manager.abandonAudioFocus(it) }
            legacyFocusListener = null
        }
    }

    companion object {
        /**
         * 把页面里所有媒体元素的音量顶到 100%。
         *
         * 几个刻意的做法：
         * - **只改 `volume`，不动 `muted`**。站点默认静音往往是为了绕自动播放限制，
         *   我们硬解静音可能触发浏览器把播放暂停，反而从「声音小」变成「不动了」。
         * - **监听 `play` 事件**：播放器常在起播那一刻把音量写回自己的默认值，
         *   必须在它之后顶回去。
         * - **不监听 `volumechange`**：那会和站点播放器互相改、来回触发，白烧 CPU。
         *   用 MutationObserver + 几个定时点覆盖"播放器晚创建"的情况即可。
         * - **顺带管同源 iframe**：跨域的进不去（try/catch 掉），同源的不少，能救一个是一个。
         * - 注入带幂等标记，重复注入直接返回，不会叠出多层 observer。
         */
        val VOLUME_BOOST_JS: String = """
            (function () {
              if (window.__mikomiVolumeBoost) return;
              window.__mikomiVolumeBoost = true;
              var boostIn = function (doc) {
                var list = doc.querySelectorAll('video,audio');
                for (var i = 0; i < list.length; i++) {
                  try { if (list[i].volume < 1) list[i].volume = 1; } catch (e) {}
                }
              };
              var boost = function () {
                boostIn(document);
                var frames = document.querySelectorAll('iframe');
                for (var i = 0; i < frames.length; i++) {
                  try { if (frames[i].contentDocument) boostIn(frames[i].contentDocument); } catch (e) {}
                }
              };
              var pending = false;
              var schedule = function () {
                if (pending) return;
                pending = true;
                setTimeout(function () { pending = false; boost(); }, 120);
              };
              boost();
              var marks = [300, 800, 1500, 3000, 6000, 12000];
              for (var i = 0; i < marks.length; i++) setTimeout(boost, marks[i]);
              document.addEventListener('play', boost, true);
              if (window.MutationObserver) {
                new MutationObserver(schedule).observe(
                  document.documentElement, { childList: true, subtree: true }
                );
              }
            })();
        """.trimIndent()
    }
}
