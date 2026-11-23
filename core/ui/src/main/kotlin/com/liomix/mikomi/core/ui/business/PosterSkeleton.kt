package com.liomix.mikomi.core.ui.business

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.SkeletonBox

/** 标题条占宽的多少。真实标题是整行文本（可能省略），留一点右侧呼吸更像一行的样子。 */
private const val TitleWidthFraction = 0.7f

/**
 * 海报卡骨架：与 [AnimePosterCard] **几何同构**（2:3 封面块 + 单行标题条）。
 *
 * 首屏骨架与翻页骨架共用它，保证「加载中」的样子在哪一段都一致。
 *
 * 「同构」不是写得像，而是**引用同一组值**：封面圆角取 [Radius.Md]（与 `AnimeCoverImage`
 * 同一个 token）、骨架到标题的间距取 [Spacing.Tight]（与卡片同一个 token）、标题条高度
 * 取自真实标题的字阶 `labelMedium.lineHeight`。任何一处另写一个数，数据到货那一刻网格的
 * 行高就会变一次 —— 用户读到的是一次轻微上窜，排查时却很难联想到骨架。
 *
 * 它只负责**一格**：排几格、补在哪一行由调用方按列数决定（见 [PosterGrid]）。
 * 以前这里还有一个「一整行骨架」的组件，靠 `BoxWithConstraints` 自己反推列数 ——
 * 它和真实卡片的最后一行之间必然漏出几个空格子，那一排空洞就是「页面破了」的来源。
 */
@Composable
fun PosterCardSkeleton(
    modifier: Modifier = Modifier,
) {
    // 字阶 → dp：`lineHeight` 是 TextUnit，要按当前密度换算。字阶一调，骨架自动跟上。
    val titleHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    }
    Column(modifier = modifier) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(Radius.Md),
        )
        Spacer(modifier = Modifier.height(Spacing.Tight))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(TitleWidthFraction)
                .height(titleHeight),
            // 文字行没有直角，也不该是一枚小标签：pill 读起来就是「这里会有一行字」。
            shape = PillShape,
        )
    }
}
