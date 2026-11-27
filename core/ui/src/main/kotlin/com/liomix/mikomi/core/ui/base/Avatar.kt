package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes

/**
 * 头像位：一个可点的圆，里面是人物图标。
 *
 * 现在全站还没有真实头像（没有账号体系），所以它是「人物图标 + 圆形底」的占位形态。
 * 将来接入真实头像时，这一处换成 `AsyncImage` 即可 —— 外面的圆、材质、点击、
 * 无障碍名都不用动，这也是先把它抽出来的原因：**换的是内容，不是形状**。
 *
 * ## 尺寸由调用方给，材质也由调用方给
 *
 * 直径由 `modifier.size(...)` 决定（首页顶栏是 40dp）。材质是一个 [GlassStyle]：
 * 压在封面上的半透明白、压在页面底色上的容器色，由业务侧算好传进来 ——
 * 底色与描边是同一种材质的两个成分，所以不再拆成两个参数。
 *
 * @param contentDescription 无障碍名（「我的」这类），不能为 null。
 * @param onClick 点击回调。
 * @param style 圆形材质（底色 + 描边）。
 * @param contentColor 图标色。
 * @param icon 人物图标；留出参数是为了「未登录」之外还能放别的身份图示。
 */
@Composable
fun Avatar(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.raised(),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector = Icons.Person,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            // 玻璃自己负责裁剪，点按接在它后面就收在圆里；图标由 contentAlignment 居中。
            modifier = modifier
                .glass(style, CircleShape)
                .mikomiClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = true,
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
