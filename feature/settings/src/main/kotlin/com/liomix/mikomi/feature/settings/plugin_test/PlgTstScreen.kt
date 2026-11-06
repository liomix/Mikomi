package com.liomix.mikomi.feature.settings.plugin_test

import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.model.VdoPlgContent
import com.liomix.mikomi.core.ui.component.base.Switch
import com.liomix.mikomi.core.ui.component.base.Tag
import com.liomix.mikomi.core.ui.component.base.TagStyle
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.data.video.VdoFailureReason
import com.liomix.mikomi.data.video.VdoStreamKind
import com.liomix.mikomi.data.video.remote.vdoplg.rule.VdoPlgStageResult
import com.liomix.mikomi.data.video.remote.vdoplg.rule.VdoPlgTestReport
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardHeader
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import kotlinx.coroutines.launch

/**
 * 插件源设置页。
 *
 * 「视频插件源」= 一份 JSON 文件，描述某个视频站怎么搜索、怎么取剧集。
 * 它只管**视频来源**，和番剧元数据（Bangumi 那类内置接口）没有关系——
 * 后者是 App 写死的，不靠插件扩展。
 *
 * 页面只有两张卡：插件仓库（插件从哪儿来）与已安装列表（装了哪些、什么顺序、每个能做什么）。
 * 页面上不放任何解释性文字：能操作的地方自己会说话，解释性段落只会把两卡之间的距离拉长。
 *
 * 添加入口在顶栏右上角；排序靠**长按某一行拖动**，所以行菜单里不再重复放上移/下移。
 *
 * 行菜单四项各有去处：编辑开编辑面板、测试开测试面板、分享走系统分享面板、删除先问一句。
 * 编辑用面板而不是独立页面——改完立刻能对照上下相邻那几条，不用推栈切走再切回。
 * 删除之所以要确认，是因为它删的是**文件**，而插件仓库里那条不一定还在（自建仓库、他人分享的插件
 * 都没法重新下载）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlgTstScreen(
    uiState: PlgTstUiState,
    onToggle: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditOpen: (String) -> Unit,
    onEditorTextChange: (String) -> Unit,
    onEditorFormat: () -> Unit,
    onEditorSave: () -> Unit,
    onEditorDismiss: () -> Unit,
    onTestOpen: (String) -> Unit,
    onTestRun: (String) -> Unit,
    onTestDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onMirrorSelected: (PlgTstMirror) -> Unit,
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
    // 待确认删除的插件：不直接用「当前行」判断，删除后列表会变，弹窗得钉住当时那一条。
    var deleteTarget by remember { mutableStateOf<VdoPlgContent?>(null) }
    // 编辑面板「先收起、再问是否放弃」：面板收起是为了让确认弹窗独占视线，
    // 选「继续编辑」时再把它放回来，内容还在 ViewModel 里，不会丢。
    var editorSheetHidden by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    // 提示语在组合期取好：Snackbar 在协程里弹，那里不能调 stringResource。
    val importPendingMessage = stringResource(R.string.settings_plg_tst_pending)

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        BaseScreenLayout(
            title = stringResource(R.string.settings_plg_tst_title),
            onBackClick = onBackClick,
            actions = {
                IconButton(onClick = { addSheetOpen = true }) {
                    Icon(
                        imageVector = MikomiIcons.Add,
                        contentDescription = stringResource(R.string.settings_plg_tst_add),
                    )
                }
            },
        ) {
            PluginRepoCard(
                repoUrl = uiState.repoUrl,
                mirror = uiState.mirror,
                onRepoClick = { repoEditorOpen = true },
                onMirrorClick = { mirrorPickerOpen = true },
            )

            PluginCard(
                uiState = uiState,
                onToggle = onToggle,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRemoveRequest = { deleteTarget = it },
                onEdit = onEditOpen,
                onTest = onTestOpen,
                onShare = onShare,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    deleteTarget?.let { target ->
        DeletePluginDialog(
            plugin = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onRemove(target.id)
            },
        )
    }

    uiState.test?.let { test ->
        PluginTestSheet(
            state = test,
            onRun = onTestRun,
            onDismiss = onTestDismiss,
        )
    }

    // 面板关掉（保存成功或放弃修改）后把两个临时标记复位，下次打开才不会被上一次挡住。
    // key 用插件名而不是整份状态：编辑区每敲一个字都换 key 会白重启协程。
    LaunchedEffect(uiState.editor?.ruleName) {
        if (uiState.editor == null) {
            editorSheetHidden = false
            discardConfirm = false
        }
    }

    val editor = uiState.editor
    if (editor != null && !editorSheetHidden) {
        PlgTstEditorSheet(
            state = editor,
            onTextChange = onEditorTextChange,
            onFormat = onEditorFormat,
            onSave = onEditorSave,
            onDismissRequest = {
                // 有未保存的修改就先问一句：改坏的 XPath 一旦随手关掉就找不回来了。
                if (editor.dirty) {
                    editorSheetHidden = true
                    discardConfirm = true
                } else {
                    onEditorDismiss()
                }
            },
        )
    }

    if (discardConfirm) {
        DiscardEditorDialog(
            onDismiss = {
                discardConfirm = false
                editorSheetHidden = false
            },
            onConfirm = {
                discardConfirm = false
                onEditorDismiss()
            },
        )
    }

    if (addSheetOpen) {
        AddPluginSheet(
            onDismiss = { addSheetOpen = false },
            onSelect = { method ->
                addSheetOpen = false
                // 「浏览仓库」是唯一已经通了路的入口：商店页就是浏览仓库。
                // 其余三种（剪贴板 / 文件 / 新建）还没接，先如实说一句。
                if (method == AddMethod.FromCatalog) onOpenStore() else notify(importPendingMessage)
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
 * 删除确认。
 *
 * 文案里点明「文件会从本机移除」而不是「删除成功」：删掉的是本地那份 JSON，
 * 站点本身、别人的插件仓库都不受影响，说清楚才不会被当成「下架了某个网站」。
 */
