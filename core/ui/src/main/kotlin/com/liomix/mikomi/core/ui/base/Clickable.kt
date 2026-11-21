package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/*
 * 可点元素的分层（规范 §5.10 / §3.8）：
 *
 *   设计系统  MikomiMotion.Press（缩多少 / 多快 / 过冲多少）
 *      ↓
 *   原子层    Modifier.mikomiClickable（这个文件）、Modifier.pressScale（Button.kt）
 *      ↓
 *   页面      .mikomiClickable(onClick = …)，不写 interactionSource / indication / role
 *
 * **全站不做按压层**（2026-11-22 决定）：按下时只有整块缩放，没有从指尖扩散的高亮。
 * 那套中性墨色 ripple 压在深色封面上几乎看不见，压在浅色面上也只是「灰一下」，
 * 不如把反馈交给形变——一个动作只表达一次，比两个弱信号叠在一起更清楚。
 *
 * 既然水波没有了，`clickable` 只需要三件事：`indication = null`、`role` 默认 Button、
 * 以及可选的 `interactionSource`（同一手势要驱动多处动画时外部传入）。
 * 这三件事每处手写会漏（漏 `role` 读屏就念成「可点区域」，漏 `indication = null`
 * 就退回 Compose 默认水波），所以收成这一个入口。
 */

/**
 * 带统一语义的点击：全站可点元素的唯一入口。
 *
 * 与直接写 `Modifier.clickable` 的区别：按压层固定为 `null`（全站无水波）、
 * 角色默认 [Role.Button]、`interactionSource` 默认由 Compose 自建。
 *
 * 需要「按下时缩小」的地方，再叠一个 `pressScale(interactionSource)`：缩放与点击是两件事，
 * 有的元素两者都要（按钮、卡片），有的只要点击（列表行）。
 *
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .clip(shape)
 *         .mikomiClickable(onClick = onClick),
 * )
 * ```
 *
 * @param enabled 置灰并吞掉点击。
 * @param role 无障碍角色。默认 [Role.Button]；选择类控件（单选/复选）传对应的角色。
 * @param interactionSource 外部已有按压态来源时传入（例如同一手势要驱动缩放）。
 * @param onClick 点击回调。**放在最后**，调用点就能写尾随 lambda：
 *   `.mikomiClickable(enabled = x) { … }`，与 `Modifier.clickable` 的写法一致。
 */
@Composable
fun Modifier.mikomiClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    role = role,
    onClick = onClick,
)
