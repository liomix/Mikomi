package com.liomix.mikomi.data.anime.remote.anirpb

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.ConnectivityManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.system.Os
import android.util.Base64
import android.util.Log
import com.yy.myuko.component.ktor.addressing.ZhuqueSessionNativeLib
import java.io.DataInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * AniRpb 的本地帧传输层。
 *
 * 真正的 ESDK / MMTLS 握手、bootstrap、寻址和重协商都在官方 `libzhuque.so`
 * 内部完成；Kotlin 侧只负责：
 *
 * 1. 把 `ZQ_FILES_DIR` 指向应用私有目录；
 * 2. 加载 `libaddressing_ffi.so` / `libzhuque.so` 并提交 DeviceContext；
 * 3. 从 JNI 取 32 字节会话密钥；
 * 4. 通过本地 UDS 收发 `ZQF1` AES-GCM 帧；
 * 5. 把业务 op + JSON 参数封进语义请求。
 *
 * 该类是阻塞 API，调用方必须放在 IO 线程上。
 */
internal class ZhuqueTransport(context: Context) {

    private val appContext = context.applicationContext
    private val random = SecureRandom()
    private val loaded = java.util.concurrent.atomic.AtomicBoolean(false)
    private val linkLock = Any()
    private var linkSocket: LocalSocket? = null
    private var linkInput: DataInputStream? = null

    /** 首次调用：设置环境变量、加载 so、提交 DeviceContext。 */
    @Synchronized
    fun initialize() {
        if (loaded.get()) return

        Os.setenv("ZQ_FILES_DIR", appContext.filesDir.absolutePath, true)

        // zhuque 内部也会 dlopen addressing_ffi；显式加载一次可以更早暴露缺库问题。
        runCatching { System.loadLibrary("addressing_ffi") }
            .onFailure { Log.w(TAG, "load addressing_ffi failed", it) }
        System.loadLibrary("zhuque")
        loaded.set(true)

        val ok = try {
            ZhuqueSessionNativeLib.nativeDeviceContextInit(
                buildDeviceContext().toByteArray(Charsets.UTF_8),
            )
        } catch (t: Throwable) {
            loaded.set(false)
            throw t
        }
        if (!ok) {
            loaded.set(false)
            throw IllegalStateException("libzhuque 拒绝了 DeviceContext")
        }
        Log.i(TAG, "native transport initialized, pid=${Process.myPid()}")
    }

    /** 幂等初始化；session key / 业务请求前会自动调用。 */
    @Synchronized
    fun ensureInitialized() {
        if (!loaded.get()) initialize()
    }

