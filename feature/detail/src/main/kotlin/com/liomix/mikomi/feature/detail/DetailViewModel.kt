package com.liomix.mikomi.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.repository.CommentRepository
import com.liomix.mikomi.data.anime.repository.DetailRepository
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
import kotlinx.coroutines.flow.onEach
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
    private val detailRepository: DetailRepository,
    private val commentRepository: CommentRepository,
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
                        detailRepository.observe(id).map { anime: Anime? ->
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
        // 收藏快照自愈：详情页看到的永远是最新数据，顺手把收藏表里那份旧快照覆盖掉。
        // 只对「已收藏」的番剧真正落行（DAO 的 WHERE 带内容比较），失败也不影响本页。
        .onEach { bound -> healFavoriteSnapshot(bound.content) }

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

    /** 上次自愈过的快照（id + 标题 + 封面）：同一内容不重复写库。 */
    private var healedSnapshot: Triple<String, String, String?>? = null

    /**
     * 把收藏表里存的标题 / 封面更新成详情页的最新值。
     *
     * 收藏页完全靠自己那份快照渲染（不查番剧表、不发网络请求），所以只要用户打开过一次
     * 已收藏的番剧，这里就顺手把快照对齐一次 —— 改名、换封面之后不会在收藏页里留旧样子。
     * 内容没变时 DAO 那条 UPDATE 不命中任何行，Room 也不会发失效通知，因此不会自激。
     */
    private fun healFavoriteSnapshot(content: DetailContent) {
        val anime = (content as? DetailContent.Success)?.anime ?: return
        val snapshot = Triple(anime.id, anime.title, anime.coverUrl)
        if (snapshot == healedSnapshot) return
        healedSnapshot = snapshot
        viewModelScope.launch {
            try {
                userDataRepository.refreshFavoriteSnapshot(anime)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh favorite snapshot failed", e)
            }
        }
    }

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
                detailRepository.refresh(animeId)
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
                // 详情与角色/职员是同一个请求（`BangumiSource.detail` 内部并发取），
                // 所以进主创 Tab 拿一次就够，不必再单独请求两类主创。
                val detail = detailRepository.detail(id)
                credits.value = AnimeCreditsUiState.Loaded(
                    characters = detail.characters,
                    staff = detail.staff,
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
    fun openCreatorDetail(id: Int, isCharacter: Boolean) {
        // **一次写入**把两个面同时切到 Loading。分成两次赋值时，中间那一帧是
        // 「详情转圈、评论区还停在 Hidden」，弹层里会闪一下空白。
        creditSheet.value = CreditSheetUiState.Loading
        viewModelScope.launch {
            try {
                val detail = detailRepository.creatorDetail(id, isCharacter)
                creditSheet.update { it.copy(detail = CreatorDetailUiState.Loaded(detail)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credit detail failed", e)
                creditSheet.update { it.copy(detail = CreatorDetailUiState.Error) }
            }
        }
        viewModelScope.launch {
            try {
                val comments = detailRepository.creatorComments(id, isCharacter)
                creditSheet.update { it.copy(comments = CreatorCommentsUiState.Loaded(comments)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credit comments failed", e)
                creditSheet.update { it.copy(comments = CreatorCommentsUiState.Error) }
            }
        }
    }

    /** 关闭主创详情弹层。 */
    fun dismissCreatorDetail() {
        creditSheet.value = CreditSheetUiState.Hidden
    }

    /**
     * 切换收藏状态（收藏 / 取消收藏）。
     *
     * 收藏时把整部番剧交给仓库 —— 收藏表要落一份展示快照，收藏页才能离线把它画出来。
     * 详情页还没加载出内容时星标不可点，所以这里取不到内容就等于没有动作。
     */
    fun toggleFavorite() {
        val state = uiState.value
        val anime = (state.content as? DetailContent.Success)?.anime ?: return
        viewModelScope.launch {
            try {
                if (state.isFavorite) {
                    userDataRepository.removeFavorite(anime.id)
                } else {
                    userDataRepository.addFavorite(anime)
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
                val page = commentRepository.comments(id, TEASING_PAGE_SIZE, offset = 0)
                teasing.value = TeasingUiState.Loaded(
                    comments = page.items,
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
                val page = commentRepository.comments(
                    animeId = id,
                    limit = TEASING_PAGE_SIZE,
                    offset = current.comments.size,
                )
                // 服务端分页可能因新评论插入而返回重复项，按 id 去重保证列表 key 唯一。
                val existingIds = current.comments.mapTo(HashSet()) { it.id }
                val appended = page.items.filter { it.id !in existingIds }
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
