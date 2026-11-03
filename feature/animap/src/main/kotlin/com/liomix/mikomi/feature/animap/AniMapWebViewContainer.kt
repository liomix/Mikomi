package com.liomix.mikomi.feature.animap

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 注入页面的样式：藏掉地图上我们不需要的一整圈浮层与标记。
 *
 * 分三类，缺一类就会留下「看着像坏了」的残影：
 * 1. **控件本体**：随机跳转作品、定位到我、配色切换（`-schemes`）、缩放/指南针、比例尺。
 * 2. **控件的壳**：`.mapboxgl-ctrl-group` 与四个角容器自带白色圆角底板和内边距。
 *    只藏按钮时壳还在，屏幕上就是一排空白小方块——这就是「隐藏后出现几个点」的来源。
 *    这里直接藏 `.mapboxgl-ctrl-group`（mapbox 所有成组控件的统一外壳），
 *    但不碰四角容器本身、也不碰 `.mapboxgl-ctrl-attrib`：右下角的 mapbox 版权标识
 *    是协议要求必须保留的，它挂在角容器里、不属于任何 group。
 * 3. **地图自己画的位置标记**：`.mapboxgl-user-location*` 是定位蓝点/精度圈。
 *    它不是控件，是 mapbox 在定位激活后画在地图画布上方的一层 DOM，得单独点名。
 *
 * 站点那句「加载中...」**不是 DOM 节点，是 `html:after` 伪元素**（样式表里
 * `html:after{content:"加载中..."}`，`html[no-touch]` 时 `opacity:1` 显形），所以
 * 手动在开发者工具里翻 DOM、或者按文本扫描，都永远抓不到它——只能按伪元素规则点名，
 * 见下面样式里的 `html:after` 那一条。剩下的文本扫描逻辑留着当兜底：有些 loading 层
 * 确实是真实节点，命中「加载中 / 正在加载 / 载入中 / loading」这类短提示后，
 * 会顺着父级爬到只装着这句提示的容器整块藏掉；容器换成正文内容时自动解除隐藏。
 *
 * 用 `<style>` 而不是「找到节点再删」：站点是 SPA，控件由 mapbox 在首屏之后异步插进来，
 * 按节点删会漏；而且站点 JS 可能持有这些按钮的引用去挂监听，删节点反而让它抛异常。
 * CSS 规则一旦在 `<head>` 里，后插进来的节点同样被命中。每次注入先摘旧再挂新，
 * 规则升级后缓存下来的 WebView 也能跟着更新。
 */
