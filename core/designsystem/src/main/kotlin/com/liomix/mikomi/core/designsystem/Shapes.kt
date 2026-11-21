package com.liomix.mikomi.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes as MaterialShapes
import androidx.compose.ui.graphics.Shape

/**
 * 胶囊形状：按百分比取 50%，所以任何高度下都是完整的半圆。
 *
 * 不能用 `RoundedCornerShape(50.dp)` 代替：那是「50dp 的圆角」，落在 40dp 高的
 * 标签上要靠渲染层截断才勉强像胶囊，一旦高度超过 100dp 就会变成圆角矩形。
 * 百分比形式与高度解耦，是多少高就圆到什么程度。
 */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/**
 * 形状 token：把 [Radius] 接到 Material 的五个形状槽位上。
 *
 * 两者是同一套数值的两副面孔：
 * - 组合里的控件（Card、Button、Surface）用 `MaterialTheme.shapes.large` —— 形状跟着主题走；
 * - 非组合的规格对象（`PressButtonStyle.Filled` 这类 `val`）用 [Radius.Xl] ——
 *   它们在类初始化时就要拿到 Shape，读不到主题。
 *
 * 所以改了 [Radius] 就等于改了这里，**不要在别处再写一份圆角常量**。
 */
val Shapes: MaterialShapes = MaterialShapes(
    /** 4dp：贴边的小块（进度条、极小的角标）。 */
    extraSmall = RoundedCornerShape(Radius.Xs),
    /** 10dp：骨架块、列表封面、图片。 */
    small = RoundedCornerShape(Radius.Md),
    /** 12dp：中等卡片、输入框。 */
    medium = RoundedCornerShape(Radius.Lg),
    /** 16dp：悬浮按钮、大卡片。 */
    large = RoundedCornerShape(Radius.Xl),
    /** 28dp：底部抽屉、全屏面板。 */
    extraLarge = RoundedCornerShape(Radius.Sheet),
)
