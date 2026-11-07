package com.liomix.mikomi.feature.profile.collection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Favorite
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 收藏区 ViewModel。
 *
 * 内部两份可变源：数据库那条收藏流（内容）与 [editing]（整理模式 / 选中集 / 撤销凭据）。
 * 对外只有一条 [uiState] —— 页面只订阅一次，也不需要在 Route 里做二次拼装。
 *
 * 内容本身**不做乐观更新**：取消收藏写完库，Room 的失效通知会把新列表送回 [uiState]。
 * 这样「列表」和「撤销凭据」不会各说一套：撤销写回的行与原行逐字段相同，卡片回原位。
 */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    /** 可编辑的那部分状态。内容来自数据库，不放这里。 */
    private val editing = MutableStateFlow(Editing())

    /** 撤销条的序号源：只有最新一次的延时任务允许把它收掉。 */
    private var undoSeq = 0L
    private var undoJob: Job? = null

    val uiState: StateFlow<CollectionUiState> = combine(
        userDataRepository.observeFavorites()
            // 收藏表读失败不该把整块内容拖进错误态：列表退回空态，其它页面照常可用。
            .catch { e ->
                Log.w(TAG, "Observe favorites failed", e)
                emit(emptyList())
            }
            .map<List<Favorite>, CollectionContent> { list ->
                if (list.isEmpty()) CollectionContent.Empty else CollectionContent.Loaded(list)
            },
        editing,
    ) { content, edit ->
        val items = (content as? CollectionContent.Loaded)?.items.orEmpty()
        // 选中集与当下真正存在的条目求交：别处删掉一条之后，这里不会残留幽灵 id，
        // 「已选 3」与列表里的高亮个数永远对得上。
        val alive = items.mapTo(HashSet()) { it.animeId }
        CollectionUiState(
            content = content,
            // 列表空了就自动退出整理模式，空态不会挂着一个没有对象的操作栏。
            managing = edit.managing && items.isNotEmpty(),
            selection = edit.selection.filterTo(HashSet()) { it in alive },
            undo = edit.undo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionUiState(),
    )

    fun onEvent(event: CollectionEvent) {
        when (event) {
            // 打开详情、进入收藏页都由 Route 转给导航，ViewModel 不持有导航能力。
            is CollectionEvent.Open -> Unit
            is CollectionEvent.OpenCollect -> Unit

            is CollectionEvent.StartManage -> editing.update {
                it.copy(managing = true, selection = it.selection + event.animeId)
            }

            is CollectionEvent.ToggleSelect -> editing.update { state ->
                state.copy(
                    selection = if (event.animeId in state.selection) {
                        state.selection - event.animeId
                    } else {
                        state.selection + event.animeId
                    },
                )
            }

            CollectionEvent.ToggleManage -> editing.update {
                it.copy(managing = !it.managing, selection = emptySet())
            }

            CollectionEvent.ToggleSelectAll -> {
                val state = uiState.value
                val ids = (state.content as? CollectionContent.Loaded)
                    ?.items.orEmpty()
                    .mapTo(HashSet()) { it.animeId }
                editing.update {
                    it.copy(selection = if (state.allSelected) emptySet() else ids)
                }
            }

            CollectionEvent.RemoveSelected -> removeSelected()

            CollectionEvent.Undo -> undoLastRemoval()
        }
    }

    /**
     * 移除选中项。
     *
     * 一次事务读删（`FavoriteDao.takeOut`），拿到被删的行作为撤销凭据；
     * 删除后自动退出整理模式 —— 用户的意图已经完成，没有继续圈选的上下文了。
     */
    private fun removeSelected() {
        val selection = uiState.value.selection
        if (selection.isEmpty()) return
        viewModelScope.launch {
            val removed = try {
                userDataRepository.removeFavorites(selection.toList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Remove favorites failed", e)
                return@launch
            }
            if (removed.isEmpty()) return@launch

            val token = ++undoSeq
            editing.update {
                it.copy(
                    managing = false,
                    selection = emptySet(),
                    undo = CollectionUndo(removed, token),
                )
            }
            // 只让最新一次取消拥有这条横条：先点的那次到点后不能把后一次的收掉。
            undoJob?.cancel()
            undoJob = viewModelScope.launch {
                delay(UndoWindowMillis)
                editing.update { state ->
                    if (state.undo?.token == token) state.copy(undo = null) else state
                }
            }
        }
    }

    /** 撤销：把删掉的行原样写回（`addedAt` 不变，所以位次也不变）。 */
    private fun undoLastRemoval() {
        val pending = uiState.value.undo ?: return
        undoJob?.cancel()
        editing.update { it.copy(undo = null) }
        viewModelScope.launch {
            try {
                userDataRepository.restoreFavorites(pending.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Restore favorites failed", e)
            }
        }
    }

    /** 整理模式那部分可变状态（内容不在这里）。 */
    private data class Editing(
        val managing: Boolean = false,
        val selection: Set<String> = emptySet(),
        val undo: CollectionUndo? = null,
    )

    private companion object {
        const val TAG = "CollectionViewModel"

        /** 撤销条的停留时长：够看清「删了哪几条」并伸手去点，又不至于挡住整理。 */
        const val UndoWindowMillis = 4000L
    }
}
