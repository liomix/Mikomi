package com.liomix.mikomi.feature.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.liomix.mikomi.core.media.MikomiPlayer
import com.liomix.mikomi.core.media.MikomiPlayerFactory
import com.liomix.mikomi.core.model.VdoPlgContent
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.video.VdoEpisode
import com.liomix.mikomi.data.video.VdoEpisodeRoad
import com.liomix.mikomi.data.video.VdoFailureReason
import com.liomix.mikomi.data.video.VdoLineRef
import com.liomix.mikomi.data.video.VdoSearchHit
import com.liomix.mikomi.data.video.VdoSourceException
import com.liomix.mikomi.data.video.VdoStreamKind
import com.liomix.mikomi.data.video.VdoStreamPlan
import com.liomix.mikomi.data.video.remote.vdoplg.VdoPlgEngine
import com.liomix.mikomi.data.video.remote.vdoplg.store.VdoPlgRepository
import com.liomix.mikomi.data.video.remote.vdoplg.stream.VdoPlgPlanRequest
import com.liomix.mikomi.data.video.remote.vdoplg.stream.VdoPlgStreamPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 播放页 ViewModel：把「番名」一路变成「画面上在动的视频」。
 *
 * 链路是 v3 里定下的那三步，只是收在一个 VM 里跑：
 * **番名 → 各插件源搜索（并发）→ 选定源的剧集/线路 → 该集的播放地址 → Media3**。
 *
 * 几个刻意的取舍：
 * 1. **不缓存在详情页**：进播放页才开始请求。用户在详情页只是看简介时，不该替他发六个请求。
 * 2. **并发打源、串行出流**：搜索/取剧集是「哪个源能用」的探测，值得并行（限 3）；
 *    出流只对最终选中的那一集做，串行且只有一条。
 * 3. **切集/换源都不重新搜索**：源与剧集在一次会话里是稳定的，重来只会白等。
 *    只有「重试」才真的重跑探测。
 * 4. **播放位置只在新流上用一次**：换源/换线路保留进度（用户是「这条线路卡了，换一条」），
 *    切集则从 0 开始。
 *
 * 注意本项目 `NavDisplay` 没有 per-entry 的 ViewModelStore，播放页拿到的是 Activity 级实例，
 * 所以 [bind] 必须按 `animeId` 判等，换番时整页重置。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val pluginRepository: VdoPlgRepository,
    private val engine: VdoPlgEngine,
    private val planner: VdoPlgStreamPlanner,
    playerFactory: MikomiPlayerFactory,
) : ViewModel(), PlayerCommandSink {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** 播放进度与播放态。UI 读它，Media3 的 listener 写它。 */
    val playback = PlayerPlaybackState().also { it.sink = this }

    /** 播放器实例：跟着这个 VM 走，`onCleared` 时释放。 */
    val player: MikomiPlayer = playerFactory.create()

    private var boundAnimeId: String? = null
    private var loadJob: Job? = null
    private var playJob: Job? = null

    /** 每个启用的规则探测出来的东西（命中、线路、失败原因）。切集/换线路都靠它，不重复请求。 */
    private var ruleStates: List<RuleState> = emptyList()

    /**
     * 已经因为「直链播不动」降级过 WebView 的 `源|集`。
     *
     * 不落 UI 状态：它只服务于防循环，界面上一旦开始降级就没有「回去」这一步。
     */
    private val webFallbacks = mutableSetOf<String>()

    // —— 播放器回写 ——

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playback.onIsPlayingChanged(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playback.onBufferingChanged(playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) {
                val duration = player.exoPlayer.duration
                if (duration != C.TIME_UNSET && duration > 0L) playback.onDurationChanged(duration)
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            playback.onSpeedChanged(playbackParameters.speed)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback failed: ${error.errorCodeName}", error)
            // 直链播不动先自己降一级去嗅探（见 fallbackToWeb），实在不行才让用户看到失败。
            if (fallbackToWeb()) return
            _uiState.update {
                it.copy(
                    loading = false,
                    failure = PlayerFailure(PlayerFailureReason.Playback, error.errorCodeName),
                )
            }
        }
    }

    init {
        player.exoPlayer.addListener(playerListener)
        startProgressTicker()
    }

    override fun onCleared() {
        super.onCleared()
        player.exoPlayer.removeListener(playerListener)
        player.release()
    }

    // —— 页面生命周期 ——

    /**
     * 绑定路由参数。
     *
     * 同一部番内只是换集：不重新探测，直接按集号跳。
     * 换了番才整页重置——上一部番的选集、源、进度全都不能留。
     */
    fun bind(animeId: String, episodeId: String) {
        if (boundAnimeId == animeId) {
            val number = episodeId.toIntOrNull() ?: return
            val current = _uiState.value.currentEpisode
            if (current?.number != number) playEpisodeAt(number - 1, keepPosition = false)
            return
        }
        boundAnimeId = animeId
        loadJob?.cancel()
        playJob?.cancel()
        player.reset()
        playback.resetForNewMedia()
        ruleStates = emptyList()
        _uiState.value = PlayerUiState(animeId = animeId, loading = true)
        loadJob = viewModelScope.launch { load(animeId, episodeId.toIntOrNull() ?: 1) }
    }

    /**
     * 离开播放页：暂停，但不释放。
     *
     * 不释放是因为这个 ViewModel 是 Activity 级的（共享 Store），返回再进来应当接着看；
     * 不暂停则是真事故——用户退回详情页，声音还在播。
     */
    fun onLeavePage() {
        player.pause()
    }

    // —— 播放控制（PlayerCommandSink） ——

    override fun togglePlayPause() {
        // WebView 形态下播放由站点播放器掌握，我们插一手只会打架。
        if (_uiState.value.mode == PlaybackMode.WEB) return
        if (player.exoPlayer.isPlaying) player.pause() else player.play()
    }

    override fun seekTo(positionMillis: Long) {
        if (_uiState.value.mode == PlaybackMode.WEB) return
        player.seekTo(positionMillis)
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (_uiState.value.mode == PlaybackMode.WEB) return
        player.setSpeed(speed)
    }

    override fun retryPlayback() {
        val state = _uiState.value
        // WebView 形态：地址是站点自己算的，我们能做的只有让它重新加载这一页。
        if (state.mode == PlaybackMode.WEB) {
            _uiState.update { it.copy(webReloadToken = it.webReloadToken + 1, failure = null) }
            return
        }
        // 「重试」是重新出流：地址多半过期了（这类站点的播放地址通常只有几十分钟寿命），
        // 对同一个地址再 prepare 一次只会再失败一次。
        val sourceId = state.selectedSourceId ?: return reload()
        playOn(sourceId, state.currentEpisodeIndex, keepPosition = true, forgetCache = true)
    }

    // —— UI 动作 ——

    fun onTabSelected(tab: PlayerTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onOrderToggle() {
        _uiState.update {
            it.copy(
                order = when (it.order) {
                    EpisodeOrder.ASCENDING -> EpisodeOrder.DESCENDING
                    EpisodeOrder.DESCENDING -> EpisodeOrder.ASCENDING
                },
            )
        }
    }

    fun onEpisodeSelected(episode: EpisodeItem) {
        val index = _uiState.value.episodes.indexOfFirst { it.id == episode.id }
        if (index >= 0) playEpisodeAt(index, keepPosition = false)
    }

    fun onPreviousEpisode() {
        val state = _uiState.value
        val previous = state.previousEpisode ?: return
        onEpisodeSelected(previous)
    }

    fun onNextEpisode() {
        val state = _uiState.value
        val next = state.nextEpisode ?: return
        onEpisodeSelected(next)
    }

    fun onDanmakuToggle() {
        _uiState.update { it.copy(danmakuEnabled = !it.danmakuEnabled) }
    }

    fun onSourceClick() {
        _uiState.update { it.copy(sourceSheetVisible = true) }
    }

    fun onSourceSheetDismiss() {
        _uiState.update { it.copy(sourceSheetVisible = false) }
    }

    /**
     * 选线路并切播。
     *
     * 只在 UI 里点「线路」时调用；单线路源由整行点击直接落到这里。
     * 保留播放进度：用户换线路的动机通常是「这条卡/挂了」，不是「我要重看开头」。
     *
     * 非就绪线路（之前出流失败过）也允许点：那等于「再试一次」，比把入口做成灰的更符合直觉。
     */
    fun onLineSelected(sourceId: String, lineId: String) {
        val state = _uiState.value
        val source = state.sources.firstOrNull { it.id == sourceId } ?: return
        if (source.lines.none { it.id == lineId }) return
        playOn(sourceId, state.currentEpisodeIndex, lineId = lineId, keepPosition = true)
    }

    /** 整源重试：重新搜索这个源，再回到当前集。 */
    fun onRetrySource(sourceId: String) {
        val rule = ruleStates.firstOrNull { it.rule.name == sourceId }?.rule ?: return
        val keyword = _uiState.value.animeTitle.ifBlank { return }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, failure = null, sources = it.sources.markChecking(setOf(sourceId))) }
            val probed = probe(rule, keyword)
            ruleStates = ruleStates.map { if (it.rule.name == sourceId) probed else it }
            publishSources()
            if (probed.roads.isEmpty()) {
                fail(probed.failure.toFailureReason(), probed.rule.name)
            } else {
                playOn(sourceId, _uiState.value.currentEpisodeIndex, keepPosition = false)
            }
        }
    }

    /** 单条线路重试：只重出这一条，不动其它线路，并且不信上次的能力结论。 */
    fun onRetryLine(sourceId: String, lineId: String) {
        playOn(sourceId, _uiState.value.currentEpisodeIndex, lineId = lineId, keepPosition = true, forgetCache = true)
    }

    /** 「全部重试」：把启用源整体重跑一遍，当前在播的集数尽量保持。 */
    fun onRetryAllSources() {
        reload(keepCurrentEpisode = true)
    }

    /**
     * WebView 在网络层拦到一条「像视频流」的请求。
     *
     * 这是把「显示网页让用户自己点」变成「直接进自己的播放器」的**唯一交接点**：
     * 站点播放器拿到地址后一定会去 GET 那个 `.m3u8`（跨域 iframe 也一样，
     * 请求走的是 WebView 自己的网络栈），我们截住它，交给 Media3。
     *
     * 三道门：
     * 1. 必须仍在 Web 形态——切回原生后这里再来消息就是重复的；
     * 2. `pageKey` 必须等于当前这一集——切集瞬间旧页面还会飞一会儿请求，
     *    不比对就会把上一集的流接到新一集上；
     * 3. 出流层复核一遍并补齐请求头（Referer / Cookie / UA），缺了就是 403。
     */
    fun onWebSubRequest(
        pageKey: String,
        url: String,
        headers: Map<String, String>,
        cookie: String?,
    ) {
        val state = _uiState.value
        if (state.mode != PlaybackMode.WEB || pageKey != state.webUrl) return
        val stream = engine.sniffStream(url, headers, pageKey, cookie) ?: return

        // 立刻切回原生形态：WebView 随之销毁，页面自己的播放器停掉，不会出现两个声音。
        playJob?.cancel()
        _uiState.update {
            it.copy(
                mode = PlaybackMode.NATIVE,
                webUrl = null,
                loading = false,
                failure = null,
            )
        }
        playback.resetForNewMedia()
        player.play(
            url = stream.url,
            mimeType = stream.kind.mimeType,
            headers = stream.headers,
        )
        // 这条线路下次可以直接验证这个地址，省掉一整轮解析。
        lineRefOf(state.selectedSourceId)?.let { line ->
            viewModelScope.launch { planner.rememberSniffed(line, stream) }
        }
    }

    // —— 主链路 ——

    private suspend fun load(animeId: String, startNumber: Int) {
        val title = resolveAnimeTitle(animeId)
        if (title.isNullOrEmpty()) {
            // 番名是搜索的唯一输入：拿不到它，后面一步都走不了，如实说明比转圈强。
            fail(PlayerFailureReason.NoTitle, animeId)
            return
        }
        _uiState.update { it.copy(animeTitle = title) }

        val rules = awaitEnabledRules()
        if (rules.isEmpty()) {
            fail(PlayerFailureReason.NoSource)
            return
        }

        ruleStates = probeAll(rules, title)
        publishSources()

        val firstPlayable = ruleStates.firstOrNull { it.roads.isNotEmpty() }
        if (firstPlayable == null) {
            fail(ruleStates.firstNotNullOfOrNull { it.failure }.toFailureReason() ?: PlayerFailureReason.NoMatch)
            return
        }
        playOn(firstPlayable.rule.name, episodeIndex = startNumber - 1, keepPosition = false)
    }

    /**
     * 取番名。
     *
     * 优先读本地（详情页刚看过就一定有）；本地没有（深链直接进播放页、或 Room 被清过）
     * 才去拉一次详情——番名是搜索唯一的输入，为它多一次请求是值得的。
     */
    private suspend fun resolveAnimeTitle(animeId: String): String? {
        val cached = animeRepository.observeAnime(animeId).first()?.title?.trim()
        if (!cached.isNullOrEmpty()) return cached
        val refreshed = withTimeoutOrNull(DETAIL_TIMEOUT_MILLIS) {
            runCatching { animeRepository.refreshAnimeDetail(animeId) }.isSuccess
        } ?: false
        if (!refreshed) return null
        return animeRepository.observeAnime(animeId).first()?.title?.trim()
    }

    private fun reload(keepCurrentEpisode: Boolean = false) {
        val animeId = boundAnimeId ?: return
        val number = _uiState.value.currentEpisode?.number ?: 1
        loadJob?.cancel()
        playJob?.cancel()
        player.reset()
        playback.resetForNewMedia()
        ruleStates = emptyList()
        _uiState.update {
            it.copy(
                loading = true,
                failure = null,
                mode = PlaybackMode.NATIVE,
                webUrl = null,
                // 重试时保留选集与当前集，用户不会看到网格突然空一下。
                episodes = if (keepCurrentEpisode) it.episodes else emptyList(),
                currentEpisode = if (keepCurrentEpisode) it.currentEpisode else null,
            )
        }
        loadJob = viewModelScope.launch { load(animeId, number) }
    }

    /**
     * 播放某个源的第 [episodeIndex] 集。
     *
     * @param lineId 目标线路；null 表示沿用当前线路，再没有就用第一条。
     * @param keepPosition 是否保留当前播放进度（换源/换线路为 true，切集为 false）。
     * @param forgetCache 是否先丢掉这条线路的能力缓存。用户点「重试」时为 true——
     *   那个动作的意思是「我不信上次的判断」，不清理的话重试只是把同一个结论再验一遍。
     */
    private fun playOn(
        sourceId: String,
        episodeIndex: Int,
        lineId: String? = null,
        keepPosition: Boolean = false,
        forgetCache: Boolean = false,
    ) {
        playJob?.cancel()
        val resumePosition = if (keepPosition) playback.positionMillis else 0L
        val wasPlaying = playback.isPlaying

        playJob = viewModelScope.launch {
            if (forgetCache) lineRefOf(sourceId)?.let { planner.forget(it) }
            val state = ruleStates.firstOrNull { it.rule.name == sourceId }
            if (state == null || state.roads.isEmpty()) {
                fail(PlayerFailureReason.NoMatch, sourceId)
                return@launch
            }
            _uiState.update { it.copy(loading = true, failure = null) }

            val roadIndex = lineId?.substringAfterLast(LINE_ID_SEPARATOR)?.toIntOrNull()
                ?.takeIf { it in state.roads.indices }
                ?: currentRoadIndex(sourceId)
            val road = state.roads[roadIndex]
            val episodes = road.toEpisodeItems()
            if (episodes.isEmpty()) {
                fail(PlayerFailureReason.NoEpisodes, state.rule.name)
                return@launch
            }

            val index = episodeIndex.coerceIn(0, episodes.lastIndex)
            val episode = episodes[index]

            // 出流交给策略链：规则里的 useWebview 只是提示，不再决定走哪条路。
            // 能直链的线路拿回进度条与倍速，拿不到才退到网页。
            val request = VdoPlgPlanRequest(
                ruleJson = state.rule.json,
                line = VdoLineRef(
                    sourceName = state.rule.name,
                    lineName = road.name,
                    // 身份用**首集**的地址形状：同一条线路上换哪一集，都不该换掉它的身份。
                    firstEpisodeUrl = road.episodes.firstOrNull()?.url ?: episode.id,
                ),
                episodeUrl = episode.id,
            )
            val plan = try {
                withTimeoutOrNull(PLAN_TIMEOUT_MILLIS) { planner.plan(request) }?.plan
            } catch (e: CancellationException) {
                throw e
            } catch (e: VdoSourceException) {
                markLine(sourceId, roadIndex, SourceAvailability.FAILED)
                fail(e.reason.toFailureReason(), e.detail)
                return@launch
            }
            if (plan == null) {
                markLine(sourceId, roadIndex, SourceAvailability.FAILED)
                fail(PlayerFailureReason.Network, "出流超时")
                return@launch
            }

            if (plan is VdoStreamPlan.Web) {
                Log.i(TAG, "Render playback page: ${plan.reason}")
                enterWebMode(state.rule, episode, episodes, sourceId, roadIndex, userAgent = plan.userAgent)
                return@launch
            }

            val stream = (plan as VdoStreamPlan.Native).stream

            // 先更新 UI 再出流：选集高亮与线路选中在这一刻就该切换，
            // 等播放器 ready 才变会让用户以为点空了。
            _uiState.update {
                it.copy(
                    loading = false,
                    failure = null,
                    episodes = episodes,
                    currentEpisode = episode,
                    selectedSourceId = sourceId,
                    sourceSheetVisible = false,
                    // 从 WebView 形态切回原生时要把 webUrl 清掉，否则画面还挂着上一个网页。
                    mode = PlaybackMode.NATIVE,
                    webUrl = null,
                    sources = it.sources.markPlaying(sourceId, roadIndex, PlaybackMode.NATIVE),
                )
            }
            playback.resetForNewMedia()
            player.play(
                url = stream.url,
                mimeType = stream.kind.mimeType,
                headers = stream.headers,
                startPositionMillis = resumePosition,
            )
            // 换源时如果原来是暂停的，就保持暂停：用户是在挑线路，不是点了继续。
            if (!wasPlaying && keepPosition) player.pause()
        }
    }

    /**
     * 直链播不动时，**原地降一级**到 WebView 嗅探。
     *
     * 静态拿到的直链经常「第一次能播、第 N 次 403」——签名过期、缺了站点播放器才会带的那几个头、
     * 或者 CDN 只认 Referer。这些在静态解析里无解（我们照抄不到站点 JS 现算的参数），
     * 但站点自己的播放器一定能播：它本来就知道该怎么要地址。
     *
     * 所以这里不把「重试」推给用户，而是让 WebView 重走一遍，拦到新地址再交回 Media3。
     * 同一「源 + 集」只降一次，否则会出现「嗅探 → 原生 → 报错 → 再嗅探」的无声循环。
     */
    private fun fallbackToWeb(): Boolean {
        val state = _uiState.value
        if (state.mode != PlaybackMode.NATIVE) return false
        val sourceId = state.selectedSourceId ?: return false
        val episode = state.currentEpisode ?: return false
        if (state.episodes.isEmpty()) return false
        val rule = ruleStates.firstOrNull { it.rule.name == sourceId }?.rule ?: return false
        if (!webFallbacks.add("$sourceId|${episode.id}")) return false

        Log.i(TAG, "Direct stream failed, falling back to WebView sniffing: $sourceId")
        // 这次失败说明「这条线路能直链」的判断已经不可靠：记一笔，连续两次就作废缓存。
        lineRefOf(sourceId)?.let { line ->
            viewModelScope.launch { planner.reportMismatch(line) }
        }
        enterWebMode(
            rule = rule,
            episode = episode,
            episodes = state.episodes,
            sourceId = sourceId,
            roadIndex = currentRoadIndex(sourceId),
        )
        return true
    }

    /**
     * 切到 WebView 形态：把播放页整页交给 WebView，让站点播放器自己播。
     *
     * 此时 Media3 要停：两条路同时出声是最典型的「两个声音」事故。
     *
     * 这里**不递增** `webReloadToken`：换集时 `webUrl` 本身变了，WebView 会因为地址变化自己加载。
     * 两个信号一起发会让它「loadUrl 新地址 + reload 上一步」连跳两次，表现为白一下再从头加载。
     * 令牌只留给「同一个地址要重来一次」的重试。
     */
    private fun enterWebMode(
        rule: VdoPlgContent,
        episode: EpisodeItem,
        episodes: List<EpisodeItem>,
        sourceId: String,
        roadIndex: Int,
        userAgent: String = engine.userAgentOf(rule.json),
    ) {
        player.reset()
        playback.resetForNewMedia()
        // 缓冲是 Media3 的语义，WebView 形态下它已停播、不会再回调，留着就是永远转圈。
        playback.onBufferingChanged(false)
        _uiState.update {
            // 同一页再点一次「重试」：地址没变，WebView 不会自己动，得靠令牌催它重新加载。
            // 换集时地址本身变了，绝不能同时递增令牌——那会连跳两次。
            val samePage = it.mode == PlaybackMode.WEB && it.webUrl == episode.id
            it.copy(
                loading = false,
                failure = null,
                mode = PlaybackMode.WEB,
                webUrl = episode.id,
                webReloadToken = if (samePage) it.webReloadToken + 1 else it.webReloadToken,
                webUserAgent = userAgent,
                episodes = episodes,
                currentEpisode = episode,
                selectedSourceId = sourceId,
                sourceSheetVisible = false,
                sources = it.sources.markPlaying(sourceId, roadIndex, PlaybackMode.WEB),
            )
        }
    }

    private fun playEpisodeAt(index: Int, keepPosition: Boolean) {
        val sourceId = _uiState.value.selectedSourceId ?: run { reload(); return }
        playOn(sourceId, index, keepPosition = keepPosition)
    }

    // —— 探测 ——

    /** 等仓库完成首次扫描再取列表；拿不到（超时）就用当下已经读到的。 */
    private suspend fun awaitEnabledRules(): List<VdoPlgContent> {
        val rules = withTimeoutOrNull(RULES_WAIT_MILLIS) {
            pluginRepository.rules.first { it.isNotEmpty() }
        } ?: pluginRepository.rules.value
        return rules.filter { it.enabled }
    }

    /**
     * 所有启用源并发探测（搜索 + 取剧集）。
     *
     * 限 3 个并发：这些站点多半有速率限制，一次放五个反而全被限流。
     */
    private suspend fun probeAll(rules: List<VdoPlgContent>, keyword: String): List<RuleState> = coroutineScope {
        val gate = Semaphore(PROBE_CONCURRENCY)
        rules.map { rule ->
            async { gate.withPermit { probe(rule, keyword) } }
        }.awaitAll()
    }

    /**
     * 单个源：搜索 → 取第一条命中的剧集。
     *
     * 超时不抛异常而是当成「网络失败」：一个源慢不该拖垮整页，其它源还能播。
     */
    private suspend fun probe(rule: VdoPlgContent, keyword: String): RuleState {
        val hits: List<VdoSearchHit>? = withTimeoutOrNull(SEARCH_TIMEOUT_MILLIS) {
            engine.search(rule.json, keyword)
        }
        if (hits == null) return RuleState(rule, failure = VdoFailureReason.NetworkError)
        if (hits.isEmpty()) return RuleState(rule)

        val detailUrl = hits.first().url
        val roads: List<VdoEpisodeRoad> = withTimeoutOrNull(EPISODES_TIMEOUT_MILLIS) {
            engine.episodes(rule.json, detailUrl)
        }.orEmpty()
        return RuleState(rule = rule, hits = hits, roads = roads)
    }

    /** 探测结果 → UI 的源列表。线路 id 用 `规则名:下标`，稳且能反解出道路序号。 */
    private fun publishSources() {
        val sources = ruleStates.map { state ->
            PlayerSource(
                id = state.rule.name,
                name = state.rule.name,
                status = when {
                    state.failure != null -> SourceAvailability.FAILED
                    state.roads.isNotEmpty() -> SourceAvailability.READY
                    else -> SourceAvailability.NO_RESULT
                },
                lines = state.roads.mapIndexed { index, road ->
                    PlayerLine(
                        id = "${state.rule.name}$LINE_ID_SEPARATOR$index",
                        name = road.name,
                        availability = SourceAvailability.READY,
                    )
                },
            )
        }
        _uiState.update { current ->
            // 保留已选中的源与线路：重试不该把用户的当前选择抖掉。
            current.copy(
                sources = sources.map { source ->
                    val previous = current.sources.firstOrNull { it.id == source.id }
                    val keepLine = previous?.selectedLineId?.takeIf { id ->
                        source.lines.any { it.id == id }
                    }
                    source.copy(selectedLineId = keepLine)
                },
            )
        }
    }

    private fun fail(reason: PlayerFailureReason?, detail: String? = null) {
        _uiState.update {
            it.copy(
                loading = false,
                failure = PlayerFailure(reason = reason ?: PlayerFailureReason.NoMatch, detail = detail),
            )
        }
    }

    // —— 状态小工具 ——

    private fun currentRoadIndex(sourceId: String): Int {
        val selected = _uiState.value.sources.firstOrNull { it.id == sourceId }?.selectedLineId
        return selected?.substringAfterLast(LINE_ID_SEPARATOR)?.toIntOrNull() ?: 0
    }

    /**
     * 当前源、当前线路的稳定身份；探测还没完成时返回 null。
     *
     * 所有需要读写能力缓存的地方都走它：线路身份由「源名 + 线路名 + 首集地址形状」三样决定，
     * 让每个调用点自己拼，早晚会有一个地方漏掉其中一样。
     */
    private fun lineRefOf(sourceId: String?): VdoLineRef? {
        val id = sourceId ?: return null
        val state = ruleStates.firstOrNull { it.rule.name == id } ?: return null
        val road = state.roads.getOrNull(currentRoadIndex(id)) ?: return null
        val firstEpisode = road.episodes.firstOrNull() ?: return null
        return VdoLineRef(sourceName = id, lineName = road.name, firstEpisodeUrl = firstEpisode.url)
    }

    private fun markLine(sourceId: String, roadIndex: Int, availability: SourceAvailability) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map { source ->
                    if (source.id != sourceId) {
                        source
                    } else {
                        source.copy(
                            lines = source.lines.map { line ->
                                if (line.id == "$sourceId$LINE_ID_SEPARATOR$roadIndex") {
                                    line.copy(availability = availability)
                                } else {
                                    line
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    /**
     * 出流成功：源与这条线路都标成就绪，并选中它。
     *
     * 必须一起改源级状态：之前失败过的源 `status` 还是 FAILED，
     * 只把线路改成就绪的话，换源弹窗里它照样显示成「请求失败」——明明正在播。
     *
     * @param mode 这条线路这次是怎么播的。写回线路，用户在换源弹窗里就能看到
     *   「直链 / 网页」——不然「这条线没有进度条」看着像 bug。
     */
    private fun List<PlayerSource>.markPlaying(
        sourceId: String,
        roadIndex: Int,
        mode: PlaybackMode,
    ): List<PlayerSource> = map {
        if (it.id != sourceId) {
            it
        } else {
            val lineId = "$sourceId$LINE_ID_SEPARATOR$roadIndex"
            it.copy(
                status = SourceAvailability.READY,
                selectedLineId = lineId,
                lines = it.lines.map { line ->
                    if (line.id == lineId) {
                        line.copy(availability = SourceAvailability.READY, playbackMode = mode)
                    } else {
                        line
                    }
                },
            )
        }
    }

    private fun List<PlayerSource>.markChecking(ids: Set<String>): List<PlayerSource> = map {
        if (it.id in ids) it.copy(status = SourceAvailability.CHECKING) else it
    }

    /**
     * 进度轮询。
     *
     * `Player.Listener` 只有状态变化时才回调，进度没有事件，只能轮询。
     * 200ms 是「进度条看起来连续」与「不白烧 CPU」的折中；暂停时直接跳过。
     */
    private fun startProgressTicker() {
        viewModelScope.launch {
            while (isActive) {
                val exo = player.exoPlayer
                if (exo.isPlaying) {
                    playback.onProgress(exo.currentPosition, exo.bufferedPosition)
                    val duration = exo.duration
                    if (duration != C.TIME_UNSET && duration > 0L) playback.onDurationChanged(duration)
                }
                delay(PROGRESS_INTERVAL_MILLIS)
            }
        }
    }

    /** 一次探测的结果。`failure == null && roads.isEmpty()` 表示「跑通了但这集没有」。 */
    private data class RuleState(
        val rule: VdoPlgContent,
        val hits: List<VdoSearchHit> = emptyList(),
        val roads: List<VdoEpisodeRoad> = emptyList(),
        val failure: VdoFailureReason? = null,
    )

    private companion object {
        const val TAG = "PlayerViewModel"

        /** 线路 id 的分隔符：`DM84:0`。用冒号而不是斜杠，反解时不用担心转义。 */
        const val LINE_ID_SEPARATOR = ":"

        const val PROBE_CONCURRENCY = 3
        const val RULES_WAIT_MILLIS = 3_000L
        const val DETAIL_TIMEOUT_MILLIS = 12_000L
        const val SEARCH_TIMEOUT_MILLIS = 12_000L
        const val EPISODES_TIMEOUT_MILLIS = 12_000L

        /** 出流要串起「抓页面 → 解配置 → 探候选」，比单次请求宽一些。 */
        const val PLAN_TIMEOUT_MILLIS = 30_000L
        const val PROGRESS_INTERVAL_MILLIS = 200L
    }
}

/** 站点给的集名五花八门，抽出第一段数字当集号；抽不到就用序号。 */
private val EpisodeNumberRegex = Regex("""\d{1,4}""")

/** 纯集数标签（「第01话」「12」）：这种就不必再当小标题重复显示了。 */
private val EpisodeLabelOnlyRegex = Regex("""^第?\s*\d{1,4}\s*[集话話回]?$""")

/** 站点给的一集 → 选集卡片。 */
private fun VdoEpisode.toEpisodeItem(index: Int): EpisodeItem {
    val number = EpisodeNumberRegex.find(name)?.value?.toIntOrNull() ?: (index + 1)
    val label = name.trim()
    return EpisodeItem(
        id = url,
        number = number,
        subtitle = label.takeIf { it.isNotEmpty() && !EpisodeLabelOnlyRegex.matches(it) },
    )
}

private fun VdoEpisodeRoad.toEpisodeItems(): List<EpisodeItem> =
    episodes.mapIndexed { index, episode -> episode.toEpisodeItem(index) }

/** 出流层的失败原因 → 播放页的原因。两边枚举刻意不复用：文案语境不同。 */
private fun VdoFailureReason?.toFailureReason(): PlayerFailureReason? = when (this) {
    null -> null
    VdoFailureReason.RuleBroken -> PlayerFailureReason.RuleBroken
    VdoFailureReason.NetworkError, VdoFailureReason.HttpError -> PlayerFailureReason.Network
    VdoFailureReason.NoResults -> PlayerFailureReason.NoMatch
    VdoFailureReason.NoEpisodes -> PlayerFailureReason.NoEpisodes
    VdoFailureReason.NoStream, VdoFailureReason.StreamUnreachable -> PlayerFailureReason.NoStream
    VdoFailureReason.NeedWebView -> PlayerFailureReason.NeedWebView
}

/** 流类型 → Media3 的 mimeType。显式给类型，省得 ExoPlayer 靠后缀猜（有些地址没有后缀）。 */
private val VdoStreamKind.mimeType: String?
    get() = when (this) {
        VdoStreamKind.Hls -> MikomiPlayer.MIME_HLS
        // mp4/mkv 之类交给 ExoPlayer 自己嗅探：给错 mimeType 比不给更糟。
        VdoStreamKind.Mp4, VdoStreamKind.Other -> null
    }
