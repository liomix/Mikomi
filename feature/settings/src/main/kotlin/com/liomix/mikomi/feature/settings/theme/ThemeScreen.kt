package com.liomix.mikomi.feature.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.feature.settings.R

/**
 * 主题设置页。
 *
 * 每类设置独立成卡片；「主题颜色」卡片内包含两块同级别内容：
 * 顶部配色色卡、底部「切换到夜间模式」开关行（中间以分隔线区隔）。
 */
@Composable
fun ThemeScreen(
    colorSchemeId: String,
    isDarkMode: Boolean,
    onSelectColorScheme: (String) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ThemeTopBar(onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemeColorCard(
                selectedId = colorSchemeId,
                isDarkMode = isDarkMode,
                onSelect = onSelectColorScheme,
                onToggleDarkMode = onToggleDarkMode,
            )
        }
    }
}

@Composable
private fun ThemeTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.theme_back),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.theme_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 主题颜色卡片：配色色卡 + 夜间模式开关（两个同级别子项）。 */
@Composable
private fun ThemeColorCard(
    selectedId: String,
    isDarkMode: Boolean,
    onSelect: (String) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp)) {
            Text(
                text = stringResource(R.string.theme_color_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = ThemePalette.entries,
                    key = { palette -> palette.id },
                ) { palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = palette.id == selectedId,
                        onClick = { onSelect(palette.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            NightModeRow(
                isDarkMode = isDarkMode,
                onToggle = onToggleDarkMode,
            )
        }
    }
}

/** 夜间模式开关行：整行可点，右侧开关同步状态。 */
@Composable
private fun NightModeRow(
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle(!isDarkMode) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DarkMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.theme_night_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = isDarkMode,
            onCheckedChange = onToggle,
        )
    }
}

/**
 * 配色色卡：渐变底 + 两个胶囊形示意块。
 *
 * 选中时外圈描主色并在内外留出间隙（对齐参考版式的选中态）。
 */
@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = modifier
            .size(width = SWATCH_WIDTH, height = SWATCH_HEIGHT)
            .border(
                width = if (selected) SELECTED_BORDER_WIDTH else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(SELECTED_BORDER_WIDTH)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(palette.gradient))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            // 上方短胶囊。
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 下方长胶囊。
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.95f)),
            )
        }
    }
}

/** 色卡尺寸（与参考版式 3.x 屏占比接近）。 */
private val SWATCH_WIDTH = 92.dp
private val SWATCH_HEIGHT = 124.dp
private val SELECTED_BORDER_WIDTH = 3.dp
