package com.liomix.mikomi.feature.animap

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 注入 anitabi 页面的全部脚本。
 *
 * 巡礼页没有自己的数据层：地图、作品、地标全在 [AniMapWebViewContainer] 那个 WebView 的
 * `window.mapApp` 上。Kotlin 侧想「搜索 / 给最近更新 / 打开某条命中」，唯一的手段是往页面里
 * 塞一段 JS、再把 return 值读回来。这个文件就是那几段 JS 的唯一驻地；容器只负责注入时机
 * 与回包解析，不再掺脚本内容。
 *
 * 几条共同的约定：
 * - 每段脚本都是自包含的 IIFE，不依赖事先注入的函数：首屏脚本比注入更晚执行时也不会出现
 *   「函数不存在」的空窗，同一段脚本重复求值也没有副作用。
 * - 数据一律在页面里筛。把整份 `bangumis` 搬到 Kotlin 侧匹配要跨 WebView 边界搬上 MB，
 *   而「页面里筛、只回传几十条」的代价可以忽略。
 * - 带参数的脚本用占位符 + 替换注入，不用 Kotlin 字符串模板：raw string 里的 `$` 有模板
 *   语义，而 JS 里 `$` 遍地都是。
 * - 字符串参数一律经 JSON 序列化成 JS 字面量，引号 / 反斜杠 / 换行都由序列化兜住。
 *
 * 这些脚本等价于「照着站点自己的实现抄一遍」——站点是 SPA、内部接口也没公开，
 * 改之前先回它的 bundle 里确认站点是怎么做的（例如封面的 `bgm-api` → `bgm-tc` 域名改写）。
 */

/** 回传的建议条数上限：脚本里的常量与这里保持一致。 */
internal const val SEARCH_HIT_LIMIT = 40

/**
 * 未输入时「最近更新」的条数。
 *
 * 给十条：一屏能扫完，也足够看出「最近有人更新过什么」；
 * 再多就成了一个没有搜索框的完整列表，反而挡住地图。
 */
internal const val LATEST_HIT_LIMIT = 10

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

/**
 * 只用来生成 JS 字面量的序列化器：`encodeToString(String.serializer(), …)` 的输出
 * 就是合法的 JS 字符串字面量，引号 / 反斜杠 / 换行一并转义，不必自己拼引号。
 */
private val JSON = Json

