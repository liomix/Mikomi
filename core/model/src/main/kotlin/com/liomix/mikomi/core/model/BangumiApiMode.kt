package com.liomix.mikomi.core.model

/**
 * Bangumi 接口的连接方式。
 *
 * `id` 持久化到 DataStore（`bangumi_api_mode`）。
 */
enum class BangumiApiMode(
    val id: String,
) {
    /** 直连：直接访问 api.bgm.tv / next.bgm.tv。 */
    Direct("direct"),

    /**
     * 镜像代理：只替换请求的域名，接口路径与查询参数保持不变。
     *
     * 镜像地址由用户配置（见网络设置页），日后自建反代时只需改这个地址。
     */
    Mirror("mirror"),
    ;

    companion object {
        /** 按 id 取模式；未知 id（含旧值/空值）回退 [Direct]。 */
        fun fromId(id: String?): BangumiApiMode =
            entries.firstOrNull { it.id == id } ?: Direct
    }
}

/**
 * Bangumi 接口的连接配置。
 *
 * 客户端只认 `api.bgm.tv` / `next.bgm.tv` 两个域名，镜像模式下由拦截器把域名替换成
 * [mirrorBaseUrl]（路径与查询串保持原样），因此业务层无需感知镜像的存在。
 */
data class BangumiNetworkConfig(
    val mode: BangumiApiMode = BangumiApiMode.Direct,
    /** 例：`https://api.kazumi.fyi`、`https://your.domain/bgm`（支持子路径）。 */
    val mirrorBaseUrl: String = "",
) {
    /**
     * 当前生效的镜像基地址：非镜像模式或地址为空时返回 `null`（拦截器据此走直连）。
     * 末尾斜杠在这里去掉，拼接路径时不会出现双斜杠。
     */
    val activeMirrorBaseUrl: String?
        get() = mirrorBaseUrl.trim().trimEnd('/')
            .takeIf { mode == BangumiApiMode.Mirror && it.isNotEmpty() }

    companion object {
        val Default = BangumiNetworkConfig()
    }
}
