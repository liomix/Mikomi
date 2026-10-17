# Mikomi 项目结构（最佳实践基线）

> 本文档依据 `android-rule.md` 项目规范，并结合官方 skills（adaptive、edge-to-edge、navigation-3、navigation-event、jetpack-compose-performance、android-intent-security、android-permissions-security、r8-analyzer、testing-setup）交叉验证，给出 Mikomi 看番 App 的推荐工程结构。
> 本文档为**默认基线**；如需偏离，走例外流程并登记到 `docs/exceptions.md`。

---

## 1. 架构概览

- 架构：**MVVM + 单向数据流（UDF）+ Repository**，三层分层（UI / Domain 可选 / Data）。
- 模块化：**多模块**，命名 `feature-{name}` / `core-{name}` / `data-{name}`，构建配置由 `build-logic` 约定插件统一管理。
- 离线优先：**Room 为结构化数据单一可信来源（SSOT）**，Retrofit + OkHttp 拉取网络数据回写本地，UI 只观察本地 `Flow`。
- 基础包名：`com.liomix.mikomi`（与现有 `applicationId` / `namespace` 保持一致）。
- 技术栈基线见 `android-rule.md` 附录（Kotlin / Compose M3 / Navigation 3 / Hilt / Room / DataStore / Retrofit+OkHttp+kotlinx.serialization / Coil / Media3 / R8+Baseline Profiles+Macrobenchmark）。

### 依赖方向（硬性约束）

```text
        ┌─────────────────────────────┐
        │            app              │  ← 装配入口：NavHost、DI、Edge-to-Edge
        └──────────────┬──────────────┘
                       │ 依赖
        ┌──────────────▼──────────────┐
        │      feature-{name}         │  ← 只能依赖 core-* / data-* / feature-api
        └──────┬─────────────┬────────┘
               │ 依赖         │ 依赖
        ┌──────▼─────┐  ┌────▼─────┐
        │  data-{name}│  │ core-*   │
        └──────┬─────┘  └──────────┘
               │ 依赖
        ┌──────▼─────┐
        │   core-*   │  ← 不依赖 feature-* / data-*
        └────────────┘
```

规则：
- `feature-{name}` → 依赖 `core-*` / `data-*`；禁止直接依赖其它 feature 的实现模块，跨 feature 复用抽到 `core-*` 或 `feature-api`。
- `core-{name}` → 不依赖 `feature-*` / `data-*`；不依赖 UI 层。
- `data-{name}` → 不依赖 UI 层（`feature-*`）。
- 禁止循环依赖；对外只暴露最小 API（接口 `api`，实现 `internal`）。

---

## 2. 顶层目录结构

```text
Mikomi/
├─ app/                       # 应用入口模块（Application / Activity / NavHost / DI 装配）
├─ build-logic/               # 约定插件（convention plugins）
│  └─ convention/
├─ core/                      # 基础能力层（跨 feature 复用，无业务归属）
│  ├─ model/                  # 领域模型 + 统一结果类型（纯 Kotlin）
│  ├─ common/                 # 通用工具、调度器、扩展
│  ├─ designsystem/           # Material3 主题 + 设计 tokens + 基础自适应组件
│  ├─ ui/                     # 共享 Compose 组件 + 预览/Insets 工具
│  ├─ navigation/             # 类型安全路由 + NavEntry 基础设施 + SceneStrategy 配置
│  ├─ database/               # Room（SSOT）
│  ├─ datastore/              # DataStore（用户偏好）
│  ├─ network/                # Retrofit + OkHttp + kotlinx.serialization
│  ├─ media/                  # Media3 播放器封装
│  ├─ analytics/              # 埋点（snake_case 事件 schema）
│  └─ testing/                # 共享测试工具（Fake、Turbine、Dispatcher Rule）
├─ data/                      # 数据层（Repository 门面 + 远程/本地数据源）
│  ├─ anime/                  # 动漫信息
│  ├─ video/                  # 视频源 / 播放信息
│  └─ user/                   # 收藏 / 历史 / 偏好
├─ feature/                   # 功能层（Route+Screen+ViewModel）
│  ├─ home/                   # 首页 / 推荐
│  ├─ search/                 # 搜索 / 筛选
│  ├─ detail/                 # 动漫详情
│  ├─ player/                 # 播放器
│  ├─ library/                # 我的追番 / 收藏
│  ├─ history/                # 观看历史
│  └─ settings/               # 设置
├─ benchmark/                 # Macrobenchmark + Baseline Profiles
├─ gradle/
│  ├─ libs.versions.toml      # 版本目录（唯一版本来源）
│  └─ wrapper/
├─ docs/                      # 文档（project-structure.md / exceptions.md）
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
└─ .gitignore
```

