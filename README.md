<p align="center">
  <img src="https://raw.githubusercontent.com/liomix/Mikomi/main/docs/logo.png" alt="Mikomi Logo" width="160" />
</p>

<h1 align="center">Mikomi</h1>

<p align="center">
  一个现代、离线优先、适配多设备的 Android 追番（动漫）应用。
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-brightgreen.svg" />
  <img alt="Language" src="https://img.shields.io/badge/language-Kotlin-blueviolet.svg" />
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24-orange.svg" />
  <img alt="Compile SDK" src="https://img.shields.io/badge/compileSdk-37-green.svg" />
  <img alt="AGP" src="https://img.shields.io/badge/AGP-9.2.1-blue.svg" />
  <img alt="License" src="https://img.shields.io/badge/license-MIT-lightgrey.svg" />
</p>

<p align="center">
  <a href="#-特性">特性</a> ·
  <a href="#-技术栈">技术栈</a> ·
  <a href="#-架构">架构</a> ·
  <a href="#-模块结构">模块结构</a> ·
  <a href="#-快速开始">快速开始</a> ·
  <a href="#-贡献">贡献</a>
</p>

---

Mikomi 是一款基于现代 Android 技术栈构建的追番 App，专注于流畅的浏览体验、可追踪的追番进度，以及覆盖手机 / 折叠屏 / 平板 / 桌面的自适应界面。项目遵循严格的架构规范与**离线优先（Offline-First）**策略，是学习 Jetpack Compose 多模块工程、类型安全导航与现代 Android 最佳实践的理想范例。

> ⚠️ 项目处于早期开发阶段，功能与 API 仍在快速迭代。

## ✨ 特性

- **追番与发现** — 首页推荐位、排行榜（Rank）、新番时间表（Schedule）、分类浏览，支持下拉刷新
- **搜索与筛选** — 关键字搜索、搜索建议、结果页，按类型 / 年代 / 状态筛选
- **番剧详情** — 简介（Overview）、剧集列表、制作人员（Credits）、吐槽（Teasing）多 Tab 切换
- **视频播放** — 基于 Media3 的播放器，支持倍速、清晰度、进度记忆
- **我的追番** — 收藏、观看历史与断点续播
- **巡礼地图（AniMap）** — WebView 承载的圣地巡礼地图，自动沉浸式体验
- **离线优先** — Room 作为结构化数据的单一可信来源（SSOT），网络数据回写本地，UI 只观察本地 `Flow`
- **自适应布局** — 手机 / 折叠屏 / 平板 / 桌面一屏适配，多窗格列表-详情
- **边到边体验** — 全屏沉浸 + 预测性返回动画
- **深度主题** — 多套配色预设 + 自适应对比度的日夜双模式
- **多数据源** — AniList（GraphQL）与 Bangumi 双源聚合

## 🛠 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.4.21 |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 |
| 架构 | MVVM + 单向数据流（UDF）+ Repository | — |
| 导航 | Jetpack Navigation 3（类型安全路由）+ navigationevent | 1.2.0 |
| 自适应 | Material3 Adaptive（NavigationSuiteScaffold / SceneStrategy） | 1.3.0 |
| 依赖注入 | Hilt（+ KSP） | 2.60.1 |
| 本地存储 | Room（结构化）+ DataStore（偏好） | Room 2.8.5 |
| 网络 | Retrofit + OkHttp + kotlinx.serialization | Retrofit 2.11.0 |
| 异步 | Coroutines + Flow | 1.10.1 |
| 图片加载 | Coil 3 | 3.6.3 |
| 播放 | Media3 (ExoPlayer) | 1.11.1 |
| 毛玻璃 | Haze | 1.7.3 |
| 构建 | Gradle Version Catalog + Convention Plugins | AGP 9.2.1 |
| 代码检查 | ktlint + Detekt + Android Lint | — |
| 性能 | R8 + Baseline Profiles + Macrobenchmark | — |

---

## 🏛 架构

Mikomi 采用**多模块、严格分层的 MVVM + UDF 架构**。核心原则是：

1. **单向数据流** — 状态自 ViewModel 向下流向 UI，事件自 UI 向上传递给 ViewModel
2. **离线优先** — Room 是结构化数据的单一可信来源（SSOT），网络仅作为数据「刷新」通道
3. **依赖单向** — `feature` → `data` / `core`，`core` 不依赖任何上层，禁止循环依赖
4. **约定优于配置** — 所有模块的构建逻辑由 `build-logic` 约定插件统一管理

### 分层依赖图