private const val HIDE_OVERLAY_JS = """
(function() {
  // 抢注时文档可能还没有 <head>（onPageStarted 后的一小段时间里），直接退出，
  // 由下一轮延迟注入接手；在 about:blank 上插进去的样式会随导航一起被丢弃，无副作用。
  var root = document.head || document.documentElement;
  if (!root) return;

  // 每次都先摘掉旧版本注入的 style：规则会随版本演进（新版本要藏更多东西），
  // 靠「有 id 就跳过」会让缓存下来的 WebView 永远停在旧规则上。
  ['mikomi-hide-changelog', 'mikomi-hide-overlay'].forEach(function(id) {
    var node = document.getElementById(id);
    if (node) node.parentNode.removeChild(node);
  });
  var style = document.createElement('style');
  style.id = 'mikomi-hide-overlay';
  style.textContent = [
    '.func-change-logs-fixed',
    '.mapboxgl-ctrl-group',
    '.mapboxgl-ctrl-random-to-bangumi',
    '.mapboxgl-ctrl-geolocate',
    '.mapboxgl-ctrl-schemes',
    '.mapboxgl-ctrl-zoom-in',
    '.mapboxgl-ctrl-zoom-out',
    '.mapboxgl-ctrl-compass',
    // 左下角的比例尺（mapbox 原生控件，独立一个 div，不在 group 里）。
    '.mapboxgl-ctrl-scale',
    // mapbox 自己贴的一个空链接触摸区（点中会跳 service.html），看不见但能误触。
    '.mbgl-service-link',
    // 站点自己的搜索抽屉（"城市、作品、地标"那个输入框 + 建议列表）。
    // 搜索 UI 已由 App 用 Compose 重画，站点的整套一并藏掉，
    // 否则会在左上角再叠一个输入框，且它下面还带着自己的建议层。
    '.side-search-box',
    '.mapboxgl-user-location',
    '.mapboxgl-user-location-dot',
    '.mapboxgl-user-location-accuracy-circle'
  ].join(',') + '{display:none!important}' +
    // 兜底：万一还有没点名的控件壳（站点自定义容器），把底板/阴影/边框一起抹掉，
    // 空壳就只剩透明像素，不会在画面上留灰色小方块。attribution 保留原样。
    '.mapboxgl-ctrl:not(.mapboxgl-ctrl-attrib){' +
      'background:none!important;box-shadow:none!important;border:0!important}' +
    // 供脚本动态打标：必须是样式表里的 !important，站点之后再改 inline style 也顶不掉。
    '.mikomi-hidden{display:none!important}' +
    // 站点右上角那个「加载中...」是 **html 伪元素**（样式表里 html:after{content:"加载中..."}，
    // html[no-touch] 时 opacity:1 显示），DOM 里根本没有对应节点 —— 所以文本扫描永远抓不到，
    // 只能按伪元素规则点名。content:none 直接不生成盒子，比 display:none 更干净。
    'html:after,html[no-touch]:after{content:none!important;display:none!important}';
  root.appendChild(style);

  // 「加载中…」按文本认人。标记放 window 上，onPageFinished 会重复注入，只装一套观察者。
  if (window.mikomiLoadingSweep) return;
  window.mikomiLoadingSweep = true;

  var HIDDEN = 'mikomi-hidden';
  // 判定为「提示语」而不是「正文」的长度上限：去掉空白后最多这么多个字。
  var MAX_TIP_LENGTH = 16;
  var hiddenCount = 0;
  var lastRefresh = 0;

  function norm(text) {
    return (text || '').replace(/\s+/g, '');
  }

  function isTipText(text) {
    var t = norm(text).toLowerCase();
    if (!t || t.length > MAX_TIP_LENGTH) return false;
    return t.indexOf('加载中') >= 0 ||
      t.indexOf('正在加载') >= 0 ||
      t.indexOf('载入中') >= 0 ||
      t.indexOf('loading') >= 0;
  }

  // 从命中文字的那一行往上爬：只要父级的文本同样是一句短提示，就继续把父级也算进来。
  // 这样弹窗的壳（底板、遮罩、转圈图标）会一起被藏；遇到体量正常的内容容器就停手，
  // 不会一路爬到地图容器或 body 上。
  function hideShell(el) {
    var target = el;
    for (var up = 0; up < 4; up++) {
      var parent = target.parentElement;
      if (!parent || parent === document.body || parent === document.documentElement) break;
      var text = norm(parent.textContent);
      if (!text || text.length > MAX_TIP_LENGTH) break;
      target = parent;
    }
    if (!target.classList.contains(HIDDEN)) {
      target.classList.add(HIDDEN);
      hiddenCount++;
    }
  }

  function sweepLoading(root) {
    if (!root || !root.querySelectorAll) return;
    var list = root.querySelectorAll('div,span,p,section,strong,em');
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      // 只处理「光秃秃一段字」的元素：里面还套着东西的是布局容器，藏了会连地图一起没。
      if (el.childElementCount > 1) continue;
      if (isTipText(el.textContent)) hideShell(el);
    }
  }

  // 同一个容器经常被复用：loading 结束后把子节点换成一堆真实内容，
  // 此时文本不再是提示语，必须把 mikomi-hidden 摘掉，否则内容永远不显示。
  function refreshHidden() {
    var list = document.querySelectorAll('.' + HIDDEN);
    hiddenCount = list.length;
    for (var i = 0; i < list.length; i++) {
      if (!isTipText(list[i].textContent)) list[i].classList.remove(HIDDEN);
    }
  }

  sweepLoading(document);

  new MutationObserver(function(mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var added = mutations[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var node = added[j];
        if (node.nodeType !== 1) continue;
        // 先比文本再遍历：地图上的 marker 增删很频繁，绝大多数节点不含这些字，
        // 直接跳过，避免每帧都在 DOM 上跑一遍选择器。
        if (norm(node.textContent).toLowerCase().indexOf('loading') < 0 &&
            norm(node.textContent).indexOf('载') < 0) continue;
        if (node.childElementCount === 0) {
          if (isTipText(node.textContent)) hideShell(node);
        } else {
          sweepLoading(node);
        }
      }
    }
    // 有隐藏中的壳时才回头复查，且 500ms 节流：地图平移时 DOM 变动非常密集。
    if (hiddenCount > 0 && Date.now() - lastRefresh > 500) {
      lastRefresh = Date.now();
      refreshHidden();
    }
  }).observe(document.documentElement, {
    childList: true,
    subtree: true,
    characterData: true
  });
})();
"""

/**
 * 导航开始后抢注样式的几个时间点（毫秒）。
 *
 * 0 是先排进消息队列、等这一帧的导航真正起来；后面几档覆盖「文档解析出 head」的时机。
 * 站点那块 loading 浮层从出现到被移除可能只有几百毫秒，单靠 onPageFinished 一定错过。
 */
private val EARLY_INJECT_DELAYS_MILLIS = longArrayOf(0L, 120L, 320L, 700L)

/** 回传的建议条数上限：脚本里的常量与这里保持一致。 */
private const val SEARCH_HIT_LIMIT = 40

/**
 * 点击命中后回读相机状态的时间点（毫秒）。
 *
 * 起飞本身有 60ms 延迟，脚本里的验收在 1.8s 收尾，所以这个数要压在它之后。
 * 只用于调试包里的 Toast，正式包不读。
 */
private const val DIAGNOSTIC_DELAY_MILLIS = 2000L

/** 搜索脚本的 JSON 字面量占位符（用替换而不是模板，避免 Kotlin raw string 里的 `$` 陷阱）。 */
private const val KEYWORD_PLACEHOLDER = "__KEYWORD__"

/** 命中类型的 JSON 字面量占位符（`"bangumi"` / `"point"` / `"city"`）。 */
private const val KIND_PLACEHOLDER = "__KIND__"

