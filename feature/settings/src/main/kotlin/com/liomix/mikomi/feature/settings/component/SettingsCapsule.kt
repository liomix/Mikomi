package com.liomix.mikomi.feature.settings.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 胶囊高度，同时就是它的触控高度（见下方说明）。 */
private val CapsuleHeight = 40.dp

/** 文字左右内边距。 */
private val CapsuleHorizontalPadding = 18.dp

/** 选中态 / 未选中态切换时的颜色过渡时长。 */
private const val ColorAnimMillis = 180

/** 选中底色透明度：浅色染主色，避免实心块视觉重量过重（看起来像主按钮）。 */
private const val ContainerAlphaSelected = 0.14f

/** 选中描边透明度。 */
private const val BorderAlphaSelected = 0.55f

/** 未选中描边透明度：只勾出边界，不抢文字。 */
private const val BorderAlphaUnselected = 0.7f

/**
 * 设置页的选项胶囊（一组里单选一个）。
 *
 * 视觉：未选中为透明底 + 中性细描边；选中为主色淡底 + 主色描边 + 主色半粗文字。
 * 颜色变化带 180ms 过渡，切换时不会硬跳。
 *
 * **触控范围 = 视觉尺寸（[CapsuleHeight]，40dp）**：按规范本应外包 48dp 触控区
 * （rule §5.3），此处由产品侧明确破例——胶囊成组横排时 48dp 的点击区会互相挤压，
 * 且 48dp 的容器让胶囊本身显得笨重。
 * 因此：只用于这类成组、可反复切换的轻量选项，不要用它承载关键的独立操作。
 *
 * @param text 选项文案（由调用方 `stringResource` 解析后传入，组件层不依赖业务资源）。
 * @param selected 是否处于选中态。
 * @param onClick 点击回调（语义按单选按钮上报）。
 */
@Composable
fun SettingsCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            colors.primary.copy(alpha = ContainerAlphaSelected)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "capsuleContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "capsuleContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            colors.primary.copy(alpha = BorderAlphaSelected)
        } else {
            colors.outlineVariant.copy(alpha = BorderAlphaUnselected)
        },
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "capsuleBorder",
    )

    // 不用 Surface 的可点击重载：它会套一层 minimumInteractiveComponentSize（48dp），
    // 与「触控范围等于视觉尺寸」的破例要求冲突；这里自己组合 selectable + 裁剪。
    Surface(
        modifier = modifier
            .heightIn(min = CapsuleHeight)
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = CapsuleHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
