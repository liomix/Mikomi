package com.liomix.mikomi.data.video.remote.vdoplugin.rule

import com.liomix.mikomi.data.video.remote.VdoFailureReason
import com.liomix.mikomi.data.video.remote.VdoSourceException
import org.json.JSONObject

/**
 * 一份规则在运行时真正用得到的那几项。
 *
 * 这里**只放规则说了什么**：地址模板、XPath、三个布尔提示。它不判断「这条线路该怎么播」——
 * 那是运行期观察出来的事（见 `remote` 包的 `VdoLineCapability`）。两者混在一起写过一次，结果是
 * 规则作者的一个 `useWebview: true` 把能直链的线路也一并钉死在网页里了。
 *
 * 解析失败一律算规则坏了（[VdoFailureReason.RuleBroken]），`detail` 就是出问题的字段名，
 * 用户在测试面板里能直接看出该改哪一行。
 */
class VdoRuleDocument private constructor(
    /** 规则名，也是 UI 上的源 id。允许为空（用户手写的草稿常常忘了写），由调用方兜底。 */
    val name: String,
    /** 站点根地址，用于把相对链接拼绝对。可以为空（规则里全是绝对地址时不写）。 */
    val baseUrl: String,
    /** 作者认为地址要渲染后才出现。**只是提示**，不参与「能不能静态解析」的判决。 */
    val useWebview: Boolean,
    /** 作者希望用站点自带的播放器。同上，只是提示。 */
    val useNativePlayer: Boolean,
    /** 规则自带的 UA；多数为空，表示用我们的默认桌面 UA。 */
    val userAgent: String,
    val searchUrl: String,
    val searchList: String,
    val searchName: String,
    val searchResult: String,
    /** 允许为空：单线路规则不写这一项。 */
    val chapterRoads: String,
    val chapterResult: String,
) {

    /**
     * 作者的意图：这条源大概率要用站点自己的播放器。
     *
     * 用途仅限两处——决定 WebView 的 UA、以及在所有静态策略都失败时把原因说成
     * 「需要网页播放」而不是含糊的「没找到地址」。
     */
    val prefersSitePlayer: Boolean get() = useWebview || useNativePlayer

    companion object {

        fun parse(json: String): VdoRuleDocument {
            val root = runCatching { JSONObject(json) }.getOrElse { error ->
                throw VdoSourceException(VdoFailureReason.RuleBroken, "JSON", error)
            }
            return VdoRuleDocument(
                name = root.optString("name").trim(),
                baseUrl = root.optString("baseURL").trim(),
                useWebview = root.optBoolean("useWebview", false),
                useNativePlayer = root.optBoolean("useNativePlayer", false),
                userAgent = root.optString("userAgent").trim(),
                searchUrl = root.required("searchURL"),
                searchList = root.required("searchList"),
                searchName = root.required("searchName"),
                searchResult = root.required("searchResult"),
                chapterRoads = root.optString("chapterRoads").trim(),
                chapterResult = root.required("chapterResult"),
            )
        }

        /** 缺失或空白都算坏规则：这几个字段没有默认值可用。 */
        private fun JSONObject.required(key: String): String =
            optString(key).trim().takeIf { it.isNotEmpty() }
                ?: throw VdoSourceException(VdoFailureReason.RuleBroken, key)
    }
}