/** 命中对象 id 占位符（作品 id 或地标点 id）。 */
private const val ID_PLACEHOLDER = "__ID__"

/** 城市名占位符（只有 `city` 类命中用到）。 */
private const val NAME_PLACEHOLDER = "__NAME__"

/** 建议条数占位符。 */
private const val LIMIT_PLACEHOLDER = "__LIMIT__"

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 在页面里跑一次搜索，返回命中的前 [SEARCH_HIT_LIMIT] 条。
 *
 * **匹配规则是照抄站点搜索框的**（站点 `j5.js` 里的 `_i`，实现在 `7a641bde.js` 导出的 `a`）：
 * 关键词**直接当正则**用（不转义、不区分大小写），依次匹配
 * ① 地标的 `cn` / `name` / `image`，命中就带上它所属的作品；
 * ② 作品的 `cn` / `en` / `title` / `cat`（分类，如 TV、剧场版）；
 * ③ 作品的 `city`（站点会先用 `getFullCityAddress` 展开父级路径再匹配，拿不到就退化成本身）。
 * 站点怎么筛，这里就怎么筛——用户看到的搜索范围与站点那张地图严格一致。
 *
 * 在此基础上多给一类 **city** 结果：站点只把城市名当关键词去筛作品，
 * 但「搜城市」最自然的期望是地图移过去，所以命中的城市单独列一条，点击缩放到该城市的全部取景点。
 *
 * 匹配完全在页面里做，因为数据就在页面里（见 [AniMapHit] 的说明）。
 * 把整份列表导出来交给 Kotlin 匹配要跨 WebView 边界搬几百 KB 到上 MB，
 * 而「页面里筛、只回传 40 条」代价可以忽略。
 *
 * 每次调用都带上完整实现，不依赖事先注入的函数：
 * 首屏脚本比注入更晚执行时也不会出现「函数不存在」的空窗。
 */
private val SEARCH_JS = """
(function() {
  var app = window.mapApp;
  if (!app) return { ready: false, total: 0, hits: [] };
  var keyword = (__KEYWORD__ || '').trim();
  if (!keyword) return { ready: true, total: 0, hits: [] };

  // 站点把关键词直接当正则用（不转义）。输入「(」这类非法模式会抛异常，
  // 这里兜住并按「没有结果」处理，别让一次输入把页面搞崩。
  var re;
  try { re = new RegExp(keyword, 'i'); } catch (e) { return { ready: true, total: 0, hits: [] }; }

  // bangumis 的 getter 每次都 Object.values + 全量排序，输入过程中反复调用
  // 会在几千部作品上白做一次 O(n log n)。按条数变化缓存一份。
  var raw = app.bangumis || [];
  if (!window.__mikomiIndex || window.__mikomiIndexSize !== raw.length) {
    window.__mikomiIndex = raw;
    window.__mikomiIndexSize = raw.length;
  }
  var list = window.__mikomiIndex;

  // 城市等字段在站点数据里可能是字符串，也可能是数组，统一成字符串再比。
  function text(v) {
    if (v == null) return '';
    if (typeof v === 'string') return v;
    if (v.join) return v.join(' ');
    return '';
  }

  // 站点存的是相对路径，补成绝对地址交给 Coil：
  // /images/xxx 走图片 CDN（与站点自己的替换规则一致），其余按站点域名补全。
  function absolute(src) {
    var s = text(src);
    if (!s) return '';
    if (/^https?:/i.test(s)) return s;
    if (s.indexOf('//') === 0) return 'https:' + s;
    if (s.indexOf('/images/') === 0) return 'https://image-anitabi.magiconch.com/' + s.slice(8);
    if (s.charAt(0) === '/') return 'https://www.anitabi.cn' + s;
    return 'https://www.anitabi.cn/' + s;
  }

  var spots = [];
  var works = [];
  // 城市项从「作品实际填的 city」反推，而不是站点那份 `Citys`：
  // 后者是按行政区划树统计的（省 / 市 / 区每一级都有条目），拿它当关键词会出现
  // 「点进去一个作品都没有」的中间级城市。用作品自己的 city 值，点了一定有东西可飞。
  var cityCounts = {};
  var cityOrder = [];
  for (var i = 0; i < list.length; i++) {
    var b = list[i];
    if (!b) continue;

    var ownCity = text(b.city);
    if (ownCity) {
      if (!cityCounts[ownCity]) { cityCounts[ownCity] = 0; cityOrder.push(ownCity); }
      cityCounts[ownCity]++;
    }

    // 一、地标：命中就记下所属作品，点的时候连同作品一起选中。
    // 这里故意不合并同名点：同一处取景地会按集数拆成多条记录（坐标差几十米、
    // id 各不相同），站点自己的 `L()` 也是逐点 push，保持一致。
    var pts = b.points || [];
    for (var j = 0; j < pts.length; j++) {
      var p = pts[j];
      if (!p) continue;
      if (re.test(text(p.cn)) || re.test(text(p.name)) || (p.image && re.test(text(p.image)))) {
        spots.push({ point: p, owner: b });
      }
    }

    // 二、作品本身：中文名 / 英文名 / 原名 / 分类。
    if ((b.cn && re.test(b.cn)) || (b.en && re.test(b.en)) ||
        (b.title && re.test(b.title)) || (b.cat && re.test(b.cat))) {
      works.push(b);
      continue;
    }

    // 三、城市：与站点同样的口径（父级路径取不到就只比城市名本身）。
    if (b.city) {
      var cityText = text(b.city);
      if (app.getFullCityAddress) {
        try {
          var path = app.getFullCityAddress(b.city);
          if (path && path.length) cityText = path.slice(1).join(',');
        } catch (e2) { }
      }
      if (re.test(cityText)) works.push(b);
    }
  }

  // 作品按取景点数量降序：同名作品里先给能在地图上看到东西的那一部。
  works.sort(function(x, y) { return (y.points || []).length - (x.points || []).length; });

  // 命中关键词的城市按作品数降序，最多三条（多级行政区很容易一次命中十几个）。
  var cities = [];
  for (var co = 0; co < cityOrder.length; co++) {
    if (re.test(cityOrder[co])) cities.push(cityOrder[co]);
  }
  cities.sort(function(x, y) { return cityCounts[y] - cityCounts[x]; });
  if (cities.length > 3) cities = cities.slice(0, 3);

  var items = [];
  for (var a = 0; a < cities.length; a++) items.push({ kind: 'city', name: cities[a] });
  for (var sp = 0; sp < spots.length; sp++) {
    items.push({ kind: 'point', point: spots[sp].point, owner: spots[sp].owner });
  }
  for (var w = 0; w < works.length; w++) items.push({ kind: 'bangumi', bangumi: works[w] });

  // 完整对象留在页面里，点击时按下标取回；回传的只是精简字段。
  window.__mikomiHits = items;

  var out = [];
  var limit = Math.min(items.length, __LIMIT__);
  for (var k = 0; k < limit; k++) {
    var it = items[k];
    if (it.kind === 'city') {
      out.push({ kind: 'city', index: k, id: '', title: it.name, subtitle: '', city: it.name, cover: '', points: 0 });
    } else if (it.kind === 'point') {
      var owner = it.owner;
      out.push({
        kind: 'point',
        index: k,
        // 点击时按 id 在页面里现取对象；下标那份快照会随页面重建失效。
        id: it.point.id == null ? '' : String(it.point.id),
        title: text(it.point.cn) || text(it.point.name),
        subtitle: owner ? (text(owner.cn) || text(owner.title)) : '',
        city: owner ? text(owner.city) : '',
        cover: absolute(it.point.image || (owner && owner.cover)),
        points: 0
      });
    } else {
      var bgm = it.bangumi;
      var cnText = text(bgm.cn);
      var enText = text(bgm.title);
      out.push({
        kind: 'bangumi',
        index: k,
        id: bgm.id == null ? '' : String(bgm.id),
        title: cnText || enText,
        subtitle: (cnText && enText && enText !== cnText) ? enText : '',
        city: text(bgm.city),
        cover: absolute(bgm.cover),
        points: (bgm.points || []).length
      });
    }
  }
  // 作品列表还是空的，说明地图数据没加载完——这时候「没找到」是假象。
  return { ready: list.length > 0, total: items.length, hits: out };
})()
"""

