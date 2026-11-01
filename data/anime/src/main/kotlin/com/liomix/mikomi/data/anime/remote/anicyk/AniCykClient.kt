package com.liomix.mikomi.data.anime.remote.anicyk

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.anime.di.AniCykHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 次元库（AniCyk）主 API 客户端。
 *
 * 匿名只读接口只需要 `app-version` + `appId` 两个业务头；响应以 `CYK2026` 开头时按
 * AES-256-CBC 解密，`Dddm` 开头是 Base64 明文。四个节点串行降级，任一节点返回 2xx/4xx
 * 即视为链路可用（4xx 代表服务端明确拒绝，换节点也没用）。
 *
 * 注意：节点是明文 HTTP，[com.liomix.mikomi.data.anime.di.AniCykHttp] 提供的客户端
 * 依赖 `network_security_config` 里按 IP 白名单放行，不要改成全局 cleartext。
 */
@Singleton
internal class AniCykClient @Inject constructor(
    @ApplicationContext private val context: Context,
    @AniCykHttp private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    @Volatile
    var lastNode: String = ""
        private set

    /** 拉取明文/解密后的响应体；全部节点失败时抛 [IOException]。 */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): String =
        withContext(ioDispatcher) {
            var lastFailure: String = "无可用节点"
            for (node in NODES) {
                try {
                    val body = execute(node, path, params)
                    lastNode = node
                    return@withContext body
                } catch (e: IOException) {
                    lastFailure = "${e.message} @ $node"
                    Log.w(TAG, "AniCyk 节点失败：$node $path", e)
                }
            }
            throw IOException("AniCyk $path 全部节点失败：$lastFailure")
        }

    private fun execute(node: String, path: String, params: Map<String, String>): String {
        val url = buildUrl(node, path, params)
            ?: throw IOException("AniCyk 非法节点地址：$node")
        val request = Request.Builder()
            .url(url)
            .get()
            .header("app-version", APP_VERSION)
            .header("appId", APP_ID)
            .header("deviceId", deviceId())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply {
                val now = System.currentTimeMillis().toString()
                header("imei", Base64.encodeToString(now.toByteArray(), Base64.NO_WRAP))
                header(
                    "cvb",
                    Base64.encodeToString(
                        (System.currentTimeMillis() - APP_VERSION.toLong()).toString()
                            .toByteArray(),
                        Base64.NO_WRAP,
                    ),
                )
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) throw IOException("空响应体")
            return decode(raw)
        }
    }

    private fun buildUrl(node: String, path: String, params: Map<String, String>): String? {
        val base = node.trimEnd('/') + path
        val url = base.toHttpUrlOrNull() ?: return null
        if (params.isEmpty()) return url.toString()
        val builder = url.newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    /** `CYK2026<iv>:<cipher>` → AES-256-CBC 解密；`Dddm<base64>` → Base64 明文。 */
    private fun decode(rawInput: String): String {
        val raw = rawInput.trim().removePrefix("\uFEFF")
        if (raw.startsWith(DDDM_PREFIX)) {
            runCatching {
                String(
                    Base64.decode(raw.removePrefix(DDDM_PREFIX), Base64.DEFAULT),
                    Charsets.UTF_8,
                )
            }.getOrNull()?.let { return it }
        }
        if (!raw.startsWith(CYK_PREFIX)) return raw

        return runCatching {
            val payload = raw.removePrefix(CYK_PREFIX)
            val separator = payload.indexOf(':')
            require(separator > 0) { "缺少 iv 分隔符" }
            val iv = Base64.decode(payload.substring(0, separator), Base64.DEFAULT)
            val cipherText = Base64.decode(payload.substring(separator + 1), Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrElse { cause ->
            throw IOException("AniCyk 响应解密失败", cause)
        }
    }

    private fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: FALLBACK_DEVICE_ID

    private companion object {
        const val TAG = "AniCykClient"
        const val APP_VERSION = "278"
        const val APP_ID = "info.ciyuan.pro"
        const val USER_AGENT = "okhttp/3.12.13"
        const val CYK_PREFIX = "CYK2026"
        const val DDDM_PREFIX = "Dddm"
        const val FALLBACK_DEVICE_ID = "mikomi"

        /** 主 API 四个节点，按可用性从高到低串行降级。 */
        val NODES = listOf(
            "http://47.103.33.207:8081",
            "http://47.93.60.215:8081",
            "http://39.106.100.156:8081",
            "http://8.134.159.218:8081",
        )

        /** 由固定盐派生 AES-256 密钥；与服务端约定一致。 */
        val KEY: ByteArray by lazy {
            MessageDigest.getInstance("SHA-256")
                .digest("%ZZJ_ciyuan_2025!@".toByteArray(Charsets.UTF_8))
        }
    }
}
