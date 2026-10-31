package com.yy.myuko.component.ktor.addressing

/**
 * libzhuque.so 的 JNI 桥接类。
 *
 * 包名、类名和方法名必须与官方库导出的符号完全一致：
 * `Java_com_yy_myuko_component_ktor_addressing_ZhuqueSessionNativeLib_nativeSessionKey`
 * 等。不要移动、重命名或混淆这个类。
 */
object ZhuqueSessionNativeLib {

    /** 传入 DeviceContext JSON（UTF-8 字节），返回原生层是否接受。 */
    external fun nativeDeviceContextInit(deviceContextJson: ByteArray): Boolean

    /** 网络状态变化通知；当前不使用。 */
    external fun nativeNetworkChanged(online: Boolean, timestampMs: Long)

    /** 返回 32 字节的本地帧加密密钥；未就绪时可能返回 null。 */
    external fun nativeSessionKey(): ByteArray?
}
