package com.liomix.mikomi.data.video.remote.vdoplugin.store

import android.content.Context
import java.io.File

/**
 * 插件源的规则在本地磁盘上的落点。
 *
 * 抽成一处是因为有两样东西要写盘：规则正文与序列状态。
 * 路径散在两个类里，改一次目录就要改两处，而且很容易只改对其中一处。
 *
 * 线路能力缓存**不在这里**：它属于三大源共用（`filesDir/vdo/lines.json`），
 * 见 `remote` 包下的 `VdoLineCapabilityStore`。
 */
internal object VdoPluginPaths {

    /** `filesDir/vdoplugin`：插件源的规则与状态都在它下面。 */
    const val ROOT = "vdoplugin"

    /** 规则正文目录，一个规则一个文件。 */
    const val RULES = "rules"

    /** 顺序与启停。 */
    const val STATE_FILE = "state.json"

    /** 首次播种标记，存在即表示「已经种过一次」。 */
    const val SEEDED_MARKER = ".seeded"

    fun root(context: Context): File = File(context.filesDir, ROOT)
}
