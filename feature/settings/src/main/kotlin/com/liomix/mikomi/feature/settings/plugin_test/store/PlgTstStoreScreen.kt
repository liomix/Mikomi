package com.liomix.mikomi.feature.settings.plugin_test.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.feature.settings.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 插件商店页。
 *
 * 「视频插件源」是一份 JSON，商店就是这些插件的目录页。与插件测试页的区别是定位：
 * 那一页管**已经装上的**（启停、排序、删除），这一页管**还没装上的**（浏览、搜索、安装）。
 *
 * 从 Kazumi 的插件仓库页搬过来的四处，都是「用起来才知道值」的细节：
 * 1. **筛选 chip 带计数**：`全部 12 / 已安装 2 / 可更新 1` 一行就把仓库规模、已装数量、
 *    待更新数量说完了，不用再单独摆一段统计文字；这三个数也正好是三种筛选的入口。
 * 2. **刷新不清空**：已有列表时再刷新只加一条 2dp 进度线，失败也只在顶部提示一行，
 *    不会把列表换成错误页——用户手里的信息不该因为一次刷新而消失。
 * 3. **装/更新/已安装三态由版本比较推导**，不在条目里存状态：目录版本一变，
 *    所有行的状态自动跟着变，不需要手动同步两份数据。
 * 4. **安装中的按钮就地转圈并禁用**，不用整页 loading：一次只装一条时，
 *    其它条目仍然可以继续浏览和安装。
 *
 * 没有搬的：顶部那块 primaryContainer 大色块（设置系页面统一是描边卡，单独一块主色会
 * 把这一页从体系里摘出去），以及「更新全部」「多选批量」——现在目录里只有几十条，
 * 先不让它们占位置。
 */
@Composable
fun PlgTstStoreScreen(
    uiState: PlgTstStoreUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (PlgTstStoreFilter) -> Unit,
    onSortChange: (PlgTstStoreSort) -> Unit,
    onRefresh: () -> Unit,
    onInstall: (PlgTstStoreItem) -> Unit,
    onMessageShown: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message = uiState.message
    // 文案在组合期取好：showSnackbar 跑在协程里，那里调不了 stringResource。
    val messageText = when (message) {
        null -> null
        is PlgTstStoreMessage.Installed ->
            stringResource(R.string.settings_store_toast_installed, message.name)
        is PlgTstStoreMessage.InstallFailed ->
            stringResource(R.string.settings_store_toast_failed, message.name, message.reason)
    }
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        // 先清状态再弹：不清的话旋屏或重进页面会把同一条提示再弹一次。
        // 弹的动作放到页面级 scope 里——清状态会让本 effect 的 key 变化并取消它自己。
        onMessageShown()
        scope.launch { snackbarHostState.showSnackbar(messageText) }
    }

    // 排序/筛选会遍历全表，缓存在这四个输入上，滚动时不再重算。
    val visibleItems = remember(
        uiState.items,
        uiState.query,
        uiState.filter,
        uiState.sort,
        uiState.installedVersions,
    ) { uiState.visibleItems() }

    val error = uiState.error

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        BaseScreenLayout(
            title = stringResource(R.string.settings_store_title),
            onBackClick = onBackClick,
            actions = {
                SortAction(current = uiState.sort, onSelect = onSortChange)
                IconButton(onClick = onRefresh, enabled = !uiState.loading) {
                    Icon(
                        imageVector = MikomiIcons.Refresh,
                        contentDescription = stringResource(R.string.settings_store_refresh),
                    )
                }
            },
            scrollable = false,
            contentPadding = PaddingValues(MikomiSpacing.None),
            verticalArrangement = Arrangement.Top,
        ) {
            SearchField(query = uiState.query, onQueryChange = onQueryChange)
            FilterRow(uiState = uiState, onFilterChange = onFilterChange)

            if (uiState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            } else if (error != null && uiState.hasContent) {
                Text(
                    text = stringResource(R.string.settings_store_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = MikomiSpacing.Room, vertical = MikomiSpacing.Tight),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isFirstLoad -> StoreSkeleton()

                    error != null && !uiState.hasContent -> StoreStateView(
                        icon = MikomiIcons.CloudOff,
                        title = stringResource(R.string.settings_store_error_title),
                        description = stringResource(R.string.settings_store_error_desc, error),
                        action = {
                            Button(onClick = onRefresh) {
                                Text(stringResource(R.string.settings_store_retry))
                            }
                        },
                    )

                    !uiState.hasContent -> StoreStateView(
                        icon = MikomiIcons.Extension,
                        title = stringResource(R.string.settings_store_empty_title),
                        description = stringResource(R.string.settings_store_empty_desc),
                    )

                    visibleItems.isEmpty() -> StoreStateView(
                        icon = MikomiIcons.SearchOff,
                        title = stringResource(R.string.settings_store_no_match_title),
                        action = {
                            TextButton(
                                onClick = {
                                    onQueryChange("")
                                    onFilterChange(PlgTstStoreFilter.All)
                                },
                            ) {
                                Text(stringResource(R.string.settings_store_clear_filter))
                            }
                        },
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        contentPadding = PaddingValues(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Compact),
                    ) {
                        items(
                            items = visibleItems,
                            key = { it.name },
                            contentType = { "plugin-store-item" },
                        ) { item ->
                            StoreItemCard(
                                item = item,
                                status = uiState.statusOf(item),
                                installing = item.name in uiState.installing,
                                onInstall = { onInstall(item) },
                            )
                        }
                    }
                }
            }

        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

/**
 * 搜索框。
 *
 * 只按名字与作者过滤，不搜插件正文：正文还没下载，搜不了。
 * 输入即过滤（几十条数据不值得加防抖），清除按钮只在有内容时出现。
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Xs),
        placeholder = { Text(stringResource(R.string.settings_store_search_hint)) },
        leadingIcon = {
            Icon(imageVector = MikomiIcons.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = MikomiIcons.Close,
                        contentDescription = stringResource(R.string.settings_store_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(MikomiRadius.Xl),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

/**
 * 筛选行。
 *
 * chip 上的数字就是筛选条件本身（全部/已装/可更新各多少条），所以三个 chip 既是筛选入口
 * 也是这一页的统计摘要，不再另起一行写「共 N 条」。
 */
@Composable
private fun FilterRow(
    uiState: PlgTstStoreUiState,
    onFilterChange: (PlgTstStoreFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
    ) {
        PlgTstStoreFilter.entries.forEach { filter ->
            FilterChip(
                selected = uiState.filter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(stringResource(filter.labelRes, uiState.countFor(filter)))
                },
            )
        }
    }
}