@Composable
private fun DeletePluginDialog(
    plugin: VdoPlgContent,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_plg_tst_delete_title, plugin.name)) },
        text = { Text(stringResource(R.string.settings_plg_tst_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_plg_tst_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_plg_tst_cancel))
            }
        },
    )
}

/**
 * 放弃修改确认。
 *
 * 只在**有未保存内容**时出现：没有改动还问「放弃吗」纯属打扰，而这类面板最常见的操作就是
 * 打开看一眼又关掉。
 */
@Composable
private fun DiscardEditorDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_plg_tst_editor_discard_title)) },
        text = { Text(stringResource(R.string.settings_plg_tst_editor_discard_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_plg_tst_editor_discard_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_plg_tst_editor_continue))
            }
        },
    )
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
private fun PluginCard(
    uiState: PlgTstUiState,
    onToggle: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemoveRequest: (VdoPlgContent) -> Unit,
    onEdit: (String) -> Unit,
    onTest: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val rules = uiState.rules
    // 拖动回调里要读「当前」顺序：pointerInput 的 lambda 只在 key 变化时重建，
    // 直接捕获 rules 会一直看到按下瞬间的旧列表。
    val currentRules by rememberUpdatedState(rules)

    val colors = MaterialTheme.colorScheme
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Float>() }

    SettingsCard {
        SettingsCardSection {
            SettingsCardHeader(
                title = stringResource(R.string.settings_plg_tst_section, rules.size),
            )

            when {
                // 首次扫盘期间既不显示空态也不显示骨架：它只持续几十毫秒，
                // 闪一段东西比短暂空白更碍眼。
                uiState.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MikomiSpacing.Room),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = MikomiStroke.Thick,
                    )
                }

                uiState.isEmpty -> Text(
                    text = stringResource(R.string.settings_plg_tst_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = MikomiSpacing.Room, end = MikomiSpacing.Room, bottom = MikomiSpacing.Room),
                )

                else -> rules.forEachIndexed { index, plugin ->
                    // key 是拖动的前提：列表没有稳定标识时，换位会让「第 i 个槽位」换人，
                    // pointerInput 的 key 一变就连手势一起重启，拖到一半就断。
                    key(plugin.id) {
                        val isDragging = draggingId == plugin.id
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
                                .onSizeChanged { rowHeights[plugin.id] = it.height.toFloat() }
                                .pointerInput(plugin.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = plugin.id
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
                                            val list = currentRules
                                            val from = list.indexOfFirst { it.id == plugin.id }
                                            if (from >= 0 && dragOffset > 0f) {
                                                // 往下拖：越过下一行一半就换位，并把已消费的位移扣掉，
                                                // 否则换位后偏移量还留着，会连着再跳一格。
                                                val below = list.getOrNull(from + 1)
                                                val height = below?.let { rowHeights[it.id] } ?: 0f
                                                if (height > 0f && dragOffset > height / 2f) {
                                                    onMoveDown(plugin.id)
                                                    dragOffset -= height
                                                }
                                            } else if (from >= 0 && dragOffset < 0f) {
                                                val above = list.getOrNull(from - 1)
                                                val height = above?.let { rowHeights[it.id] } ?: 0f
                                                if (height > 0f && dragOffset < -height / 2f) {
                                                    onMoveUp(plugin.id)
                                                    dragOffset += height
                                                }
                                            }
                                        },
                                    )
                                },
                        ) {
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = MikomiSpacing.Room),
                                    color = colors.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            PluginRow(
                                plugin = plugin,
                                dragging = isDragging,
                                onToggle = { onToggle(plugin.id) },
                                onRemove = { onRemoveRequest(plugin) },
                                onEdit = { onEdit(plugin.id) },
                                onTest = { onTest(plugin.id) },
                                onShare = { onShare(plugin.id) },
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
 * 整行就是那个开关：走 [Modifier.toggleable]（`Role.Switch`）而不是 `clickable`，
 * 无障碍把整行报成一个开关（不是按钮 + 开关两个节点），点文字、点开关、点空白都是同一次切换。
 *
 * [Switch] 在这里传 `onCheckedChange = null`：它退化成纯视觉、**不挂任何手势**，
 * 所以点它身上会穿透到整行；同时把行的 [MutableInteractionSource] 递进去，
 * 按下去拨片照样「张开」，反馈不缺。
 *
 * 长按拖动时把 [toggleable] 的写入挡掉：拖动结束的那一下偶尔仍会被判成点击，
 * 这里挡比事后修状态便宜。
 */
@Composable
private fun PluginRow(
    plugin: VdoPlgContent,
    dragging: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    // 一行只有一个手势源：整行 toggleable，开关只做视觉、借这个源显示「张开」。
    // 开关自己不再挂手势 —— 之前那种「开关只读」的写法会吞掉点在自己身上的触摸，
    // 真机上就是「点开关没反应，只有点文字才切」。
    val rowInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = plugin.enabled,
                interactionSource = rowInteraction,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = { if (!dragging) onToggle() },
            )
            .padding(start = MikomiSpacing.Room, end = MikomiSpacing.Sm, top = MikomiSpacing.Md, bottom = MikomiSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleSmall,
                    // 停用的源整体压低存在感，但仍留在原位——它不是「待删除」，只是暂时不上场。
                    color = if (plugin.enabled) colors.onSurface else colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
                Tag(
                    text = stringResource(R.string.settings_plg_tst_version, plugin.version),
                    style = TagStyle.Meta,
                )
            }
            Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
            Text(
                text = pluginMetaLine(plugin),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(MikomiSpacing.Compact))
        Switch(
            checked = plugin.enabled,
            onCheckedChange = null,
            interactionSource = rowInteraction,
        )

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = MikomiIcons.MoreVert,
                    contentDescription = stringResource(R.string.settings_plg_tst_more, plugin.name),
                    tint = colors.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                PluginAction(
                    label = stringResource(R.string.settings_plg_tst_edit),
                    icon = MikomiIcons.Edit,
                    onClick = onEdit,
                    close = { menuOpen = false },
                )
                PluginAction(
                    label = stringResource(R.string.settings_plg_tst_test),
                    icon = MikomiIcons.Science,
                    onClick = onTest,
                    close = { menuOpen = false },
                )
                PluginAction(
                    label = stringResource(R.string.settings_plg_tst_share),
                    icon = MikomiIcons.Share,
                    onClick = onShare,
                    close = { menuOpen = false },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MikomiSpacing.Xs),
                    color = colors.outlineVariant.copy(alpha = 0.5f),
                )
                PluginAction(
                    label = stringResource(R.string.settings_plg_tst_remove),
                    icon = MikomiIcons.Delete,
                    tint = colors.error,
                    onClick = onRemove,
                    close = { menuOpen = false },
                )
            }
        }
    }
}

