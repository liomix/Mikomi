package com.liomix.mikomi.data.video.remote.vdoplugin

import com.liomix.mikomi.core.model.PluginRule
import org.json.JSONException
import org.json.JSONObject

/**
 * 规则解析失败的原因。
 *
 * 只给**原因**，不给文案：仓库/解析器不认识多语言，用户看到的那句话由 feature 层按
 * [InvalidVdoPluginRuleException.Reason] 映射（见 `PluginScreen` 里的 `reasonRes`）。
 */
class InvalidVdoPluginRuleException(
    /** 失败原因。UI 据此选一句人话。 */
    val reason: Reason,
    /** 出问题的字段名；与字段无关（JSON 语法错）时为 null。 */
    val field: String? = null,
    cause: Throwable? = null,
) : Exception("Invalid plugin rule: $reason${field?.let { " ($it)" }.orEmpty()}", cause) {

    enum class Reason {
        /** 根本不是 JSON 对象。 */
        MalformedJson,

        /** 缺 `name`：没有它就存不了文件，也无从在列表里称呼这条规则。 */
        MissingName,

        /** 缺 `baseURL`：拼相对地址、显示站点都要用它。 */
        MissingBaseUrl,

        /** `baseURL` 不是 http(s) 绝对地址。 */
        InvalidBaseUrl,

        /** 改名后撞上了已有的另一条规则：规则名是文件名，不能重名。 */
        NameTaken,
    }
}

/**
 * 规则 JSON 的读写口径。
 *
 * 仓库（落盘）和 UI（编辑时的即时校验、格式化）共用这一份，避免「编辑器说合法、保存时又报错」
 * 这种两套校验打架的情况。
 *
 * 只校验**元信息**（name / baseURL）：搜索地址、XPath 这些属于规则内容，写错不致命，
 * 只是搜不到东西——那该由「测试」去体检，不该拦着保存。
 */
object VdoPluginRuleFormat {

    /**
     * 解析一份规则原文。
     *
     * @param enabled 启停状态不在 JSON 里，由仓库的 state 文件单独记，这里原样带进去。
     * @throws InvalidVdoPluginRuleException 缺少元信息或不是合法 JSON 对象。
     */
    fun parse(json: String, enabled: Boolean = true): PluginRule {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw InvalidVdoPluginRuleException(InvalidVdoPluginRuleException.Reason.MalformedJson, cause = e)
        }
        val name = root.optString("name").trim()
        if (name.isEmpty()) {
            throw InvalidVdoPluginRuleException(InvalidVdoPluginRuleException.Reason.MissingName, "name")
        }
        val baseUrl = root.optString("baseURL").trim()
        if (baseUrl.isEmpty()) {
            throw InvalidVdoPluginRuleException(InvalidVdoPluginRuleException.Reason.MissingBaseUrl, "baseURL")
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw InvalidVdoPluginRuleException(InvalidVdoPluginRuleException.Reason.InvalidBaseUrl, "baseURL")
        }
        return PluginRule(
            name = name,
            version = root.optString("version").trim(),
            author = root.optString("author").trim(),
            baseUrl = baseUrl,
            enabled = enabled,
            // 原文整段留着，编辑/分享/测试都直接用它。
            json = json,
        )
    }

    /**
     * 只判断「能不能解析」，不抛异常——给编辑器的即时反馈用。
     *
     * @return null 表示合法，否则是要提示给用户的原因。
     */
    fun validate(json: String): InvalidVdoPluginRuleException.Reason? = try {
        parse(json)
        null
    } catch (e: InvalidVdoPluginRuleException) {
        e.reason
    }

    /**
     * 重新缩进成 4 格的漂亮格式；JSON 不合法时返回 null。
     *
     * 用 org.json 重新序列化而不是字符串拼接：规则原文里有 `//` 开头的 XPath，
     * 手写缩进很容易把字符串里的字符当成结构处理。
     */
    fun pretty(json: String): String? = try {
        JSONObject(json).toString(4)
    } catch (e: JSONException) {
        null
    }
}
