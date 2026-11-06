package com.liomix.mikomi.feature.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.base.ErrorState
import com.liomix.mikomi.core.ui.component.base.PressButton
import com.liomix.mikomi.core.ui.component.base.PressButtonStyle
import com.liomix.mikomi.core.ui.component.base.PressIconButton
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.feature.detail.component.CreditDetailSheet
import com.liomix.mikomi.feature.detail.component.collapsingHeader
import com.liomix.mikomi.feature.detail.component.rememberHeaderCollapse
import com.liomix.mikomi.feature.detail.component.rememberHeaderCollapseConnection
import kotlin.math.roundToInt

/** 头部完整高度（展开态）。 */
private val HEADER_HEIGHT = 420.dp

/**
 * 收到底之后头部仍保留的高度：**只是首帧占位**。
 *
 * 真正的值由头部实测得出 —— 信息列（评分排在最下面）的底边 + Tab 栏 + 一点余量，
 * 收到那里时 Tab 栏刚好停在评分下方。状态栏高度、字体缩放、刘海安全区都会改变这个数，
 * 写死一个 dp 数不是压住评分，就是收得不够，所以这里只给一个足够小的兜底：
 * 首帧布局完成的同一个回调里就会被真实值替换，用户看不到它。
 */
private val HEADER_MIN_VISIBLE_PLACEHOLDER = 140.dp

/**
 * 收到底时，Tab 栏顶边与评分下沿之间留的缝。
 *
 * 「一点点」就是这几 dp：不贴死（贴死会让两行文字挤在一起像同一块），
 * 也不拉开（拉开就白收了，封面留在屏幕上没意义）。
 */
private val HEADER_TAIL_GAP = 8.dp

/**
 * Tab 栏高度。
 *
 * M3 `TabRow` 的高度由 Tab 的最小高度定死在这个值上，用来在「收到底」时把 Tab 栏自己的
 * 高度算进去 —— 收到底的位置是 Tab 栏的**顶边**对齐评分下方，而不是可见区域的底边。
 */
private val TAB_BAR_HEIGHT = 48.dp

/**
 * 橡皮筋最大位移。
 *
 * 内容已经到顶/到底之后继续拖，整页会被拉出这么远（越接近这个距离越拉不动），
 * 松手弹回。40dp 是「手感上明确有一下回弹、又几乎不改变页面版式」的量。
 */
private val HEADER_STRETCH_LIMIT = 40.dp

/** 内容区底部留白：避让右下角悬浮按钮组（收藏 48 + 间距 12 + 追番 56 + 外边距 24 + 余量）。 */
internal val CONTENT_BOTTOM_SPACER = 156.dp

/** 收藏按钮直径（比追番按钮更收，形成主次）。 */
private val FAVORITE_BUTTON_SIZE = 48.dp

/**
 * Tab 栏底色的不透明度。
 *
 * 它压在模糊封面上，留一点透明度让封面的色调透上来，整块看起来是一体的；
 * 0.88 是「看得出底色、又不影响文字对比度」的平衡点（浅色主题下接近白，深色主题下接近 #181818）。
 */
private const val TAB_BAR_ALPHA = 0.88f