/** 菜单项的统一形状：先关菜单再执行，避免动作里跳页面或弹窗时菜单还挂着。 */
@Composable
private fun PluginAction(
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
private fun PluginRepoCard(
    repoUrl: String,
    mirror: PlgTstMirror,
    onRepoClick: () -> Unit,
    onMirrorClick: () -> Unit,
) {
    SettingsCard {
        SettingsCardSection {
            SettingsCardHeader(stringResource(R.string.settings_plg_tst_repo_section))
            Column(modifier = Modifier.padding(bottom = MikomiSpacing.Sm)) {
                RepoRow(
                    title = stringResource(R.string.settings_plg_tst_repo_address),
                    value = repoUrl,
                    onClick = onRepoClick,
                )
                RepoDivider()
                RepoRow(
                    title = stringResource(R.string.settings_plg_tst_repo_mirror),
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
        modifier = Modifier.padding(horizontal = MikomiSpacing.Room),
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
            .clip(RoundedCornerShape(MikomiRadius.Xl))
            .clickable(onClick = onClick)
            .padding(horizontal = MikomiSpacing.Room, vertical = MikomiSpacing.Md),
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
        Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
        Icon(
            imageVector = MikomiIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}

/**
 * 插件测试面板：只测两件真正要紧的事——**剧集拿不拿得到、视频出不出流**。
 *
 * 三步是串起来的：搜索 → 剧集 → 出流。哪一步断了就停在那一步并说明原因，后面的步骤标「未执行」。
 * 不再单独列字段体检清单：字段缺失会以「插件读不出来（字段名）」的形式落在具体那一步上，
 * 比十条「未填写」更接近用户真正遇到的问题。
 *
 * 出流会真的拉开一次播放地址（只读开头 2KB），所以它慢；换来的是现场数据：
 * HLS 还是直链 mp4、HTTP 码多少，都写在行里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginTestSheet(
    state: PlgTstTestState,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 输入框用面板内部状态：它只影响「下一次跑什么」，不该在每次结果回来时被重置。
    var keyword by rememberSaveable(state.ruleName) { mutableStateOf(state.keyword) }
    val report = state.report

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MikomiSpacing.Room)
                .padding(bottom = MikomiSpacing.Xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_plg_tst_test_title, state.ruleName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(MikomiSpacing.Md))
                TestSummaryChip(running = state.running, report = report)
            }

            Spacer(modifier = Modifier.height(MikomiSpacing.Md))
            StageRow(
                label = stringResource(R.string.settings_plg_tst_test_stage_search),
                result = report?.search,
                running = state.running,
                summary = report?.searchHits?.takeIf { it.isNotEmpty() }?.let { hits ->
                    stringResource(
                        R.string.settings_plg_tst_test_hit,
                        hits.size,
                        hits.first().name,
                    )
                },
            )
            StageRow(
                label = stringResource(R.string.settings_plg_tst_test_stage_episodes),
                result = report?.episodes,
                running = state.running,
                summary = report?.roads?.takeIf { it.isNotEmpty() }?.let { roads ->
                    // 每条线路都保证至少有 1 集（引擎侧滤掉空线路），这里仍按可空处理，免得坏数据把面板搞崩。
                    roads.firstOrNull()?.episodes?.firstOrNull()?.let { first ->
                        stringResource(
                            R.string.settings_plg_tst_test_roads,
                            roads.size,
                            report.episodeCount,
                            first.name,
                        )
                    }
                },
            )
            StageRow(
                label = stringResource(R.string.settings_plg_tst_test_stage_stream),
                result = report?.stream,
                running = state.running,
                summary = report?.streamInfo?.let { stream ->
                    stringResource(
                        R.string.settings_plg_tst_test_stream,
                        stringResource(stream.kind.labelRes()),
                        stream.statusCode,
                        stream.byteCount,
                    )
                },
                mono = report?.streamInfo?.url,
            )

            Spacer(modifier = Modifier.height(MikomiSpacing.Room))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text(stringResource(R.string.settings_plg_tst_test_keyword_label)) },
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Md))
            Button(
                onClick = { onRun(keyword) },
                // 没有关键词就没法走第一步：搜索是后面两步唯一的入口。
                enabled = !state.running && keyword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = MikomiStroke.Thick,
                        color = colors.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(MikomiSpacing.Compact))
                }
                Text(stringResource(R.string.settings_plg_tst_test_run))
            }
        }
    }
}

/** 结论小标签：剧集与出流都通 / 哪几步没过 / 测试中。 */
@Composable
private fun TestSummaryChip(running: Boolean, report: VdoPlgTestReport?) {
    val colors = MaterialTheme.colorScheme
    val (text, foreground, background) = when {
        running -> Triple(
            stringResource(R.string.settings_plg_tst_test_running),
            colors.onSurfaceVariant,
            colors.surfaceContainerHigh,
        )

        // 关键词为空时报告里三步全跳过，那不是「都通过」，要按未测试呈现。
        report == null || report.keyword.isBlank() -> Triple(
            stringResource(R.string.settings_plg_tst_test_idle),
            colors.onSurfaceVariant,
            colors.surfaceContainerHigh,
        )

        report.failedCount == 0 -> Triple(
            stringResource(R.string.settings_plg_tst_test_summary_pass),
            colors.primary,
            colors.primary.copy(alpha = 0.12f),
        )

        else -> Triple(
            stringResource(R.string.settings_plg_tst_test_summary_fail, report.failedCount),
            colors.error,
            colors.error.copy(alpha = 0.12f),
        )
    }
    Surface(shape = RoundedCornerShape(MikomiRadius.Md), color = background, contentColor = foreground) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = MikomiSpacing.Sm, vertical = MikomiSpacing.Xxs),
        )
    }
}

/**
 * 一步的结论行。
 *
 * @param summary 通过时那行「事实」（命中几条、几条线路、什么类型的流）。
 * @param mono 附加的等宽细节（目前只有出流地址），失败时是出问题的字段名或地址。
 */
@Composable
private fun StageRow(
    label: String,
    result: VdoPlgStageResult?,
    running: Boolean,
    summary: String? = null,
    mono: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when (result) {
        is VdoPlgStageResult.Passed -> colors.primary
        is VdoPlgStageResult.Failed -> colors.error
        else -> colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MikomiSpacing.Tight),
        verticalAlignment = Alignment.Top,
    ) {
        when {
            running && result == null -> CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = MikomiSpacing.Xxs)
                    .size(16.dp),
                strokeWidth = MikomiStroke.Thick,
            )

            else -> Icon(
                imageVector = when (result) {
                    is VdoPlgStageResult.Passed -> MikomiIcons.CheckCircle
                    is VdoPlgStageResult.Failed -> MikomiIcons.Error
                    else -> MikomiIcons.RadioUnchecked
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(top = MikomiSpacing.Xxs)
                    .size(MikomiSizes.IconSm),
            )
        }
        Spacer(modifier = Modifier.width(MikomiSpacing.Compact))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            when (result) {
                is VdoPlgStageResult.Passed -> if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                is VdoPlgStageResult.Failed -> {
                    Text(
                        text = stringResource(result.reason.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                    result.detail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                VdoPlgStageResult.Skipped -> Text(
                    text = stringResource(R.string.settings_plg_tst_test_skipped),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                null -> if (!running) {
                    Text(
                        text = stringResource(R.string.settings_plg_tst_test_idle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (result is VdoPlgStageResult.Passed && mono != null) {
                Text(
                    text = mono,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 失败原因 → 一句人话。数据层只给枚举，文案留在这里。 */
@StringRes
private fun VdoFailureReason.labelRes(): Int = when (this) {
    VdoFailureReason.RuleBroken -> R.string.settings_plg_tst_test_reason_rule_broken
    VdoFailureReason.NetworkError -> R.string.settings_plg_tst_test_reason_network
    VdoFailureReason.HttpError -> R.string.settings_plg_tst_test_reason_http
    VdoFailureReason.NoResults -> R.string.settings_plg_tst_test_reason_no_results
    VdoFailureReason.NoEpisodes -> R.string.settings_plg_tst_test_reason_no_episodes
    VdoFailureReason.NoStream -> R.string.settings_plg_tst_test_reason_no_stream
    VdoFailureReason.StreamUnreachable -> R.string.settings_plg_tst_test_reason_stream_unreachable
    VdoFailureReason.NeedWebView -> R.string.settings_plg_tst_test_reason_webview
}

/** 流类型 → 文案。HLS 与直链的差别，决定了播放器要不要走 HLS 分支。 */
@StringRes
private fun VdoStreamKind.labelRes(): Int = when (this) {
    VdoStreamKind.Hls -> R.string.settings_plg_tst_test_stream_hls
    VdoStreamKind.Mp4 -> R.string.settings_plg_tst_test_stream_mp4
    VdoStreamKind.Other -> R.string.settings_plg_tst_test_stream_other
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
        title = { Text(stringResource(R.string.settings_plg_tst_repo_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid,
                supportingText = {
                    if (!valid) {
                        Text(stringResource(R.string.settings_plg_tst_repo_edit_invalid))
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
                Text(stringResource(R.string.settings_plg_tst_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_plg_tst_cancel))
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
    current: PlgTstMirror,
    onDismiss: () -> Unit,
    onSelect: (PlgTstMirror) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_plg_tst_repo_mirror_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                PlgTstMirror.entries.forEach { mirror ->
                    val selected = mirror == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MikomiRadius.Lg))
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mirror) },
                            )
                            .padding(vertical = MikomiSpacing.Compact),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
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
                Text(stringResource(R.string.settings_plg_tst_cancel))
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
 * 「浏览插件仓库」用主色淡底托一下：仓库是这个 App 唯一的官方来源，其余三种是补充，
 * 四行长得一样的话，新用户会去点「新建」，然后在空白模板里卡住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPluginSheet(
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
                text = stringResource(R.string.settings_plg_tst_add_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(start = MikomiSpacing.Room, end = MikomiSpacing.Room),
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Md))

            AddMethod.entries.forEach { method ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 左右各让 8dp：推荐项要铺一层淡底，贴着屏幕边会显得像状态色块。
                        .padding(horizontal = MikomiSpacing.Sm)
                        .clip(RoundedCornerShape(MikomiRadius.Xl))
                        .then(
                            if (method == AddMethod.FromCatalog) {
                                Modifier.background(colors.primary.copy(alpha = 0.10f))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(method) }
                        .padding(horizontal = MikomiSpacing.Md, vertical = MikomiSpacing.Gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(MikomiRadius.Lg),
                        color = colors.surfaceContainerHighest,
                        contentColor = colors.onSurfaceVariant,
                    ) {
                        Icon(
                            imageVector = method.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(MikomiSpacing.Sm)
                                .size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(MikomiSpacing.Md))
                    Text(
                        text = stringResource(method.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(MikomiSpacing.Md))
        }
    }
}

/** 添加方式。顺序即展示顺序：三个导入在前，「新建」垫底。 */
private enum class AddMethod(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    FromCatalog(
        titleRes = R.string.settings_plg_tst_add_catalog,
        icon = MikomiIcons.TravelExplore,
    ),
    FromClipboard(
        titleRes = R.string.settings_plg_tst_add_clipboard,
        icon = MikomiIcons.ContentPaste,
    ),
    FromFile(
        titleRes = R.string.settings_plg_tst_add_file,
        icon = MikomiIcons.FileOpen,
    ),
    FromTemplate(
        titleRes = R.string.settings_plg_tst_add_create,
        icon = MikomiIcons.EditNote,
    ),
}

/** 副标题：作者（可缺省）+ 站点 host。写完整 URL 太长，host 足够认出来源。 */
private fun pluginMetaLine(plugin: VdoPlgContent): String {
    val host = runCatching { java.net.URI(plugin.baseUrl).host }.getOrNull()
        ?: plugin.baseUrl
    return listOf(plugin.author, host)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
