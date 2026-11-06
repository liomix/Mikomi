package com.liomix.mikomi.feature.home.rank

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.designsystem.theme.MikomiShadowColor
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.ui.component.base.ScrollingText
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.image.MikomiAsyncImage

/**
 * 排名卡片：封面铺底 + 名次 + 标题。
 *
 * 卡片是「横条」（宽高比 2.4:1），而封面几乎都是 2:3 竖图：铺满后纵向只剩约
 * 28% 可见。所以裁切位置比比例更关键——把锚点抬到上部（[CoverCropAlignment]），
 * 保住人物头部而不是切到腰身。
 *
 * 卡片只由「封面 + 文字」构成，不铺底色、不叠任何遮罩：多铺一层兜底色，
 * 图片没就绪时会先闪出一块深灰，看起来像卡片坏了。加载态交给列表骨架屏
 * （`RankCardSkeletonList`），卡片本身保持空白等图。文字靠
 * [RankTitleShadow] / [RankNumberShadow] 的深色描影压在画面上。
 *
 * 缺封面（数据里没有图）的条目也按「还没加载出来」处理：整条退化成同尺寸骨架，
 * 不留一张空白卡——亮字压在页面背景上等于什么都没有，看起来更像坏了。
 */
@Composable
internal fun RankAnimeCard(
    entry: RankEntry,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    val isTopRank = rank <= TopRankHighlight
    val coverUrl = anime.coverUrl

    if (coverUrl.isNullOrBlank()) {
        SkeletonBox(
            modifier = modifier
                .fillMaxWidth()
                .height(RankCardHeight),
            shape = RoundedCornerShape(RankCardCorner),
        )
        return
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RankCardCorner),
        // 卡片本身不铺底色：有封面时是整张封面，无封面时留空交给页面骨架屏。
        color = Color.Transparent,
        contentColor = MikomiTheme.colors.media.content,
        border = BorderStroke(
            width = MikomiStroke.Thin,
            // 前三名用描边色勾一圈：压在封面上时金属色本身就够亮，描边只做收边。
            color = rankOutline(rank)?.copy(alpha = 0.55f) ?: MikomiTheme.colors.media.outline,
        ),
        shadowElevation = if (isTopRank) TopRankShadow else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(RankCardHeight),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 横条满铺：竖图裁成宽幅时锚定上部（人物头部多在上 1/3），不做模糊。
            CoverBackdrop(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = RowStartPadding, end = RowEndPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RankNumber(
                    rank = rank,
                    modifier = Modifier.width(RankNumberWidth),
                )

                Spacer(modifier = Modifier.width(NumberToTitleGap))

                // 关闭手动拖动，避免标题抢占 HorizontalPager 的左右切页手势；
                // 网格内多卡同时跑 marquee 会拖慢滚动，走静态省略。
                ScrollingText(
                    text = anime.title,
                    enabled = false,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MikomiTheme.colors.media.content,
                        shadow = RankTitleShadow,
                    ),
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false,
                )
            }
        }
    }
}

/**
 * 名次数字。
 *
 * 前三名用金 / 银 / 铜这类高亮金属色：压在封面上够醒目，但遇到浅色封面
 * （天空、雪地）会糊进背景，所以给它们垫一层同字形的深色描边
 * （见 [RankNumberOutline]），靠轮廓把字形勾出来。第 4 名起用亮灰，
 * 靠 [RankNumberShadow] 的深色描影即可。
 */
@Composable
private fun RankNumber(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val accent = rankAccent(rank)
    val outlineColor = rankOutline(rank)
    val outlineStyle = rememberRankNumberOutline()
    // 字号阶梯（42 / 34 / 26）住在设计系统里：搜索页的排名圆标用的是同一套，
    // 两处各写一份就会出现「同一个第 12 名，两个页面大小不同」。
    val style = MikomiTextStyles.rankNumber(rank).copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (outlineColor != null) {
            Text(
                text = rank.toString(),
                modifier = Modifier.fillMaxWidth(),
                color = outlineColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = style.copy(drawStyle = outlineStyle),
            )
        }
        Text(
            text = rank.toString(),
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // 有描边时不再叠投影：两层边缘会互相弄脏。
            style = if (outlineColor == null) {
                style.copy(shadow = RankNumberShadow)
            } else {
                style
            },
        )
    }
}