/**
 * 详情页。
 *
 * 从上到下一条纵向流：头部（封面 + 标题 + 信息 + Tab 栏）→ 内容区。
 *
 * **Tab 栏是长在头部上的**：它叠在封面底部，上滑时由 [NestedScrollConnection] 把增量先给头部，
 * Tab 栏贴着封面的下边缘往上盖，封面本身钉在原地不动、可见高度一路收到「评分下方一点」
 * 就停住 —— 头部永远不会被收没，标题与评分都还在。收到底后增量才落到内容区自己身上；
 * 内容回到顶部再继续下滑，Tab 栏又滑下来、封面重新露全。
 *
 * 每个 Tab 仍保有自己的滚动状态（各自 `ScrollState` / `LazyListState`），切 Tab 互不影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    creditsState: AnimeCreditsUiState,
    creditDetailState: CreditDetailUiState,
    creditCommentsState: CreditCommentsUiState,
    teasingState: TeasingUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLoadCredits: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    onDismissCreditDetail: () -> Unit,
    onLoadTeasing: () -> Unit,
    onLoadMoreTeasing: () -> Unit,
    onRetryTeasing: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 头部封面直接顶到屏幕最上方（状态栏之下由 header 自行留白），insets 在页面内自行处理。
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        when (uiState) {
            // 加载态用骨架屏而不是空白页：从列表点进来的首帧若渲染空白页，浅色主题下就是
            // 整屏发白的「白屏闪一下」；骨架屏与真实布局同构，转场过程中也不会突兀。
            DetailUiState.Loading -> DetailLoadingSkeleton(
                modifier = Modifier.padding(innerPadding),
            )

            DetailUiState.Error -> ErrorState(
                message = stringResource(R.string.detail_load_failed),
                modifier = Modifier.padding(innerPadding),
            )

            is DetailUiState.Success -> DetailContent(
                anime = uiState.anime,
                creditsState = creditsState,
                teasingState = teasingState,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onLoadCredits = onLoadCredits,
                onLoadTeasing = onLoadTeasing,
                onLoadMoreTeasing = onLoadMoreTeasing,
                onRetryTeasing = onRetryTeasing,
                onCharacterClick = onCharacterClick,
                onStaffClick = onStaffClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // 主创详情底部弹层。
    if (creditDetailState != CreditDetailUiState.Hidden) {
        CreditDetailSheet(
            detailState = creditDetailState,
            commentsState = creditCommentsState,
            onDismiss = onDismissCreditDetail,
        )
    }
}

@Composable
private fun DetailContent(
    anime: Anime,
    creditsState: AnimeCreditsUiState,
    teasingState: TeasingUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLoadCredits: () -> Unit,
    onLoadTeasing: () -> Unit,
    onLoadMoreTeasing: () -> Unit,
    onRetryTeasing: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(DetailTabType.OVERVIEW) }
    // 头部封面一直画到屏幕最顶端，状态栏底下没有遮罩，图标必须转成浅色才看得见。
    ImmersiveStatusBar()
    // 头部收缩：内容区的滚动先经过这里，Tab 栏贴着封面下边缘往上盖。
    val collapse = rememberHeaderCollapse(
        fullHeight = HEADER_HEIGHT,
        initialMinVisible = HEADER_MIN_VISIBLE_PLACEHOLDER,
        stretchLimit = HEADER_STRETCH_LIMIT,
    )
    val collapseConnection = rememberHeaderCollapseConnection(collapse)
    // 收到底 = 评分下沿 + Tab 栏 + 余量：Tab 栏的顶边正好落在评分下面一点点。
    val headerTailPx = with(LocalDensity.current) {
        (TAB_BAR_HEIGHT + HEADER_TAIL_GAP).toPx()
    }

    // 每个 Tab 独立的滚动状态，切 Tab 后各自位置保留、互不干扰。
    val overviewScroll = rememberScrollState()
    val creditsScroll = rememberScrollState()
    val commentScroll = rememberScrollState()
    val teasingListState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        // 整页橡皮筋把内容推走时，屏幕顶部会露出一条缝。这里垫一层与头部同源的模糊封面，
        // 露出来的是「封面自己的上沿」，而不是一条页面底色（深色主题下是纯黑）。
        HeaderEdgeBackdrop(
            model = anime.coverUrl,
            headerHeight = HEADER_HEIGHT,
            stretchPx = { collapse.stretchPx },
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_STRETCH_LIMIT),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(collapseConnection)
                // 橡皮筋：整页（头部 + Tab 栏 + 内容）被拉出的临时位移，只在两端越界时非 0；
                // 读取发生在布局阶段，不触发重组。
                .offset { IntOffset(0, collapse.stretchPx.roundToInt()) },
        ) {
            // 头部 + Tab 栏：同一个块。Tab 栏叠在封面底部，随收缩沿封面上移，
            // 裁切由 collapsingHeader 负责 —— 露不出来的只有封面下半截，标题永远在。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .collapsingHeader(collapse),
            ) {
                DetailHeader(
                    anime = anime,
                    // 头部能收到多高由评分的位置决定：实测底边 + Tab 栏 + 余量。
                    // 读的是布局结果，不是每帧变化的量，滚动里不会重复回调。
                    onInfoBottomMeasured = { infoBottomPx ->
                        collapse.updateMinVisible(infoBottomPx + headerTailPx)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                DetailTabBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                    // 贴住可见区域的下边缘：读取放进 offset 的 lambda 里，走布局阶段。
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, -collapse.offsetPx.roundToInt()) },
                )
            }
            // 内容区：占满剩余高度，内部各自滚动。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    DetailTabType.OVERVIEW -> ScrollableTabPane(scrollState = overviewScroll) {
                        OverviewTab(anime = anime)
                    }

                    DetailTabType.CREDITS -> ScrollableTabPane(scrollState = creditsScroll) {
                        CreditsTab(
                            creditsState = creditsState,
                            onLoadCredits = onLoadCredits,
                            onCharacterClick = onCharacterClick,
                            onStaffClick = onStaffClick,
                        )
                    }

                    DetailTabType.TEASING -> TeasingTab(
                        state = teasingState,
                        listState = teasingListState,
                        onLoadTeasing = onLoadTeasing,
                        onLoadMore = onLoadMoreTeasing,
                        onRetry = onRetryTeasing,
                    )
                }
            }
        }

        // 右下角悬浮按钮组：收藏（圆形星标）+ 追番（扩展按钮）。
        BottomActions(
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onFollowClick = onPlayClick,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * 加载骨架：与真实布局同构（头部封面 + 右侧信息行 + 正文行）。
 *
 * 详情数据来自 Room，通常一两帧就到；这里只负责让这一两帧不出现「整屏空白」。
 */