    /** 轮询等待本地帧密钥（32 字节）。 */
    fun awaitSessionKey(timeoutMs: Long = SESSION_KEY_TIMEOUT_MS): ByteArray? {
        ensureInitialized()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val key = runCatching { ZhuqueSessionNativeLib.nativeSessionKey() }.getOrNull()
            if (key != null && key.size == KEY_SIZE) return key
            Thread.sleep(KEY_POLL_INTERVAL_MS)
        }
        return null
    }

    /** 频道列表（type.listActive），用于动态解析日漫 / 国漫的 categoryId。 */
    fun channels(): JSONObject = semantic(
        op = OP_TYPE_LIST_ACTIVE,
        paramsJson = JSONObject()
            .put("pageIndex", 1)
            .put("pageSize", 100)
            .put("parentId", JSONObject.NULL)
            .put("parentCode", JSONObject.NULL)
            .put("withChannel", JSONObject.NULL)
            .put("dataId", JSONObject.NULL)
            .toString(),
    )

    /** 日漫 / 国漫热播榜（home.load）。 */
    fun hotRanking(
        categoryId: String,
        pageIndex: Int = 1,
        pageSize: Int = 20,
    ): JSONObject = semantic(
        op = OP_HOME_LOAD,
        paramsJson = JSONObject()
            .put("categoryId", categoryId)
            .put("pageIndex", pageIndex)
            .put("pageSize", pageSize)
            .toString(),
    )

    /**
     * 发送一个语义请求并返回包装对象：
     * `{op, frameStatus, tokenMatch, body}`，其中 `body` 是业务 JSON 字符串。
     */
    fun semantic(op: String, paramsJson: String, attempts: Int = 3): JSONObject {
        var last: Exception? = null
        for (attempt in 0 until attempts) {
            try {
                return semanticOnce(op, paramsJson)
            } catch (t: Throwable) {
                last = t as? Exception ?: RuntimeException(t)
                Log.w(TAG, "semantic($op) attempt ${attempt + 1} failed: ${t.message}")
                if (attempt + 1 < attempts) {
                    Thread.sleep(RETRY_BASE_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw last ?: IllegalStateException("semantic($op) failed")
    }

    private fun semanticOnce(op: String, paramsJson: String): JSONObject {
        val key = awaitSessionKey()
            ?: error("session key 未就绪：检查 so / ABI / ZQ_FILES_DIR")

        val token = hex(randomBytes(TOKEN_SIZE))
        val request = JSONObject()
            .put("token", token)
            .put("op", op)
            .put("params", paramsJson)
            .put("ad_signature", JSONObject.NULL)
            .put("authorization", JSONObject.NULL)
            .put("replay_safe", op in REPLAY_SAFE_OPS)
            .toString()

        val frame = sealFrame(FRAME_VERSION_SEMANTIC, request.toByteArray(Charsets.UTF_8), key)

        synchronized(linkLock) {
            try {
                val (socket, input) = ensureLink()
                socket.outputStream.write(frame)
                socket.outputStream.flush()

                val raw = readFrame(input, key)
                    ?: error("bad or empty response frame")
                val response = JSONObject(String(raw, Charsets.UTF_8))
                val status = response.optInt("status", -1)
                val tokenBack = response.optString("token", "")
                val bodyB64 = response.optString("body_b64", "")
                val body = if (bodyB64.isEmpty()) {
                    ""
                } else {
                    String(Base64.decode(bodyB64, Base64.NO_WRAP), Charsets.UTF_8)
                }

                Log.i(TAG, "op=$op status=$status tokenMatch=${tokenBack == token} bodyLen=${body.length}")
                return JSONObject()
                    .put("op", op)
                    .put("frameStatus", status)
                    .put("tokenMatch", tokenBack == token)
                    .put("body", body)
            } catch (t: Throwable) {
                closeLink()
                throw t
            }
        }
    }

    private fun ensureLink(): Pair<LocalSocket, DataInputStream> {
        val existing = linkSocket
        val existingInput = linkInput
        if (existing != null && existingInput != null) return existing to existingInput

        // 构造参数与官方 ZhuqueLocalSocket 保持一致。
        val socket = LocalSocket(SOCKET_TYPE_STREAM)
        socket.connect(
            LocalSocketAddress(socketPath(), LocalSocketAddress.Namespace.FILESYSTEM),
        )
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = DataInputStream(socket.inputStream)
        linkSocket = socket
        linkInput = input
        Log.i(TAG, "UDS connected: ${socketPath()}")
        return socket to input
    }

    private fun closeLink() {
        runCatching { linkSocket?.close() }
        linkSocket = null
        linkInput = null
    }

    // ---------------------------------------------------------------------
    // ZQF1 帧
    // ---------------------------------------------------------------------

    private fun sealFrame(version: Byte, payload: ByteArray, key: ByteArray): ByteArray {
        val iv = randomBytes(IV_SIZE)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(payload)

        val out = ByteArray(HEADER_SIZE + cipherText.size)
        out[0] = FRAME_MAGIC_0
        out[1] = FRAME_MAGIC_1
        out[2] = FRAME_MAGIC_2
        out[3] = FRAME_MAGIC_3
        out[4] = version
        System.arraycopy(iv, 0, out, 5, IV_SIZE)
        val length = cipherText.size
        out[17] = (length ushr 24).toByte()
        out[18] = (length ushr 16).toByte()
        out[19] = (length ushr 8).toByte()
        out[20] = length.toByte()
        System.arraycopy(cipherText, 0, out, HEADER_SIZE, cipherText.size)
        return out
    }

    private fun readFrame(input: DataInputStream, key: ByteArray): ByteArray? {
        val header = ByteArray(HEADER_SIZE)
        input.readFully(header)
        if (header[0] != FRAME_MAGIC_0 || header[1] != FRAME_MAGIC_1 ||
            header[2] != FRAME_MAGIC_2 || header[3] != FRAME_MAGIC_3
        ) {
            return null
        }
        val iv = header.copyOfRange(5, 17)
        val length = ((header[17].toInt() and 0xFF) shl 24) or
            ((header[18].toInt() and 0xFF) shl 16) or
            ((header[19].toInt() and 0xFF) shl 8) or
            (header[20].toInt() and 0xFF)
        if (length < GCM_TAG_BYTES || length > MAX_FRAME_BYTES) return null

        val cipherText = ByteArray(length)
        input.readFully(cipherText)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    // ---------------------------------------------------------------------
    // DeviceContext
    // ---------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun buildDeviceContext(): String {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

        val certDer = signatures().firstOrNull()?.toByteArray() ?: ByteArray(0)
        val metrics = appContext.resources.displayMetrics
        val deviceId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.takeIf { it.isNotBlank() } ?: "Unknown"

        return JSONObject()
            .put("fp", deviceId)
            .put("dn", "${Build.BRAND} ${Build.MODEL}".trim().ifBlank { "Unknown" })
            .put("dm", "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Unknown" })
            .put("os", "Android")
            .put("osv", Build.VERSION.RELEASE ?: "Unknown")
            .put("av", versionName)
            .put("avc", versionCode)
            .put("pkg", appContext.packageName)
            .put("asig", if (certDer.isEmpty()) "" else hex(sha256(certDer)))
            .put("hv", "0")
            .put("pt", "android")
            .put("dt", if (appContext.resources.configuration.smallestScreenWidthDp >= 600) 2 else 1)
            .put("sw", metrics.widthPixels)
            .put("sh", metrics.heightPixels)
            .put("lang", Locale.getDefault().toLanguageTag())
            .put("tz", TimeZone.getDefault().id)
            .put("net", networkType())
            .put("ts", 0L)
            .put("bi", biEncode(versionName, versionCode))
            .put("acert", if (certDer.isEmpty()) "" else Base64.encodeToString(certDer, Base64.NO_WRAP))
            .toString()
    }

    @Suppress("DEPRECATION")
    private fun signatures(): Array<Signature> {
        val pm = appContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNATURES)
                .signatures ?: emptyArray()
        }
    }

    private fun networkType(): String = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching "other"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching "other"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }.getOrDefault("other")

    /** 与官方 DeviceContextBiEncoder 等价的零宽字符编码。 */
    private fun biEncode(appVersion: String, versionCode: Long): String {
        val mask = 16 // specialEdition = false
        return buildString {
            for (bit in 7 downTo 4) append(if ((1 shl bit) and mask != 0) '\u2060' else '\u200B')
            append(appVersion).append('-').append(versionCode)
            for (bit in 3 downTo 0) append(if ((1 shl bit) and mask != 0) '\u2060' else '\u200B')
        }
    }

    private fun socketPath(): String =
        appContext.filesDir.absolutePath + "/.zqsock-" + Process.myPid()

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            append(HEX_CHARS[value ushr 4])
            append(HEX_CHARS[value and 0x0F])
        }
    }

    private companion object {
        const val TAG = "AniRpbTransport"

        const val OP_TYPE_LIST_ACTIVE = "type.listActive"
        const val OP_HOME_LOAD = "home.load"

        /** 与官方 RdicReplaySafeOps 对齐；home.load 不在集合里，必须为 false。 */
        val REPLAY_SAFE_OPS = setOf(
            "type.listActive",
            "search.suggest",
            "calendar.videos",
            "video.categoryList",
            "video.detail",
            "resolve.episodes",
            "resolve.qualities",
            "comment.list",
            "comment.replies",
            "review.list",
            "review.summary",
        )

        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
        const val TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val KEY_SIZE = 32
        const val IV_SIZE = 12
        const val TOKEN_SIZE = 16
        const val HEADER_SIZE = 21
        const val MAX_FRAME_BYTES = 8 * 1024 * 1024
        const val SOCKET_TIMEOUT_MS = 60_000
        const val SESSION_KEY_TIMEOUT_MS = 20_000L
        const val KEY_POLL_INTERVAL_MS = 200L
        const val RETRY_BASE_DELAY_MS = 800L
        const val SOCKET_TYPE_STREAM = 2

        val FRAME_VERSION_SEMANTIC: Byte = 3
        val FRAME_MAGIC_0: Byte = 0x5A.toByte() // Z
        val FRAME_MAGIC_1: Byte = 0x51.toByte() // Q
        val FRAME_MAGIC_2: Byte = 0x46.toByte() // F
        val FRAME_MAGIC_3: Byte = 0x31.toByte() // 1

        const val HEX_CHARS = "0123456789abcdef"
    }
}

