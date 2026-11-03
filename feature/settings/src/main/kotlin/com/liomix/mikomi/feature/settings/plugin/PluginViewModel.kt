package com.liomix.mikomi.feature.settings.plugin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.InvalidVdoPluginRuleException
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.VdoPluginRuleFormat
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.VdoPluginRuleVerifier
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.VdoRuleTestReport
import com.liomix.mikomi.data.video.remote.vdoplugin.store.VdoPluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 插件源设置页的 ViewModel。
 *
 * 列表来自 [VdoPluginRepository] 的热流：安装/删除/改名在商店页发生后，这一页会自己跟着变。
 * 页面上每个动作都是仓库的一次挂起写操作——UI 不再自己拼列表，因为磁盘才是真相。
 *
 * 「编辑规则」也在这里：编辑是一个面板而不是独立页面，状态就放在本页状态里，
 * 面板关掉即回到列表，不用推栈。
 *
 * 仓库地址与加速方式**仍只在内存里**：它们属于「从哪儿拉」，和规则文件无关，
 * 等 DataStore 那一层接上再落盘，现在改了只影响本次会话的下拉行为。
 */
@HiltViewModel
class PluginViewModel @Inject constructor(
    private val repository: VdoPluginRepository,
    private val verifier: VdoPluginRuleVerifier,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()

    /** 当前在跑的测试。重复点「开始测试」时掐掉上一条，免得旧结果后到覆盖新结果。 */
    private var testJob: Job? = null

    /** 正在读某条规则的原文。面板关掉或换了对象就掐掉，避免旧结果写进新面板。 */
    private var editorJob: Job? = null

    /** 上次测试用的关键词：同一个源常要反复测，重新打开面板时带出来省得重打。 */
    private var lastKeyword: String = ""

    init {
        viewModelScope.launch {
            // loaded 单独取出来：首次扫描完成前列表是空数组，但那时界面该显示加载态而不是「没有」。
            combine(repository.rules, repository.loaded) { rules, loaded -> rules to loaded }
                .collect { (rules, loaded) ->
                    _uiState.update { it.copy(rules = rules, loading = !loaded) }
                }
        }
    }

    /** 启用/停用：保留条目，只是不再参与搜索。 */
    fun onToggle(id: String) {
        val rule = _uiState.value.rules.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { repository.setEnabled(id, !rule.enabled) }
    }

    /**
     * 上移一格：长按拖动列表时，被拖的那条每越过半行就调一次。
     *
     * 已在首位时仓库内部什么都不做，不抛错——UI 侧按行高判断阈值，边界上仍可能多喊一次。
     */
    fun onMoveUp(id: String) {
        viewModelScope.launch { repository.move(id, -1) }
    }

    /** 下移一格。 */
    fun onMoveDown(id: String) {
        viewModelScope.launch { repository.move(id, 1) }
    }

    /** 删除：连 `.json` 文件一起删。确认弹窗在 UI 侧，走到这里就是真删。 */
    fun onRemove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    /**
     * 改规则仓库地址。
     *
     * 末尾斜杠统一去掉：拼 `<repo>/index.json` 时多一道斜杠有些 CDN 会 404。
     * 空串与非 http(s) 直接忽略——UI 侧已做校验，这里是最后一道。
     */
    fun onRepoUrlChanged(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
        _uiState.update { it.copy(repoUrl = normalized) }
    }

    /** 切换规则仓库加速。只改内存，见类注释。 */
    fun onMirrorSelected(mirror: RuleMirror) {
        _uiState.update { it.copy(mirror = mirror) }
    }

    /**
     * 准备分享：把规则原文塞进状态，由 Route 拉起系统分享面板。
     *
     * 分享的是**原文**而不是重排后的字段：规则的价值就在于原封不动地交给别人，
     * 转成模型再序列化回去，丢了字段顺序也丢了几行注释性的键。
     */
    fun onShare(id: String) {
        viewModelScope.launch {
            val rule = repository.get(id) ?: return@launch
            _uiState.update { it.copy(pendingShare = PluginShare(rule.name, rule.json)) }
        }
    }

    /** 分享面板已拉起，清掉一次性状态，免得下次进页面又弹一遍。 */
    fun onShareHandled() {
        _uiState.update { it.copy(pendingShare = null) }
    }

    /**
     * 打开测试面板。
     *
     * 打开时**不自动跑**：出流要真发三次请求（搜索/详情/播放），没填关键词时跑出来全是「未执行」，
     * 白等一轮还看不出问题。带上次用过的词进来，用户按一下就能测。
     */
    fun onTestOpen(id: String) {
        if (_uiState.value.rules.none { it.id == id }) return
        testJob?.cancel()
        testJob = null
        _uiState.update { it.copy(test = RuleTestState(ruleName = id, keyword = lastKeyword)) }
    }

    /** 按当前关键词跑完整链路：搜索 → 剧集 → 出流。 */
    fun onTestRun(keyword: String) {
        val name = _uiState.value.test?.ruleName ?: return
        startTest(ruleName = name, keyword = keyword.trim())
    }

    /** 关闭面板并掐掉在跑的测试。 */
    fun onTestDismiss() {
        testJob?.cancel()
        testJob = null
        _uiState.update { it.copy(test = null) }
    }

    private fun startTest(ruleName: String, keyword: String) {
        testJob?.cancel()
        lastKeyword = keyword
        _uiState.update {
            it.copy(test = RuleTestState(ruleName = ruleName, keyword = keyword, running = true))
        }
        testJob = viewModelScope.launch {
            val rule = repository.get(ruleName)
            val report: VdoRuleTestReport = if (rule == null) {
                // 文件被别的页面删了：给一份三步都未执行的空报告，比一句「规则不存在」更有信息量。
                VdoRuleTestReport(keyword = keyword)
            } else {
                try {
                    verifier.verify(rule.json, keyword)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 体检本身不该把面板搞崩：解析/网络之外的意外在这里兜住并留日志。
                    Log.w(TAG, "Rule test crashed, rule=$ruleName", e)
                    VdoRuleTestReport(keyword = keyword)
                }
            }
            _uiState.update { state ->
                // 面板可能已经被关掉或换了对象，只在还是这次请求时才写回。
                if (state.test?.ruleName != ruleName) {
                    state
                } else {
                    state.copy(test = state.test.copy(running = false, report = report))
                }
            }
        }
    }

    /**
     * 打开编辑面板并读盘。
     *
     * 打开即读原文、保存即写回原文，中间不做「字段模型 ↔ 文本」的双向转换：
     * 规则的字段多半是 XPath 字符串，转来转去只会把用户手写的换行和顺序揉掉。
     * 校验只做「能不能存」（JSON 合法 + 有 name/baseURL），选择器写得对不对交给「测试」。
     */
    fun onEditOpen(id: String) {
        editorJob?.cancel()
        _uiState.update { it.copy(editor = RuleEditorState(ruleName = id, loading = true)) }
        editorJob = viewModelScope.launch {
            val rule = repository.get(id)
            _uiState.update { state ->
                val editor = state.editor ?: return@update state
                // 读盘期间用户可能已经关掉面板又打开另一条，名字对不上就丢弃这次结果。
                if (editor.ruleName != id) {
                    state
                } else if (rule == null) {
                    state.copy(editor = editor.copy(loading = false, missing = true))
                } else {
                    state.copy(
                        editor = editor.copy(
                            loading = false,
                            text = rule.json,
                            originalText = rule.json,
                        ),
                    )
                }
            }
        }
    }

    fun onEditorTextChange(text: String) {
        // 改动即清掉上一次的报错：报错说的是「刚才那次」，不该赖在新内容上。
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(text = text, reason = null, failure = null))
        }
    }

    /**
     * 格式化。
     *
     * JSON 本身不合法时不清空内容、也不弹提示，只把错误标出来——用户正盯着那段文本，
     * 换掉它等于把人家改了一半的东西扔了。
     */
    fun onEditorFormat() {
        val editor = _uiState.value.editor ?: return
        val pretty = VdoPluginRuleFormat.pretty(editor.text)
        _uiState.update { state ->
            val current = state.editor ?: return@update state
            state.copy(
                editor = if (pretty == null) {
                    current.copy(reason = InvalidVdoPluginRuleException.Reason.MalformedJson)
                } else {
                    current.copy(text = pretty, reason = null, failure = null)
                },
            )
        }
    }

    /**
     * 保存：先本地校验，通过才写盘。
     *
     * 成功后直接把 `editor` 置空——面板就是「正在编辑」这件事本身，写盘成功它就该消失；
     * 列表那边由仓库热流自己更新（改名也会换到新的一行，不需要 UI 做任何事）。
     */
    fun onEditorSave() {
        val editor = _uiState.value.editor ?: return
        if (!editor.canSave) return

        val reason = VdoPluginRuleFormat.validate(editor.text)
        if (reason != null) {
            _uiState.update { it.copy(editor = it.editor?.copy(reason = reason)) }
            return
        }

        _uiState.update { it.copy(editor = it.editor?.copy(saving = true, reason = null, failure = null)) }
        viewModelScope.launch {
            try {
                repository.save(originalName = editor.ruleName, json = editor.text)
                _uiState.update { it.copy(editor = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidVdoPluginRuleException) {
                // 本地校验刚过、这里又不过，只可能是改名撞车：另有同名规则占着文件。
                _uiState.update { it.copy(editor = it.editor?.copy(saving = false, reason = e.reason)) }
            } catch (e: Exception) {
                Log.w(TAG, "Save plugin rule failed: ${editor.ruleName}", e)
                _uiState.update {
                    it.copy(
                        editor = it.editor?.copy(
                            saving = false,
                            failure = e.message ?: e::class.java.simpleName,
                        ),
                    )
                }
            }
        }
    }

    /** 关闭编辑面板。未保存内容的确认在 UI 侧问完了才调到这里。 */
    fun onEditorDismiss() {
        editorJob?.cancel()
        editorJob = null
        _uiState.update { it.copy(editor = null) }
    }

    private companion object {
        const val TAG = "PluginViewModel"
    }
}