```
                        ┌─────────────────────────────┐
                        │            app              │  装配入口
                        │  Application / Activity /   │  NavHost / DI / Edge-to-Edge
                        │        MikomiNavHost        │
                        └──────────────┬──────────────┘
                                       │ 依赖
                        ┌──────────────▼──────────────┐
                        │      feature-{name}         │  功能层
                        │  Route + Screen + ViewModel │  只能依赖 core-* / data-*
                        └──────┬──────────────┬───────┘
                               │ 依赖          │ 依赖
                    ┌──────────▼──────┐  ┌─────▼──────────┐
                    │   data-{name}   │  │    core-*      │
                    │ Repository 门面 │  │  基础能力层    │
                    └──────────┬──────┘  └────────────────┘
                               │ 依赖
                    ┌──────────▼──────┐
                    │     core-*      │  不依赖 feature / data
                    └─────────────────┘
```

### 三层架构（MVVM + UDF）

每个功能模块内部遵循经典三层，数据只向内流动：

```
┌─────────────── UI 层 ───────────────┐
│  Screen (无状态)  ←  Route (有状态)  │
│   渲染 UiState        收集状态、传回调 │
└───────────────┬─────────────────────┘
                │ StateFlow<UiState> ↓  事件 ↑
┌───────────────▼─────────────────────┐
│  ViewModel (@HiltViewModel)          │
│  持有 UiState、调用 UseCase/Repository│
└───────────────┬─────────────────────┘
                │ suspend / Flow
┌───────────────▼─── Data 层 ─────────┐
│  Repository (门面，暴露 Flow)         │
│   ├── Remote (Retrofit / GraphQL)    │
│   └── Local  (Room / DataStore)      │
└──────────────────────────────────────┘
```

**Route + Screen 双组件**是每个屏幕的固定范式：

- **Route（有状态）** — 连接 ViewModel，`collectAsStateWithLifecycle()` 收集状态，通过回调把事件传给 ViewModel
- **Screen（无状态）** — 纯 UI 渲染，可预览、可测试，不直接持有任何业务状态

### 数据流：离线优先与 SSOT

Mikomi 的数据架构以 **Room 为单一可信来源**，网络请求不是「直接给 UI」，而是「回写本地」：

```
 UI 层               Data 层                          Remote
 ─────               ─────────                        ──────
  │  observe Flow      │                                │
  ├───────────────────►│  Room (SSOT) ◄── 回写 ──┐      │
  │  collect           │     ▲                     │     │
  │  StateFlow         │     │                      │     │
  │                    │  refresh*()                │     │
  │                    │     └─────────► Retrofit / GraphQL
  │                    │                        (AniList / Bangumi)
```

- **结构化数据**（番剧、剧集、收藏、历史、排行、时间表）→ Room，6 张 Entity，`version = 5`，`exportSchema = true` + 手写 `Migration`
- **非结构化偏好**（搜索历史、用户偏好）→ DataStore（`UserPreferencesDataSource` / `SearchHistoryDataSource`）
- **分层模型** — Network DTO、Room Entity、Domain Model 三者独立，由 `mapper/` 负责转换（如 `AniListMapper` / `BangumiMapper`）
- **错误处理** — Repository 抛出领域异常，UI 通过 `UiState` 集中映射，不返回用户文案字符串

### 导航系统

Mikomi 基于 **Jetpack Navigation 3** 的类型安全路由，采用自定义 `Navigator`（`@ActivityRetainedScoped`）持有返回栈：

```
core:navigation
 ├── Routes.kt            @Serializable 路由定义（data object / data class : NavKey）
 ├── Navigator.kt         返回栈管理（goTo / goBack / selectTopLevel）
 ├── NavigationModule.kt  Hilt 装配（ActivityRetainedComponent）
 ├── EntryProviderInstaller  各 feature 的 entry 注入接口
 └── ImmersionBridge      跨 entry 的沉浸状态桥（巡礼页专用）

app:navigation
 └── MikomiNavHost.kt     装配 NavDisplay + 底部导航栏 + 沉浸/返回协调
```

**路由定义**全部为 `@Serializable` 的 `NavKey`，禁止字符串拼接：

```kotlin
data object HomeDestination : NavKey          // 顶级目的地
data class DetailDestination(val animeId: String) : NavKey   // 参数化路由
data class PlayerDestination(val animeId: String, val episodeId: String) : NavKey
```

**关键设计**：

