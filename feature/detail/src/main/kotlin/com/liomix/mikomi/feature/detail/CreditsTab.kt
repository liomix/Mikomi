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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTagColor
import com.liomix.mikomi.core.designsystem.theme.MikomiTagHue
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.StaffMember
import com.liomix.mikomi.core.ui.component.base.EmptyState
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.base.ScrollingText
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.image.MikomiAsyncImage

/** 主创卡片固定宽度（名字无限滚动 + 关系 badge 需要更宽空间）。 */
private val CREDIT_CARD_WIDTH = 180.dp

/** 主创卡片固定高度（对齐 Dart 版 80，双行布局规整）。 */
private val CREDIT_CARD_HEIGHT = 80.dp

/** 头像直径。 */
private val AVATAR_SIZE = 60.dp

/** 关系/职位徽标的圆角：小圆角底片，不是胶囊（理由见 [RelationBadge]）。 */
private val RelationBadgeShape: Shape = RoundedCornerShape(MikomiRadius.Xs)

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
            .padding(MikomiSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xxl),
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
    LazyRow(
        contentPadding = PaddingValues(MikomiSpacing.None),
        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
    ) {
        items(count = columnCount, key = { it }) { columnIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
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
        shape = RoundedCornerShape(MikomiRadius.Lg),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(MikomiSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                url = character.images?.grid ?: character.images?.medium,
                contentDescription = character.name,
            )
            Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字：单行无限滚动（超宽自动横向滚动，对齐 Dart 版 ScrollingText）。
                    ScrollingText(
                        text = character.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        enabled = false,
                    )
                    Spacer(modifier = Modifier.width(MikomiSpacing.Xs))
                    RelationBadge(text = character.relation)
                }
                character.voiceActors.firstOrNull()?.let { actor ->
                    Spacer(modifier = Modifier.height(MikomiSpacing.Xs))
                    ScrollingText(
                        text = actor.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        enabled = false,
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
        shape = RoundedCornerShape(MikomiRadius.Lg),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(MikomiSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                url = member.images?.grid ?: member.images?.medium,
                contentDescription = member.displayName,
            )
            Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字：单行无限滚动。
                    ScrollingText(
                        text = member.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        enabled = false,
                    )
                    if (member.positionText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(MikomiSpacing.Xs))
                        RelationBadge(text = member.positionText)
                    }
                }
                if (member.localizedName?.isNotBlank() == true &&
                    member.localizedName != member.name
                ) {
                    Spacer(modifier = Modifier.height(MikomiSpacing.Xs))
                    ScrollingText(
                        text = member.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        enabled = false,
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
            MikomiIcon(
                icon = MikomiIcons.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MikomiSpacing.Md),
                // design-ok 头像内嵌人物占位：随 AVATAR_SIZE 的视觉比例定，不属图标三档。
                size = 30.dp,
            )
        } else {
            MikomiAsyncImage(
                url = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * 关系/职位小徽标：名字后面那枚「主角 / 导演」。
 *
 * 它不复用 `core:ui` 的 `Tag`：颜色来自本页那张关键词配色表，尺寸也只服务
 * 「和名字挤在同一行」这一种场景。要搬进通用层，就得为它开三个覆盖参数
 * （颜色 / 圆角 / 内边距），等于把一个用例的偏好写进公共 API。
 */
@Composable
private fun RelationBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val badge = creditBadgeColors(text)
    Surface(
        // 4dp 圆角而不是胶囊：它是「贴着文字的底片」，和右侧名字同处一行时，
        // 胶囊的圆头会让它显得比名字更重。
        shape = RelationBadgeShape,
        color = badge.container,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = badge.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = MikomiSpacing.Xs,
                // design-ok: 徽标的光学补偿 —— labelSmall 的行高已经够，这里只补一丝呼吸，
                // 换成 Sm 会把整枚徽标撑高、把同一行的名字挤得上下不齐。
                vertical = 1.dp,
            ),
        )
    }
}

/**
 * 关系/职位标签语义配色。
 *
 * **色值不在这张表里**。「主角是红的」是业务知识，留在本页；「红色标签在日间长什么样、
 * 夜间长什么样」是设计系统的知识，放在 `MikomiColors.tag(hue)`。分开之后同一套红色能被
 * 别处的状态标签复用，日夜各自有对应的一档 —— 这里原来那张浅底色板在夜间会变成一块
 * 压在纯黑页面上的发光板，正是因为色值只有日间一份。
 *
 * 对齐 UX 规范：颜色不单独承载信息（标签文字本身即语义），仅作视觉增强；
 * 角色关系与制作人员职位各一套色相，未命中回退中性灰。
 */
@Composable
private fun creditBadgeColors(text: String): MikomiTagColor =
    MikomiTheme.colors.tag(creditHue(text))

/** 关键词 → 色相。命中即取色，未命中兜底中性灰。 */
private fun creditHue(text: String): MikomiTagHue =
    CREDIT_BADGE_HUES.entries.firstOrNull { (keyword, _) ->
        text.contains(keyword)
    }?.value ?: MikomiTagHue.Neutral

/** 标签色相表：只说「这个职位属于哪个红」，不说「红是多少号」。 */
private val CREDIT_BADGE_HUES = linkedMapOf(
    // 角色关系。
    "主角" to MikomiTagHue.Red,
    "配角" to MikomiTagHue.Blue,
    "客串" to MikomiTagHue.Violet,
    // 制作人员职位。
    "原作" to MikomiTagHue.Amber,
    "导演" to MikomiTagHue.Coral,
    "系列构成" to MikomiTagHue.Teal,
    "角色设计" to MikomiTagHue.Green,
    "音乐" to MikomiTagHue.Magenta,
)

/** 主创骨架屏：角色 + 制作人员各两排卡片占位。 */
@Composable
private fun CreditsSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MikomiSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xxl),
    ) {
        SkeletonBox(
            modifier = Modifier.size(width = 48.dp, height = 18.dp),
            shape = MikomiPillShape,
        )
        CreditSkeletonRow()
        SkeletonBox(
            modifier = Modifier.size(width = 72.dp, height = 18.dp),
            shape = MikomiPillShape,
        )
        CreditSkeletonRow()
    }
}

@Composable
private fun CreditSkeletonRow() {
    Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md)) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(CREDIT_CARD_WIDTH)
                            .height(76.dp),
                        shape = RoundedCornerShape(MikomiRadius.Lg),
                    )
                }
            }
        }
    }
}
