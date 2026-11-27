package com.liomix.mikomi.feature.home.rank

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.RankEntry
import com.liomix.mikomi.core.designsystem.TextStyles
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.FrostedText
import com.liomix.mikomi.core.ui.base.Number
import com.liomix.mikomi.core.ui.business.AnimeCoverImage
import androidx.compose.ui.draw.clip
import com.liomix.mikomi.core.ui.base.mikomiClickable

/**
 * 排名卡片：封面铺底 + 名次 + 标题。
 *
 * 卡片是「横条」（宽高比 2.4:1），而封面几乎都是 2:3 竖图：铺满后纵向只剩约
 * 28% 可见。所以裁切位置比比例更关键——把锚点抬到上部（[CoverCropAlignment]），
 * 保住人物头部而不是切到腰身。
 *
 * 卡片只由「封面 + 文字」构成，不铺底色、不描边、不叠任何遮罩：多铺一层兜底色，
 * 图片没就绪时会先闪出一块深灰，看起来像卡片坏了。加载态交给列表骨架屏
 * （`RankCardSkeletonList`），卡片本身保持空白等图。文字靠
 * [RankTitleShadow] 的深色描影压在画面上。
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

    val cardShape = RoundedCornerShape(RankCardCorner)
    Surface(
        shape = cardShape,
        // 卡片本身不铺底色：有封面时是整张封面，无封面时留空交给页面骨架屏。
        color = Color.Transparent,
        contentColor = AppTheme.colors.media.content,
        // 不描边、不加阴影。卡片的边界由封面自己交代：圆角裁切 + 画面与页面的亮暗差。
        //
        // 依次去掉的这两样都是「与内容无关的线」：第 4 名起那圈是 `Ranks.other` 的灰，
        // 前三名那圈是 4dp `shadowElevation` 在透明底上拖出的晕 —— 两者都在「骨架换成
        // 卡片」那一刻突然出现，读起来就是「卡片四周冒出一圈灰」。名次本身已经是金属色，
        // 不需要再靠外框提醒「这是前三」。
        modifier = modifier
            .fillMaxWidth()
            .height(RankCardHeight)
            .clip(cardShape)
            .mikomiClickable(onClick = onClick),
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
                Number(
                    rank = rank,
                    color = AppTheme.colors.rank(rank).fill,
                    // 固定宽度：两位数与一位数占同一格，标题的起点才不会左右跳。
                    modifier = Modifier.width(RankNumberWidth),
                )

                Spacer(modifier = Modifier.width(NumberToTitleGap))

                // 毛玻璃标题：亮字 + 描影，压在封面上读得出来。
                // 不跑马灯 —— 网格内多卡同时滚动会拖慢帧率，也避免抢走 pager 的横向手势。
                FrostedText(
                    text = anime.title,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 封面底图：整块铺满并按 [CoverCropAlignment] 裁切，不做任何模糊。
 *
 * 走统一的 [AnimeCoverImage]（`aspectRatio = null`：横条尺寸由这张卡自己给），
 * 于是加载中也是**骨架同款扫光**。此前这里直接调 [AsyncImage]，占位是静止的骨架色 ——
 * 榜单数据到手、封面还在下载的那一两秒，整屏卡片看起来就是空的。
 * 入参是必填的 url —— 空封面的条目在 [RankAnimeCard] 入口就被拦成骨架了。
 */
@Composable
private fun CoverBackdrop(
    url: String,
    modifier: Modifier = Modifier,
) {
    AnimeCoverImage(
        url = url,
        contentDescription = null,
        modifier = modifier,
        // **不能开淡入**。`AnimeCoverImage` 是在 `onSuccess` 那一刻撤掉扫光的，而 crossfade
        // 会让图片从 alpha 0 开始——「扫光没了、图还没到」的那几帧露出的是静止的骨架底色，
        // 也就是首屏卡片上那一下灰。关掉之后图片与撤扫光同帧落地，骨架的材质由封面直接接住。
        //
        // 这也和全站口径一致：懒列表里的图一律 `crossfade = false`。
        crossfade = false,
        aspectRatio = null,
        cropAlignment = CoverCropAlignment,
        // 与卡片同一个圆角（默认的 10dp 比卡片小 4dp）：差这一档时，亮色封面的四角
        // 会从卡片裁剪里露出几条细边，看着像卡片没铺满。
        cornerRadius = RankCardCorner,
    )
}

/** 卡片高度：固定 112dp 的横条，一屏能看到约 5 张，不与封面比例挂钩。 */
internal val RankCardHeight = 112.dp

/** 卡片圆角。 */
internal val RankCardCorner = 14.dp

/** 卡片间距。 */
internal val RankCardSpacing = Spacing.Sm

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
private val NumberToTitleGap = Spacing.Compact

/** 行左右内边距。 */
private val RowStartPadding = Spacing.Md
private val RowEndPadding = 18.dp // design-ok: 非梯队精确值，排名行右侧与封面视觉等距
