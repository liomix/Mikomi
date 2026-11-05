package com.liomix.mikomi.feature.settings.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing

/** 设置卡片圆角。 */
private val CardCorner = 24.dp

/** 卡片描边宽度（极淡，只用来勾出边界）。 */
private val CardBorderWidth = 1.dp

/** 子卡片左右内边距（与标题、内容左缘对齐）。 */
private val SectionHorizontalPadding = 20.dp

/**
 * 设置页的大卡片。
 *
 * 容器自身不留内边距，由内部各 [SettingsCardSection] 自带 padding：
 * 首尾子项因此会一直贴到卡片边缘，其背景与点击涟漪由 Surface 的 clip
 * 统一裁成卡片圆角，内外圆角自然重合。
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        // 极淡描边：日间白卡落在浅灰底上、夜间 #181818 落在纯黑上，
        // 边界都更利落，又不会像实线框那样抢眼。
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * 卡片标题。
 *
 * 放在卡片内部顶缘，而不是卡片外面另起一段：它与下方各行的左缘（[SectionHorizontalPadding]）
 * 严格对齐，分组关系一眼可见，也不用再多占一段垂直留白。
 */
@Composable
fun SettingsCardHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(
            start = SectionHorizontalPadding,
            end = SectionHorizontalPadding,
            top = MikomiSpacing.Room,
            bottom = MikomiSpacing.Md,
        ),
    )
}

/**
 * 卡片内的子卡片（分节）。
 *
 * @param dividerAbove 是否在顶部画一条与上一节的分隔线。
 */
@Composable
fun SettingsCardSection(
    modifier: Modifier = Modifier,
    dividerAbove: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (dividerAbove) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = SectionHorizontalPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        content()
    }
}