/** 排序菜单：只有两档，用下拉而不是分段控件——它不常用，不值得常驻两个按钮的宽度。 */
@Composable
private fun SortAction(
    current: PlgTstStoreSort,
    onSelect: (PlgTstStoreSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MikomiIcons.Sort,
                contentDescription = stringResource(R.string.settings_store_sort),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlgTstStoreSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                    trailingIcon = {
                        if (sort == current) {
                            Icon(
                                imageVector = MikomiIcons.Check,
                                contentDescription = stringResource(R.string.settings_store_sort),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 一个插件。
 *
 * 名称与版本胶囊同一行、作者与时间同一行，信息密度压到三行以内：
 * 商店要的是「一眼扫完一屏」，不是每条都展开成详情卡。
 */
@Composable
private fun StoreItemCard(
    item: PlgTstStoreItem,
    status: PlgTstStoreStatus,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val updatedLabel = remember(item.updatedAtMillis) { formatUpdatedAt(item.updatedAtMillis) }
    val meta = when {
        item.author.isNotEmpty() && updatedLabel != null ->
            stringResource(R.string.settings_store_meta_both, item.author, updatedLabel)
        item.author.isNotEmpty() ->
            stringResource(R.string.settings_store_meta_author, item.author)
        updatedLabel != null ->
            stringResource(R.string.settings_store_meta_date, updatedLabel)
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MikomiRadius.Xxl),
        color = colors.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MikomiSpacing.Gutter, vertical = MikomiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTile(status = status)
            Spacer(modifier = Modifier.width(MikomiSpacing.Md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
                    StoreTag(
                        text = stringResource(R.string.settings_store_version, item.version),
                        background = colors.surfaceContainerHigh,
                        foreground = colors.onSurfaceVariant,
                    )
                }
                if (meta != null) {
                    Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.antiCrawler) {
                    Spacer(modifier = Modifier.height(MikomiSpacing.Tight))
                    StoreTag(
                        text = stringResource(R.string.settings_store_anti_crawler),
                        background = colors.tertiaryContainer,
                        foreground = colors.onTertiaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(MikomiSpacing.Md))
            StoreAction(
                status = status,
                installing = installing,
                onInstall = onInstall,
            )
        }
    }
}

/** 左侧状态块：装着的给对勾，没装的给插件图标，颜色一并区分。 */
@Composable
private fun StatusTile(status: PlgTstStoreStatus) {
    val colors = MaterialTheme.colorScheme
    val installed = status != PlgTstStoreStatus.Install

    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(MikomiRadius.Xl),
        color = if (installed) colors.surfaceContainerHigh else colors.primary.copy(alpha = 0.12f),
        contentColor = if (installed) colors.onSurfaceVariant else colors.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (installed) MikomiIcons.Check else MikomiIcons.Extension,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 行尾动作。
 *
 * 三种形态宽度接近：安装/更新是同一个按钮，装完了换成一段「已安装」文字，
 * 下载中按钮变灰转圈。宽度不跳，滚动时右边缘才是齐的。
 */
@Composable
private fun StoreAction(
    status: PlgTstStoreStatus,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when {
        installing -> FilledTonalButton(
            onClick = {},
            enabled = false,
            contentPadding = ActionContentPadding,
            modifier = Modifier.height(40.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = MikomiStroke.Thick,
                color = colors.primary,
            )
            Spacer(modifier = Modifier.width(MikomiSpacing.Tight))
            Text(
                text = stringResource(
                    if (status == PlgTstStoreStatus.Update) {
                        R.string.settings_store_updating
                    } else {
                        R.string.settings_store_installing
                    },
                ),
            )
        }

        status == PlgTstStoreStatus.Installed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MikomiIcons.Check,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(MikomiSpacing.Xs))
            Text(
                text = stringResource(R.string.settings_store_installed),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }

        else -> FilledTonalButton(
            onClick = onInstall,
            contentPadding = ActionContentPadding,
            modifier = Modifier.height(40.dp),
        ) {
            Icon(
                imageVector = if (status == PlgTstStoreStatus.Update) MikomiIcons.Sync else MikomiIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(MikomiSpacing.Tight))
            Text(
                text = stringResource(
                    if (status == PlgTstStoreStatus.Update) {
                        R.string.settings_store_update
                    } else {
                        R.string.settings_store_install
                    },
                ),
            )
        }
    }
}

/** 小标签：版本号、反爬这类短信息。 */
@Composable
private fun StoreTag(
    text: String,
    background: Color,
    foreground: Color,
) {
    Surface(
        shape = RoundedCornerShape(MikomiRadius.Md),
        color = background,
        contentColor = foreground,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = MikomiSpacing.Sm, vertical = MikomiSpacing.Xxs),
        )
    }
}

/**
 * 空态 / 错误态的统一外壳。
 *
 * 三处（加载失败、仓库为空、筛选无结果）只有图标、标题与动作不同：合成一个，
 * 三者才会在同一高度、同一对齐方式上，翻页时不会一屏一个样。
 */
@Composable
private fun StoreStateView(
    icon: ImageVector,
    title: String,
    description: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MikomiSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(MikomiSizes.AvatarMd),
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(MikomiSpacing.Tight))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(MikomiSpacing.Lg))
            action()
        }
    }
}

