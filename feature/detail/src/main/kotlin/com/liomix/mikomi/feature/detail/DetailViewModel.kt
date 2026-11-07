package com.liomix.mikomi.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.AnimeCommentRepository
import com.liomix.mikomi.data.anime.AnimeCreditsRepository
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel：观察番剧详情（Room SSOT），主创数据懒加载。
 *
 * [animeId] 由 Route 经 [bind] 注入（Navigation3 的 key 参数传递）。
 * 绑定后异步触发 Bangumi 详情刷新；主创（角色/制作人员）在进入主创 Tab 时按需拉取。
 *
 * **对外只有一条状态流**（[uiState]）。内部仍是五份可变源，因为 `credits` / `teasing` / 主创弹层
 * 各有独立的懒加载时机与失败重试路径，硬塞进一个 `MutableStateFlow` 只会让每次写入都要
 * 重建整个对象、且读的时候要二次解包。对外合并的理由与内部不合并的理由是同一条判据：
 * **UI 只该有「一次刷新 = 一个状态对象」，而写入方各自有各自的触发条件。**
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val creditsRepository: AnimeCreditsRepository,
    private val commentRepository: AnimeCommentRepository,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val animeId = MutableStateFlow<String?>(null)

    private val credits = MutableStateFlow<AnimeCreditsUiState>(AnimeCreditsUiState.Idle)
    private val creditSheet = MutableStateFlow(CreditSheetUiState.Hidden)
    private val teasing = MutableStateFlow<TeasingUiState>(TeasingUiState.Loading)

    /**
     * 内容态连同它所属的 id。
     *
     * 二者必须是同一个值：拆成两条流就一定会出现「id 已切到新的、内容还是上一部」的那一帧，
     * 表现出来是点 A 打开 B 的头图。
     */
    private val boundContent: Flow<BoundContent> = animeId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(BoundContent(null, DetailContent.Loading))
            } else {
                // 每次切换 id 先回到 Loading，避免复用 ViewModel 时短暂渲染上一部。
                flow {
                    emit(BoundContent(id, DetailContent.Loading))
                    emitAll(
                        animeRepository.observeAnime(id).map { anime: Anime? ->
                            BoundContent(
                                id = id,
                                content = if (anime != null) {
                                    DetailContent.Success(anime)
                                } else {
                                    DetailContent.Loading
                                },
                            )
                        },
                    )
                }
            }
        }
        .catch { emit(BoundContent(animeId.value, DetailContent.Error)) }

    /** 是否已收藏。跟随本地收藏列表实时变化；收藏成功后 ActionBar 的星标靠它翻转。 */
    private val favorite: Flow<Boolean> = animeId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(false)
            } else {
                userDataRepository.observeFavorites()
                    .map { favorites -> favorites.any { it.animeId == id } }
            }
        }
        // 收藏表读失败不该把整页拖进错误态：详情内容本身是好的，星标退回「未收藏」即可。
        .catch { emit(false) }

    val uiState: StateFlow<DetailUiState> = combine(
        boundContent,
        favorite,
        credits,
        creditSheet,
        teasing,
    ) { bound, isFavorite, creditsState, sheet, teasingState ->
        DetailUiState(
            boundId = bound.id,
            content = bound.content,
            isFavorite = isFavorite,
            credits = creditsState,
            creditDetail = sheet.detail,
            creditComments = sheet.comments,
            teasing = teasingState,
        )
    }.stateIn(
        scope = viewModelScope,
        // 页面不可见 5 秒后停掉三路数据库观察。这是把「一屏一状态」做成**一条导出流**之后
        // 仍然保留的收益：内部再怎么写，对外只有一个订阅者生命周期要管。
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(),
    )

    /** 吐槽首屏是否已发起请求（避免重复拉取）。 */
    private var teasingRequested = false

    fun bind(animeId: String) {
        // 无条件重置：共享 ViewModelStore 下 ViewModel 会被复用，
        // 必须每次 bind 都回到干净状态并重新加载，否则「搜过 A 再搜 B 仍显示 A」。
        //
        // 四处赋值都在同一个协程 tick 内完成，Compose 的失效是帧级批处理的，
        // 因此不会渲染出「id 换了但 Tab 还是上一部」的中间态。
        this.animeId.value = animeId
        credits.value = AnimeCreditsUiState.Idle
        creditSheet.value = CreditSheetUiState.Hidden
        teasing.value = TeasingUiState.Loading
        teasingRequested = false
        refreshDetail(animeId)
    }

    /** 拉取 Bangumi 完整详情并回写本地；失败静默（本地已有数据优先）。 */
    private fun refreshDetail(animeId: String) {
        viewModelScope.launch {
            try {
                animeRepository.refreshAnimeDetail(animeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh anime detail failed", e)
            }
        }
    }

    /** 懒加载主创（角色 + 制作人员）；进入主创 Tab 时调用。 */
    fun loadCredits() {
        val current = credits.value
        if (current is AnimeCreditsUiState.Loaded || current is AnimeCreditsUiState.Loading) return
        val id = animeId.value ?: return
        credits.value = AnimeCreditsUiState.Loading
        viewModelScope.launch {
            try {
                val result = creditsRepository.fetchCredits(id)
                credits.value = AnimeCreditsUiState.Loaded(
                    characters = result.characters,
                    staff = result.staff,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credits failed", e)
                credits.value = AnimeCreditsUiState.Error
            }
        }
    }

    /** 打开主创详情弹层（isCharacter 区分角色/制作人员），同时拉详情与评论。 */
    fun openCreditDetail(id: Int, isCharacter: Boolean) {
        // **一次写入**把两个面同时切到 Loading。分成两次赋值时，中间那一帧是
        // 「详情转圈、评论区还停在 Hidden」，弹层里会闪一下空白。
        creditSheet.value = CreditSheetUiState.Loading
        viewModelScope.launch {
            try {
                val detail = if (isCharacter) {
                    creditsRepository.fetchCharacterDetail(id)
                } else {
                    creditsRepository.fetchPersonDetail(id)
                }
                creditSheet.update { it.copy(detail = CreditDetailUiState.Loaded(detail)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credit detail failed", e)
                creditSheet.update { it.copy(detail = CreditDetailUiState.Error) }
            }
        }
        viewModelScope.launch {
            try {
                val comments = if (isCharacter) {
                    creditsRepository.fetchCharacterComments(id)
                } else {
                    creditsRepository.fetchPersonComments(id)
                }
                creditSheet.update { it.copy(comments = CreditCommentsUiState.Loaded(comments)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credit comments failed", e)
                creditSheet.update { it.copy(comments = CreditCommentsUiState.Error) }
            }
        }
    }

    /** 关闭主创详情弹层。 */
    fun dismissCreditDetail() {
        creditSheet.value = CreditSheetUiState.Hidden
    }

    /** 切换收藏状态（收藏 / 取消收藏）。 */
    fun toggleFavorite() {
        val id = animeId.value ?: return
        val isFavorite = uiState.value.isFavorite
        viewModelScope.launch {
            try {
                if (isFavorite) {
                    userDataRepository.removeFavorite(id)
                } else {
                    userDataRepository.addFavorite(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Toggle favorite failed", e)
            }
        }
    }

    /** 加载吐槽首屏（首次进入吐槽 Tab 时调用，重复调用无副作用）。 */
    fun loadTeasing() {
        if (teasingRequested) return
        val id = animeId.value ?: return
        teasingRequested = true
        teasing.value = TeasingUiState.Loading
        viewModelScope.launch {
            try {
                val page = commentRepository.fetchTeasingComments(id, TEASING_PAGE_SIZE, offset = 0)
                teasing.value = TeasingUiState.Loaded(
                    comments = page.comments,
                    hasMore = page.hasMore,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load teasing failed", e)
                teasing.value = TeasingUiState.Error
            }
        }
    }

    /** 加载吐槽下一页（列表滚动到底部时调用）。 */
    fun loadMoreTeasing() {
        val current = teasing.value as? TeasingUiState.Loaded ?: return
        if (current.appending || current.appendFailed || !current.hasMore) return
        val id = animeId.value ?: return
        teasing.value = current.copy(appending = true)
        viewModelScope.launch {
            try {
                // 以已加载条数作为 offset，避免维护额外游标。
                val page = commentRepository.fetchTeasingComments(
                    animeId = id,
                    limit = TEASING_PAGE_SIZE,
                    offset = current.comments.size,
                )
                // 服务端分页可能因新评论插入而返回重复项，按 id 去重保证列表 key 唯一。
                val existingIds = current.comments.mapTo(HashSet()) { it.id }
                val appended = page.comments.filter { it.id !in existingIds }
                teasing.value = TeasingUiState.Loaded(
                    comments = current.comments + appended,
                    // 无新增内容时终止分页，避免同一 offset 反复请求。
                    hasMore = page.hasMore && appended.isNotEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load more teasing failed", e)
                teasing.value = current.copy(appending = false, appendFailed = true)
            }
        }
    }

    /**
     * 吐槽加载失败后重试。
     *
     * 首屏失败 → 重新拉第一页；翻页失败 → 续拉下一页（保留已加载内容）。
     */
    fun retryTeasing() {
        val current = teasing.value
        if (current is TeasingUiState.Loaded) {
            if (!current.appendFailed) return
            teasing.value = current.copy(appendFailed = false)
            loadMoreTeasing()
        } else {
            teasingRequested = false
            loadTeasing()
        }
    }

    private companion object {
        const val TAG = "DetailViewModel"

        /** 吐槽每页条数（对齐 Dart 版）。 */
        const val TEASING_PAGE_SIZE = 20
    }
}