---

## 3. 各模块详细结构

### 3.1 `app` — 应用入口

```text
app/src/main/
├─ kotlin/com/liomix/mikomi/
│  ├─ MikomiApp.kt              # @HiltAndroidApp Application
│  ├─ MainActivity.kt           # setContent 前 enableEdgeToEdge()；adjustResize
│  ├─ navigation/
│  │  └─ MikomiNavHost.kt       # NavDisplay + 各 feature 的 entryProvider 装配
│  └─ di/
│     └─ AppModule.kt           # (可选) 全局绑定
├─ res/
│  ├─ values/strings.xml        # 仅框架级文案（业务文案下沉到各 feature）
│  ├─ values/themes.xml         # 系统主题（背景、状态栏对比度）
│  └─ mipmap-*/                  # 应用图标
└─ AndroidManifest.xml          # exported 声明、adjustResize、预测性返回开关
```

要点（对应 skills）：
- `MainActivity` 在 `setContent` 前调用 `enableEdgeToEdge()`；含软键盘的 Activity 设 `android:windowSoftInputMode="adjustResize"`（edge-to-edge）。
- 私有组件 `android:exported="false"`；`android:enableOnBackInvokedCallback` 不为 `false`（navigation-event）。
- Release 启用 R8 + 资源压缩，`proguard-rules.pro` 保持窄而具体（r8-analyzer）。
- manifest 权限最小化：媒体选择用 Photo Picker，不申请 `READ_MEDIA_*`（android-permissions-security）。

### 3.2 `feature` 模块（以 `feature/home` 为例，其余同构）

```text
feature/home/src/
├─ main/kotlin/com/liomix/mikomi/feature/home/
│  ├─ HomeRoute.kt              # 有状态：连接 ViewModel、收集状态、回调
│  ├─ HomeScreen.kt             # 无状态：纯 UI、可预览、可测试
│  ├─ HomeViewModel.kt          # @HiltViewModel，StateFlow<HomeUiState>
│  ├─ HomeUiState.kt            # UiState data/sealed class
│  ├─ component/
│  │  ├─ AnimeCard.kt           # 列表项组件（稳定 key + contentType）
│  │  ├─ HomeTopBar.kt
│  │  └─ RankingSection.kt
│  ├─ navigation/
│  │  └─ HomeNavigation.kt      # @Serializable route + entryProvider（Hilt）
│  └─ di/
│     └─ HomeModule.kt          # (可选) 若需 feature 级绑定
├─ test/kotlin/...              # HomeViewModelTest、HomeScreenTest（Robolectric）
├─ androidTest/kotlin/...       # 设备 UI 测试、导航测试、无障碍扫描
└─ screenshotTest/kotlin/...    # Compose Preview 截图测试（多尺寸/主题/字体）
```

功能对应关系：

| feature 模块 | 核心职责 | 关键 skills |
|---|---|---|
| `home` | 推荐位、番剧列表/排行榜、下拉刷新 | adaptive、jetpack-compose-performance |
| `search` | 关键字搜索、按类型/年代/状态筛选 | 同上 |
| `detail` | 简介、剧集列表、收藏入口、追番状态 | navigation-3（多窗格列表-详情） |
| `player` | Media3 播放、倍速/清晰度/进度记忆、PiP | media3、navigation-event（预测性返回） |
| `library` | 收藏 / 我的追番、分类浏览 | — |
| `history` | 观看历史、断点续播 | — |
| `settings` | 主题、播放设置、关于、数据/隐私声明 | — |

Compose 规范（每个 feature 都要遵守，见 rule §2.4/2.5）：
- Route + Screen 双组件；状态提升到最低公共祖先。
- `modifier: Modifier = Modifier` 首个可选参数；Slot API。
- 懒列表 `key` + `contentType`；`collectAsStateWithLifecycle()`；Coil `AsyncImage`。
- 自适应：竖列表按宽度切 `LazyVerticalGrid(GridCells.Adaptive(...))`；底部导航用 `NavigationSuiteScaffold`，多窗格用 Navigation 3 `SceneStrategy`。

