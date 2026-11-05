package com.liomix.mikomi.feature.settings.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.model.PlayerPlaybackSettings
import com.liomix.mikomi.core.ui.component.base.Tag
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import java.util.Locale

/**
 * 播放设置页（无状态）。
 *
 * 三段设置都是「一组里单选一个」，所以统一用标签：[Tag] 的选中态是主色淡底 +
 * 主色描边，比下拉菜单更适合这种一眼就能看完全部档位的场景——选之前就知道还有哪些可选。
 *
 * 每一项下面都写一句这个设置**具体改变什么**（不是复述标题）：
 * 「左右滑动幅度」如果只写「30 秒」，没人知道那是「滑一整屏跳 30 秒」。
 */
@Composable
fun PlaybackSettingsScreen(
    settings: PlayerPlaybackSettings,
    onSelectDefaultSpeed: (Float) -> Unit,
    onSelectLongPressSpeed: (Float) -> Unit,
    onSelectSeekSweepSeconds: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseScreenLayout(
        title = stringResource(R.string.settings_playback_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        SettingsCard {
            SettingsSection(
                title = stringResource(R.string.settings_playback_default_speed),
                description = stringResource(R.string.settings_playback_default_speed_desc),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MikomiSpacing.Room),
                    horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Compact),
                ) {
                    items(
                        items = PlayerPlaybackSettings.SpeedOptions,
                        key = { speed -> speed.toString() },
                        contentType = { "speed" },
                    ) { speed ->
                        Tag(
                            text = stringResource(
                                R.string.settings_playback_speed_value,
                                formatValue(speed),
                            ),
                            selected = speed == settings.defaultSpeed,
                            onClick = { onSelectDefaultSpeed(speed) },
                        )
                    }
                }
            }
        }

        SettingsCard {
            SettingsSection(
                title = stringResource(R.string.settings_playback_long_press),
                description = stringResource(R.string.settings_playback_long_press_desc),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MikomiSpacing.Room),
                    horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Compact),
                ) {
                    items(
                        items = PlayerPlaybackSettings.LongPressOptions,
                        key = { speed -> "long$speed" },
                        contentType = { "long_press" },
                    ) { speed ->
                        Tag(
                            text = stringResource(
                                R.string.settings_playback_speed_value,
                                formatValue(speed),
                            ),
                            selected = speed == settings.longPressSpeed,
                            onClick = { onSelectLongPressSpeed(speed) },
                        )
                    }
                }
            }
        }

        SettingsCard {
            SettingsSection(
                title = stringResource(R.string.settings_playback_seek_sweep),
                description = stringResource(R.string.settings_playback_seek_sweep_desc),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MikomiSpacing.Room),
                    horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Compact),
                ) {
                    items(
                        items = PlayerPlaybackSettings.SeekSweepOptions,
                        key = { seconds -> "sweep$seconds" },
                        contentType = { "seek_sweep" },
                    ) { seconds ->
                        Tag(
                            text = stringResource(R.string.settings_playback_seconds, seconds),
                            selected = seconds == settings.seekSweepSeconds,
                            onClick = { onSelectSeekSweepSeconds(seconds) },
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_playback_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MikomiSpacing.Sm, vertical = MikomiSpacing.Xs),
        )
    }
}

/** 一设置一节：标题 + 一句说明 + 选项行。 */
@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    SettingsCardSection {
        Column(modifier = Modifier.padding(top = MikomiSpacing.Room, bottom = MikomiSpacing.Room)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = MikomiSpacing.Room),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = MikomiSpacing.Room,
                    end = MikomiSpacing.Room,
                    top = MikomiSpacing.Xs,
                    bottom = MikomiSpacing.Gutter,
                ),
            )
            content()
        }
    }
}

/** 倍速读数：整数档只留一位小数（`1.0x`），其余保留两位（`0.75x`）。 */
private fun formatValue(speed: Float): String {
    val decimals = if ((speed * 100f).toInt() % 10 == 0) 1 else 2
    return String.format(Locale.ROOT, "%.${decimals}f", speed)
}
