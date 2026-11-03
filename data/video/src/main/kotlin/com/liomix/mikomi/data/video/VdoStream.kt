package com.liomix.mikomi.data.video

/**
 * 一条能播的地址，以及播放它所需要的全部信息。
 *
 * 这是三大源（插件源 / 站点源 / API 源）共同的产出物：不管地址是从 XPath 里翻出来的、
 * 从网络层嗅探到的、还是接口直接给的，最后都要落到这一个类型上，播放层才不用认识源的类型。
 */
data class VdoStream(
    val url: String,
    val kind: VdoStreamKind,
    val statusCode: Int,
    /** 采样到的字节数（有的源只读开头 2KB 就够认出是 m3u8 还是 mp4，嗅探路径为 0）。 */
    val byteCount: Int,
    /**
     * 播放器请求这条流时要带的头：`User-Agent` / `Referer` / `Cookie`。
     *
     * 不能省：站点按 Referer 判盗链，按 Cookie 判会话，缺一样就是 403 或一张空列表。
     * 播放层（Media3）与出流层（OkHttp）不共享会话，所以这里把值拷出来交给它。
     */
    val headers: Map<String, String> = emptyMap(),
)

enum class VdoStreamKind {
    /** HLS 播放列表（`.m3u8`），播放器要 Media3 的 HLS 模块。 */
    Hls,

    /** 直链 mp4。 */
    Mp4,

    /** 认不出类型但确实拿到了二进制流。 */
    Other,
}
