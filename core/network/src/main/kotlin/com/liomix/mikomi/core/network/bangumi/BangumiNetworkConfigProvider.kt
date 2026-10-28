package com.liomix.mikomi.core.network.bangumi

import com.liomix.mikomi.core.model.BangumiNetworkConfig

/**
 * 提供当前生效的 Bangumi 连接配置。
 *
 * 拦截器在请求线程上**同步**调用，实现方必须返回一份已经准备好的快照（不能阻塞读 Flow）。
 */
fun interface BangumiNetworkConfigProvider {
    fun current(): BangumiNetworkConfig
}
