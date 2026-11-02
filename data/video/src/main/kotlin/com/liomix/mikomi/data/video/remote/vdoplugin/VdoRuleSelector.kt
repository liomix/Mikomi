package com.liomix.mikomi.data.video.remote.vdoplugin

import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.w3c.dom.Node

/**
 * 规则里的选择器执行器。
 *
 * 规则语言照抄 Kazumi：XPath 1.0（`//div/div[3]/ul/li` 这种）。站点 HTML 千奇百怪、
 * 极少是 well-formed XML，所以先让 jsoup 把页面解析成树、再由 `W3CDom` 转成标准 DOM，
 * 最后交给 `javax.xml.xpath` 求值——**这一步才是解析，不是启发式**：规则写什么就查什么。
 *
 * 两处与 Kazumi 对齐的语义：
 * - **相对选择器**：`searchName` / `chapterResult` 这类字段写的是 `//div/a[2]` 的绝对样子，
 *   但语义是「在这一条搜索结果里找」。XPath 规范里 `//` 永远从文档根开始，所以用前把开头的
 *   `/` 改写成 `./`（[scoped]）——不然每条都返回全页第一个匹配，规则就废了。
 * - **文本取法**：`searchName` 可能选中元素也可能选中文本节点（`//div[2]/text()`），
 *   统一取 `textContent` 再压空白，两种都能用。
 */
internal object VdoRuleSelector {

    /** XPathFactory 的创建不算便宜，且本身不是线程安全的，按线程缓存一个。 */
    private val xpathFactory = object : ThreadLocal<XPathFactory>() {
        override fun initialValue(): XPathFactory = XPathFactory.newInstance()
    }

    /**
     * 把 HTML 解析成 DOM。
     *
     * @param baseUrl 用于解析页面里的相对地址；解析器只把 `<base>` 记下来，真正的拼接由 [resolve] 做。
     */
    fun document(html: String, baseUrl: String?): org.w3c.dom.Document =
        // namespaceAware(false) 是必须的，不是口味问题：`W3CDom` 默认把元素放进 XHTML 命名空间，
        // 而 XPath 1.0 里不带前缀的名字只匹配「无命名空间」的节点，于是 `//div` 一个都命中不了
        // （实测 `//div` 返回 0、`//*[local-name()='div']` 才返回 2）。规则是按无命名空间写的，
        // 这里也必须按无命名空间读。
        W3CDom().namespaceAware(false).fromJsoup(Jsoup.parse(html, baseUrl.orEmpty()))

    /** 在 [scope] 下按 [expr] 取节点；表达式写错或解析失败都算「这条规则坏了」，不该静默成空结果。 */
    fun select(scope: Node, expr: String): List<Node> {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return emptyList()
        val factory = xpathFactory.get() ?: XPathFactory.newInstance().also(xpathFactory::set)
        val nodes = runCatching {
            factory.newXPath().evaluate(trimmed, scope, XPathConstants.NODESET) as org.w3c.dom.NodeList
        }.getOrElse { e ->
            throw VdoPluginException(VdoTestReason.RuleBroken, trimmed, e)
        }
        return (0 until nodes.length).map(nodes::item)
    }

    /** 相对选择器：`//x` → `.//x`、`/x` → `./x`，其余原样。 */
    fun scoped(expr: String): String {
        val trimmed = expr.trim()
        return if (trimmed.startsWith("/")) ".$trimmed" else trimmed
    }

    /** 取第一条匹配的文本；没有匹配或全是空白时返回 null。 */
    fun firstText(scope: Node, expr: String): String? =
        select(scope, scoped(expr)).firstNotNullOfOrNull { it.text()?.takeIf(String::isNotBlank) }

    /**
     * 取第一条匹配节点指向的链接。
     *
     * 规则里的 `searchResult` / `chapterResult` 通常直接选 `<a>`，但也有选外层 `<div>` 的，
     * 所以依次看 `href`、`src`、`data-src`，最后再往里找第一个 `<a>`——比直接报「缺链接」有用。
     */
    fun firstHref(scope: Node, expr: String, baseUrl: String?): String? {
        val node = select(scope, scoped(expr)).firstOrNull() ?: return null
        val raw = node.attr("href")
            ?: node.attr("src")
            ?: node.attr("data-src")
            ?: select(node, ".//a").firstOrNull()?.attr("href")
            ?: return null
        return resolve(baseUrl, raw)
    }

    /**
     * 把相对地址拼成绝对地址。
     *
     * 用 OkHttp 的 `HttpUrl.resolve` 而不是手写 `URI.resolve`：后者对 `//host/x`、
     * 缺尾斜杠、含中文的路径这几种情况各有各的坑，而 `HttpUrl` 的语义正好是「浏览器怎么拼」。
     */
    fun resolve(baseUrl: String?, raw: String?): String? {
        val href = raw?.trim().orEmpty()
        if (href.isEmpty()) return null
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val base = baseUrl?.toHttpUrlOrNull() ?: return null
        return base.resolve(href)?.toString()
    }
}

/**
 * 节点自身的属性。文本节点也走得通，只是永远为空。
 *
 * 做成顶层扩展而不是对象成员：`Node` 是 `org.w3c.dom` 的类型，引擎那边也要用，
 * 对象内的成员扩展只能在对象作用域里调用。
 */
internal fun Node.attr(name: String): String? {
    val value = (this as? org.w3c.dom.Element)?.getAttribute(name).orEmpty()
    return value.takeIf { it.isNotBlank() }
}

/** 节点文本，压掉多余空白。 */
internal fun Node.text(): String? = textContent?.let(::collapseText)

/** 站点为了排版会塞一堆换行和 `&nbsp;`，原样显示会撑破一行。 */
internal fun collapseText(text: String): String =
    text.replace('\u00A0', ' ').replace(WHITESPACE_REGEX, " ").trim()

private val WHITESPACE_REGEX = Regex("\\s+")
