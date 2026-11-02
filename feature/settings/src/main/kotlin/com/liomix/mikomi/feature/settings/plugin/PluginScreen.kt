package com.liomix.mikomi.feature.settings.plugin

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardHeader
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import com.liomix.mikomi.feature.settings.component.SettingsTopBar
import kotlinx.coroutines.launch

/**
 * 插件源设置页。
 *
 * 「插件源」= 一份 JSON 规则文件，描述某个视频站怎么搜索、怎么取剧集。
 * 它只管**视频来源**，和番剧元数据（Bangumi 那类内置接口）没有关系——
 * 后者是 App 写死的，不靠插件扩展。
 *
 * 页面只有两张卡：规则仓库（源从哪儿来）与已安装列表（装了哪些、什么顺序、每条能做什么）。
 * 页面上不放任何解释性文字：能操作的地方自己会说话，解释性段落只会把两卡之间的距离拉长。
 *
 * 添加入口在顶栏右上角；排序靠**长按某一行拖动**，所以行菜单里不再重复放上移/下移。
 *
 * 现阶段的边界：列表来自 `PluginViewModel` 里写死的两份真规则，导入/编辑/测试/分享
 * 尚未接入，点击只提示「尚未接入」。启停、排序、删除、加速方式、仓库地址是真的改内存，
 * 但退出页面就还原。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(
    uiState: PluginUiState,
    onToggle: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMirrorSelected: (RuleMirror) -> Unit,
    onRepoUrlChanged: (String) -> Unit,
    onOpenStore: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addSheetOpen by rememberSaveable { mutableStateOf(false) }
    var mirrorPickerOpen by rememberSaveable { mutableStateOf(false) }
    var repoEditorOpen by rememberSaveable { mutableStateOf(false) }
    // 提示语在组合期取好：Snackbar 在协程里弹，那里不能调 stringResource。
    val pendingMessage = stringResource(R.string.settings_plugin_pending)
    val editPendingMessage = stringResource(R.string.settings_plugin_pending_edit)
    val testPendingMessage = stringResource(R.string.settings_plugin_pending_test)
    val sharePendingMessage = stringResource(R.string.settings_plugin_pending_share)

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = stringResource(R.string.settings_plugin_title),
                backContentDescription = stringResource(R.string.settings_plugin_back),
                onBackClick = onBackClick,
            ) {
                IconButton(onClick = { addSheetOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.settings_plugin_add),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RuleRepoCard(
                    repoUrl = uiState.repoUrl,
                    mirror = uiState.mirror,
                    onRepoClick = { repoEditorOpen = true },
                    onMirrorClick = { mirrorPickerOpen = true },
                )

                SourcesCard(
                    uiState = uiState,
                    onToggle = onToggle,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onRemove = onRemove,
                    onEdit = { notify(editPendingMessage) },
                    onTest = { notify(testPendingMessage) },
                    onShare = { notify(sharePendingMessage) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    if (addSheetOpen) {
        AddSourceSheet(
            onDismiss = { addSheetOpen = false },
            onSelect = { method ->
                addSheetOpen = false
                // 「浏览仓库」是唯一已经通了路的入口：商店页就是浏览仓库。
                // 其余三种（剪贴板 / 文件 / 新建）还没接，先如实说一句。
                if (method == AddMethod.FromCatalog) onOpenStore() else notify(pendingMessage)
            },
        )
    }

    if (repoEditorOpen) {
        RepoEditorDialog(
            current = uiState.repoUrl,
            onDismiss = { repoEditorOpen = false },
            onConfirm = { url ->
                repoEditorOpen = false
                onRepoUrlChanged(url)
            },
        )
    }

    if (mirrorPickerOpen) {
        MirrorPickerDialog(
            current = uiState.mirror,
            onDismiss = { mirrorPickerOpen = false },
            onSelect = { mirror ->
                mirrorPickerOpen = false
                onMirrorSelected(mirror)
            },
        )
    }
}

/**
 * 已安装列表。
 *
 * 顺序就是搜索顺序：播放时按这个顺序从上往下问来源，第一个命中的就被采用。
 * 所以排序做成**长按某一行直接拖**，而不是在菜单里点上移/下移——列表只有几行，
 * 拖动一次到位，菜单里那三项反而是每次都要重新找入口的多余步骤。
 *
 * 拖动阈值按「邻行高度的一半」判断：行高不固定（作者行可能缺失），用固定 dp 会在
 * 一行两行交替的列表里越拖越偏。
 */
