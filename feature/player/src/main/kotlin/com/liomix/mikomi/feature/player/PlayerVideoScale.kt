package com.liomix.mikomi.feature.player

import androidx.annotation.StringRes

/**
 * 画面填充方式。
 *
 * 三种语义与 `Media3` 的 `AspectRatioFrameLayout.RESIZE_MODE_*` 一一对应，
 * 接播放器时直接按 [entries] 的序号映射即可（`AUTO → FIT`、`CROP → ZOOM`、`STRETCH → FILL`）。
 *
 * 它是**视图状态**，不进 [PlayerUiState]：与业务无关，只影响画面怎么铺满这一块区域。
 */
enum class PlayerVideoScale(@param:StringRes val labelRes: Int) {
    /** 等比铺满，多余留黑边（默认）。 */
    AUTO(R.string.player_scale_auto),

    /** 等比放大到填满，超出部分裁掉。 */
    CROP(R.string.player_scale_crop),

    /** 不保持宽高比，直接拉伸铺满。 */
    STRETCH(R.string.player_scale_stretch),
}