/**
 * 打开一条命中（按 id 现场取对象，不依赖搜索时的下标）。
 *
 * 为什么不用下标：`window.__mikomiHits` 是搜索那一刻的快照，页面一旦重建
 * （SPA 换路由、地图数据分片重新灌入）这份数组就和界面上的列表对不上了，
 * 取出来是 `undefined`，脚本静默返回，表现就是「点了没反应」。
 * 站点自己把作品 / 地标分别存在 `BangumiIds` / `PointIds` 里，按 id 现取永远是最新的。
 *
 * 三段处理：
 * 1. 先调站点入口（`setBangumi` / `setBangumiAndPoint`），让筛选、主题、标题、URL 同步；
 * 2. **再无条件按自己的口径飞一次**：站点那条链是
 *    `setBangumiNoFly`（路由 + 图层筛选 + 主题重置 + 事件广播）→ `$nextTick(fitBoundsByBangumi)`，
 *    中途任何一步抛异常都会断在起飞之前，而 `$nextTick` 是
 *    `Promise.resolve().then()`，异常回不到调用方 —— 只能自己确保相机移动；
 * 3. 60ms 后再飞，是为了让站点那次 `fitBounds`（duration 2000）先起跑、由我们收尾，
 *    两边目标一致，不会出现「飞两次到不同地方」。
 *
 * 并且**不相信自己的 `fitBounds` 就一定会动相机**：1.3s 后回看一眼 `getZoom/getCenter`，
 * 跟起飞前一样就直接 `jumpTo` 硬落位。只发起、不验收是这类「点了不飞」的根源 ——
 * 两边的起飞都在异步链路里，异常一律静默。
 *
 * 坐标顺序：站点存 `[lat, lng]`（`LngLatBounds.extend(new LngLat(geo[1], geo[0]))`），
 * mapbox 要 `[lng, lat]`。
 */