- **顶级目的地**：`HomeDestination` / `AniMapDestination` / `ProfileDestination`（`topLevelDestinations`）
- **返回栈**：单一 `SnapshotStateList<NavKey>`，`Navigator.goBack()` 在根页面返回 `false`，由调用方交给系统退出应用
- **多模块装配**：每个 feature 提供自己的 `entryProvider`，`MikomiNavHost` 遍历 `EntryProviderInstaller` 注入
- **沉浸式协调**：`ImmersionBridge` 作为权威副本存于 Activity Retained 作用域，跨 entry 重建与配置变更存活

### 依赖注入

统一使用 **Hilt**，作用域严格分层：

| 作用域 | 用途 | 示例 |
|--------|------|------|
| `@Singleton` | 全局单例 | 网络客户端、数据库 |
| `@ActivityRetainedScoped` | 跨配置变更存活的依赖 | `Navigator`、`ImmersionBridge` |
| `@ViewModelScoped` | 随屏幕 / ViewModel 生命周期 | 各 feature ViewModel |

Module 按职责拆分：`NetworkModule`（Retrofit/OkHttp）、`DatabaseModule`（Room）、`AniListModule` / `BangumiModule`（双源 Retrofit 限定符）、`DispatchersModule`（`@Dispatcher(IO)` 调度器注入）等。接口用 `@Binds`，第三方构造用 `@Provides`。

### 主题与设计系统

`core:designsystem` 是全局唯一的主题来源，业务页面禁止散落颜色 / 字号常量：

- **多配色预设** — `ThemePalette` 提供多套主色（默认蓝色），用户可在设置页切换
- **自适应对比度** — 夜间主色经 `Color.forNight()` 自动向白色提亮，直到纯黑背景上达到 ≥ 3.8 的对比度
- **日夜表面层级** — 夜间「纯黑底 + `#181818` 卡片」，日间「浅灰底 + 纯白卡片」，两种模式卡片都有清晰边界
- **`LocalContentColor` 兜底** — 在 `MikomiTheme` 内统一提供 `onBackground`，避免非 `Surface` 容器内的 `Text` / `Icon` 在夜间变黑

### 构建系统（build-logic）

所有模块通过约定插件统一管理，版本集中在 `gradle/libs.versions.toml`：

| 约定插件 | 职责 |
|----------|------|
| `MikomiApplicationConventionPlugin` | app 模块（R8 / flavor / 签名） |
| `MikomiComposeConventionPlugin` | Compose 编译器 + Strong Skipping |
| `MikomiCoreConventionPlugin` | core 模块 |
| `MikomiDataConventionPlugin` | data 模块 |
| `MikomiFeatureConventionPlugin` | feature 模块（Compose + Hilt + 测试） |
| `MikomiHiltConventionPlugin` | Hilt + KSP |
| `MikomiRoomConventionPlugin` | Room + KSP + schema 导出 |
| `MikomiSerializationConventionPlugin` | kotlinx.serialization |
| `MikomiLibraryConventionPlugin` | 通用 Android Library |

---

## 📦 模块结构

### `app` — 应用入口

```
app/src/main/kotlin/com/liomix/mikomi/
├── MikomiApp.kt           @HiltAndroidApp Application
├── MainActivity.kt        setContent 前 enableEdgeToEdge()
└── navigation/
    └── MikomiNavHost.kt   NavDisplay + 底部导航 + 沉浸/返回协调
```

### `core` — 基础能力层

| 模块 | 关键内容 |
|------|----------|
| `core:model` | 领域模型：`Anime` / `Episode` / `VideoSource` / `Comment` / `Credits` / `Rank` / `Schedule` / `Library` / `UserPreferences`。**纯 Kotlin，无 Android 依赖** |
| `core:common` | `@Dispatcher(IO/Default)` 调度器限定符 + `DispatchersModule` |
| `core:designsystem` | `theme/`（`Color` / `Theme` / `ThemePalette` / `Type`）+ `MikomiNavigationBar` |
| `core:ui` | 共享组件：`AnimeCoverImage` / `AnimePosterCard` / `Skeleton` / `StateViews` / `MikomiToast` / `ScrollingText` |
| `core:navigation` | `Routes` / `Navigator` / `NavigationModule` / `EntryProviderInstaller` / `ImmersionBridge` |
| `core:database` | `MikomiDatabase`（6 Entity，v5）+ `dao/` + `entity/` + `migration/` |
| `core:datastore` | `UserPreferencesDataSource` / `SearchHistoryDataSource` |
| `core:network` | `NetworkModule`（OkHttp / Retrofit 工厂、脱敏日志拦截器） |
| `core:media` | `MikomiPlayer` 播放器封装 |
| `core:analytics` | `Analytics` + `AnalyticsModule` |
| `core:testing` | `MainDispatcherRule` 等共享测试工具 |

