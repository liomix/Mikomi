package com.liomix.mikomi.data.anime.schedule

import android.content.Context
import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.dao.AnimeIdentityDao
import com.liomix.mikomi.core.database.entity.AnimeIdentityEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 身份映射表实现。
 *
 * 数据来源分层：
 * - **运营配置**（assets `schedule_aliases.json`）：人工维护，命中即 MANUAL，优先级最高；
 * - **数据库**：由外部 id 自动关联（EXTERNAL）与自动匹配（AUTO）累积而来。
 *
 * 映射关系只在内存里做一次索引（标题 → canonical、外部 id → canonical），刷新时 O(1) 命中；
 * 运营配置更新后需重启进程（时间表是周级数据，不值得为此加监听）。
 */
@Singleton
internal class AnimeIdentityRegistryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnimeIdentityDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnimeIdentityRegistry {

    private val loadMutex = Mutex()
    private val learnMutex = Mutex()
    private var loaded = false

    private var entities: Map<String, AnimeIdentityEntity> = emptyMap()
    private var titleIndex: Map<String, String> = emptyMap()
    private var externalIndex: Map<String, String> = emptyMap()

    override suspend fun load(): Unit = loadMutex.withLock {
        if (loaded) return
        withContext(ioDispatcher) {
            val configured = readAssetConfig()
            if (configured.isNotEmpty()) {
                dao.upsertAll(configured.map { it.toEntity() })
            }
            val stored = dao.all()
            entities = stored.associateBy { it.canonicalKey }
            rebuildIndex(stored)
            loaded = true
            Log.i(TAG, "身份映射表加载完成：${stored.size} 条，运营配置 ${configured.size} 条")
        }
    }

    override fun resolve(item: ScheduleFeedItem): ResolvedIdentity {
        val signature = TitleNormalizer.signature(item.title)
        val normalizedTitles = TitleNormalizer.normalizeAll(listOf(item.title) + item.aliases)

        // 1) 共享外部 id 最可靠，命中即用（并保留映射表里既有的 canonical 键）。
        CanonicalKey.EXTERNAL_NAMESPACES.forEach { namespace ->
            val id = item.externalIds[namespace]?.takeIf { it.isNotBlank() } ?: return@forEach
            val canonical = externalIndex["$namespace:$id"] ?: CanonicalKey.external(namespace, id)
            return ResolvedIdentity(
                canonicalKey = canonical,
                signature = signature,
                aliases = normalizedTitles,
                externalIds = item.externalIds,
                matchState = ResolvedIdentity.MatchState.EXTERNAL,
            )
        }

        // 2) 映射表：主标题或任一别名命中即归组（双向展开）。
        normalizedTitles.firstNotNullOfOrNull { titleIndex[it] }?.let { canonical ->
            return ResolvedIdentity(
                canonicalKey = canonical,
                signature = signature,
                aliases = normalizedTitles,
                externalIds = item.externalIds,
                matchState = entities[canonical].toMatchState(),
            )
        }

        // 3) 兜底：用归一化标题自建身份，后续由 learn 补成完整映射。
        val primary = signature.normalized.ifBlank { item.title.trim() }
        return ResolvedIdentity(
            canonicalKey = CanonicalKey.title(primary),
            signature = signature,
            aliases = normalizedTitles,
            externalIds = item.externalIds,
            matchState = ResolvedIdentity.MatchState.NONE,
        )
    }