/**
 * 首屏骨架。
 *
 * 用骨架而不是转圈：卡片形状先摆出来，数据到达时是「填空」而不是「整页换一次」，
 * 视觉跳变小很多。行数取 5，约等于常见手机一屏能看到的条数。
 */
@Composable
private fun StoreSkeleton() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Compact),
    ) {
        repeat(5) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MikomiRadius.Xxl))
                    .background(colors.surfaceContainerLow)
                    .padding(horizontal = MikomiSpacing.Gutter, vertical = MikomiSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(MikomiRadius.Xl),
                )
                Spacer(modifier = Modifier.width(MikomiSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    // 宽度交替，看起来像真实的名称长短，不像一排等长色块。
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.42f else 0.56f)
                            .height(14.dp),
                    )
                    Spacer(modifier = Modifier.height(MikomiSpacing.Sm))
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(10.dp),
                    )
                }
                Spacer(modifier = Modifier.width(MikomiSpacing.Md))
                SkeletonBox(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp),
                    shape = RoundedCornerShape(MikomiRadius.Xxl),
                )
            }
        }
    }
}

/** 更新时间的展示形式：只到天，时区取本机。 */
private fun formatUpdatedAt(millis: Long): String? {
    if (millis <= 0L) return null
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}

/** 行尾按钮的左右内边距：比默认更窄，「安装」两字不用撑成一个大按钮。 */
private val ActionContentPadding = PaddingValues(horizontal = MikomiSpacing.Gutter)
