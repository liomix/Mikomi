package com.liomix.mikomi.data.anime.schedule

import com.liomix.mikomi.core.database.entity.AnimeIdentityEntity

/** canonical 身份键的构造与解析。 */
internal object CanonicalKey {

    private const val EXTERNAL_PREFIX = "ext:"
    private const val TITLE_PREFIX = "title:"

    /** 外部 id 的信任顺序：共享 id 越多、越稳定，越靠前。 */
    val EXTERNAL_NAMESPACES = listOf("bangumi", "mal", "anilist")

    fun external(namespace: String, id: String): String = "$EXTERNAL_PREFIX$namespace:$id"

    fun title(normalizedTitle: String): String = "$TITLE_PREFIX$normalizedTitle"

    /** 外部 id 对（`bangumi:123`）→ canonical 键。 */
    fun externalPair(pair: String): String {
        val separator = pair.indexOf(':')
        if (separator <= 0) return "$EXTERNAL_PREFIX$pair"
        return external(pair.substring(0, separator), pair.substring(separator + 1))
    }
}

/**
 * 解析后的身份。
 *
 * [canonicalKey] 是对外唯一的判重依据；[signature] 的季数用于第二层消歧
 * （同名不同季不合并），[aliases] 是本次合并中新发现的标题变体。
 */
internal data class ResolvedIdentity(
    val canonicalKey: String,
    val signature: TitleNormalizer.Signature,
    val aliases: Set<String>,
    val externalIds: Map<String, String>,
    /** 命中来源：EXTERNAL（外部 id）/ MANUAL（运营配置）/ AUTO（自动匹配）/ NONE。 */
    val matchState: MatchState,
) {
    enum class MatchState { EXTERNAL, MANUAL, AUTO, NONE }
}

/** 合并结束后回写身份映射表的输入。 */
internal data class IdentityGroup(
    val canonicalKey: String,
    val baseTitle: String,
    val titles: Set<String>,
    val externalIds: Map<String, String>,
)

/**
 * 动漫身份映射表：把「源内条目」解析成跨源可比的 canonical 身份。
 *
 * 解析顺序（不依赖各源内部 id，跨源不可比）：
 * 1. **共享外部 id**：Bangumi / MAL / AniList（最可靠）；
 * 2. **运营配置 / 人工复核过的映射**：标题或别名命中即归组；
 * 3. **归一化标题**：去字幕组前缀、季数、标点、全半角后的主干。
 *
 * 别名是双向的：新条目的标题命中已有 canonical 的任一别名、或已有主标题命中新条目的
 * 别名集合，都会归到同一个 canonicalKey（见 [AnimeIdentityRegistryImpl.resolve]）。
 */
internal interface AnimeIdentityRegistry {

    /** 加载映射表（运营配置 + 数据库），首次调用生效，后续走内存索引。 */
    suspend fun load()

    /** 解析单条身份。 */
    fun resolve(item: ScheduleFeedItem): ResolvedIdentity

    /** 把本次合并观察到的标题变体回写，形成「自动匹配 + 待人工复核」的候选。 */
    suspend fun learn(groups: List<IdentityGroup>)
}

/** 别名在数据库里用不可见字符分隔，避免 JSON 解析开销。 */
internal fun AnimeIdentityEntity.aliasList(): List<String> =
    aliases.split(ALIAS_SEPARATOR).filter { it.isNotBlank() }

internal fun List<String>.joinAliases(): String =
    filter { it.isNotBlank() }.distinct().joinToString(ALIAS_SEPARATOR)

internal fun AnimeIdentityEntity.externalIdMap(): Map<String, String> =
    externalIds.split(EXTERNAL_SEPARATOR)
        .filter { it.isNotBlank() }
        .mapNotNull { pair ->
            val index = pair.indexOf(':')
            if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
        }
        .toMap()

internal fun Map<String, String>.joinExternalIds(): String =
    entries.joinToString(EXTERNAL_SEPARATOR) { "${it.key}:${it.value}" }

internal const val ALIAS_SEPARATOR = "\u0001"
internal const val EXTERNAL_SEPARATOR = "|"
