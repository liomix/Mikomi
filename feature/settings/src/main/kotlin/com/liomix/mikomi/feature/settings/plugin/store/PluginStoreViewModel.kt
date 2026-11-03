package com.liomix.mikomi.feature.settings.plugin.store

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.video.remote.vdoplg.rule.InvalidVdoPlgSourceException
import com.liomix.mikomi.data.video.remote.vdoplg.store.VdoPlgRepository
import com.liomix.mikomi.feature.settings.plugin.DEFAULT_RULES_REPO_URL
import com.liomix.mikomi.feature.settings.plugin.RuleMirror
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException

/**
 * 插件商店页的 ViewModel。
 *
 * 数据来源是规则仓库的 `index.json`（一个 JSON 数组，每条只有名字/版本/作者/更新时间）。
 * 一次进页面拉一次，**不落盘**：目录变得快，缓存到磁盘反而要处理过期与清理。
 *
 * 这里有三处刻意的取舍：
 * 1. **失败自动换镜像**：gh-proxy → ghfast → GitCode → 直连，逐个试到成功为止。
 *    Kazumi 是让用户自己去设置里切，本页改成自动降级——拉目录本来就不该需要用户做决定，
 *    而且切完还得回来看是否生效，来回两趟。
 * 2. **有数据时刷新不清空**：旧列表留着，只显示一条细进度；失败也只在顶部提示一行。
 *    商店里最烦的是「一刷新整页变白，然后报错」。
 * 3. **安装 = 下载 + 校验 + 落盘**：真正下载 `<name>.json`，交给 `VdoPlgRepository` 写进
 *    本机规则目录。落盘之后插件源页立刻就能看到这一条——两页读的是同一个仓库的热流。
 */
