package com.liomix.mikomi.core.designsystem.theme

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 系统是否允许播放动画。
 *
 * `ANIMATOR_DURATION_SCALE = 0` 时（开发者选项的「动画时长 0」、无障碍的「移除动画」、
 * 部分省电模式），系统会把每一帧直接算到终点：动画**仍然会跑**，只是没有过程。
 * 这时候任何「为动画做准备」的开销都是纯浪费 —— 抓窗口快照、建遮罩、跑帧循环，
 * 用户一帧都看不到。
 *
 * 所以所有自绘动效在启动前都问一句：夜间切换的圆形揭示用它决定要不要抓帧；
 * 页面转场用它决定是否直接落位。
 *
 * API 26 起有直接答案（`ValueAnimator.areAnimatorsEnabled`），更早的系统只能读那个
 * 设置项（minSdk 24）。读不到一律当作「允许」，宁可多播一次动画，也不要误判成禁用。
 */
fun Context.animationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
