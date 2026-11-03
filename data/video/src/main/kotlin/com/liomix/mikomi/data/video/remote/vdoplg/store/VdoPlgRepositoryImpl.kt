package com.liomix.mikomi.data.video.remote.vdoplg.store

import android.content.Context
import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.VdoPlgContent
import com.liomix.mikomi.data.video.remote.vdoplg.rule.InvalidVdoPlgSourceException
import com.liomix.mikomi.data.video.remote.vdoplg.rule.VdoPlgFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 插件源仓库的落盘实现。
 *
 * 存储就两份东西：正文目录 `vdoplugin/plugin/<name>.json` 与状态文件 `vdoplugin/state.json`。
 * 没有数据库——一个插件源就是几百字节的文件，用 Room 反而要处理迁移与 blob，收益为零。
 *
 * 三处刻意的设计：
 * 1. **首次启动播种**：把随包的插件样例（本包 `plugin/` 目录下那几份 json，构建时登记成
 *    assets 源集，见 build.gradle.kts）复制成已安装的源。播完往正文目录里写一个 `.seeded` 标记，
 *    之后即使用户把源全删光也不会再种回来——
 *    「我删掉了它」和「它自己又冒出来」是两种截然不同的体验。
 * 2. **单个坏文件不拖垮整页**：解析失败的那份跳过并列一条 warn 日志，文件原样留在磁盘，
 *    用户可以进编辑器改好它；反过来若整页因一个坏文件打不开，那个文件就再也修不到了。
 * 3. **写完即全量重扫**：所有写操作最后都走 [reloadLocked]，让内存列表永远等于磁盘现状。
 *    源的数量是个位数，重扫的成本远低于维护增量更新的一致性。
 */
@Singleton
internal class VdoPlgRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VdoPlgRepository {

    private val pluginsDir = VdoPlgPaths.plugins(context)
    private val stateFile = File(VdoPlgPaths.root(context), VdoPlgPaths.STATE_FILE)
    private val seededMarker = File(pluginsDir, VdoPlgPaths.SEEDED_MARKER)
    private val assets = context.assets

    /** 写操作串行化：两个源同时被删/改时，state 文件的读改写不会互相覆盖。 */
    private val mutex = Mutex()

