package com.liomix.mikomi.feature.settings.network

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.BangumiApiMode
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 网络设置页的 ViewModel。
 *
 * 状态来源与去向都是 `UserDataRepository`（DataStore）：进入时读一次当前值，
 * 之后用户改动即时反映在本页状态并写回仓库。地址输入按「停止输入 600ms 后落盘」
 * 处理，避免每敲一个字符都写一次 DataStore。
 *
 * 写库之后**不做持续回读**：本页是唯一写入方，回读反而可能用旧值覆盖用户正在输入的内容。
 */
@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userDataRepository: UserDataRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = userDataRepository.observeUserPreferences().first()
            _uiState.update {
                it.copy(
                    mode = BangumiApiMode.fromId(prefs.bangumiApiMode),
                    mirrorUrl = prefs.bangumiMirrorUrl,
                )
            }
        }
    }

    /** 选择连接方式；切换模式会让上一次的测试结果失效。 */
    fun selectMode(mode: BangumiApiMode) {
        if (_uiState.value.mode == mode) return
        testJob?.cancel()
        _uiState.update { it.copy(mode = mode, mirrorTest = MirrorTestResult.Idle) }
        viewModelScope.launch {
            try {
                userDataRepository.setBangumiApiMode(mode.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Persist bangumi api mode failed, mode=${mode.id}", e)
            }
        }
    }

    /** 编辑镜像地址：本地即时更新，停止输入后落盘；地址变化同时让测试结果失效。 */
    fun updateMirrorUrl(url: String) {
        if (_uiState.value.mirrorUrl == url) return
        testJob?.cancel()
        _uiState.update { it.copy(mirrorUrl = url, mirrorTest = MirrorTestResult.Idle) }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(MIRROR_URL_SAVE_DELAY_MILLIS)
            try {
                userDataRepository.setBangumiMirrorUrl(url.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Persist bangumi mirror url failed", e)
            }
        }
    }

    /** 测试镜像是否**真的可用**：GET 首页依赖的接口，2xx 才算通过。 */
    fun testConnection() {
        val state = _uiState.value
        if (!state.hasUsableMirror) return
        val baseUrl = state.mirrorUrl.trim().trimEnd('/')

        testJob?.cancel()
        _uiState.update { it.copy(mirrorTest = MirrorTestResult.Testing) }
        testJob = viewModelScope.launch {
            val result = withContext(ioDispatcher) { probe(baseUrl) }
            _uiState.update { it.copy(mirrorTest = result) }
        }
    }

    private fun probe(baseUrl: String): MirrorTestResult {
        // 单独的短超时 client：这里只需要判断「通不通」，不该等默认的 10s。
        val client = okHttpClient.newBuilder()
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(baseUrl + PROBE_PATH)
            .get()
            .build()
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            client.newCall(request).execute().use { response ->
                val latency = SystemClock.elapsedRealtime() - startedAt
                if (response.isSuccessful) {
                    MirrorTestResult.Reachable(latency)
                } else {
                    MirrorTestResult.HttpError(response.code)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MirrorTestResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private companion object {
        const val TAG = "NetworkViewModel"

        /** 探测路径就用首页真正依赖的接口：它不通，镜像配了也白配。 */
        const val PROBE_PATH = "/p1/trending/subjects?type=2&limit=1&offset=0"
        const val PROBE_TIMEOUT_SECONDS = 6L

        /** 地址输入的落盘延迟（防抖）。 */
        const val MIRROR_URL_SAVE_DELAY_MILLIS = 600L
    }
}
