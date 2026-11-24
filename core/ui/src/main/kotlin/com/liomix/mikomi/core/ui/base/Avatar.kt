package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes

/**
 * 头像位：一个可点的圆，里面是人物图标。
 *
 * 现在全站还没有真实头像（没有账号体系），所以它是「人物图标 + 圆形底」的占位形态。
 * 将来接入真实头像时，这一处换成 `AsyncImage` 即可 —— 外面的圆、描边、点击、
 * 无障碍名都不用动，这也是先把它抽出来的原因：**换的是内容，不是形状**。
 *
 * ## 尺寸与颜色由调用方给
 *
 * 直径由 `modifier.size(...)` 决定（首页顶栏是 40dp），颜色默认取页面配色；
 * 压在封面上的半透明白由业务侧算好传进来。
 *
 * @param contentDescription 无障碍名（「我的」这类），不能为 null。
 * @param onClick 点击回调。
 * @param containerColor 圆形底色。
 * @param contentColor 图标色。
 * @param border 描边；null 表示不描边。
 * @param icon 人物图标；留出参数是为了「未登录」之外还能放别的身份图示。
 */
@Composable
fun Avatar(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null,
    icon: ImageVector = Icons.Person,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .clip(CircleShape)
            .mikomiClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                // 24 在 40dp 圆里正好，不会顶满圆底。
                size = Sizes.IconMd,
            )
        }
    }
}