/**
 * 注入页面的样式：藏掉地图上我们不需要的一整圈浮层与标记。
 *
 * 分五类，缺一类就会留下「看着像坏了」的残影：
 * 1. **控件本体**：随机跳转作品、定位到我、配色切换（`-schemes`）、缩放/指南针、比例尺。
 * 2. **控件的壳**：`.mapboxgl-ctrl-group` 与四个角容器自带白色圆角底板和内边距。
 *    只藏按钮时壳还在，屏幕上就是一排空白小方块——这就是「隐藏后出现几个点」的来源。
 *    这里直接藏 `.mapboxgl-ctrl-group`（mapbox 所有成组控件的统一外壳），
 *    但不碰四角容器本身、也不碰 `.mapboxgl-ctrl-attrib`：右下角的 mapbox 版权标识
 *    是协议要求必须保留的，它挂在角容器里、不属于任何 group。
 * 3. **地图自己画的位置标记**：`.mapboxgl-user-location*` 是定位蓝点/精度圈。
 *    它不是控件，是 mapbox 在定位激活后画在地图画布上方的一层 DOM，得单独点名。
 * 4. **站点自己的加载指示**：`.g-progress-box`（左上角青色进度条）、`.ui-loading`
 *    （全屏浅蓝启动页，里面是一张 1520px 的地图 SVG）、`.ui-loading-box`（局部占位）。
 *    这三块盖住的正是「HTML 到了、地图还没画出来」的空窗 —— 藏掉它们之后，
 *    这段等待改由 App 自己的环形提示接住（见 [MAP_READY_JS] 与 `awaitMapReady`）。
 * 5. **地标缩略图放大**：`.window-point-item` 里的 `img.ui-image.cover` 是一行五个的小图，
 *    手机上认不出取景地，按「一行三个」反算宽度放大。
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
internal const val HIDE_OVERLAY_JS = """
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
    // 站点自带的加载指示，全部藏掉 —— App 自己有一份「地图加载中」的环形提示，
    // 站点这几个只会在地图数据到位前后重复闪一下：
    //   `.g-progress-box` 是左上角那条青色进度条（`position:fixed;top:10px;width:300px`，
    //   真机实测出现约 3.4s），`.ui-loading` 是全屏蓝底启动页（里面是 1520px 的地图 SVG），
    //   `.ui-loading-box` 是局部占位容器。
    '.g-progress-box,.ui-loading,.ui-loading-box{display:none!important}' +
    // 侧栏内容区的加载占位（`padding:100px 0 1000px` 的灰字，用来把侧栏撑开）。
    // 这里只抹掉文字、保留占位：直接 display:none 会让侧栏高度跳一下。
    '.side-loading-box{visibility:hidden!important}' +
    // 站点底部的地标缩略图（`.window-point-item` 里的 `img.ui-image.cover`）在手机上偏小：
    // 站点按「一行 5 张」算宽度（`--w:calc((100vw - 70px)/5 - 2px)`，360dp 下约 56px），
    // 取景地根本认不出来。改成一行 3 张，并把容器宽度用满：
    // 容器宽 = vw - left(2) - right(48)，每张还带 2px 右边距 → w = (vw - 50) / n - 2，
    // 三张即 (vw - 56) / 3，360dp 下约 56 -> 101（+80%）。
    // 高度由 `--h:calc(var(--w) * .8)` 自动跟随，容器高度也按 `--h` 算，不用另调。
    '.window-points-box{--w:calc((100vw - 56px)/3)!important}' +
    // 站点右上角那个「加载中...」是 **html 伪元素**（样式表里 html:after{content:"加载中..."}，
    // html[no-touch] 时 opacity:1 显示），DOM 里根本没有对应节点 —— 所以文本扫描永远抓不到，
    // 只能按伪元素规则点名。content:none 直接不生成盒子，比 display:none 更干净。
    'html:after,html[no-touch]:after{content:none!important;display:none!important}';
  root.appendChild(style);

  // 地图容器尺寸变了，但 mapbox 没收到通知 —— 站点的同步只挂在 `window.resize` 上，
  // 而站点自己的侧栏（作品详情）开关是改 DOM 布局，窗口尺寸一点没变。实测：
  // 打开侧栏 → 点「返回地图」→ `.map-box` 回到 759px 高，canvas 还是侧栏期间那 319px，
  // 下半屏就是一片底色，底部地标缩略图也跟着不显示（它的可见性按视口内的点算）。
  // 这里补一个 ResizeObserver：容器真的变了才 `map.resize()`（不是每帧），
  // 避免把正在跑的飞行动画打断。
  if (!window.mikomiMapResizeHooked) {
    window.mikomiMapResizeHooked = true;
    var mapLastW = 0;
    var mapLastH = 0;
    function syncMapSize() {
      try {
        if (window.mapApp && window.mapApp.map) window.mapApp.map.resize();
      } catch (e) { }
    }
    function onMapBoxResize() {
      var boxNow = document.querySelector('.map-box');
      if (!boxNow) return;
      var rect = boxNow.getBoundingClientRect();
      if (Math.abs(rect.width - mapLastW) < 1 && Math.abs(rect.height - mapLastH) < 1) return;
      mapLastW = rect.width;
      mapLastH = rect.height;
      syncMapSize();
    }
    function attachMapResize() {
      if (window.mikomiMapResizeWatched) return;
      var box = document.querySelector('.map-box');
      if (!box || !window.ResizeObserver) return;
      window.mikomiMapResizeWatched = true;
      new ResizeObserver(onMapBoxResize).observe(box);
      onMapBoxResize();
    }
    // 首屏注入时 `.map-box` 往往还没插进 DOM，隔几档再试。
    attachMapResize();
    setTimeout(attachMapResize, 900);
    setTimeout(attachMapResize, 2600);
  }

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
 * 判断地图「画到什么程度」的探针，返回 `0` / `1` / `2`。
 *
 * `onPageFinished` 只代表 HTML 与资源请求结束，此刻 anitabi 才刚开始拉作品数据、
 * mapbox 才刚开始画第一帧瓦片 —— 站点原本用那块浅蓝色启动页（`.ui-loading`）盖住这段空白。
 * 启动页被我们藏掉之后，这里必须自己补上「什么时候算加载完」，否则会先白屏一下。
 *
 * - `0` 还不能撤遮罩：地图实例没挂出来 / 画布没尺寸 / 作品数据没到 / 瓦片还没齐；
 * - `1` 瓦片齐了，但 `map.loaded()` 还没转真（它有过渡动画在跑时一直是 false）；
 * - `2` 完全就绪，可以立刻撤遮罩。
 *
 * 为什么要分 1 和 2：`loaded()` 在某些版本里对「正在进行中的过渡」长期返回 false，
 * 只认它会出现「图明明画好了、转圈还在」；只认 `areTilesLoaded()` 又会在刚建图、
 * 一个瓦片都还没请求时误判成功。所以 1 只在起跑一段宽限期之后才算数（见 `MAP_READY_TILES_GRACE_MILLIS`）。
 */
