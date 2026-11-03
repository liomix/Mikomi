package com.liomix.mikomi.feature.player.component

import android.util.Log
import android.webkit.JavascriptInterface

/** 页面里注入的桥对象名。JS 侧写死这个名字，两边必须一致。 */
internal const val SNIFF_BRIDGE = "MikomiSniffer"

/**
 * 页面内的流地址探测脚本。
 *
 * 网络层（`shouldInterceptRequest`）能认出「后缀是 .m3u8」和「带 Range 的媒体请求」，
 * 但认不出**没有后缀的播放列表**——`/play.php?id=123` 返回的也是 `#EXTM3U` 正文。
 * 这类地址在请求头里毫无特征，网络层只能放过。页面里的 JS 却知道答案：
 * 播放器把响应读成文本，第一行就是 `#EXTM3U`。
 *
 * 所以这是网络层判据的**补充**，不是替代：能拦到的仍然先在网络层拦（那条路跨域 iframe 也有效），
 * 这里只补两种网络层看不见的情况——
 * 1. `fetch` / `XHR` 读到的 `#EXTM3U` 正文（地址可以没有任何后缀特征）；
 * 2. `<video>` / `<source>` 上写着 `src`（有的站直接给 mp4 文件）。
 *
 * 同一个来源的仿写：Kazumi 的 `blobParserScript` + `videoTagParserScript`。
 * 它那边用 flutter_inappwebview 的 handler 回传，这里用 `addJavascriptInterface` 的桥。
 *
 * 脚本带 `__mikomiHooked` 幂等标记：`onPageStarted` 与 `onPageFinished` 都会注一次，
 * 重复注入不能把 `Response.prototype.text` 包成两层（那会让页面自己的 Promise 变慢）。
 */
internal val SNIFF_HOOK_JS: String = """
(function () {
  if (window.__mikomiHooked) return;
  window.__mikomiHooked = true;

  function abs(u) {
    if (!u || typeof u !== 'string') return null;
    if (u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return null;
    try { return new URL(u, location.href).href; } catch (e) { return null; }
  }

  function report(u) {
    var a = abs(u);
    if (!a || a.indexOf('http') !== 0) return;
    try { ${SNIFF_BRIDGE}.onStream(a); } catch (e) {}
  }

  function isPlaylist(t) {
    return typeof t === 'string' && t.slice(0, 64).indexOf('#EXTM3U') >= 0;
  }

  // 1) fetch：正文是播放列表就报出它自己的 URL
  var _text = window.Response && window.Response.prototype && window.Response.prototype.text;
  if (_text) {
    window.Response.prototype.text = function () {
      var self = this;
      return _text.apply(this, arguments).then(function (t) {
        if (isPlaylist(t)) report(self.url);
        return t;
      });
    };
  }

  // 2) XHR：同上，地址在 open 的参数里
  var _open = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (m, u) {
    this.__mikomiUrl = u;
    this.addEventListener('load', function () {
      try { if (isPlaylist(this.responseText)) report(this.__mikomiUrl); } catch (e) {}
    });
    return _open.apply(this, arguments);
  };

  // 3) <video src> / <video><source src>
  //    只认「像主播放器」的那个：页面里常有几十像素的小预览/贴片，它们也是 mp4，
  //    报上去就会把一条 5 秒的广告当成正片交给播放器。
  function fromVideo(v) {
    if (!v) return false;
    var w = v.clientWidth || 0;
    if (w > 0 && w < 200) return false;
    var s = v.getAttribute && v.getAttribute('src');
    if (s) { report(s); return true; }
    var list = v.getElementsByTagName ? v.getElementsByTagName('source') : [];
    for (var i = 0; i < list.length; i++) {
      var src = list[i].getAttribute('src');
      if (src) { report(src); return true; }
    }
    return false;
  }

  // 4) 顺手催一下：站点播放器常常停在「等你点一下」的状态，而它不点就不会去要地址。
  //    play() 被拒（没有用户手势）是无害的，catch 掉即可。真正的点击我们替不了。
  function nudge(v) {
    try {
      if (v && v.paused && v.play) {
        var p = v.play();
        if (p && p.catch) p.catch(function () {});
      }
    } catch (e) {}
  }

  function scan() {
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) { fromVideo(vs[i]); nudge(vs[i]); }
  }

  try {
    new MutationObserver(function (ms) {
      for (var i = 0; i < ms.length; i++) {
        var m = ms[i], t = m.target;
        if (t && t.nodeName === 'VIDEO') { fromVideo(t); continue; }
        var added = m.addedNodes || [];
        for (var j = 0; j < added.length; j++) {
          var n = added[j];
          if (!n) continue;
          if (n.nodeName === 'VIDEO') { fromVideo(n); continue; }
          if (n.querySelectorAll) {
            var inner = n.querySelectorAll('video');
            for (var k = 0; k < inner.length; k++) fromVideo(inner[k]);
          }
        }
      }
    }).observe(document.documentElement || document, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['src']
    });
  } catch (e) {}

  setInterval(scan, 1500);
  scan();
})();
"""

/**
 * 页面 → 原生 的地址回传桥。
 *
 * 暴露给**任意第三方页面**，所以只做一件事：收一个字符串地址、校验协议、转交主线程。
 * 没有反射、没有文件、没有执行能力——最坏情况是页面多报一个地址，那也只是被
 * `VdoPlgEngine.sniffStream` 挡掉。
 *
 * 回调在 JavaBridge 线程，必须由调用方 `post` 回主线程再动 Compose 状态。
 */
internal class WebSniffBridge(private val onUrl: (String) -> Unit) {

    @JavascriptInterface
    fun onStream(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        Log.d("WebSniffBridge", "page reported: $url")
        onUrl(url)
    }
}