/**
 * 封面底图：整块铺满并按 [CoverCropAlignment] 裁切，不做任何模糊。
 *
 * 只画图、不画占位：图片未就绪时这一层为空，露出页面背景；
 * 卡片级的加载态由列表的骨架屏（`RankCardSkeletonList`）负责。
 * 入参是必填的 url —— 空封面的条目在 [RankAnimeCard] 入口就被拦成骨架了。
 */
@Composable
private fun CoverBackdrop(
    url: String,
    modifier: Modifier = Modifier,
) {
    MikomiAsyncImage(
        url = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = CoverCropAlignment,
        crossfade = true,
        modifier = modifier,
    )
}

/** 前三名对应的金 / 银 / 铜高亮色，其余统一冷灰。 */
/**
 * 名次填色：前三名取设计系统的金属色（金 / 银 / 铜），第 4 名起用亮灰。
 *
 * 金属色分 fill / outline / ink 三档，这里是 `fill`（压在封面上的那一档）：
 * 银色因此是偏冷的浅金属灰而不是近白 —— 近白压在浅色封面（天空、雪地）上几乎消失。
 */
@Composable
private fun rankAccent(rank: Int): Color =
    MikomiTheme.colors.rank(rank)?.fill ?: MikomiTheme.colors.media.content

/** 前三名对应的描边色；第 4 名起无描边（返回 null）。 */
@Composable
private fun rankOutline(rank: Int): Color? = MikomiTheme.colors.rank(rank)?.outline

/**
 * 标题文字的深色描影。
 *
 * 卡片上不再铺提亮遮罩，标题直接压在封面上：亮底（草地、天空）会把亮字吃掉，
 * 所以垫一层黑色描影勾出轮廓（与名次数字同一套做法），比加一层白雾干净。
 */
private val RankTitleShadow = Shadow(
    color = MikomiShadowColor.copy(alpha = 0.8f),
    offset = Offset(0f, 1.5f),
    blurRadius = 8f,
)

/** 名次文字的深色描影：亮底封面（草地、天空）上靠它勾住轮廓。 */
private val RankNumberShadow = Shadow(
    color = MikomiShadowColor.copy(alpha = 0.65f),
    offset = Offset(0f, 2f),
    blurRadius = 9f,
)

/** 卡片高度：固定 112dp 的横条，一屏能看到约 5 张，不与封面比例挂钩。 */
internal val RankCardHeight = 112.dp

/** 卡片圆角。 */
internal val RankCardCorner = 14.dp

/** 卡片间距。 */
internal val RankCardSpacing = 8.dp

/**
 * 竖图裁成横条时的锚点。
 *
 * 2:3 海报在 2.4:1 的窗口里纵向只能看到约 28%，切中间大概率落在腰身或空白处；
 * 人物头部几乎都在上 1/3，所以把锚点抬高（-0.45 ≈ 靠近顶部、略向下留一点余量）。
 */
private val CoverCropAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.45f)

/** 左侧名次区域的固定宽度，保证个位数与两位数都居中对齐。 */
private val RankNumberWidth = 54.dp

/** 名次与标题之间的间距。 */
private val NumberToTitleGap = 10.dp

/** 行左右内边距。 */
private val RowStartPadding = 12.dp
private val RowEndPadding = 18.dp

/** 前三名的高亮阴影。 */
private val TopRankShadow = 4.dp

/** 前三名高亮名次的最大名次。 */
private const val TopRankHighlight = 3

/** 描边宽度：细到只勾轮廓，粗了会把金属色挤没。 */
private val RankNumberOutlineWidth = 1.2.dp

/** 描边笔：圆角接头，避免斜体衬线的尖角处出现毛刺。宽度需按屏幕密度换算。 */
@Composable
private fun rememberRankNumberOutline(): Stroke {
    val density = LocalDensity.current
    return remember(density) {
        Stroke(
            width = with(density) { RankNumberOutlineWidth.toPx() },
            join = StrokeJoin.Round,
        )
    }
}
