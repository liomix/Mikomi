package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.AnimeCharacter
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
}