internal const val MAP_READY_JS = """
(function() {
  var app = window.mapApp;
  if (!app) return 0;
  var map = app.map;
  if (!map || typeof map.loaded !== 'function') return 0;
  var canvas = document.querySelector('.mapboxgl-canvas');
  if (!canvas || !canvas.clientWidth || !canvas.clientHeight) return 0;
  // 作品数据没到之前地图上没有任何标记，等于半个空页。
  // `bangumis` 是每次访问都重算的 getter（Object.values + 排序），
  // 这里读 `BangumiIds` 这个普通对象，避免 8 次/秒的重复计算。
  var ids = app.BangumiIds;
  var hasData = ids ? Object.keys(ids).length > 0 : !!(app.bangumis && app.bangumis.length);
  if (!hasData) return 0;
  try {
    if (map.loaded()) return 2;
  } catch (e) { }
  try {
    if (map.areTilesLoaded && map.areTilesLoaded()) return 1;
  } catch (e) { }
  return 0;
})()
"""

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
    // 站点有一部分老数据的 cover 是 http:// 明文地址，Android 默认禁明文，
    // 原样交给 Coil 会直接失败；统一升成 https（站点自己也做这一步）。
    if (/^http:\/\//i.test(s)) s = 'https://' + s.slice(7);
    // 官方 Bangumi 图（lain.bgm.tv）先并到 anitabi 的图床上，路径不变、加 /img 前缀。
    if (/^(https?:)?\/\/lain\.bgm\.tv/i.test(s)) {
      s = s.replace(/^(https?:)?\/\/lain\.bgm\.tv/i, 'https://bgm-api.anitabi.cn/img');
    }
    // 关键一步：`bgm-api` 是接口域名，直连会被 Cloudflare 挡成 403（浏览器单独打开
    // 同一张图也打不开，Chrome 报 ERR_BLOCKED_BY_ORB）。站点自己取图时把它换成转存域名
    // `bgm-tc`（见 af0fb620.js 的 T$），这里跟它保持同一口径。
    s = s.replace('https://bgm-api.anitabi.cn', 'https://bgm-tc.anitabi.cn');
    if (/^https:/i.test(s)) return s;
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
 * 组装一次搜索脚本。
 *
 * [limit] 就是回传条数上限：匹配在页面里做，只把前 `limit` 条搬回来。
 */
internal fun searchScript(keyword: String, limit: Int = SEARCH_HIT_LIMIT): String =
    SEARCH_JS
        .replace(KEYWORD_PLACEHOLDER, JSON.encodeToString(String.serializer(), keyword))
        .replace(LIMIT_PLACEHOLDER, limit.toString())

/**
 * 未输入关键词时的默认建议：最近更新的若干作品。
 *
 * 站点自己那块搜索框也是这么做的——空着的时候给一屏「最近更新」，让人有东西可点。
 * 数据同样从 `window.mapApp.bangumis` 里取，按站点的 `modified`（最近修改时间，毫秒）
 * 降序；时间戳相同的再按取景点数排，保证每次打开的顺序一致，不会看着像在乱跳。
 *
 * 只取前 [LATEST_HIT_LIMIT] 条：这里是「让人有个起点」，不是完整目录，
 * 真要看全的应该去搜关键词。回包因此很小，一次就搬完。
 *
 * 回包结构与 [SEARCH_JS] 完全一致，界面上走同一套建议条（`kind` 恒为 `bangumi`）。
 */
private val LATEST_JS = """
(function() {
  var app = window.mapApp;
  if (!app) return { ready: false, total: 0, hits: [] };
  var raw = app.bangumis || [];
  if (!raw.length) return { ready: false, total: 0, hits: [] };

  function text(v) {
    if (v == null) return '';
    if (typeof v === 'string') return v;
    if (v.join) return v.join(' ');
    return '';
  }

  function absolute(src) {
    var s = text(src);
    if (!s) return '';
    // 站点有一部分老数据的 cover 是 http:// 明文地址，Android 默认禁明文，
    // 原样交给 Coil 会直接失败；统一升成 https（站点自己也做这一步）。
    if (/^http:\/\//i.test(s)) s = 'https://' + s.slice(7);
    // 官方 Bangumi 图（lain.bgm.tv）先并到 anitabi 的图床上，路径不变、加 /img 前缀。
    if (/^(https?:)?\/\/lain\.bgm\.tv/i.test(s)) {
      s = s.replace(/^(https?:)?\/\/lain\.bgm\.tv/i, 'https://bgm-api.anitabi.cn/img');
    }
    // 关键一步：`bgm-api` 是接口域名，直连会被 Cloudflare 挡成 403（浏览器单独打开
    // 同一张图也打不开，Chrome 报 ERR_BLOCKED_BY_ORB）。站点自己取图时把它换成转存域名
    // `bgm-tc`（见 af0fb620.js 的 T$），这里跟它保持同一口径。
    s = s.replace('https://bgm-api.anitabi.cn', 'https://bgm-tc.anitabi.cn');
    if (/^https:/i.test(s)) return s;
    if (s.indexOf('//') === 0) return 'https:' + s;
    if (s.indexOf('/images/') === 0) return 'https://image-anitabi.magiconch.com/' + s.slice(8);
    if (s.charAt(0) === '/') return 'https://www.anitabi.cn' + s;
    return 'https://www.anitabi.cn/' + s;
  }

  // 排副本，别动站点那份数组：`bangumis` 是 getter，但返回的是内部对象引用，
  // 原地 sort 会把站点自己的顺序改掉（它按 priority 排过，改了会影响地图上的显示）。
  var list = raw.slice().sort(function(x, y) {
    var d = ((y && y.modified) || 0) - ((x && x.modified) || 0);
    if (d) return d;
    return ((y && y.points) || []).length - ((x && x.points) || []).length;
  });

  var out = [];
  var limit = Math.min(list.length, __LIMIT__);
  for (var i = 0; i < limit; i++) {
    var bgm = list[i];
    if (!bgm) continue;
    var cnText = text(bgm.cn);
    var enText = text(bgm.title);
    out.push({
      kind: 'bangumi',
      index: i,
      id: bgm.id == null ? '' : String(bgm.id),
      title: cnText || enText,
      subtitle: (cnText && enText && enText !== cnText) ? enText : '',
      city: text(bgm.city),
      cover: absolute(bgm.cover),
      points: (bgm.points || []).length
    });
  }
  return { ready: true, total: list.length, hits: out };
})()
"""

/**
 * 组装「最近更新」脚本。
 *
 * 与 [searchScript] 同一套回包结构，界面上不用分叉；排序键是站点的 `modified`（修改时间）。
 */
internal fun latestScript(limit: Int = LATEST_HIT_LIMIT): String =
    LATEST_JS.replace(LIMIT_PLACEHOLDER, limit.toString())

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
 * 组装「打开某条命中」脚本。
 *
 * 三个参数都是字符串：`kind` 决定走作品 / 地标 / 城市哪条分支，`id` 用来在页面里现取对象，
 * `name` 只在城市命中时用到。
 */
internal fun openHitScript(kind: String, id: String, name: String): String =
    OPEN_HIT_JS
        .replace(KIND_PLACEHOLDER, JSON.encodeToString(String.serializer(), kind))
        .replace(ID_PLACEHOLDER, JSON.encodeToString(String.serializer(), id))
        .replace(NAME_PLACEHOLDER, JSON.encodeToString(String.serializer(), name))
