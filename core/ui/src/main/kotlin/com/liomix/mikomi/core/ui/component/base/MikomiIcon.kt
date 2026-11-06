package com.liomix.mikomi.core.ui.component.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes

/**
 * 全站唯一的图标入口。
 *
 * 存在的理由只有一个：**让「图标默认多大」有负责人**。
 *
 * Material3 的 [Icon] 里有个 `defaultSizeFor(painter)`：painter 的 intrinsicSize 有限时
 * 它**不加**默认尺寸。我们的图标是 `ImageVector.Builder(defaultWidth = 24.dp)`，
 * 每个都带着 24dp 的固有尺寸，所以「不写 size 也正好 24」—— 那不是 token 在管，
 * 是图形自己带来的。结果就是：改 `MikomiSizes.IconMd`，一句 size 都没写的调用点
 * 一个都不会动。
 *
 * 这里把默认值改由 [MikomiSizes.IconMd] 提供，参数名 [size] 也顺手把「尺寸」从
 * `Modifier` 链里挪出来：调用点不必再记 `Modifier.size(...)` 的写法，也不会因为
 * 在链条中间插入别的 modifier（`rotate` / `padding`）而与尺寸互相干扰。
 *
 * 三种尺寸刚好对应三档（§5.3）：[MikomiSizes.IconXs] 文字行内、[MikomiSizes.IconSm]
 * 次级动作、[MikomiSizes.IconMd] 标准。大于 24 的是单个组件的物理尺寸，不走这里，
 * 在组件里写 `private val`。
 *
 * @param icon 语义图标，取自 [com.liomix.mikomi.core.designsystem.icon.MikomiIcons]。
 * @param contentDescription 无障碍描述；纯装饰（旁边已有文字）传 `null`。
 * @param size 显示尺寸，默认 [MikomiSizes.IconMd]。
 */
@Composable
fun MikomiIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = MikomiSizes.IconMd,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