### 3.3 `data` 模块（以 `data/anime` 为例）

```text
data/anime/src/main/kotlin/com/liomix/mikomi/data/anime/
├─ AnimeRepository.kt           # 接口（api 暴露，实现 internal）
├─ internal/
│  └─ AnimeRepositoryImpl.kt    # 协调本地 Room + 远程网络，暴露 Flow
├─ remote/
│  ├─ AnimeApi.kt               # Retrofit 接口
│  └─ dto/                      # AnimeDto.kt / EpisodeDto.kt（网络 DTO）
├─ mapper/
│  └─ AnimeMapper.kt            # DTO ↔ Entity ↔ Domain 三向映射
└─ di/
   └─ AnimeDataModule.kt        # @Binds AnimeRepository → Impl
```

分层模型（rule §4.2）：**Network DTO、Room Entity、Domain Model 三者独立**，Repository 负责协调与转换，暴露 `Flow`。
错误处理（rule §4.3）：Repository 抛出领域异常或返回 `Result`/sealed class，不得直接返回用户文案。

### 3.4 `core` 模块

| 模块 | 关键内容 |
|---|---|
| `core:model` | 领域模型：`Anime` / `Episode` / `VideoSource` / `Favorite` / `WatchHistory` / `UserPreferences`；统一 `Result<T>` + 领域异常。**纯 Kotlin，无 Android 依赖** |
| `core:common` | `@Dispatcher(IO/Default)` 调度器限定符与 `DispatchersModule`；通用扩展、时间格式化等工具 |
| `core:designsystem` | `theme/`（Color/Type/Shape/Theme）、设计 tokens、基础自适应组件（`AdaptiveScaffold` 等）。**统一主题来源，业务页禁止散落颜色/字号常量** |
| `core:ui` | 共享组件（Loading/Error/Empty、封面图、通用列表项）、WindowSize 工具、预览工具 |
| `core:navigation` | 类型安全路由对象（`@Serializable`）、NavEntry DSL 基础设施、`SceneStrategy` 配置 helper、`NavigationBackHandler` 封装 |
| `core:database` | `MikomiDatabase`、`entity/`、`dao/`、`converter/`、`migration/`、`di/DatabaseModule`；`schemas/` 提交版本库 |
| `core:datastore` | `UserPreferencesDataSource`、Proto/Preferences 定义、`di/DataStoreModule`；单例实例 |
| `core:network` | `MikomiNetwork`（OkHttp/Retrofit 工厂）、`AuthInterceptor`（Token 来自 Keystore 加密存储）、脱敏日志拦截器、`JsonConfig`、`di/NetworkModule` |
| `core:media` | `MikomiPlayer`、播放状态、`PlaybackController`、`di/MediaModule` |
| `core:analytics` | `AnalyticsEvent`（snake_case schema 集中定义）、`AnalyticsTracker`（异步、失败限次重试）、`di/AnalyticsModule` |
| `core:testing` | 共享 Fake（FakeRepository/FakeDao/FakeApi）、`MainDispatcherRule`、Turbine 扩展 |

### 3.5 `build-logic` — 约定插件

```text
build-logic/
├─ settings.gradle.kts
└─ convention/
   ├─ build.gradle.kts
   └─ src/main/kotlin/com/liomix/mikomi/buildlogic/
      ├─ MikomiApplicationPlugin.kt    # app：R8、flavor、签名配置
      ├─ MikomiFeaturePlugin.kt        # feature：Compose + Hilt + 测试
      ├─ MikomiDataPlugin.kt           # data：Room + Network + Serialization
      ├─ MikomiCorePlugin.kt           # core：按需组合（model 无 Android 依赖）
      ├─ AndroidComposePlugin.kt       # Compose 编译器（与 Kotlin 绑定）+ Strong Skipping
      ├─ AndroidHiltPlugin.kt          # Hilt + KSP
      ├─ AndroidRoomPlugin.kt          # Room + KSP + schemaLocation 导出
      ├─ AndroidNetworkPlugin.kt       # Retrofit/OkHttp/serialization
      ├─ AndroidLintPlugin.kt          # ktlint + Detekt + Android Lint
      ├─ AndroidTestPlugin.kt          # JUnit5(本地) / AndroidX Test(仪器化) / Robolectric / 截图
      └─ AndroidFlavorPlugin.kt        # dev/staging/prod flavors
```

