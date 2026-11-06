package com.liomix.mikomi

import android.app.Application
import com.liomix.mikomi.core.common.dispatcher.DefaultDispatcher
import com.liomix.mikomi.core.designsystem.theme.warmUpColorSchemes
import com.liomix.mikomi.core.ui.image.MikomiImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MikomiApp : Application() {

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    override fun onCreate() {
        super.onCreate()
        // 图片加载器的配置必须在**第一个图片请求之前**装好，否则缓存上限与 UA 都不生效
        // 且不报错。`Application.onCreate` 是最早的时机。
        MikomiImageLoader.install(this)
        // 主题方案原本在「第一次点日夜开关」那一帧才构建：`darkColorScheme()` 的类初始化、
        // 夜间主色为凑对比度跑的多轮 lerp + luminance、三次 ColorScheme.copy，全挤在那 16ms 里。
        // 冷启动后的第一次切换因此会明显顿一下。这里在后台线程先把所有配色的日夜两套都算出来，
        // 点击时只剩一次 Map 命中。
        //
        // 进程级作用域：没有界面生命周期可依附，进程结束即回收，也不需要取消。
        CoroutineScope(SupervisorJob() + defaultDispatcher).launch { warmUpColorSchemes() }
    }
}