    override suspend fun learn(groups: List<IdentityGroup>): Unit = learnMutex.withLock {
        if (groups.isEmpty()) return
        withContext(ioDispatcher) {
            val existing = dao.all().associateBy { it.canonicalKey }
            val now = System.currentTimeMillis()
            val updates = groups.mapNotNull { group ->
                val current = existing[group.canonicalKey]
                val titles = group.titles.filter { it.isNotBlank() }
                when {
                    // 从未见过：记为自动匹配候选，等运营复核。
                    current == null -> AnimeIdentityEntity(
                        canonicalKey = group.canonicalKey,
                        primaryTitle = group.baseTitle,
                        aliases = titles.joinAliases(),
                        externalIds = group.externalIds.joinExternalIds(),
                        matchState = STATE_AUTO,
                        updatedAt = now,
                    )

                    // 人工/外部关联的映射只追加别名，不改状态、不改主标题。
                    canOnlyAppend(current.matchState) -> {
                        val mergedAliases = (current.aliasList() + titles).joinAliases()
                        val mergedExternal = current.externalIdMap() + group.externalIds
                        val mergedExternalText = mergedExternal.joinExternalIds()
                        if (mergedAliases == current.aliases &&
                            mergedExternalText == current.externalIds
                        ) {
                            null
                        } else {
                            current.copy(
                                aliases = mergedAliases,
                                externalIds = mergedExternalText,
                                updatedAt = now,
                            )
                        }
                    }

                    else -> current.copy(
                        aliases = (current.aliasList() + titles).joinAliases(),
                        externalIds = (current.externalIdMap() + group.externalIds).joinExternalIds(),
                        updatedAt = now,
                    )
                }
            }
            if (updates.isNotEmpty()) {
                dao.upsertAll(updates)
                entities = entities + updates.associateBy { it.canonicalKey }
                rebuildIndex(entities.values.toList())
            }
        }
    }

    /** 索引构建：MANUAL / EXTERNAL 先写入，AUTO 后写，保证人工配置不被自动匹配覆盖。 */
    private fun rebuildIndex(all: List<AnimeIdentityEntity>) {
        val ordered = all.sortedBy { stateWeight(it.matchState) }
        val titles = HashMap<String, String>(ordered.size * 3)
        val externals = HashMap<String, String>(ordered.size)
        ordered.forEach { entity ->
            listOf(entity.primaryTitle).plus(entity.aliasList()).forEach { title ->
                val normalized = TitleNormalizer.normalize(title)
                if (normalized.isNotBlank() && !titles.containsKey(normalized)) {
                    titles[normalized] = entity.canonicalKey
                }
            }
            entity.externalIdMap().forEach { (namespace, id) ->
                val pair = "$namespace:$id"
                if (!externals.containsKey(pair)) externals[pair] = entity.canonicalKey
            }
        }
        titleIndex = titles
        externalIndex = externals
    }

    private fun readAssetConfig(): List<AliasConfigEntry> {
        val available = runCatching {
            context.assets.open(ASSET_ALIAS_FILE).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        return runCatching { json.decodeFromString<List<AliasConfigEntry>>(available) }
            .getOrElse { cause ->
                Log.w(TAG, "运营别名配置解析失败，已跳过：$ASSET_ALIAS_FILE", cause)
                emptyList()
            }
    }

    private fun AliasConfigEntry.toEntity(): AnimeIdentityEntity = AnimeIdentityEntity(
        canonicalKey = canonicalKey.ifBlank { CanonicalKey.title(TitleNormalizer.normalize(primaryTitle)) },
        primaryTitle = primaryTitle,
        aliases = aliases.joinAliases(),
        externalIds = externalIds.joinExternalIds(),
        matchState = STATE_MANUAL,
        updatedAt = System.currentTimeMillis(),
    )

    private fun canOnlyAppend(state: String): Boolean =
        state == STATE_MANUAL || state == STATE_EXTERNAL

    private fun stateWeight(state: String): Int = when (state) {
        STATE_MANUAL -> 0
        STATE_EXTERNAL -> 1
        else -> 2
    }

    private fun AnimeIdentityEntity?.toMatchState(): ResolvedIdentity.MatchState = when (this?.matchState) {
        STATE_MANUAL -> ResolvedIdentity.MatchState.MANUAL
        STATE_EXTERNAL -> ResolvedIdentity.MatchState.EXTERNAL
        STATE_AUTO -> ResolvedIdentity.MatchState.AUTO
        else -> ResolvedIdentity.MatchState.NONE
    }

    /** 运营维护的别名 / 外部 id 映射（可选文件，缺失即跳过）。 */
    @Serializable
    private data class AliasConfigEntry(
        val canonicalKey: String = "",
        val primaryTitle: String = "",
        val aliases: List<String> = emptyList(),
        val externalIds: Map<String, String> = emptyMap(),
    )

    private companion object {
        const val TAG = "AnimeIdentity"
        const val ASSET_ALIAS_FILE = "schedule_aliases.json"
        const val STATE_MANUAL = "MANUAL"
        const val STATE_EXTERNAL = "EXTERNAL"
        const val STATE_AUTO = "AUTO"
    }
}