### 3.6 `benchmark` 模块

```text
benchmark/src/main/kotlin/...
├─ ColdStartBenchmark.kt       # 冷启动
├─ HomeScrollBenchmark.kt      # 主要滚动流
└─ BaselineProfileGenerator.kt # Baseline Profiles 生成
```

配合 R8 与 Macrobenchmark 回归，劣化即阻塞合并（rule §7.1/§10.4）。

---

## 4. 版本目录组织（`gradle/libs.versions.toml`）

按关注点分组，**所有版本唯一来源**，禁止在各模块硬编码版本号；依赖名用 kebab-case，Compose 用 bundle 统一管理。

```toml
[versions]
agp = "..."
kotlin = "..."
compose-bom = "..."
navigation3 = "..."
navigation-event = "..."
hilt = "..."
room = "..."
datastore = "..."
retrofit = "..."
okhttp = "..."
kotlinx-serialization = "..."
coil = "..."
media3 = "..."
ksp = "..."
junit5 = "..."
robolectric = "..."
turbine = "..."
# ...

[libraries]
# Compose（bundle 统一管理）
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material3-adaptive = { group = "androidx.compose.material3.adaptive", name = "adaptive" }
# ...

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
# ...
```

---

## 5. 测试结构（rule §10 + testing-setup）

| 测试类型 | 目录 | 环境 | 工具 |
|---|---|---|---|
| 单元测试（ViewModel/Repository/UseCase/工具） | `src/test/kotlin` | 本地 JVM | JUnit5 + Turbine |
| Compose UI 行为测试 | `src/test/kotlin` | JVM + Robolectric | Compose Testing + `DeviceConfigurationOverride` |
| 集成 / UI / 导航 / 无障碍扫描 | `src/androidTest/kotlin` | 设备/模拟器 | AndroidX Test + semantics API |
| 截图测试 | `src/screenshotTest/kotlin` | — | Compose Preview Screenshot Testing + Dropshots |
| 数据库迁移测试 | `core/database/src/androidTest` | 设备/模拟器 | `androidx.room:room-testing` MigrationTestHelper |
| 性能基准 | `benchmark/src/main` | 设备 | Macrobenchmark |

覆盖率目标：核心业务逻辑 ≥ 80%。

---

## 6. 技能 ↔ 模块映射

| 官方 skill | 落点模块 / 目录 |
|---|---|
| `adaptive` | `core:designsystem`、`core:ui`、各 feature（NavigationSuiteScaffold / SceneStrategy / Adaptive 网格） |
| `edge-to-edge` | `app/MainActivity`、`core:designsystem`（theme、系统栏对比度） |
| `navigation-3` | `core:navigation`、各 feature 的 `navigation/`、`app/navigation` |
| `navigation-event` | `core:navigation`（NavigationBackHandler）、`app`（预测性返回开关） |
| `jetpack-compose-performance` | 各 feature Screen/组件 + `core:designsystem`（Strong Skipping、懒列表、阶段延迟） |
| `android-intent-security` | `app`（manifest/exported）、`core:common`（IntentSanitizer 等安全工具） |
| `android-permissions-security` | `app`（manifest）、`core:common`（三态权限流程封装） |
| `r8-analyzer` | `app/proguard-rules.pro`、`build-logic`（R8 配置） |
| `testing-setup` | 各模块 test/androidTest/screenshotTest、`core:testing`、`build-logic`（测试约定插件） |
| `media3-cast-integration` / 播放 | `core:media`、`feature:player` |

---

## 7. 落地顺序建议

1. `build-logic/convention` 约定插件 + `libs.versions.toml` 基线（依赖/插件/KSP/Hilt/Compose/Room）。
2. `core:model` → `core:common` → `core:designsystem` → `core:ui`（无业务的基础层）。
3. `core:database` → `core:datastore` → `core:network`（数据基础设施，含 schema 导出与迁移）。
4. `data:anime` → `data:video` → `data:user`（Repository 门面）。
5. `core:navigation` + `core:media` + `core:analytics`。
6. 各 `feature` 模块 + `app` 装配（NavHost、Hilt、edge-to-edge）。
7. 测试基建（`core:testing` + 各模块测试）+ `benchmark`。
