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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.StaffMember
import com.liomix.mikomi.core.ui.component.EmptyState
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 主创卡片固定宽度（对齐 Dart 版 160）。 */
private val CREDIT_CARD_WIDTH = 160.dp

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
            modifier = modifier,
        )
    }
}

@Composable
private fun CreditsContent(
    characters: List<AnimeCharacter>,
    staff: List<StaffMember>,
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
            CreditGrid {
                items(
                    items = characters,
                    key = { "char_${it.id}" },
                ) { character ->
                    CharacterCard(character = character)
                }
            }
        }

        // 制作人员区块。
        SectionTitle(text = stringResource(R.string.detail_staff))
        if (staff.isEmpty()) {
            EmptyState(message = stringResource(R.string.detail_no_staff))
        } else {
            CreditGrid {
                items(
                    items = staff,
                    key = { "staff_${it.id}" },
                ) { member ->
                    StaffCard(member = member)
                }
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

/** 横向卡片容器（对齐 Dart 版 horizontal grid）。 */
@Composable
private fun CreditGrid(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun CharacterCard(
    character: AnimeCharacter,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(CREDIT_CARD_WIDTH),
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
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    RelationBadge(text = character.relation)
                }
                character.voiceActors.firstOrNull()?.let { actor ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = actor.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffCard(
    member: StaffMember,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(CREDIT_CARD_WIDTH),
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
                    Text(
                        text = member.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

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