private val OPEN_HIT_JS = """
(function() {
  var app = window.mapApp;
  if (!app) return 'no-app';
  var map = app.map || null;
  var kind = __KIND__;
  var id = __ID__;
  var name = __NAME__;
  var log = [];
  window.__mikomiFit = '';

  function text(v) {
    if (v == null) return '';
    if (typeof v === 'string') return v;
    if (v.join) return v.join(' ');
    return '';
  }

  // 站点存的地标坐标偶尔有脏值（缺字段、越界、0,0），一个坏点就能把 bbox
  // 撑到半个地球 —— 落到世界视图，看起来就像没动。
  function usable(lat, lng) {
    if (!isFinite(lat) || !isFinite(lng)) return false;
    if (Math.abs(lat) > 85) return false;
    if (Math.abs(lng) > 200) return false;
    return !(Math.abs(lat) < 0.01 && Math.abs(lng) < 0.01);
  }

  // 球面距离（米），仅用于离群点裁剪。
  function dist(a, b) {
    var R = 6371000;
    var dLat = (b[0] - a[0]) * Math.PI / 180;
    var dLng = (b[1] - a[1]) * Math.PI / 180;
    var la1 = a[0] * Math.PI / 180, la2 = b[0] * Math.PI / 180;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
  }

  // 与站点 `nr(points, .8)` 同口径的离群点裁剪：按「到其余各点距离之和」取
  // (max+avg)/2*0.8 作阈值。城市项会把多部作品的点合在一起，不裁就会把
  // 横滨撑成整个关东（实测 zoom 5.5 → 7.8）。
  function trimOutliers(list) {
    if (list.length < 4) return list;
    var sums = [];
    var i, j;
    for (i = 0; i < list.length; i++) {
      var s = 0;
      for (j = 0; j < list.length; j++) {
        if (j !== i) s += dist(list[i], list[j]);
      }
      sums.push(s / (list.length - 1));
    }
    var max = -Infinity, total = 0;
    for (i = 0; i < sums.length; i++) {
      if (sums[i] > max) max = sums[i];
      total += sums[i];
    }
    var th = (max + total / sums.length) / 2 * 0.8;
    var kept = [];
    for (i = 0; i < list.length; i++) {
      if (sums[i] <= th) kept.push(list[i]);
    }
    return kept.length < 3 ? list : kept;
  }

  function boundsOf(points) {
    if (!points || !points.length) return null;
    var geos = [];
    for (var i = 0; i < points.length; i++) {
      var geo = points[i] && points[i].geo;
      if (!geo || geo.length < 2) continue;
      var lat = Number(geo[0]);
      var lng = Number(geo[1]);
      if (!usable(lat, lng)) continue;
      geos.push([lat, lng]);
    }
    if (!geos.length) return null;
    var kept = trimOutliers(geos);
    var minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
    for (var k = 0; k < kept.length; k++) {
      var la = kept[k][0], ln = kept[k][1];
      if (la < minLat) minLat = la;
      if (la > maxLat) maxLat = la;
      if (ln < minLng) minLng = ln;
      if (ln > maxLng) maxLng = ln;
    }
    return { minLat: minLat, maxLat: maxLat, minLng: minLng, maxLng: maxLng, count: kept.length };
  }

  function centerOf(b) {
    return [(b.minLng + b.maxLng) / 2, (b.minLat + b.maxLat) / 2];
  }

  function isSingle(b) {
    return (b.maxLat - b.minLat) < 0.0001 && (b.maxLng - b.minLng) < 0.0001;
  }

  // 墨卡托 y（0..1，赤道 0.5）。自己算缩放是为了不依赖 mapbox 的
  // cameraForBounds —— v1/v2 的签名与返回结构都变过，而 fitBounds 的
  // 内边距语义在不同版本里也不一致。
  function mercY(lat) {
    var s = Math.sin(lat * Math.PI / 180);
    if (s > 0.9999) s = 0.9999;
    if (s < -0.9999) s = -0.9999;
    return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
  }

  // 与站点 fitBoundsByBangumi 同口径：min(宽,高) 的 7% 作内边距、maxZoom 17。
  // 世界宽 = 512 * 2^zoom 像素，所以 zoom = log2(可用像素 / (跨度 * 512))。
  //
  // @param minZoom 0 表示不设下限。城市项给一个城市尺度的下限：横滨这类
  //   「一部作品一个城市、点散在都市圈」的数据按 bbox 算出来只有 z7~8，
  //   跟默认的日本全景几乎没差别，用户会以为没飞。
  function cameraOf(b, minZoom) {
    var pad = Math.round(Math.min(window.innerWidth || 400, window.innerHeight || 800) * 0.07);
    var z;
    if (isSingle(b)) {
      z = 15;
    } else {
      var w = Math.max(32, (window.innerWidth || 400) - pad * 2);
      var h = Math.max(32, (window.innerHeight || 800) - pad * 2);
      var dx = Math.abs(b.maxLng - b.minLng) / 360;
      var dy = Math.abs(mercY(b.minLat) - mercY(b.maxLat));
      var zx = dx > 0 ? Math.log2(w / (dx * 512)) : 17;
      var zy = dy > 0 ? Math.log2(h / (dy * 512)) : 17;
      z = Math.min(zx, zy, 17);
    }
    if (!isFinite(z)) z = 15;
    if (minZoom && z < minZoom) z = minZoom;
    if (z > 17) z = 17;
    if (z < 1) z = 1;
    return { center: centerOf(b), zoom: Math.round(z * 100) / 100 };
  }

  function snap() {
    try {
      var c = map.getCenter();
      return { lat: c.lat, lng: c.lng, zoom: map.getZoom() };
    } catch (e) {
      return null;
    }
  }

  function fmt(s) {
    return s ? s.zoom.toFixed(2) + '@' + s.lat.toFixed(2) + ',' + s.lng.toFixed(2) : 'null';
  }

  function nearlySame(a, b) {
    if (!a || !b) return true;
    return Math.abs(a.zoom - b.zoom) < 0.05 &&
      Math.abs(a.lat - b.lat) < 0.001 &&
      Math.abs(a.lng - b.lng) < 0.001;
  }

  // 飞一次，并且**确认它真的飞了**。
  //
  // 只调 fitBounds 是不够的，有两层静默失败：
  // ① 站点自己那条链（setBangumiNoFly 的六个副作用 → nextTick(fitBoundsByBangumi)）
  //    任何一步抛异常都会断在起飞之前，而 nextTick 是 Promise.resolve().then()，
  //    异常回不到调用方；我们这条也在 setTimeout 里，同样静默。
  // ② **setPadding 会立刻掐掉正在跑的动画**（实测：飞行途中 300ms 调一次，
  //    相机冻在 z=4.61 再也不动）。而点击后收起键盘 / insets 变化会改变 WebView
  //    高度 → 站点的 resize 监听 → `map.setPadding(Xn())`。所以站点自己的 2s 飞行
  //    也会被打断 —— 这才是「定位 ok 但没飞」的真凶。
  //
  // 对策：resize 之后把这次飞行重发一遍（等布局稳定），再配两道验收。
  function flyToBounds(b, minZoom) {
    if (!map) { log.push('no-map'); window.__mikomiFit = log.join(' '); return; }
    var before = snap();
    var cam = cameraOf(b, minZoom);
    var tries = 0;
    log.push('from=' + fmt(before), 'to=' + fmt({ zoom: cam.zoom, lat: cam.center[1], lng: cam.center[0] }));

    // 只用 flyTo + 自己算的相机，不调 fitBounds：
    // 相机口径完全在我们手里，缩放能夹、落点可复现，也不踩 mapbox 各版本
    // 之间 fitBounds / cameraForBounds 的差异。
    function apply() {
      tries++;
      try { map.resize(); } catch (e) { }
      try {
        map.flyTo({ center: cam.center, zoom: cam.zoom, duration: 1200, essential: true });
        log.push('fly#' + tries);
      } catch (e2) {
        log.push('fly-err=' + (e2 && e2.message ? e2.message : 'err'));
      }
    }

    function moved() {
      var now = snap();
      return !!(before && now && !nearlySame(before, now));
    }

    var guard = 0;
    function onResize() {
      clearTimeout(guard);
      guard = setTimeout(apply, 220);
    }
    try { window.addEventListener('resize', onResize); } catch (e) { }

    apply();

    // 700ms 还在原地（多半是刚起跑就被打断）再发一次；
    // 1.8s 仍未到位就直接 jumpTo 硬落位。
    setTimeout(function () { if (!moved()) apply(); }, 700);
    setTimeout(function () {
      try { window.removeEventListener('resize', onResize); } catch (e) { }
      clearTimeout(guard);
      var aim = { zoom: cam.zoom, lat: cam.center[1], lng: cam.center[0] };
      var jumped = false;
      if (!moved() && !nearlySame(snap(), aim)) {
        try {
          map.jumpTo({ center: cam.center, zoom: cam.zoom });
          jumped = true;
        } catch (e3) { }
      }
      window.__mikomiFit = log.join(' ') + ' | end=' + fmt(snap()) + (jumped ? ' (jumped)' : ' (eased)');
    }, 1800);
  }

  function findBangumi() {
    var byId = app.BangumiIds || {};
    if (byId[id]) return byId[id];
    var list = app.bangumis || [];
    for (var i = 0; i < list.length; i++) {
      if (list[i] && String(list[i].id) === id) return list[i];
    }
    return null;
  }

  function findPoint() {
    var byId = app.PointIds || {};
    if (byId[id]) return byId[id];
    var list = app.bangumis || [];
    for (var i = 0; i < list.length; i++) {
      var pts = (list[i] && list[i].points) || [];
      for (var j = 0; j < pts.length; j++) {
        if (pts[j] && String(pts[j].id) === id) return pts[j];
      }
    }
    return null;
  }

  // 某座城市下所有作品的取景点（城市项是从「作品自己填的 city」反推的，精确比对即可）。
  function pointsOfCity(cityName) {
    var all = [];
    if (!cityName) return all;
    var list = app.bangumis || [];
    for (var i = 0; i < list.length; i++) {
      var b = list[i];
      if (!b || text(b.city) !== cityName) continue;
      var pts = b.points || [];
      for (var j = 0; j < pts.length; j++) all.push(pts[j]);
    }
    return all;
  }

  var target = null;

  if (kind === 'bangumi') {
    var bgm = findBangumi();
    if (!bgm) return 'miss-bangumi';
    target = boundsOf(bgm.points);
    if (!target) {
      // 极少数条目一个取景点都没有（数据还没细化到坐标），站点自己此时也是
      // `if (!o.length) return`，等于点了没反应。用同城其它作品的点兜底。
      target = boundsOf(pointsOfCity(text(bgm.city)));
    }
    try { app.setBangumi(bgm); } catch (e) {
      try { app.setBangumiNoFly(bgm); } catch (e2) { }
    }
  } else if (kind === 'point') {
    var p = findPoint();
    if (!p) return 'miss-point';
    var owner = app.BangumiIds ? app.BangumiIds[p.aid] : null;
    target = boundsOf([p]);
    // 站点原始点上没有 aid（那是它搜索时临时补的），`setBangumiAndPoint` 靠它反查作品。
    var spot = p.aid == null && owner ? Object.assign({}, p, { aid: owner.id }) : p;
    try { app.setBangumiAndPoint(spot); } catch (e3) {
      try { if (owner) app.setBangumiNoFly(owner); } catch (e4) { }
    }
  } else if (kind === 'city') {
    target = boundsOf(pointsOfCity(name));
  } else {
    return 'miss-kind';
  }

  if (!target) return 'no-geo';

  // 无条件自己飞一次：站点那条链可能断在中途（见上面的说明），必须确保相机移动。
  setTimeout(function () { flyToBounds(target, kind === 'city' ? 11 : 0); }, 60);
  return 'ok|map=' + (map ? 1 : 0) + '|pts=' + target.count;
})()
"""