@Composable
private fun SourcesCard(
    uiState: PluginUiState,
    onToggle: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    onTest: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val sources = uiState.sources
    // 拖动回调里要读「当前」顺序：pointerInput 的 lambda 只在 key 变化时重建，
    // 直接捕获 sources 会一直看到按下瞬间的旧列表。
    val currentSources by rememberUpdatedState(sources)

    val colors = MaterialTheme.colorScheme
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Float>() }

    SettingsCard {
        SettingsCardSection {
            SettingsCardHeader(
                title = stringResource(R.string.settings_plugin_section, sources.size),
            )

            if (uiState.isEmpty) {
                Text(
                    text = stringResource(R.string.settings_plugin_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                )
            } else {
                sources.forEachIndexed { index, source ->
                    // key 是拖动的前提：列表没有稳定标识时，换位会让「第 i 个槽位」换人，
                    // pointerInput 的 key 一变就连手势一起重启，拖到一半就断。
                    key(source.id) {
                        val isDragging = draggingId == source.id
                        Column(
                            modifier = Modifier
                                // 被拖的那行浮在其它行之上，否则缩放后会被下一行盖住一截。
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    // 读在绘制阶段：拖动时每帧只重绘，不重组。
                                    translationY = if (isDragging) dragOffset else 0f
                                    if (isDragging) {
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .then(
                                    // 拖动中的行给一层不透明底：它会被平移、放大，
                                    // 透出下面的列表像重影。
                                    if (isDragging) {
                                        Modifier.background(colors.surfaceContainerHigh)
                                    } else {
                                        Modifier
                                    },
                                )
                                .onSizeChanged { rowHeights[source.id] = it.height.toFloat() }
                                .pointerInput(source.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = source.id
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val list = currentSources
                                            val from = list.indexOfFirst { it.id == source.id }
                                            if (from >= 0 && dragOffset > 0f) {
                                                // 往下拖：越过下一行一半就换位，并把已消费的位移扣掉，
                                                // 否则换位后偏移量还留着，会连着再跳一格。
                                                val below = list.getOrNull(from + 1)
                                                val height = below?.let { rowHeights[it.id] } ?: 0f
                                                if (height > 0f && dragOffset > height / 2f) {
                                                    onMoveDown(source.id)
                                                    dragOffset -= height
                                                }
                                            } else if (from >= 0 && dragOffset < 0f) {
                                                val above = list.getOrNull(from - 1)
                                                val height = above?.let { rowHeights[it.id] } ?: 0f
                                                if (height > 0f && dragOffset < -height / 2f) {
                                                    onMoveUp(source.id)
                                                    dragOffset += height
                                                }
                                            }
                                        },
                                    )
                                },
                        ) {
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = colors.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            SourceRow(
                                source = source,
                                dragging = isDragging,
                                onToggle = { onToggle(source.id) },
                                onRemove = { onRemove(source.id) },
                                onEdit = { onEdit(source.id) },
                                onTest = { onTest(source.id) },
                                onShare = { onShare(source.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一行插件源：名称 + 版本 + 更新态 + 站点，右侧启停开关与更多菜单。
 *
 * 整行走 [Modifier.toggleable]（`Role.Switch`）而不是 `clickable`：
 * 无障碍会把整行报成开关而不是按钮，同时开关自己不吃点击（`onCheckedChange = null`），
 * 否则点开关会触发两次——真机上表现为「点了没反应」。
 *
 * 长按拖动时把 [toggleable] 的写入挡掉：拖动结束的那一下偶尔仍会被判成点击，
 * 这里挡比事后修状态便宜。
 */
@Composable
private fun SourceRow(
    source: PluginSource,
    dragging: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = source.enabled,
                role = Role.Switch,
                onValueChange = { if (!dragging) onToggle() },
            )
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleSmall,
                    // 停用的源整体压低存在感，但仍留在原位——它不是「待删除」，只是暂时不上场。
                    color = if (source.enabled) colors.onSurface else colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TagChip(
                    text = stringResource(R.string.settings_plugin_version, source.version),
                    background = colors.surfaceContainerHigh,
                    foreground = colors.onSurfaceVariant,
                )
                if (source.updateAvailable) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TagChip(
                        text = stringResource(R.string.settings_plugin_update_available),
                        background = colors.primary.copy(alpha = 0.14f),
                        foreground = colors.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metaLine(source),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = source.enabled, onCheckedChange = null)

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.settings_plugin_more, source.name),
                    tint = colors.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SourceAction(
                    label = stringResource(R.string.settings_plugin_edit),
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                    close = { menuOpen = false },
                )
                SourceAction(
                    label = stringResource(R.string.settings_plugin_test),
                    icon = Icons.Rounded.Science,
                    onClick = onTest,
                    close = { menuOpen = false },
                )
                SourceAction(
                    label = stringResource(R.string.settings_plugin_share),
                    icon = Icons.Rounded.Share,
                    onClick = onShare,
                    close = { menuOpen = false },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = colors.outlineVariant.copy(alpha = 0.5f),
                )
                SourceAction(
                    label = stringResource(R.string.settings_plugin_remove),
                    icon = Icons.Rounded.DeleteOutline,
                    tint = colors.error,
                    onClick = onRemove,
                    close = { menuOpen = false },
                )
            }
        }
    }
}

/** 菜单项的统一形状：先关菜单再执行，避免动作里改列表时菜单还挂在被移除的节点上。 */
@Composable
private fun SourceAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    close: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: LocalContentColor.current,
            )
        },
        enabled = enabled,
        onClick = {
            close()
            onClick()
        },
    )
}