    /** 仓库自己持有的作用域，只用来做首次磁盘扫描；随进程存活，无需取消。 */
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _rules = MutableStateFlow<List<VdoPlgContent>>(emptyList())
    override val rules: StateFlow<List<VdoPlgContent>> = _rules.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        // 不在构造函数里同步读盘：这个单例是在主线程上创建的，同步 IO 会直接踩 StrictMode。
        scope.launch { mutex.withLock { reloadLocked() } }
    }

    override suspend fun get(name: String): VdoPlgContent? {
        _rules.value.firstOrNull { it.name == name }?.let { return it }
        // 列表里没有但磁盘上有：文件是刚被外部改过的，读一次好过在编辑器里报「源不存在」。
        return withContext(ioDispatcher) {
            val file = File(pluginsDir, "$name$JSON_SUFFIX")
            if (!file.exists()) return@withContext null
            runCatching { VdoPlgFormat.parse(file.readText(), enabled = name !in readState().disabled) }
                .getOrNull()
        }
    }

    override suspend fun install(json: String): VdoPlgContent = mutex.withLock {
        val rule = VdoPlgFormat.parse(json)
        withContext(ioDispatcher) {
            ensureSeeded()
            val state = readState()
            File(pluginsDir, "${rule.name}$JSON_SUFFIX").writeText(json)
            // 已装过的保留原位置；新装的排到末尾。启停状态一律沿用 state，不因覆盖安装而变化。
            val order = if (rule.name in state.order) {
                state.order
            } else {
                state.order + rule.name
            }
            writeState(order, state.disabled)
            reloadLocked()
            requireNotNull(_rules.value.firstOrNull { it.name == rule.name }) {
                "源写入后未被扫描到：${rule.name}"
            }
        }
    }

    override suspend fun save(originalName: String, json: String): VdoPlgContent = mutex.withLock {
        val rule = VdoPlgFormat.parse(json)
        withContext(ioDispatcher) {
            ensureSeeded()
            val state = readState()
            val renamed = rule.name != originalName
            if (renamed && File(pluginsDir, "${rule.name}$JSON_SUFFIX").exists()) {
                // 源名同时是文件名，改名撞车会直接覆盖掉另一个源的内容。
                throw InvalidVdoPlgSourceException(InvalidVdoPlgSourceException.Reason.NameTaken, "name")
            }

            File(pluginsDir, "${rule.name}$JSON_SUFFIX").writeText(json)
            if (renamed) {
                // 新文件先写成功再删旧文件：中途失败最多多一个文件，不会把源整个弄丢。
                File(pluginsDir, "$originalName$JSON_SUFFIX").delete()
            }

            val disabled = state.disabled.toMutableSet()
            if (renamed && disabled.remove(originalName)) disabled += rule.name
            // 改名要顶替原位置，否则改个名字源就会掉到列表末尾，搜索优先级跟着变。
            val order = state.order.map { if (renamed && it == originalName) rule.name else it }
                .toMutableList()
                .apply { if (rule.name !in this) add(rule.name) }
            writeState(order.distinct(), disabled)
            reloadLocked()
            requireNotNull(_rules.value.firstOrNull { it.name == rule.name }) {
                "源写入后未被扫描到：${rule.name}"
            }
        }
    }

    override suspend fun remove(name: String) = mutex.withLock {
        withContext(ioDispatcher) {
            File(pluginsDir, "$name$JSON_SUFFIX").delete()
            val state = readState()
            writeState(state.order - name, state.disabled - name)
            reloadLocked()
        }
    }

    override suspend fun setEnabled(name: String, enabled: Boolean) = mutex.withLock {
        withContext(ioDispatcher) {
            val state = readState()
            val disabled = if (enabled) state.disabled - name else state.disabled + name
            writeState(state.order, disabled)
            reloadLocked()
        }
    }

    override suspend fun move(name: String, delta: Int) = mutex.withLock {
        withContext(ioDispatcher) {
            // 以内存里的顺序为基准：state 文件若因外部改动落后，重排会算错一格。
            val current = _rules.value.map { it.name }
            val from = current.indexOf(name)
            if (from < 0) return@withContext
            val to = (from + delta).coerceIn(0, current.lastIndex)
            if (to == from) return@withContext
            val order = current.toMutableList().apply { add(to, removeAt(from)) }
            writeState(order, readState().disabled)
            reloadLocked()
        }
    }

    /** 全量重扫：目录 → 源列表，顺带把漂掉的状态文件写回去。调用方必须已持有 [mutex]。 */
    private suspend fun reloadLocked() = withContext(ioDispatcher) {
        ensureSeeded()
        val state = readState()
        val files = pluginsDir.listFiles { file -> file.isFile && file.name.endsWith(JSON_SUFFIX) }.orEmpty()

        val byName = HashMap<String, VdoPlgContent>(files.size)
        files.forEach { file ->
            val rule = runCatching { VdoPlgFormat.parse(file.readText()) }
                .onFailure { Log.w(TAG, "Skip unreadable rule file: ${file.name}", it) }
                .getOrNull() ?: return@forEach
            // 文件名必须等于源名，否则 remove/save 按名字找文件会找空。
            // 外部拷进来的文件常有这个问题，这里就地改名对齐；目标已存在就只好放弃这一个。
            if (file.nameWithoutExtension != rule.name && !normalizeFileName(file, rule.name)) {
                return@forEach
            }
            byName[rule.name] = rule.copy(enabled = rule.name !in state.disabled)
        }

        val ordered = buildList {
            state.order.forEach { name -> byName[name]?.let(::add) }
            // 不在 state 里的（外部拷进来的文件）按名字补在末尾，保证顺序稳定。
            byName.values
                .filterNot { it.name in state.order }
                .sortedBy { it.name.lowercase() }
                .forEach(::add)
        }

        val order = ordered.map { it.name }
        if (order != state.order) writeState(order, state.disabled)
        _rules.value = ordered
        _loaded.value = true
    }

    /**
     * 把文件名改成与源名一致。
     *
     * @return false 表示没法改（目标已存在或改名失败），调用方应当跳过这个文件。
     */
    private fun normalizeFileName(file: File, name: String): Boolean {
        val target = File(pluginsDir, "$name$JSON_SUFFIX")
        if (target.exists()) {
            Log.w(TAG, "Rule file ${file.name} collides with ${target.name}, skipped")
            return false
        }
        return if (file.renameTo(target)) {
            true
        } else {
            Log.w(TAG, "Rename rule file ${file.name} -> ${target.name} failed")
            false
        }
    }

    /** 建立目录并做一次性播种。播种失败不抛：只影响首屏有没有样例，不该让页面打不开。 */
    private fun ensureSeeded() {
        if (!pluginsDir.exists()) pluginsDir.mkdirs()
        if (seededMarker.exists()) return
        // 内置源目录被整体登记成 assets 源集，所以它们就躺在 assets 根下、没有前缀。
        // 但 assets 根是各库共用的，别的依赖也会往这里塞 json（实测就有一个 schedule_aliases.json），
        // 所以不按文件名认，只认「能解析成插件源」的那几份，别把别人的配置文件种成源。
        val seeds = runCatching { assets.list("").orEmpty() }.getOrDefault(emptyArray())
        seeds.filter { it.endsWith(JSON_SUFFIX) }.forEach { assetName ->
            runCatching {
                val text = assets.open(assetName).use { it.readBytes().toString(Charsets.UTF_8) }
                val rule = VdoPlgFormat.parse(text)
                // 落盘用源名而不是 assets 里的文件名：外面改过文件名也不会种出一个名不符实的源。
                val target = File(pluginsDir, "${rule.name}$JSON_SUFFIX")
                if (!target.exists()) target.writeText(text)
            }.onFailure { Log.i(TAG, "Skip seed asset: $assetName") }
        }
        // 标记写在最后：中途崩了下次启动还能补种。
        runCatching { seededMarker.createNewFile() }
    }

    private fun readState(): VdoPlgState = runCatching {
        val json = JSONObject(stateFile.readText())
        VdoPlgState(
            order = json.optJSONArray(KEY_ORDER).toStringList(),
            disabled = json.optJSONArray(KEY_DISABLED).toStringList().toSet(),
        )
    }.getOrDefault(VdoPlgState())

    private fun writeState(order: List<String>, disabled: Set<String>) {
        runCatching {
            val json = JSONObject()
                .put(KEY_ORDER, JSONArray(order))
                .put(KEY_DISABLED, JSONArray(disabled.toList()))
            stateFile.writeText(json.toString(2))
        }.onFailure { Log.w(TAG, "Write plugin state failed", it) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotEmpty() }
        }
    }

    private data class VdoPlgState(
        val order: List<String> = emptyList(),
        val disabled: Set<String> = emptySet(),
    )

    private companion object {
        const val TAG = "VdoPlgRepository"

        const val JSON_SUFFIX = ".json"
        const val KEY_ORDER = "order"
        const val KEY_DISABLED = "disabled"
    }
}