@HiltViewModel
class PluginStoreViewModel @Inject constructor(
    okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val repository: VdoPlgRepository,
) : ViewModel() {

    /**
     * 拉目录专用的 client：套一层短超时。
     *
     * 默认超时是 10s，四个通道依次超时就是 40s，用户只会看到一整屏白。
     * 目录就十几 KB，8 秒还不响应基本可以判定这条路不通，赶紧换下一条。
     */
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(PluginStoreUiState())
    val uiState: StateFlow<PluginStoreUiState> = _uiState.asStateFlow()

    /** 上次成功拉到目录的时间，用于「短时间内重进页面不重复拉」。 */
    private var loadedAtMillis = 0L

    init {
        // 已装版本直接来自规则仓库的热流：在插件源页删掉一条，这里的按钮会自己变回「安装」。
        viewModelScope.launch {
            repository.rules.collect { rules ->
                _uiState.update { state ->
                    state.copy(installedVersions = rules.associate { it.name to it.version })
                }
            }
        }
        loadCatalog(force = false)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onFilterChange(filter: StoreFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun onSortChange(sort: StoreSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    /** 顶栏刷新：无论是否有缓存都重新拉。 */
    fun onRefresh() = loadCatalog(force = true)

    /** 提示弹完清空，避免旋屏或重进页面时又弹一遍。 */
    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 安装或更新一条规则。
     *
     * `installing` 既是按钮的转圈依据，也是并发闸门：同一条规则被连点时，第二次直接返回，
     * 不会真的发两个请求。
     */
    fun onInstall(item: StoreItem) {
        val state = _uiState.value
        if (item.name in state.installing) return
        if (state.statusOf(item) == StoreItemStatus.Installed) return

        _uiState.update { it.copy(installing = it.installing + item.name) }
        viewModelScope.launch {
            try {
                val installedVersion = downloadRule(item.name)
                _uiState.update {
                    it.copy(
                        installing = it.installing - item.name,
                        installedVersions = it.installedVersions + (item.name to installedVersion),
                        message = StoreMessage.Installed(item.name),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failInstall(item.name, e)
            } catch (e: JSONException) {
                failInstall(item.name, e)
            } catch (e: InvalidVdoPlgSourceException) {
                failInstall(item.name, e)
            }
        }
    }

    private fun failInstall(name: String, error: Exception) {
        Log.w(TAG, "Install rule failed, name=$name", error)
        _uiState.update {
            it.copy(
                installing = it.installing - name,
                message = StoreMessage.InstallFailed(
                    name = name,
                    reason = error.message ?: error::class.java.simpleName,
                ),
            )
        }
    }

    /**
     * 拉目录。
     *
     * @param force false 时走「刚拉过就不拉」：从别的页面返回来不该再等一次网络。
     */
    private fun loadCatalog(force: Boolean) {
        // 已经在拉就不再开第二个：连点刷新只会排队，不会让请求数翻倍。
        if (_uiState.value.loading) return
        val state = _uiState.value
        val fresh = System.currentTimeMillis() - loadedAtMillis < CATALOG_TTL_MILLIS
        if (!force && state.hasContent && fresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val items = fetchCatalog()
                loadedAtMillis = System.currentTimeMillis()
                _uiState.update { it.copy(items = items, loading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failLoad(e)
            } catch (e: JSONException) {
                failLoad(e)
            }
        }
    }

    private fun failLoad(error: Exception) {
        Log.w(TAG, "Load rule catalog failed", error)
        _uiState.update {
            it.copy(
                loading = false,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private suspend fun fetchCatalog(): List<StoreItem> = withContext(ioDispatcher) {
        parseCatalog(fetchText(INDEX_FILE))
    }

    /**
     * 下载单条规则、落盘，并返回它的版本号。
     *
     * 校验交给仓库：它会检查 JSON 合法、`name` 与 `baseURL` 齐备，不合法就抛异常。
     * 因此「报成功」时磁盘上一定有一份能用的规则，不会出现「装上了但打开是空白」。
     */
    private suspend fun downloadRule(name: String): String = withContext(ioDispatcher) {
        val json = fetchText("$name.json")
        repository.install(json).version
    }

    private fun parseCatalog(raw: String): List<StoreItem> {
        val array = JSONArray(raw)
        val items = ArrayList<StoreItem>(array.length())
        for (index in 0 until array.length()) {
            // 单条坏数据只跳过，不拖垮整页：仓库是多人维护的，一条写错的规则不该让商店打不开。
            val entry = array.optJSONObject(index) ?: continue
            val name = entry.optString("name").trim()
            val version = entry.optString("version").trim()
            if (name.isEmpty() || version.isEmpty()) continue
            items += StoreItem(
                name = name,
                version = version,
                author = entry.optString("author").trim(),
                updatedAtMillis = entry.optLong("lastUpdate", 0L),
                // 新版字段是 antiCrawlerConfig.enabled，老规则直接给 antiCrawlerEnabled 布尔；
                // 两种都认，免得老规则被当成「无反爬」，装上才被验证页拦下来。
                antiCrawler = entry.optJSONObject("antiCrawlerConfig")?.optBoolean("enabled") == true ||
                    entry.optBoolean("antiCrawlerEnabled", false),
            )
        }
        if (array.length() > 0 && items.isEmpty()) {
            throw JSONException("目录里没有可用的规则")
        }
        return items
    }

    /**
     * 按 [MIRROR_FALLBACK] 顺序逐个通道试，第一个成功的赢。
     *
     * 全部失败时抛**最后一次**的错误：前面的多半是 DNS/连接被拒，
     * 直连那次的信息对用户最有参考价值。
     */
    private suspend fun fetchText(path: String): String = withContext(ioDispatcher) {
        var lastError: IOException? = null
        for (mirror in MIRROR_FALLBACK) {
            try {
                return@withContext getText(mirror.resolveUrl(path))
            } catch (e: IOException) {
                Log.d(TAG, "Mirror ${mirror.name} failed for $path: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IOException("没有可用的下载通道")
    }

    private fun getText(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) throw IOException("响应为空")
            return body
        }
    }

    /**
     * 把仓库路径拼成真实下载地址。
     *
     * 两个通用反代只加前缀（它们接受完整 URL）；GitCode 是**按仓库**做的镜像，
     * 只能把上游仓库地址整段换掉——换了仓库地址（自建/换源）时这一步自动退化成直连，
     * 不会拼出一个必然 404 的地址。
     */
    private fun RuleMirror.resolveUrl(path: String): String {
        val fullUrl = "$REPO_ROOT$path"
        return when (this) {
            RuleMirror.Direct -> fullUrl
            RuleMirror.GhProxy -> GH_PROXY_PREFIX + fullUrl
            RuleMirror.GhFast -> GH_FAST_PREFIX + fullUrl
            RuleMirror.GitCode -> fullUrl.replaceFirst(GITHUB_RAW_ROOT, GITCODE_ROOT)
        }
    }

    private companion object {
        const val TAG = "PluginStoreViewModel"

        /** 目录文件名；单条规则则是 `<name>.json`。 */
        const val INDEX_FILE = "index.json"

        /** 目录有效期：这段时间内重进页面直接复用，不重新发请求。 */
        const val CATALOG_TTL_MILLIS = 10 * 60 * 1000L

        const val FETCH_TIMEOUT_SECONDS = 8L

        /** 仓库根地址（带结尾斜杠），与插件源页共用同一个默认值。 */
        val REPO_ROOT = "$DEFAULT_RULES_REPO_URL/"

        /** 上游仓库的 raw 根地址：GitCode 镜像只能整段替换它。 */
        const val GITHUB_RAW_ROOT = "https://raw.githubusercontent.com/Predidit/KazumiRules/main"

        /** 两个通用反代：把完整 URL 接在后面即可。 */
        const val GH_PROXY_PREFIX = "https://gh-proxy.org/"
        const val GH_FAST_PREFIX = "https://ghfast.top/"

        /** GitCode 的 KazumiRules 镜像根地址。 */
        const val GITCODE_ROOT = "https://raw.gitcode.com/gh_mirrors/ka/KazumiRules/raw/main"

        /** 降级顺序：默认镜像 → 备用镜像 → 国内镜像 → 直连。 */
        val MIRROR_FALLBACK = listOf(
            RuleMirror.GhProxy,
            RuleMirror.GhFast,
            RuleMirror.GitCode,
            RuleMirror.Direct,
        )
    }
}
