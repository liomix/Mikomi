package com.liomix.mikomi.data.user.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.BangumiApiMode
import com.liomix.mikomi.core.model.BangumiNetworkConfig
import com.liomix.mikomi.core.network.bangumi.BangumiNetworkConfigProvider
import com.liomix.mikomi.data.user.UserDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 把持久化的偏好转成「可同步读取」的网络配置快照。
 *
 * OkHttp 的拦截器在请求线程上同步执行，既不能 `collect` Flow 也不能阻塞，
 * 所以这里用后台协程把 DataStore 的值同步到 [snapshot]，拦截器只读这一个字段。
 *
 * 协程用应用级生命周期（不做取消）：配置监听需要跟随进程一直存在，
 * 而它只有一个 `distinctUntilChanged` 的轻量收集器，开销可以忽略。
 */
@Singleton
class BangumiNetworkConfigStore @Inject constructor(
    userDataRepository: UserDataRepository,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : BangumiNetworkConfigProvider {

    @Volatile
    private var snapshot: BangumiNetworkConfig = BangumiNetworkConfig.Default

    init {
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        scope.launch {
            userDataRepository.observeUserPreferences()
                .map {
                    BangumiNetworkConfig(
                        mode = BangumiApiMode.fromId(it.bangumiApiMode),
                        mirrorBaseUrl = it.bangumiMirrorUrl,
                    )
                }
                .distinctUntilChanged()
                .collect { snapshot = it }
        }
    }

    override fun current(): BangumiNetworkConfig = snapshot
}