### `data` — 数据层（Repository 门面）

| 模块 | 关键内容 |
|------|----------|
| `data:anime` | `AnimeRepository` / `HomeRepository` / `SearchRepository` / `RankRepository` / `ScheduleRepository` / `AnimeCommentRepository` / `AnimeCreditsRepository` + `internal/` 实现 + `mapper/` + `remote/{anilist,bangumi}` |
| `data:video` | `VideoRepository`（视频源 / 播放信息） |
| `data:user` | `UserDataRepository`（收藏 / 历史 / 偏好） |

`data:anime` 是数据层的核心，采用**接口 + internal 实现**分离：

```
data/anime/
├── AnimeRepository.kt           接口（api 暴露）
├── internal/                    internal 实现（AnimeRepositoryImpl 等）
├── mapper/                      DTO ↔ Entity ↔ Domain 三向映射
├── remote/
│   ├── anilist/                 AniListApi（GraphQL）+ AniListQueries + dto
│   └── bangumi/                 BangumiApi + dto
└── di/                          AniListModule / BangumiModule / AnimeDataModule
```

### `feature` — 功能层

| 模块 | 核心职责 |
|------|----------|
| `feature:home` | 推荐位、排行榜、新番时间表、分类浏览（`HomeRoute` / `RankRoute` / `ScheduleRoute`） |
| `feature:animap` | 圣地巡礼地图（WebView 承载 + 沉浸式） |
| `feature:profile` | 个人主页 |
| `feature:search` | 搜索、搜索建议、结果页（`SearchRoute` / `SearchResultRoute`） |
| `feature:detail` | 番剧详情（Overview / Credits / Teasing 多 Tab） |
| `feature:player` | Media3 播放器 |
| `feature:library` | 我的追番 / 收藏 |
| `feature:history` | 观看历史 |
| `feature:settings` | 主题设置、应用图标切换、关于 |

每个 feature 遵循统一结构（以 `home` 为例）：

```
feature/home/src/main/kotlin/com/liomix/mikomi/feature/home/
├── HomeRoute.kt            有状态：连接 ViewModel、收集状态
├── HomeScreen.kt           无状态：纯 UI
├── HomeViewModel.kt        @HiltViewModel，StateFlow<HomeUiState>
├── HomeUiState.kt          UiState
├── component/              列表项组件
├── navigation/             HomeNavigationModule（entryProvider）
├── rank/                   排行榜子页面（Route + Screen + ViewModel）
└── schedule/               时间表子页面
```

### `benchmark` & `build-logic`

- `benchmark` — Macrobenchmark（冷启动 / 滚动流）+ Baseline Profiles 生成
- `build-logic` — 9 个约定插件，见上文构建系统

---

## 🚀 快速开始

### 环境要求

| 项 | 要求 |
|----|------|
| Android Studio | 最新稳定版 |
| JDK | 17+ |
| compileSdk | 37 |
| targetSdk | 36 |
| minSdk | 24 |
| Gradle | 8.9+（项目自带 wrapper） |

### 构建与运行

```bash
# 克隆仓库
git clone https://github.com/liomix/Mikomi.git
cd Mikomi

# 构建 Debug APK
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew test

# 运行 lint 检查
./gradlew lint

# 构建 Release（启用 R8 混淆）
./gradlew :app:assembleRelease
```

> 💡 项目仓库已配置阿里云 Maven 镜像（`settings.gradle.kts`），中国大陆地区可显著加速依赖下载。

---

## 🤝 贡献

欢迎提交 Issue 与 Pull Request！请遵循以下约定：

- 从 `main` 分支创建 `feature/*` 或 `fix/*` 分支
- 提交信息格式：`type(scope): description`，如 `feat(home): add ranking section`
- PR 需通过 CI 检查（构建 + 测试 + lint）后方可合入，使用 Squash Merge 保持主干清晰
- **提交前必读**：
  - [`docs/mikomi-rules.md`](docs/mikomi-rules.md) — 项目级开发规范（命名 / Compose / 架构 / 安全 / 可观测性）
  - [`docs/project-structure.md`](docs/project-structure.md) — 工程结构基线 + 模块职责 + 落地顺序

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源。

## 📚 文档

- [`docs/mikomi-rules.md`](docs/mikomi-rules.md) — 项目级开发规范
- [`docs/project-structure.md`](docs/project-structure.md) — 工程结构基线
- [`docs/exceptions.md`](docs/exceptions.md) — 规范例外登记