@Composable
private fun RuleRepoCard(
    repoUrl: String,
    mirror: RuleMirror,
    onRepoClick: () -> Unit,
    onMirrorClick: () -> Unit,
) {
    SettingsCard {
        SettingsCardSection {
            SettingsCardHeader(stringResource(R.string.settings_plugin_repo_section))
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                RepoRow(
                    title = stringResource(R.string.settings_plugin_repo_address),
                    value = repoUrl,
                    onClick = onRepoClick,
                )
                RepoDivider()
                RepoRow(
                    title = stringResource(R.string.settings_plugin_repo_mirror),
                    value = stringResource(mirror.labelRes),
                    onClick = onMirrorClick,
                )
            }
        }
    }
}

@Composable
private fun RepoDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * 仓库信息行：标题在上、当前值在下，右侧箭头。
 *
 * 值放第二行而不是右边：仓库地址一长，「标题 + 右侧值」会把标题挤成省略号，
 * 而地址恰恰是这一行里更要紧的信息。两行则地址能吃满宽度，只在自己过长时省略。
 */
@Composable
private fun RepoRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}

/**
 * 仓库地址编辑弹窗。
 *
 * 只校验协议前缀：这是本地拼 URL 用的，不做网络探测，也不允许空串——
 * 空串会让后面拼出来的 `index.json` 变成一个相对路径。
 */
@Composable
private fun RepoEditorDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(current) { mutableStateOf(current) }
    val trimmed = text.trim()
    val valid = trimmed.startsWith("http://") || trimmed.startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_plugin_repo_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid,
                supportingText = {
                    if (!valid) {
                        Text(stringResource(R.string.settings_plugin_repo_edit_invalid))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(trimmed)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_plugin_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_plugin_cancel))
            }
        },
    )
}

/**
 * 加速方式选择。
 *
 * 用单选弹窗而不是下拉菜单：四档之间是互斥的、要带一句说明才选得明白，
 * 菜单项的副标题在窄屏上会被截，弹窗里一行放得下。
 */
@Composable
private fun MirrorPickerDialog(
    current: RuleMirror,
    onDismiss: () -> Unit,
    onSelect: (RuleMirror) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_plugin_repo_mirror_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                RuleMirror.entries.forEach { mirror ->
                    val selected = mirror == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mirror) },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(mirror.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(mirror.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_plugin_cancel))
            }
        },
    )
}

/**
 * 添加插件源的方式选择。
 *
 * 只给「从哪儿拿」这一个决定，不在这里让人填字段：手写选择器的成本远高于抄一份现成的，
 * 所以把「新建」放在最后，前三个都是导入。
 *
 * 「浏览规则仓库」用主色淡底托一下：仓库是这个 App 唯一的官方来源，其余三种是补充，
 * 四行长得一样的话，新用户会去点「新建」，然后在空白模板里卡住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceSheet(
    onDismiss: () -> Unit,
    onSelect: (AddMethod) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_plugin_add_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))

            AddMethod.entries.forEach { method ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 左右各让 8dp：推荐项要铺一层淡底，贴着屏幕边会显得像状态色块。
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (method == AddMethod.FromCatalog) {
                                Modifier.background(colors.primary.copy(alpha = 0.10f))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(method) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceContainerHighest,
                        contentColor = colors.onSurfaceVariant,
                    ) {
                        Icon(
                            imageVector = method.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(method.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 添加方式。顺序即展示顺序：三个导入在前，「新建」垫底。 */
private enum class AddMethod(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    FromCatalog(
        titleRes = R.string.settings_plugin_add_catalog,
        icon = Icons.Rounded.TravelExplore,
    ),
    FromClipboard(
        titleRes = R.string.settings_plugin_add_clipboard,
        icon = Icons.Rounded.ContentPaste,
    ),
    FromFile(
        titleRes = R.string.settings_plugin_add_file,
        icon = Icons.Rounded.FileOpen,
    ),
    FromTemplate(
        titleRes = R.string.settings_plugin_add_create,
        icon = Icons.Rounded.EditNote,
    ),
}

/** 版本号 / 可更新这类小标签：压住字号，避免和插件名抢注意力。 */
@Composable
private fun TagChip(
    text: String,
    background: Color,
    foreground: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = background,
        contentColor = foreground,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** 副标题：作者（可缺省）+ 站点 host。写完整 URL 太长，host 足够认出来源。 */
private fun metaLine(source: PluginSource): String {
    val host = runCatching { java.net.URI(source.baseUrl).host }.getOrNull()
        ?: source.baseUrl
    return listOf(source.author, host)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
