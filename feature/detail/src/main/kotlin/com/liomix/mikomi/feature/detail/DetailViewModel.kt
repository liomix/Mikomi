package com.liomix.mikomi.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.data.anime.AnimeCommentRepository
import com.liomix.mikomi.data.anime.AnimeCreditsRepository
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel：观察番剧详情（Room SSOT），主创数据懒加载。
 *
 * [animeId] 由 Route 经 [bind] 注入（Navigation3 的 key 参数传递）。
 * 绑定后异步触发 Bangumi 详情刷新；主创（角色/制作人员）在进入主创 Tab 时按需拉取。
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val creditsRepository: AnimeCreditsRepository,
    private val commentRepository: AnimeCommentRepository,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val animeId = MutableStateFlow<String?>(null)

    /** 当前番剧是否已收藏（跟随本地收藏列表实时变化）。 */
    val isFavorite: StateFlow<Boolean> = animeId
        .filterNotNull()
        .flatMapLatest { id ->
            userDataRepository.observeFavorites()
                .map { favorites -> favorites.any { it.animeId == id } }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val uiState: StateFlow<DetailUiState> = animeId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(DetailUiState.Loading)
            } else {
                // 每次切换 animeId 先回到 Loading，避免复用 ViewModel 时
                // 短暂渲染上一个动漫的详情（「闪一下」问题）。
                flow<DetailUiState> {
                    emit(DetailUiState.Loading)
                    emitAll(
                        animeRepository.observeAnime(id).map { anime: Anime? ->
                            if (anime != null) DetailUiState.Success(anime = anime)
                            else DetailUiState.Loading
                        },
                    )
                }
            }
        }
        .catch { emit(DetailUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState.Loading,
        )

    private val _creditsState = MutableStateFlow<AnimeCreditsUiState>(AnimeCreditsUiState.Idle)
    val creditsState: StateFlow<AnimeCreditsUiState> = _creditsState

    private val _creditDetailState = MutableStateFlow<CreditDetailUiState>(CreditDetailUiState.Hidden)
    val creditDetailState: StateFlow<CreditDetailUiState> = _creditDetailState

    private val _creditCommentsState = MutableStateFlow<CreditCommentsUiState>(CreditCommentsUiState.Hidden)
    val creditCommentsState: StateFlow<CreditCommentsUiState> = _creditCommentsState

    private val _teasingState = MutableStateFlow<TeasingUiState>(TeasingUiState.Loading)
    val teasingState: StateFlow<TeasingUiState> = _teasingState

    /** 吐槽首屏是否已发起请求（避免重复拉取）。 */
    private var teasingRequested = false

    fun bind(animeId: String) {
        // 无条件重置：共享 ViewModelStore 下 ViewModel 会被复用，
        // 必须每次 bind 都回到干净状态并重新加载，否则「搜过 A 再搜 B 仍显示 A」。
        this.animeId.value = animeId
        _creditsState.value = AnimeCreditsUiState.Idle
        _creditDetailState.value = CreditDetailUiState.Hidden
        _creditCommentsState.value = CreditCommentsUiState.Hidden
        _teasingState.value = TeasingUiState.Loading
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
        val current = _creditsState.value
        if (current is AnimeCreditsUiState.Loaded || current is AnimeCreditsUiState.Loading) return
        val id = animeId.value ?: return
        _creditsState.value = AnimeCreditsUiState.Loading
        viewModelScope.launch {
            try {
                val credits = creditsRepository.fetchCredits(id)
                _creditsState.value = AnimeCreditsUiState.Loaded(
                    characters = credits.characters,
                    staff = credits.staff,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credits failed", e)
                _creditsState.value = AnimeCreditsUiState.Error
            }
        }
    }

    /** 打开主创详情弹层（isCharacter 区分角色/制作人员），同时拉详情与评论。 */
    fun openCreditDetail(id: Int, isCharacter: Boolean) {
        _creditDetailState.value = CreditDetailUiState.Loading
        _creditCommentsState.value = CreditCommentsUiState.Loading
        viewModelScope.launch {
            try {
                val detail = if (isCharacter) {
                    creditsRepository.fetchCharacterDetail(id)
                } else {
                    creditsRepository.fetchPersonDetail(id)
                }
                _creditDetailState.value = CreditDetailUiState.Loaded(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credit detail failed", e)
                _creditDetailState.value = CreditDetailUiState.Error
            }
        }
        viewModelScope.launch {
            try {
                val comments = if (isCharacter) {
                    creditsRepository.fetchCharacterComments(id)
                } else {
                    creditsRepository.fetchPersonComments(id)
                }
                _creditCommentsState.value = CreditCommentsUiState.Loaded(comments)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credit comments failed", e)
                _creditCommentsState.value = CreditCommentsUiState.Error
            }
        }
    }

    /** 关闭主创详情弹层。 */
    fun dismissCreditDetail() {
        _creditDetailState.value = CreditDetailUiState.Hidden
        _creditCommentsState.value = CreditCommentsUiState.Hidden
    }

    /** 切换收藏状态（收藏 / 取消收藏）。 */
    fun toggleFavorite() {
        val id = animeId.value ?: return
        val favorite = isFavorite.value
        viewModelScope.launch {
            try {
                if (favorite) {
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
        _teasingState.value = TeasingUiState.Loading
        viewModelScope.launch {
            try {
                val page = commentRepository.fetchTeasingComments(id, TEASING_PAGE_SIZE, offset = 0)
                _teasingState.value = TeasingUiState.Loaded(
                    comments = page.comments,
                    hasMore = page.hasMore,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load teasing failed", e)
                _teasingState.value = TeasingUiState.Error
            }
        }
    }

    /** 加载吐槽下一页（列表滚动到底部时调用）。 */
    fun loadMoreTeasing() {
        val current = _teasingState.value as? TeasingUiState.Loaded ?: return
        if (current.appending || current.appendFailed || !current.hasMore) return
        val id = animeId.value ?: return
        _teasingState.value = current.copy(appending = true)
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
                _teasingState.value = TeasingUiState.Loaded(
                    comments = current.comments + appended,
                    // 无新增内容时终止分页，避免同一 offset 反复请求。
                    hasMore = page.hasMore && appended.isNotEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load more teasing failed", e)
                _teasingState.value = current.copy(appending = false, appendFailed = true)
            }
        }
    }

    /**
     * 吐槽加载失败后重试。
     *
     * 首屏失败 → 重新拉第一页；翻页失败 → 续拉下一页（保留已加载内容）。
     */
    fun retryTeasing() {
        val current = _teasingState.value
        if (current is TeasingUiState.Loaded) {
            if (!current.appendFailed) return
            _teasingState.value = current.copy(appendFailed = false)
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
