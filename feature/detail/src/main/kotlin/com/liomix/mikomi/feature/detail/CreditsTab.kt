package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.StaffMember
import com.liomix.mikomi.core.ui.component.EmptyState
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 主创卡片固定宽度（名字无限滚动 + 关系 badge 需要更宽空间）。 */
private val CREDIT_CARD_WIDTH = 180.dp

/** 主创卡片固定高度（对齐 Dart 版 80，双行布局规整）。 */
private val CREDIT_CARD_HEIGHT = 80.dp

/** 头像直径。 */
private val AVATAR_SIZE = 60.dp

/**
 * 主创 Tab：角色 + 制作人员两个区块，横向两列卡片。
 *
 * 对齐 Dart 版 `DetailTabContent`：角色按「主角 > 配角 > 客串」排序，
 * 制作人员按「原作 > 导演 > 系列构成 > 角色设计 > 音乐」排序。
 * 懒加载：[onLoadCredits] 首次进入时触发。
 */
@Composable
internal fun CreditsTab(
    creditsState: AnimeCreditsUiState,
    onLoadCredits: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 懒加载：首次进入（Idle）时触发拉取。
    LaunchedEffect(creditsState) {
        if (creditsState is AnimeCreditsUiState.Idle) {
            onLoadCredits()
        }
    }

    when (creditsState) {
        AnimeCreditsUiState.Idle,
        AnimeCreditsUiState.Loading,
        -> CreditsSkeleton(modifier = modifier)

        AnimeCreditsUiState.Error -> EmptyState(
            message = stringResource(R.string.detail_load_failed),
            modifier = modifier,
        )

        is AnimeCreditsUiState.Loaded -> CreditsContent(
            characters = creditsState.characters,
            staff = creditsState.staff,
            onCharacterClick = onCharacterClick,
            onStaffClick = onStaffClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun CreditsContent(
    characters: List<AnimeCharacter>,
    staff: List<StaffMember>,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 作为外层 LazyColumn 的 item，纵向用普通 Column（非懒加载），横向用 LazyRow 滚动。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // 角色区块。
        SectionTitle(text = stringResource(R.string.detail_characters))
        if (characters.isEmpty()) {
            EmptyState(message = stringResource(R.string.detail_no_characters))
        } else {
            CreditGrid(items = characters) { character ->
                CharacterCard(
                    character = character,
                    onClick = { onCharacterClick(character.id) },
                )
            }
        }

        // 制作人员区块。
        SectionTitle(text = stringResource(R.string.detail_staff))
        if (staff.isEmpty()) {
            EmptyState(message = stringResource(R.string.detail_no_staff))
        } else {
            CreditGrid(items = staff) { member ->
                StaffCard(
                    member = member,
                    onClick = { onStaffClick(member.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** 横向双行卡片容器（对齐 Dart 版 horizontal grid：每列 2 个卡片纵向堆叠）。 */
@Composable
private fun <T> CreditGrid(
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) {
    // 每列 2 个卡片，横向滚动；列数 = ceil(items.size / 2)。
    val columnCount = (items.size + 1) / 2
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count = columnCount, key = { it }) { columnIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val firstIndex = columnIndex * 2
                itemContent(items[firstIndex])
                val secondIndex = firstIndex + 1
                if (secondIndex < items.size) {
                    itemContent(items[secondIndex])
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: AnimeCharacter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(CREDIT_CARD_WIDTH)
            .height(CREDIT_CARD_HEIGHT),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                url = character.images?.grid ?: character.images?.medium,
                contentDescription = character.name,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字：单行无限滚动（超宽自动横向滚动，对齐 Dart 版 ScrollingText）。
                    ScrollingText(
                        text = character.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    RelationBadge(text = character.relation)
                }
                character.voiceActors.firstOrNull()?.let { actor ->
                    Spacer(modifier = Modifier.height(4.dp))
                    ScrollingText(
                        text = actor.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffCard(
    member: StaffMember,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(CREDIT_CARD_WIDTH)
            .height(CREDIT_CARD_HEIGHT),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                url = member.images?.grid ?: member.images?.medium,
                contentDescription = member.displayName,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字：单行无限滚动。
                    ScrollingText(
                        text = member.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (member.positionText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        RelationBadge(text = member.positionText)
                    }
                }
                if (member.localizedName?.isNotBlank() == true &&
                    member.localizedName != member.name
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ScrollingText(
                        text = member.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(AVATAR_SIZE),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(12.dp)
                    .size(30.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun RelationBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val (container, content) = creditBadgeColors(text)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = container,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/**
 * 关系/职位标签语义配色（浅底 + 深字，保证文字对比度 ≥ 4.5:1）。
 *
 * 对齐 UX 规范：颜色不单独承载信息（标签文字本身即语义），仅作视觉增强；
 * 角色关系与制作人员职位各一套柔和色，未命中回退中性灰。
 */
private fun creditBadgeColors(text: String): Pair<Color, Color> {
    val style = CREDIT_BADGE_STYLES.entries.firstOrNull { (keyword, _) ->
        text.contains(keyword)
    }?.value ?: NeutralBadgeStyle
    return style.container to style.content
}

/** 标签样式：底色 + 文字色。 */
private data class CreditBadgeStyle(
    val container: Color,
    val content: Color,
)

/** 标签配色表（关键词命中即取色；「其他」兜底中性灰）。 */
private val CREDIT_BADGE_STYLES = linkedMapOf(
    // 角色关系。
    "主角" to CreditBadgeStyle(
        container = Color(0xFFFFE0E0),
        content = Color(0xFFB3261E),
    ),
    "配角" to CreditBadgeStyle(
        container = Color(0xFFD6E4FF),
        content = Color(0xFF1A56C7),
    ),
    "客串" to CreditBadgeStyle(
        container = Color(0xFFE8DFFF),
        content = Color(0xFF5A2FA6),
    ),
    // 制作人员职位。
    "原作" to CreditBadgeStyle(
        container = Color(0xFFFFE8C7),
        content = Color(0xFF8A4B00),
    ),
    "导演" to CreditBadgeStyle(
        container = Color(0xFFFFD9D2),
        content = Color(0xFFA52714),
    ),
    "系列构成" to CreditBadgeStyle(
        container = Color(0xFFD0F0E8),
        content = Color(0xFF0B6B5A),
    ),
    "角色设计" to CreditBadgeStyle(
        container = Color(0xFFDDF2D2),
        content = Color(0xFF2E6B1E),
    ),
    "音乐" to CreditBadgeStyle(
        container = Color(0xFFF5DDF5),
        content = Color(0xFF7A2B7A),
    ),
)

/** 未匹配关键词时的中性灰兜底。 */
private val NeutralBadgeStyle = CreditBadgeStyle(
    container = Color(0xFFE8EAED),
    content = Color(0xFF4A5568),
)

/** 主创骨架屏：角色 + 制作人员各两排卡片占位。 */
@Composable
private fun CreditsSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        SkeletonBox(
            modifier = Modifier.size(width = 48.dp, height = 18.dp),
            shape = RoundedCornerShape(9.dp),
        )
        CreditSkeletonRow()
        SkeletonBox(
            modifier = Modifier.size(width = 72.dp, height = 18.dp),
            shape = RoundedCornerShape(9.dp),
        )
        CreditSkeletonRow()
    }
}

@Composable
private fun CreditSkeletonRow() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(CREDIT_CARD_WIDTH)
                            .height(76.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
    }
}
