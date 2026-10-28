package com.liomix.mikomi.core.network.bangumi

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 把 Bangumi 官方域名改写成镜像地址（可选）。
 *
 * 只替换 scheme/host/端口，**路径与查询串原样保留**，因此 Retrofit 的 baseUrl、
 * 接口定义、业务代码都不需要知道镜像的存在；日后自建反向代理只需改配置里的地址。
 *
 * 三点约定：
 * - 仅作用于 [MIRRORABLE_HOSTS]，其他请求一律透传，避免误伤第三方接口；
 * - 镜像地址解析失败（空串、非法 URL）时直接走直连，不让配置错误导致整个数据层不可用；
 * - 镜像地址支持子路径（`https://your.domain/bgm`），拼接时不会出现双斜杠。
 */
class BangumiMirrorInterceptor(
    private val configProvider: BangumiNetworkConfigProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val mirrorBase = configProvider.current().activeMirrorBaseUrl
            ?.toHttpUrlOrNull()
            ?: return chain.proceed(request)

        val original = request.url
        if (original.host !in MIRRORABLE_HOSTS) return chain.proceed(request)

        val rewritten = mirrorBase.newBuilder()
            .encodedPath(mirrorBase.encodedPath.trimEnd('/') + original.encodedPath)
            .encodedQuery(original.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private companion object {
        /** 允许被镜像改写的官方域名。 */
        val MIRRORABLE_HOSTS = setOf("api.bgm.tv", "next.bgm.tv")
    }
}
