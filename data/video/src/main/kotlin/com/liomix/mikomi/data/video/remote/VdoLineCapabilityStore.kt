package com.liomix.mikomi.data.video.remote

import android.content.Context
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 线路能力缓存：记住「这条线路上次是怎么出流的」。
 *
 * 目的只有一个——**省掉一轮没有必要的解析**。上次是直链，这次就直接验证那条地址；
 * 上次必须渲染，这次就别再浪费两次 HTTP 去翻 HTML。
 *
 * 不区分源：三大源的线路都记在这一个文件里（`filesDir/vdo/lines.json`），
 * 键里带源名，谁也不会读到别人的结论。
 *
 * 三条刻意的规则：
 * 1. **结论是猜的，用之前必须验证**。缓存只被当作「候选」交给解析策略，
 *    命中后仍然要 probe 一次。不验证就用等于赌，站点一改版就会拿一个死地址去播。
 * 2. **过期与连续不符都作废**。TTL 七天；同一条线路连续两次与缓存不符就删掉，
 *    避免一次误判永久生效（比如某天站点临时挂了，我们不能把它记成「必须渲染」）。
 * 3. **不落 Cookie**。缓存里只有地址和类型，请求头每次重新算——Cookie 会过期，
 *    存下来的旧头只会制造 403，而且是那种「测试通过、播放 403」的难查问题。
 *
 * 文件损坏时静默当成空缓存：这个文件的全部价值就是省时间，不能因为它坏了让用户看不了番。
 */
@Singleton
class VdoLineCapabilityStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** 跨源共用的落点：`filesDir/vdo/lines.json`。放在 `vdoplugin/` 之外，因为它不属于任何一个源。 */
    private val file = File(File(context.filesDir, STORAGE_ROOT), STORAGE_FILE)

    /** 读改写串行：多条线路同时探测时，不能各写各的。 */
    private val mutex = Mutex()

    /** 懒加载；null 表示还没读过盘。 */
    private var entries: MutableMap<String, Entry>? = null

    /** 查一条线路的结论；没有、过期、或读盘失败都返回 null（等于「没缓存」）。 */
    suspend fun find(line: VdoLineRef): VdoLineCapability? = mutex.withLock {
        val all = read()
        val key = line.storageKey
        val entry = all[key] ?: return@withLock null
        if (entry.isExpired()) {
            all.remove(key)
            write(all)
            return@withLock null
        }
        entry.capability
    }

    /** 记下一条新结论。成功出流时调用。 */
    suspend fun remember(line: VdoLineRef, capability: VdoLineCapability) = mutex.withLock {
        val all = read()
        all[line.storageKey] = Entry(capability, savedAtMillis = System.currentTimeMillis(), mismatches = 0)
        write(all)
    }

    /**
     * 报告「缓存与这次的实际结果不符」。
     *
     * 第一次只记一笔，第二次才删——单次不符很可能只是站点临时抽风（限流、CDN 抖一下），
     * 立刻删掉会让缓存永远活不过一次小故障。
     */
    suspend fun reportMismatch(line: VdoLineRef) = mutex.withLock {
        val all = read()
        val key = line.storageKey
        val entry = all[key] ?: return@withLock
        if (entry.mismatches + 1 >= MAX_MISMATCHES) {
            all.remove(key)
        } else {
            all[key] = entry.copy(mismatches = entry.mismatches + 1)
        }
        write(all)
    }

    /**
     * 直接丢掉一条线路的结论。
     *
     * 用户主动点「重试」时调用：那个动作的语义就是「我不信上次的判断」，
     * 不清理缓存的话，重试只会把同一个结论再确认一遍。
     */
    suspend fun forget(line: VdoLineRef) = mutex.withLock {
        val all = read()
        if (all.remove(line.storageKey) != null) write(all)
    }

    private suspend fun read(): MutableMap<String, Entry> {
        entries?.let { return it }
        val loaded = withContext(ioDispatcher) {
            runCatching { decode(file.readText()) }.getOrDefault(mutableMapOf())
        }
        entries = loaded
        return loaded
    }

    private suspend fun write(all: Map<String, Entry>) {
        withContext(ioDispatcher) {
            entries = all.toMutableMap()
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(encode(all))
            }
        }
    }

    private fun decode(raw: String): MutableMap<String, Entry> {
        val lines = JSONObject(raw).optJSONObject(KEY_LINES) ?: return mutableMapOf()
        return buildMap {
            lines.keys().forEach { key ->
                val item = lines.optJSONObject(key) ?: return@forEach
                val capability = when (item.optString("type")) {
                    TYPE_DIRECT -> VdoLineCapability.Direct(
                        url = item.optString("url"),
                        kind = kindOf(item.optString("kind")),
                    )

                    TYPE_RENDERED -> VdoLineCapability.Rendered(item.optString("note"))
                    else -> return@forEach
                }
                put(
                    key,
                    Entry(
                        capability = capability,
                        savedAtMillis = item.optLong("savedAt"),
                        mismatches = item.optInt("mismatches"),
                    ),
                )
            }
        }.toMutableMap()
    }

    private fun encode(all: Map<String, Entry>): String {
        val lines = JSONObject()
        all.forEach { (key, entry) ->
            lines.put(
                key,
                JSONObject().apply {
                    when (val capability = entry.capability) {
                        is VdoLineCapability.Direct -> {
                            put("type", TYPE_DIRECT)
                            put("url", capability.url)
                            put("kind", capability.kind.name)
                        }

                        is VdoLineCapability.Rendered -> {
                            put("type", TYPE_RENDERED)
                            put("note", capability.note)
                        }
                    }
                    put("savedAt", entry.savedAtMillis)
                    put("mismatches", entry.mismatches)
                },
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put(KEY_LINES, lines)
            .toString()
    }

    private fun kindOf(name: String): VdoStreamKind =
        VdoStreamKind.entries.firstOrNull { it.name == name } ?: VdoStreamKind.Other

    private data class Entry(
        val capability: VdoLineCapability,
        val savedAtMillis: Long,
        val mismatches: Int,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
            // 「必须渲染」的结论过期得更快：站点哪天改成明文直链了，我们得早点发现。
            val ttl = when (capability) {
                is VdoLineCapability.Direct -> TTL_MILLIS
                is VdoLineCapability.Rendered -> RENDERED_TTL_MILLIS
            }
            return now - savedAtMillis > ttl
        }
    }

    private companion object {
        /** 与源无关的落点：`filesDir/vdo/`。 */
        const val STORAGE_ROOT = "vdo"
        const val STORAGE_FILE = "lines.json"

        const val FORMAT_VERSION = 1
        const val KEY_LINES = "lines"
        const val TYPE_DIRECT = "direct"
        const val TYPE_RENDERED = "rendered"

        const val MAX_MISMATCHES = 2

        /** 七天。再长就会把「站点上个月的样子」当成现在。 */
        const val TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** 三天。「必须渲染」是个更强的结论，给它的新鲜度要求也更高。 */
        const val RENDERED_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
