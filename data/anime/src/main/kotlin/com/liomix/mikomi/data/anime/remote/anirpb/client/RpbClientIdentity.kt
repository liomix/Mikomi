package com.liomix.mikomi.data.anime.remote.anirpb.client

/**
 * 上报给 RDIC 的客户端身份。
 *
 * 服务端会校验 DeviceContext 的 `pkg` 与 `asig`：两者必须同时等于官方值，`acert` 不参与判定。
 * 任一不符时接口仍返回 200，但会把列表里每条记录的 `title` 改写成
 * 「当前为破解版 请到官网安装正版 www.dongman.to」，并清空 `description` / `director` /
 * `posterUrl` 等字段 —— 表现为"数据源坏了"，其实是服务端的软屏蔽。
 *
 * 2026-11-22 真机对照实验（同一 op，只改这两个字段）：
 * 本机 pkg + 本机签名 → 失败；官方 pkg + 本机签名 → 失败；
 * 本机 pkg + 官方签名 → 失败；两者都官方 → 正常。
 *
 * `av` / `avc` / `acert` 不是判定项（`av` 传本机版本号同样能取到数据），但既然要扮演官方客户端，
 * 就一并上报官方值，避免 `bi`（零宽字符里编码的版本串）与 `av` 自相矛盾。
 */
internal object RpbClientIdentity {

    /** 官方包名 */
    const val PACKAGE_NAME: String = "com.sdmnfowa.soad.p1"

    /** 官方版本名（`av`；同时用于 `bi` 里的版本串） */
    const val VERSION_NAME: String = "1.0.13"

    /** 官方版本号（`avc`；同时用于 `bi` 里的版本号） */
    const val VERSION_CODE: Long = 114L

    /** 官方签名证书 DER 的 SHA-256（取自官方 APK 的 META-INF/YZS.RSA） */
    const val CERT_SHA256: String = "de17140c22b492a9e38d188c675a185d9d67e1607342f03ed0a181d9f3696e74"

    /** 官方签名证书 DER 的 Base64 */
    const val CERT_BASE64: String =
        "MIIDRzCCAi+gAwIBAgIEOZfzfDANBgkqhkiG9w0BAQsFADBTMQswCQYDVQQGEwJjbjELMAkGA1UECBMCYnYxCzAJBg" +
        "NVBAcTAnBrMQ0wCwYDVQQKEwR0amF4MQ0wCwYDVQQLEwR0amF4MQwwCgYDVQQDEwN5enMwIBcNMjQwOTExMDEyODQ3" +
        "WhgPMjA2MzAxMTAwMTI4NDdaMFMxCzAJBgNVBAYTAmNuMQswCQYDVQQIEwJidjELMAkGA1UEBxMCcGsxDTALBgNVBA" +
        "oTBHRqYXgxDTALBgNVBAsTBHRqYXgxDDAKBgNVBAMTA3l6czCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB" +
        "AKbcaD5vhksvYyvUvtLQvvacGv129oQE0dgTwxdfOHQAN1V3lcayud6BM3nY7Nr8YOUAWxS90fp9XhR3izmIXhYvH2" +
        "ZvhsLaboICdmI1SAR35/rvOlxCeuzo5GEFF1e3ZcjU/BDUc/rgDU1jcoX6X5Kadhy/oo1mxr2fwRmVnU2ttvk5H8tM" +
        "Qjn/g6ReLnhfgyd866kdkyGC3BxMcER6P3dyEKNdqxBQseS8lxkWuPbi4V9hVcX6xAg4OMsxCXhp3BhmM7K2IJo73O" +
        "1qXbKL3VltUHAMO8Sj6BLU4e+ZAUAChppQlJ2X3Pbdy+q5wFSWukOX7SZMjZonBW64FtKcVKsCAwEAAaMhMB8wHQYD" +
        "VR0OBBYEFH+FmOVzIiG1d8ynjKIWUhaUo01dMA0GCSqGSIb3DQEBCwUAA4IBAQAg6UheCCOipgyP7gBHxQfI1gpClv" +
        "XyFZg0MQz62hLnDzW4XQZNm3dw5t/FMb8f3FHT+JQ92GAw1kcu8a+ZyhDITMsalEToc/VUnwt1D7OTGIQsq6YABpxC" +
        "vGOewZb1U/jxInB0U725CIurNab8Y1aspbZUiohKQ/qjwXmmZph1UMmXX5I4bXtsh0ueZlI3pdKCyH+nX1HhZiz3Ut" +
        "V2U9hljmm0PGvcyTv2S/1Zw2HLJkXS6NS/Or5JsiiIdwJeor3Fns57Ui9ENrLZyert1h8UP24W+AZfOIMTrl1x+OUd" +
        "5cy5WC7J2VypY6VI+hgy1NsPb8EqTXahznIVqRZ6irhO"
}