/**
 * 巡礼地图的 WebView 容器。
 *
 * **这里刻意不碰窗口与渲染层**：WebView 就是一个普通的子 View，和 Compose 的
 * `AndroidView` 一起待在 Activity 窗口里，触摸、焦点、输入法全部走系统原生那一条路。
 *
 * 曾经的做法是把 WebView 塞进 `TextureView` 提供的 `VirtualDisplay`、由 `Presentation`
 * 承载，以躲开宿主 View 树上的反射遍历。隔离确实干净，代价是**键盘彻底用不了**：
 * 输入法只绑在默认显示上，虚拟显示里没有 IME，页内搜索框点了不会弹键盘，
 * `InputMethodManager.showSoftInput` 一律返回 false。地图页要搜片名、要打字，
 * 这个代价换不起——所以回到最朴素的内嵌方案。
 *
 * 对齐 flutter_inappwebview 插件的默认设置：
 * loadWithOverviewMode=true、useWideViewPort=true、textZoom=100。
 */
class AniMapWebViewContainer(
    context: Context,
    private val initialUrl: String,
    private val cachedWebView: WebView? = null,
) : FrameLayout(context) {

    /** 容器内的 WebView，创建后可调用 goBack 等。 */
    var webView: WebView? = null
        private set

    /** 页面开始加载/加载完成时回调 true/false。 */
    var onLoadingChanged: (Boolean) -> Unit = {}

    /** 页面加载完成（onPageFinished）时回调，携带页面当前 URL（可能被站点重定向改写）。 */
    var onPageFinished: (WebView, String?) -> Unit = { _, _ -> }

    /** 是否复用了已加载完成的缓存 WebView。 */
    internal val hasCachedWebView: Boolean
        get() = cachedWebView != null

    init {
        val web = cachedWebView ?: createWebView(context)
        // 缓存命中时 WebView 可能仍挂在旧容器上（detachForCache 未摘除的兜底），
        // 挂载前必须先摘下，否则 addView 抛
        // IllegalStateException "The specified child already has a parent."。
        (web.parent as? ViewGroup)?.removeView(web)
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        webView = web
        if (cachedWebView == null) {
            onLoadingChanged(true)
            web.loadUrl(initialUrl)
        } else {
            // 缓存页面的样式早就注入过；SPA 偶尔会重建 head，补一次不吃亏。
            injectOverlayStyle(web)
        }
    }

    /** 停止加载并销毁 WebView。 */
    fun destroy() {
        webView?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
    }

    /**
     * 仅把 WebView 从容器上摘下，保留本体由 [AniMapWebCache] 持有，供下次进入复用。
     * 调用后容器不可再用。
     */
    fun detachForCache() {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        // 保持 WebView 默认渲染模式：不要用 LAYER_TYPE_SOFTWARE（会废掉 WebGL）。
        setLayerType(View.LAYER_TYPE_NONE, null)
        // 不暴露无障碍节点：地图页没有需要读屏遍历的内容，也省掉一遍树遍历。
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        WebView.setWebContentsDebuggingEnabled(context.isDebuggable())
        settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            // 地图用 Canvas/WebGL 手势缩放，须关掉 WebView 自带缩放，
            // 否则 WebView 拦截双指捏合，地图收不到 pinch、底图渲染错位。
            setSupportZoom(false)
            setBuiltInZoomControls(false)
            setDisplayZoomControls(false)
            setSupportMultipleWindows(false)
        }
        // 禁用长按弹出文本选择菜单；输入框的键盘不受影响。
        setOnLongClickListener { true }
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 站点那块「加载中」浮层在首屏数据到达后就被从 DOM 上摘掉了：
                // 等 onPageFinished 时它早就不在，连手动抓 HTML 都抓不到。
                // 所以从导航一开始就分几轮抢注——文档一旦有了 head 就把规则落下去，
                // 后续插进来的节点交给脚本里的 MutationObserver。
                for (delay in EARLY_INJECT_DELAYS_MILLIS) {
                    view.postDelayed({ injectOverlayStyle(view) }, delay)
                }
            }

            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                injectOverlayStyle(view)
                onLoadingChanged(false)
                // 显式限定到类属性，避免与本匿名类重写的 onPageFinished 方法同名遮蔽，
                // 否则 Kotlin 会解析成递归调用自身导致 StackOverflow。
                this@AniMapWebViewContainer.onPageFinished(view, finishedUrl)
            }
        }
    }

    /** 把隐藏规则注入当前文档；文档还没就绪时脚本自己会退出，交给下一轮重试。 */
    private fun injectOverlayStyle(view: WebView) {
        runCatching { view.evaluateJavascript(HIDE_OVERLAY_JS, null) }
    }

    /**
     * 在页面里检索，返回命中（作品 / 地标 / 城市三类混排）。
     *
     * 必须在主线程调用（WebView 的硬约束）；Compose 里由 `rememberCoroutineScope`
     * 起的协程默认就在主线程，因此这里不再切换调度器。
     * WebView 已销毁或页面处在不可求值状态时返回空包，不向调用方抛异常——
     * 搜索是随输入触发的，一次失败不应该让界面停在「搜索中」。
     */
    suspend fun search(keyword: String): AniMapSearchPayload {
        val view = webView ?: return AniMapSearchPayload()
        val script = SEARCH_JS
            // 用 JSON 编码关键词，得到的是合法的 JS 字符串字面量，引号/反斜杠/换行都由它处理。
            .replace(KEYWORD_PLACEHOLDER, JSON.encodeToString(String.serializer(), keyword))
            .replace(LIMIT_PLACEHOLDER, SEARCH_HIT_LIMIT.toString())
        return suspendCancellableCoroutine { continuation ->
            val dispatched = runCatching {
                view.evaluateJavascript(script) { raw ->
                    if (continuation.isActive) continuation.resume(parsePayload(raw))
                }
            }
            // evaluateJavascript 本身抛了异常时回调不会再来了，必须在这里收尾，否则协程永久挂起。
            if (dispatched.isFailure && continuation.isActive) {
                continuation.resume(AniMapSearchPayload())
            }
        }
    }

    /**
     * 打开一条命中：作品缩放到取景地范围、地标落到具体坐标、城市圈住该城市全部取景点。
     *
     * 按 [AniMapHit.id] 在页面里现取对象，不用搜索时的下标——那份快照会过期。
     * 不重新加载页面，站点自己的筛选 / 标题 / URL 同步都保留。
     *
     * @param onResult 注入脚本的返回值 + 2s 后回读的相机状态，
     *   形如 `ok|map=1|pts=2 / from=1.50@… to=14.56@… | end=14.56@… (jumped)`。
     *   前半段说明点击走到哪一步（`miss-bangumi` / `no-geo` / `no-app` 等），
     *   后半段说明相机到底动没动；抓不到 WebView 时不会回调。
     */
    fun openHit(hit: AniMapHit, onResult: ((String?) -> Unit)? = null) {
        val view = webView ?: return
        val script = OPEN_HIT_JS
            .replace(KIND_PLACEHOLDER, JSON.encodeToString(String.serializer(), hit.kind))
            .replace(ID_PLACEHOLDER, JSON.encodeToString(String.serializer(), hit.id))
            .replace(NAME_PLACEHOLDER, JSON.encodeToString(String.serializer(), hit.title))
        runCatching {
            view.evaluateJavascript(script) { raw ->
                if (onResult == null) return@evaluateJavascript
                // 同步拿到的只是「脚本走到哪一步」；相机是 60ms 后才起跑的，
                // 真正有没有动起来要等脚本里的验收回调（1.3s）写完页内记录再取。
                view.postDelayed({
                    runCatching {
                        view.evaluateJavascript("window.__mikomiFit || ''") { diag ->
                            onResult("${raw.asText()} / ${diag.asText()}")
                        }
                    }.onFailure { onResult("${raw.asText()} / diag-throw:${it.message}") }
                }, DIAGNOSTIC_DELAY_MILLIS)
            }
        }.onFailure { onResult?.invoke("throw:${it.message}") }
    }

    /** `evaluateJavascript` 回来的是 JSON 字符串字面量，去掉外层引号再给人看。 */
    private fun String?.asText(): String =
        this?.trim().orEmpty().removeSurrounding("\"").replace("\\\"", "\"")

    private fun parsePayload(raw: String?): AniMapSearchPayload {
        if (raw.isNullOrBlank() || raw == "null") return AniMapSearchPayload()
        return runCatching { JSON.decodeFromString<AniMapSearchPayload>(raw) }
            .getOrElse { AniMapSearchPayload() }
    }

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
