package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.CreditComment
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.core.model.StaffMember

/** 番剧主创结果：角色 + 制作人员。 */
data class AnimeCredits(
    val characters: List<AnimeCharacter>,
    val staff: List<StaffMember>,
)

/** 番剧主创数据门面：Bangumi 角色 + 制作人员。 */
interface AnimeCreditsRepository {

    /** 拉取番剧主创（角色 + 制作人员，已按关系/职位排序）；失败向上抛出。 */
    suspend fun fetchCredits(animeId: String): AnimeCredits

    /** 拉取角色详情（底部弹层资料）。 */
    suspend fun fetchCharacterDetail(characterId: Int): CreditDetail

    /** 拉取制作人员详情（底部弹层资料）。 */
    suspend fun fetchPersonDetail(personId: Int): CreditDetail

    /** 拉取角色吐槽/评论。 */
    suspend fun fetchCharacterComments(characterId: Int): List<CreditComment>

    /** 拉取制作人员吐槽/评论。 */
    suspend fun fetchPersonComments(personId: Int): List<CreditComment>
}