@Composable
private fun DetailLoadingSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .windowInsetsPadding(WindowInsets.statusBars)
                // 与真实头部一致：状态栏之下不再留额外上边距。
                .padding(start = MikomiSpacing.Room, end = MikomiSpacing.Room, bottom = MikomiSpacing.Room),
        ) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(26.dp),
                shape = MikomiPillShape,
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Room))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Room),
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(MikomiRadius.Xl),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Lg),
                ) {
                    repeat(3) {
                        Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xs)) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 56.dp, height = 14.dp),
                                shape = MikomiPillShape,
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 84.dp, height = 20.dp),
                                shape = RoundedCornerShape(MikomiRadius.Md),
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = MikomiSpacing.Room, vertical = MikomiSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
        ) {
            repeat(4) { index ->
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 3) 0.6f else 1f)
                        .height(14.dp),
                    shape = MikomiPillShape,
                )
            }
        }
    }
}

/** 右下角按钮组：收藏在上、追番在下，纵向右对齐。 */
@Composable
private fun BottomActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = MikomiSpacing.Room, bottom = MikomiSpacing.Xl),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
    ) {
        FavoriteButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
        )
        // 追番按钮：对齐 Dart 版 PlayButton —— 播放图标 + 文案，浮在内容之上。
        PressButton(
            text = stringResource(R.string.detail_follow),
            onClick = onFollowClick,
            leadingIcon = MikomiIcons.Play,
            style = PressButtonStyle.Elevated,
        )
    }
}

