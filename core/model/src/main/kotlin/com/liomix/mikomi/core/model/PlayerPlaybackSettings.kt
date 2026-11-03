package com.liomix.mikomi.core.model

/**
 * 播放器的手势与倍速偏好。
 *
 * 放在 `core:model` 而不是任一个 feature 里：设置页要画这三组选项，播放页要按它们行事，
 * 而两个 feature 之间不许互相依赖。选项清单也放在这里，两边的档位就不可能对不上。
 */
data class PlayerPlaybackSettings(
    /** 每集开播时的倍速，1f 为原速。 */
    val defaultSpeed: Float = 1f,
    /** 长按画面的临时倍速，松手恢复。 */
    val longPressSpeed: Float = 2f,
    /** 左右滑动：手指横移**整屏宽度**对应的快进 / 快退秒数。 */
    val seekSweepSeconds: Int = 90,
) {
    companion object {
        /** 默认倍速的可选档位：与播放器底栏的倍速胶囊同一套值。 */
        val SpeedOptions: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

        /** 长按倍速的可选档位。 */
        val LongPressOptions: List<Float> = listOf(1.5f, 2f, 3f)

        /** 左右滑动幅度的可选档位（秒 / 整屏宽度）。 */
        val SeekSweepOptions: List<Int> = listOf(15, 30, 60, 90)
    }
}

/** 偏好表 → 播放器设置。字段名不同（持久化用的是 `player*` 前缀），转换只在这里做一次。 */
fun UserPreferences.toPlayerPlaybackSettings(): PlayerPlaybackSettings = PlayerPlaybackSettings(
    defaultSpeed = playerDefaultSpeed,
    longPressSpeed = playerLongPressSpeed,
    seekSweepSeconds = playerSeekSweepSeconds,
)
