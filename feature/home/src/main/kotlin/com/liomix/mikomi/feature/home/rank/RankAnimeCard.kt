package com.liomix.mikomi.feature.home.rank

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.ui.component.ProgressiveBlurBackdrop
import com.liomix.mikomi.core.ui.component.ScrollingText

/**
 * 排名卡片：模糊封面铺底 + 名次 + 标题（灰白卡片）。
 *
 * 卡片是「横条」（宽高比 2.4:1），而封面几乎都是 2:3 竖图：铺满后纵向只剩约
 * 28% 可见。所以裁切位置比比例更关键——把锚点抬到上部（[CoverCropAlignment]），
 * 保住人物头部而不是切到腰身。背景再做「左清晰 → 右模糊」的过渡，
 * 右侧模糊层始终有封面颜色延展，再向右洗进灰白底色，不出现黑尾。
 *
 * 卡片底色为灰白：封面在右半洗进浅色底，深色标题压在干净面上；
 * 遮罩与渐变都提到顶层，避免每张卡片重组时重复分配。
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

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RankCardCorner),
        color = RankCardBase,
        contentColor = RankCardContent,
        border = BorderStroke(
            width = 1.dp,
            // 前三名用描边色勾一圈：亮金属直接当边框在灰白底上几乎看不见。
            color = rankOutline(rank)?.copy(alpha = 0.55f) ?: RankBorderColor,
        ),
        shadowElevation = if (isTopRank) TopRankShadow else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(RankCardHeight),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 横条满铺：竖图裁成宽幅时锚定上部（人物头部多在上 1/3），
            // 左侧保留清晰画面、右侧过渡到模糊，右端始终有封面颜色，不会出现黑尾。
            ProgressiveBlurBackdrop(
                model = anime.coverUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadius = BackgroundBlurRadius,
                alignment = CoverCropAlignment,
                placeholder = { CoverFallbackBackdrop() },
            )

            // 右半洗进灰白底色，深色标题压在干净的浅面上。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RankCardScrim),
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

                // 关闭手动拖动，避免标题抢占 HorizontalPager 的左右切页手势。
                ScrollingText(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = RankCardContent,
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
 * 前三名用的是亮金属色（金 / 银 / 铜），这类颜色是为深色底设计的：压在灰白卡上
 * 对比度只有 1.0~2.4:1，直接填色等于看不见。所以给它们垫一层同字形的深色描边
 * （见 [RankNumberOutline]），靠轮廓把字形勾出来——颜色保持高亮，字还读得清。
 * 第 4 名起是中性灰，本身对比度够（4.3:1），不需要描边。
 */
@Composable
private fun RankNumber(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val accent = rankAccent(rank)
    val outlineColor = rankOutline(rank)
    val outlineStyle = rememberRankNumberOutline()
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontSize = if (rank >= DoubleDigitThreshold) DoubleDigitFontSize else SingleDigitFontSize,
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
 * 无封面时的底：灰白斜向渐变。
 *
 * 卡片底色是灰白，缺图时直接留空会显得「卡片根本没内容」，
 * 这里用左上更亮、右下沉一档的灰白渐变做出细微体积感，
 * 既不刺眼，也和「有封面」时的观感保持同一套明度。
 */
@Composable
private fun CoverFallbackBackdrop(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(RankCardBase)
            .background(RankCardFallbackGradient),
    )
}

/** 前三名对应的金 / 银 / 铜高亮色，其余统一冷灰。 */
private fun rankAccent(rank: Int): Color = when (rank) {
    1 -> RankGold
    2 -> RankSilver
    3 -> RankBronze
    else -> RankCoolGray
}

/** 前三名对应的描边色；第 4 名起无描边（返回 null）。 */
private fun rankOutline(rank: Int): Color? = when (rank) {
    1 -> RankGoldOutline
    2 -> RankSilverOutline
    3 -> RankBronzeOutline
    else -> null
}

/**
 * 卡片底色：灰白。
 *
 * 封面只占左侧，右侧要过渡到「干净的浅底」才能让深色标题站得住，
 * 所以底色取略带冷调的灰白，而不是纯白（纯白在深色页面上会发刺）。
 */
private val RankCardBase = Color(0xFFECEEF1)

/** 卡片正文色：压在灰白底上的深色文字。 */
private val RankCardContent = Color(0xFF1B1D20)

/** 缺图时的灰白斜向渐变（常量，避免每张卡片重组时重新分配）。 */
private val RankCardFallbackGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF6F7F9),
        Color(0xFFECEEF1),
        Color(0xFFDEE1E5),
    ),
)

/**
 * 卡片上的水平提亮遮罩：左侧几乎透明（露出封面与模糊层），向右逐步叠上灰白。
 *
 * 关键在**末档不封顶**（0.72 而不是 1.0）：完全盖住的话右端会变成一块平的色板，
 * 看起来像「后面没内容」；留 28% 的模糊纹理透出来，模糊画面就一路延续到卡片末尾。
 * 0.72 叠在任意封面上得到的亮度，都足够让 [RankCardContent] 达到 10:1 以上对比度。
 */
private val RankCardScrim = Brush.horizontalGradient(
    colorStops = arrayOf(
        // 最左留一层极淡的白纱：名次压在清晰封面上时也有个底，不至于糊在画面里。
        0f to RankCardBase.copy(alpha = 0.14f),
        // 中段几乎不遮，让封面的模糊纹理完整露出来。
        0.26f to RankCardBase.copy(alpha = 0.08f),
        0.55f to RankCardBase.copy(alpha = 0.44f),
        // 末尾止步于 0.72：底色被提亮到能压深色标题，但模糊纹理仍然透出来，
        // 不会变成一块平的色板（也不封死成纯灰白）。
        0.78f to RankCardBase.copy(alpha = 0.66f),
        1f to RankCardBase.copy(alpha = 0.72f),
    ),
)

/** 名次文字的浅色描影：压在封面或灰白底上都能勾住轮廓，但不抢眼。 */
private val RankNumberShadow = Shadow(
    color = Color.White.copy(alpha = 0.85f),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
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

/** 背景模糊半径：右侧糊成色块即可，不需要糊成一团。 */
private val BackgroundBlurRadius = 16.dp

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

/** 名次进入两位数的最小值（此时字号收一档）。 */
private const val DoubleDigitThreshold = 10

/** 个位数与两位数名次的字号。 */
private val SingleDigitFontSize = 42.sp
private val DoubleDigitFontSize = 34.sp

/**
 * 名次配色：高亮金属色（金 / 银 / 铜）。
 *
 * 这三个色是「深色底专用」的亮度：#FFD700 对纯黑 15:1，但压在灰白卡上只有 1.2:1；
 * 银色 #E8E8E8 更是接近白（1.05:1）。所以前三名必须配深色描边，见 [RankNumber]。
 */
private val RankGold = Color(0xFFFFD700)
private val RankSilver = Color(0xFFE8E8E8)
private val RankBronze = Color(0xFFD98719)

/** 第 4 名起的统一冷灰（本身对比度够，不描边）。 */
private val RankCoolGray = Color(0xFF66707D)

/** 前三名的描边色：各自取一枚比填色深很多的同色系，勾边不抢色。 */
private val RankGoldOutline = Color(0xFF4A3700)
private val RankSilverOutline = Color(0xFF39414B)
private val RankBronzeOutline = Color(0xFF4B2A00)

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

/** 非前三名卡片的细边框。 */
private val RankBorderColor = Color(0xFF1B1D20).copy(alpha = 0.10f)
