package com.liomix.mikomi.data.video.remote.vdoplg.store

import android.content.Context
import java.io.File

/**
 * 插件源在本地磁盘上的落点。
 *
 * 抽成一处是因为有两样东西要写盘：插件正文与序列状态。
 * 路径散在两个类里，改一次目录就要改两处，而且很容易只改对其中一处。
 *
 * 术语上这里一律叫 **plugin（视频插件源）**，不叫 rule：
 * 一份 json 在 Kazumi 那边叫「规则」，在这边它就是一个可安装/可停用的视频插件源。
 *
 * 线路能力缓存**不在这里**：它属于三大源共用（`filesDir/vdo/lines.json`），
 * 见模块根包的 `VdoLineCapabilityStore`。
 */
internal object VdoPlgPaths {

    /** `filesDir/vdoplugin`：插件与状态都在它下面。 */
    const val ROOT = "vdoplugin"

    /** 插件正文目录，一个插件一个文件。 */
    const val PLUGINS = "plugin"

    /** 顺序与启停。 */
    const val STATE_FILE = "state.json"

    /**
     * 首次播种标记，存在即表示「已经种过一次」。
     *
     * 刻意放在**正文目录内部**而不是根目录：标记要跟着它标记的东西走。
     * 曾经放在根上，结果正文目录换了名字、标记还在，播种被跳过 → 首次启动一个插件都没有。
     */
    const val SEEDED_MARKER = ".seeded"

    fun root(context: Context): File = File(context.filesDir, ROOT)

    fun plugins(context: Context): File = File(root(context), PLUGINS)
}
