package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/*
 * 可点元素的分层（规范 §5.10 / §3.8）：
 *
 *   设计系统  Motion.Press（缩多少 / 多快 / 过冲多少）
 *      ↓
 *   原子层    Modifier.mikomiClickable（这个文件）—— 所有可点元素的唯一入口
 *      ↓
 *   页面      .mikomiClickable(onClick = …)，不写 interactionSource / indication / role
 *
 * `Modifier.pressScale` **不在这一层**：它只是 `PressButton` / `PressIconButton`
 * 的实现细节，见下方说明。
 *
 * **全站不做按压层**（2026-11-22 决定）：没有从指尖扩散的高亮。那套中性墨色 ripple
 * 压在深色封面上几乎看不见，压在浅色面上也只是「灰一下」。按钮用整块缩放代替它；
 * 其余可点元素（列表行、卡片、横幅）只保留各自的状态变化，不加通用按压反馈。
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
 * **缩放是按钮独有的，不是公共原子。** `pressScale` 只在 `PressButton` /
 * `PressIconButton` 内部使用，不对外充当通用能力：别的组件或页面确实需要按下缩放时，
 * 在**自己模块的组件里**写一份，不要 import 按钮的实现。
 *
 * 理由不是「复用不好」，而是**手感的所有权**：一旦按钮的缩放变成公共 API，各处就会按
 * 自己的场景改参数（横幅想缩得轻一点、动作卡想缩得慢一点），最后按钮自己反而不敢动；
 * 而「按下去的节奏必须全站一致」只对同一类控件成立，对大面积图片本来就不成立。
 * 宁可多写十几行，也不要让按钮的实现变成全站的事实标准。
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