/**
 * 收藏按钮：圆形星标，已收藏时填充主色。
 *
 * 收藏成功的那一刻在按钮上叠一次星星反应（`star_collect.json`）：
 * 素材本身是「星星轮廓炸开、粒子飞散再消失」，所以只在播放期间存在于组合里，
 * 播完立刻摘掉 —— 平时这张页面不为它保留任何绘制节点，也没有常驻的帧回调。
 *
 * 触发看的是**状态**而不是点击：`isFavorite` 由数据库观察流驱动，只有真正写进去了才会
 * `false -> true`，写失败时不会白炸一次。取消收藏不播（素材也不是为倒放设计的）。
 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescriptionRes = if (isFavorite) {
        R.string.detail_remove_favorite
    } else {
        R.string.detail_add_favorite
    }
    // 进页面就解析好 json（异步，几十毫秒），真正点收藏时不必等加载。
    val composition by rememberLottieComposition(StarCollectSpec)
    val progress = remember { Animatable(0f) }
    var bursting by remember { mutableStateOf(false) }
    var playToken by remember { mutableIntStateOf(0) }
    // 记住上一次的状态：进页面时已经是「已收藏」不该补播一遍。
    var wasFavorite by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (isFavorite && !wasFavorite) playToken++
        wasFavorite = isFavorite
    }

    val loaded = composition
    LaunchedEffect(playToken, loaded) {
        if (playToken == 0 || loaded == null) return@LaunchedEffect
        // 先归零再挂上去，避免复用上一次的末帧闪一下。
        progress.snapTo(0f)
        bursting = true
        // 进度手写而不用 animateLottieCompositionAsState：需要「播一次就撤」，
        // 且速率必须是线性 —— json 关键帧自带缓动，再叠一层会拖尾。
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = loaded.duration.toInt().coerceAtLeast(1),
                easing = MikomiMotion.Curve.Linear,
            ),
        )
        bursting = false
    }

    Box(modifier = modifier.size(FAVORITE_BUTTON_SIZE)) {
        // 形状是圆的、图标是星星，但按下/松手的节奏和追番按钮完全一样（共用 pressScale）。
        PressIconButton(
            // 图标全程不撤：动画后段粒子已经散得差不多，撤掉图标会留下半秒空窗，
            // 看着像按钮没响应；叠在一起反而像「星星被点亮」。
            icon = if (isFavorite) MikomiIcons.Star else MikomiIcons.StarBorder,
            contentDescription = stringResource(contentDescriptionRes),
            onClick = onClick,
            size = FAVORITE_BUTTON_SIZE,
            iconSize = 26.dp,
            containerColor = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            // 星标固定金色：空心的描边和点满的填充是同一个色，只换字形不换颜色，
            // 这样「点没点」只看形状，不会被两套配色搅乱。色值取自设计系统的评分金 ——
            // 收藏星与评分星同色，否则同一屏里两个「金」会显得其中一个发脏。
            contentColor = MikomiTheme.colors.rating,
        )
        // 动画是按钮的兄弟节点：按钮被 clip 成圆形，星级要能飞出这个圆才好看。
        if (bursting && loaded != null) {
            LottieAnimation(
                composition = loaded,
                progress = { progress.value },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * 收藏反应动画的加载描述。
 *
 * 放在顶层：`LottieCompositionSpec.RawRes` 是普通 class（不是 data class），
 * 每次新建都会让 `rememberLottieComposition` 的 key 变化、重新走一遍加载。
 */
private val StarCollectSpec = LottieCompositionSpec.RawRes(R.raw.star_collect)

/** 普通内容 Tab 的滚动容器（概述 / 主创等非懒加载内容）。 */
@Composable
private fun ScrollableTabPane(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = CONTENT_BOTTOM_SPACER),
    ) {
        content()
    }
}

/**
 * Tab 栏。
 *
 * 它叠在封面上（不是内容区的第一条），所以底色用半透明的 surface：模糊封面的颜色会透一点上来，
 * 观感上是「封面下沿的一条玻璃条」，而不是把 banner 切两刀的实心块。
 * 透明度是常量的 —— 收缩过程中不换色，只有位置在动，因此这条栏在滚动里没有任何重组。
 */
@Composable
private fun DetailTabBar(
    selectedTab: DetailTabType,
    onSelect: (DetailTabType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = TAB_BAR_ALPHA),
    ) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            DetailTabType.entries.forEachIndexed { index, tab ->
                val selected = selectedTab == tab
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = MikomiMotion.Duration.Short),
                    label = "tabTextColor",
                )
                Tab(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = textColor,
                        )
                    },
                )
            }
        }
    }
}

/**
 * 把状态栏图标切成浅色，离开详情页时还原。
 *
 * 头部封面画到了屏幕最顶端，状态栏区域没有任何遮罩 —— 图标是直接压在模糊封面上的。
 * 日间主题默认给深色图标，压在深底封面上几乎看不见，所以这一页要临时翻成浅色。
 *
 * 用**快照还原**而不是写死 `true`/`false`：进来时读到什么就还回什么，
 * 夜间主题本来就是浅色图标，写死深色反而会在返回后错一拍。
 * 与播放页同样的做法，各自负责自己那一页的窗口外观。
 */
@Composable
private fun ImmersiveStatusBar() {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    val window = activity.window
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val origin = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = origin }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 详情页三个 Tab。 */
internal enum class DetailTabType(
    val labelRes: Int,
) {
    OVERVIEW(com.liomix.mikomi.feature.detail.R.string.detail_tab_overview),
    CREDITS(com.liomix.mikomi.feature.detail.R.string.detail_tab_credits),
    TEASING(com.liomix.mikomi.feature.detail.R.string.detail_tab_teasing),
}
