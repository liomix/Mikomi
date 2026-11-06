---
name: android-mikomi-rules
description: Mikomi 项目级 Android 开发规范。开始任何代码编写、修改、重构或评审前必须阅读。涵盖命名、Kotlin/Compose 风格、架构与导航、数据层与视频源插件、设计系统与 TG 数值基准、自适应与边到边、构建、安全、可观测性、性能与流畅度。AI 助手默认不编写测试代码（除非用户明确要求）。
---

# Mikomi 开发规范

> 这份文档管**决策**，不管教程：它回答「这处该写哪个值、该放哪个模块、这样写会不会出问题」。
> 机制层面的拆解在别处 —— 设计系统的全量数值与由来见本文 §5，Telegram 为什么丝滑见 `docs/reference/telegram-smoothness.md`，
> Lottie 用法见 `docs/reference/lottie-guide.md`，「什么任务该读哪个 skill」见 `.workbuddy/skills/mikomi-project/SKILL.md` §4。

> **AI 助手默认不编写测试代码**（单元、UI、截图、集成都不写），除非用户明确要求。
> 仓库里已有的测试文件（导航、映射、首页 ViewModel）是历史资产：**改到它们覆盖的逻辑时，要跑一遍并让它继续通过**（见 §0.1）。

---

## 0. 怎么用这份文档

### 0.1 验收：改完跑哪条命令

| 改了什么 | 跑什么 |
| --- | --- |
| 任何 Kotlin / 资源 / 路由 | `./gradlew :app:assembleDebug --offline` |
| 颜色、间距、字号、圆角、线宽、动效档位 | `./gradlew verifyDesign`（`tokenAudit` + `contrastAudit`，strict） |
| `core:navigation` | `./gradlew :core:navigation:testDebugUnitTest` |
| 已有测试覆盖的逻辑（`data:anime` 映射、`feature:home` ViewModel） | `./gradlew test` |

**编译通过就是验收线。** 不主动在模拟器上反复安装、截图；需要视觉确认时按需求跑一次，看完就停。
`--offline` 优先：能在离线缓存里跑通就说明依赖声明是自洽的，卡在下载上说明有别的问题，别先怪网络。

### 0.2 六条判据

这些是从过往轮次里长出来的，比任何单条规则都常用。**判据冲突时，判据优先。**

1. **先取证，再改。** 说「应该是这里的问题」之前，先拿到证据：一条命令的输出、一个 `dump`、一次 `grep` 的计数。改动前的假设写下来，改完拿同样的取证方式验证它。没有取证过的「优化」和「修复」都只是换了一种写法。
2. **一个开关能表达的事，别升级成一套模型。** 日 / 夜本来就是二态，给它加「跟随系统 / 浅色 / 深色 / 定时」四档，只是把一眼看完的开关变成一页要读懂的设置。反过来，**写进去却没有任何路径读的字段直接删掉**（`darkPrimary` 就是这么死的）。
3. **「没有名字」和「不该有名字」是两件事。** 一个值出现 33 次却还是字面量，说明缺的是命名；两个档只差 2dp 且都不常用，才说明阶梯该收紧。**先数用量，再下判断**（`tokenAudit` 会打印每档的使用次数）。
4. **两件事各自可调，就别塞进一个选项。** 封面动效画在页面之上的 overlay 里，页面转场怎么动都不影响它的落点 —— 它们本来就无关，所以各存一条偏好。合进一个列表，用户就只能二选一。
5. **用户改的是「以后怎么动」，不是「把这一页再动一次」。** 凡是决定转场外观的设置，都不能出现在 `remember(key)` / `LaunchedEffect(key)` 的 key 里，也不能被装饰器实例捕获。做法统一为：设置经 `CompositionLocal` 下发，页面**首次组合时冻结**，从下一个新页面开始生效。
6. **同一份真相只写一处。** 栈只在 `Navigator` 里改；尺寸只有 `spec` 一个来源；档位只有 `.steps` 一个来源；组件 API 的默认参数不重复一份规格常量。出现第二份，就一定会有分叉。

### 0.3 从 Telegram 拿到的，以及有一条不学

Telegram 没有路由表，页面身份就是「类 + Bundle」，跳转全靠 `presentFragment(new Xxx(args))`。它的**状态恢复**是反面教材（manifest 挂 `configChanges` 硬扛旋转，`onSaveInstanceState` 只存栈顶一个白名单页面，中间历史全丢）—— 这条不学，见 §3.5。

真正值得吸收的是四条工程做法：

| TG 的做法 | 在这套代码里落成什么 |
| --- | --- |
| 页面栈只有一个权威（`ActionBarLayout.fragmentsStack`），所有跳转都过它 | 栈的写操作只在 `Navigator` 收口，`NavDisplay` 只读（§3.5） |
| 转场互斥：动画没跑完不许再压栈，转场参数与设置解耦 | 转场档位首次组合时冻结、装饰器实例不随档位重建（§3.5） |
| 结构数值全部实测，不凭手感 | 间距 / 圆角 / 时长 / 线宽对齐 TG 表，偏差必须进例外清单（§5.3） |
| 布局在「数据 / 窗口变化」时算一次，滚动静止；主线程只负责 draw | §10 全部内容 |

---

## 1. 命名规范

### 1.1 文件与资源命名（硬性约束）

基于文件的资源名（`drawable/`、`mipmap/`、`values/` 等目录下的文件）**只能使用小写字母 a-z、数字 0-9 和下划线 `_`**；大写字母、空格、连字符和中文均为非法。资源必须放在预定义类型目录中，不能放在 `res/` 根目录。

### 1.2 代码命名

| 类别 | 规则 | 示例 |
| --- | --- | --- |
| Kotlin 类 / 接口 | PascalCase | `AnimeRepository` |
| 函数 / 属性 | camelCase | `fetchAnimeList()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 包名 | 全小写，固定前缀 `com.liomix.mikomi` | `com.liomix.mikomi.feature.home` |
| 测试类 | 被测类名 + `Test` | `HomeViewModelTest` |
| 设计 token 字段 | PascalCase（跟着 t-shirt 名走） | `MikomiSpacing.Gutter`、`MikomiMotion.Duration.Fast` |

源文件只包含一个顶级类时，文件名应为该类名（区分大小写）加 `.kt`。

### 1.3 资源前缀

| 前缀 | 用途 | 示例 |
| --- | --- | --- |
| `ic_` | **多色 / 复杂矢量图**（含自适应图标各层） | `ic_launcher_foreground` |
| `bg_` | 背景 | `bg_episode_card` |
| `img_` | 位图图片 | `img_anime_cover` |

**单色图标不进 `res/drawable`，一律进 `MikomiIcons`**（见 §5.6）。`ic_` 前缀留给「一张 path 画不完」的图：启动图标各层、多色插画。判据是**这张图会不会被 tint 染色** —— 会，就该是 `ImageVector`；不会（多色、渐变、位图），才落资源。弹幕那三个图标曾经是这条规则的例外（带小字的气泡图形），现已按同一判据收进 `MikomiIcons`，`feature:player` 的 `res/drawable` 已空。

本项目**纯 Compose**，不存在 `activity_` / `fragment_` / `item_` / `screen_` 这类布局文件命名，不要再照抄 XML 时代的模板。库模块中所有导出的资源**必须**用 `resourcePrefix` 配置唯一前缀。新增前先搜可复用资源。

**Compose 可组合函数用 PascalCase，不带前缀**（`AnimeDetailScreen`，不是 `screen_anime_detail`）。

---

## 2. 代码风格

### 2.1 编码与格式化

- 所有源文件 **UTF-8**，行尾 **LF**（文档、Kotlin、Gradle 脚本一致；只有 `.cmd` 用 CRLF）。
- 代码行**不超过 100 个字符**。
- 编辑完 `.kt` / `.kts` / `.xml` 必须格式化。

**本项目没有接入 ktlint / Detekt**（仓库里没有这两项配置，也没有 CI）。不要在新模块里顺手加 lint 依赖来「补全规范」—— 加之前先按 §7.5 评估，并想清楚它和已有的 `verifyDesign` 闸门怎么共存。规范里写成「必须集成」而实际不存在，比没有这条规范更糟。

### 2.2 Kotlin 语言约定

- 默认 `val`；`var` 必须有明确理由。
- 禁止 `GlobalScope`；协程一律结构化并发（`viewModelScope`、`lifecycleScope`）。
- **调度器必须注入**（`@Dispatcher(IO)` / `@Dispatcher(Default)`），禁止硬编码 `Dispatchers.IO`。
- 捕获具体异常类型（`IOException` 等）；只有**顶层错误边界**（崩溃兜底）可以捕 `Exception` / `Throwable`，且必须上报或重抛，**不得静默吞掉**。
- `CancellationException` 永远不吞：`catch (e: Exception)` 会把协程取消一起吃掉，需要时先 `if (e is CancellationException) throw e`。
- `kotlinx.serialization` 不用 Gson；KSP 不用 KAPT。
- `Flow` / `StateFlow`，不用 `LiveData`。

### 2.3 协程与 Flow

- **一个 ViewModel 的一条上游流一次 `stateIn`。** 统一 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`。多条 `map` 各自 `stateIn` 会在恢复订阅时重算多遍，也让「初始值」出现在两个地方。
- **参数变化用 `flatMapLatest` 承接，不要清空状态再重发。** 详情页的模式是范本：`animeId: MutableStateFlow<String?>` → `flatMapLatest { id -> flow { emit(Loading); emitAll(repo.observe(id)) } }` → `catch` → `stateIn`。切换参数时先回到 `Loading`，不要让人看到上一部番。
- **`catch` 放在 `stateIn` 之前**，把异常映射成 `UiState.Error`；放在之后会漏掉 `stateIn` 内部的异常。
- 搜索这类「输入驱动」的流用 `collectLatest` + `debounce`，不要用 `collect`（旧请求会和新请求抢着写状态）。

### 2.4 Compose 组件规范

- 可复用 Composable 必须以 `modifier: Modifier = Modifier` 作为**第一个可选参数**。
- 使用 Slot API（`content: @Composable () -> Unit`）让布局组件可复用。
- Modifier 链顺序：**Size → Padding → Clip → Background → Clickable**。默认点击区覆盖 padding（利于 ≥48dp 触控目标）；确需点击区仅限内容时，把 `clickable` 放到 `padding` 之前并显式限定，同时确认触控目标仍达标。
- 不把 ViewModel 或 DI 对象往深层 Composable 传，用回调 Lambda。
- **组件的默认参数只取 token，不取规格。** `size: Dp = MikomiSizes.Icon` 可以；`size: Dp = 37.dp`（某个开关轨道的实测宽度）不行 —— 那会把「一个控件的体格」变成所有人的默认值，第二个调用点就被污染了。

### 2.5 Compose 性能与稳定性

- 开启 **Strong Skipping**（Compose Compiler 默认跳过不稳定参数，含标准 `List`，按实例 `===` 比较）。**不要**为了「稳定性」给含集合（`List` / `Set` / `Map`）的模型加 `@Stable` / `@Immutable` 或改用 `ImmutableList`：其 `O(N)` 的 `equals()` 在重组中比 `O(1)` 实例比较更贵。只有编译器推断不出稳定性、数据源每次发射新对象、且类扁平无集合时，才加注解。
- **懒列表 item 只收稳定参数。** 带 `List<String>` 的 `Anime` 整个传进卡片会让它永远不可跳过 —— 收窄成 `id / title / coverUrl + (String) -> Unit`（`AnimePosterCard` 已是这个形状）。尺寸类接口对象一律 `remember`。
- **禁止向后写入**：不得在组合已读取某状态之后，于 Layout / Draw 阶段写回同一个状态。
- **阶段延迟**：逐帧变化的值不许读进组合阶段。首选 `graphicsLayer { }`（位移 / 透明度 / 缩放）→ 其次 `Modifier.layout` / `offset { }`（尺寸 / 位置）→ 实在要改文字结构才回组合。反例：`progress.value` 在 composable 里做 `offset / width / height`，300ms 动效 = 18~36 次整块重组。
- **要改宽度就不能用 `scaleX` 顶替**：拉伸形变（Tab 指示器弹性宽度）用 `scaleX` 会把圆角压成椭圆 —— 尺寸走 layout，位移走 layer（参考 `core:ui` 的 `TabIndicator`）。
- **懒列表**：每项必须有稳定 `key`（`Int` / `Long` / `String`）与 `contentType`；用 `itemsIndexed` 而不是 `list.indexOf(item)`；选择态用 `Set`（`mutableStateOf(emptySet())`）而不是 `mutableStateListOf`；item 内禁止 `collectAsStateWithLifecycle()`、埋点 `LaunchedEffect`、查询 `CompositionLocal` —— 提到父级当参数传入。
- **坐标与定位**：避免用 `onGloballyPositioned` 追坐标，改用 `Modifier.onPlaced` / `onVisibilityChanged` / `Modifier.Node` 的 `requireLayoutCoordinates()`；禁止用 `offset` + `MutableState` 定位浮层（差一帧），用自定义 `Layout`。
- **副作用**：不在 Composable 函数体直接执行副作用（`SideEffect` / `LaunchedEffect` / 事件回调）；重对象用 `remember` 缓存；长生命周期协程引用回调时用 `rememberUpdatedState` 防闭包过期。
- **`remember` 的 key 只放「真的换了一个东西」**。放设置值进去等于「改一次设置就把当前界面重做一次」（判据 5）。
- 自定义 Modifier 用 `Modifier.Node`，不用 `Modifier.composed`（子组合开销）。
- 网络图用 Coil `AsyncImage`，不用裸 `Image`；收集 UI Flow 用 `collectAsStateWithLifecycle()`。

---

## 3. 架构与模块化

### 3.1 分层

三层，只向内依赖：

- **UI 层**：Compose 组件 + ViewModel。ViewModel 暴露 `StateFlow<UiState>`，事件经方法调用上抛。
- **Domain 层（可省）**：UseCase。**本仓库现状是没有 UseCase 层** —— 逻辑不复杂时，多一层转发只是多一个文件。出现「两个 ViewModel 需要同一段编排」时才引入。
- **Data 层**：Repository 作为数据访问门面，协调本地与远程；**Room 是结构化数据的单一可信来源（SSOT）**。UI 只观察本地数据，不直接碰数据源。

### 3.2 单向数据流（UDF）

状态从 ViewModel 向下流向 UI，事件从 UI 向上回到 ViewModel。ViewModel 用 `StateFlow<UiState>` 暴露状态，UI 用 `collectAsStateWithLifecycle()` 收集。禁止 UI 直接改 Repository 之外的状态，也禁止 ViewModel 持有 `Context`。

### 3.3 Route + Screen，以及参数化路由怎么写

每个屏幕是 **Route + Screen** 双组件：

- **Route（有状态）**：`hiltViewModel()`、收集状态、传回调。命名 `AnimeDetailRoute`。
- **Screen（无状态）**：纯渲染，可预览。命名 `AnimeDetailScreen`。

状态提升到所有读写它的 Composable 的**最低公共祖先**。

**参数化路由（`DetailDestination(animeId)` 这类）的固定写法**，四步缺一不可：

1. Route 里 `LaunchedEffect(param) { viewModel.bind(param) }` —— 不在组合期直接调 `bind`，否则每次重组都执行一次。
2. ViewModel 里 `bind` 只写一个 `MutableStateFlow<String?>`，真正的加载由 §2.3 的 `flatMapLatest` 承接。**`bind` 内不判「已经绑过就跳过」**，参数变了必须重新加载。
3. `uiState` 的 `stateIn` 缓存会残留上一次的值，而 `bind` 要等首帧之后才跑 —— **UI 层必须拿「已绑定的 id」和当前参数对账**，不一致就按 `Loading` 渲染（`DetailRoute` 的 `shownState`）。少了这一步，症状是「点 A 先闪出 B」。
4. 需要「退出时暂停 / 释放」的一次性动作，用 `DisposableEffect`，不要指望 ViewModel 自动知道用户走了（后台播放这类场景必须显式处理）。

### 3.4 模块化与依赖方向

多模块划分遵循**高内聚、低耦合**，命名 `feature-{name}` / `core-{name}` / `data-{name}`，每个模块的 Gradle 配置由 `build-logic` 约定插件统一管理。

**依赖方向（硬性）**：

```text
app  →  feature  →  data  →  core
                         └──→  core
```

- `feature-*` 只依赖 `core-*` / `data-*`；跨 feature 复用的东西抽到 `core-*`，**禁止** feature 之间互相依赖实现模块。
- `core-*` **不依赖** `feature-*` / `data-*`。
- `data-*` 不依赖 UI 层。
- 禁止循环依赖；对外只暴露最小 API（接口 `api`，实现 `internal`）。
- `core:model` **纯 Kotlin**，不引 Android 依赖；它能被单独编译是这条边界的验收方式。

### 3.5 导航

统一 **Jetpack Navigation 3**（`NavDisplay` + `entry` DSL），**类型安全路由**（`@Serializable` 的 `data object` / `data class : NavKey`），禁止字符串路由拼接。

- 每个目的地仍是 Route + Screen（§3.3）：Route 作为 `NavEntry` 内容。
- 多模块由各 feature 提供自己的 installer，`MikomiNavHost` 遍历注入，避免单一巨型导航图。
- **返回栈不从 DI 拿。** 栈由 `rememberNavBackStack` 在组合期创建（配置变更与进程死亡都靠它恢复），`Navigator` 包在外面收口写操作，再经 `LocalNavigator` 下发。把栈放进 `@ActivityRetainedScoped` 单例只在配置变更时活着 —— 进程一被回收，用户从详情页回来就落到首页（这正是 TG 那条不学的做法）。
- **栈的不变量只写一遍。** `Navigator.navigate()` 里两条：顶级目的地走 `selectTopLevel`（切 tab，不压栈）；同一个 `NavKey` 已在栈里就不再压。后者不只是防连点 —— `NavEntry.contentKey` 默认是 `"$key:${key::class}"`，同值出现两次会得到两个相同 `contentKey` 的 entry，而 `NavDisplay` 的 `decorateEntry` 正是拿它做 `key()` 的，两页的 SavedState / ViewModelStore 会互相顶掉。
- **`selectTopLevel` 不许 `clear()` 再 `add()`。** 那会在两帧之间留下一个空栈，`NavDisplay` 遇到空栈直接抛 `backstack cannot be empty`。从尾部收缩到只剩一项再原地换根，任何一帧看过去栈都非空。
- **feature 不认识导航器实例。** installer 是无参 lambda，在自己的 `entry<...> { }` 里读 `LocalNavigator`；新增 feature 模块不需要改 `app` 的任何一行。需要沉浸状态的页面自己注入 `ImmersionBridge`，不要往 installer 上挂「人人有份、只有一个用」的参数。
- **ViewModel 按 entry 计生命周期。** `MikomiNavHost` 必须挂 `rememberViewModelStoreNavEntryDecorator`：少了它，`hiltViewModel()` 会一路找到 Activity 的 owner，所有页面共用一个实例 —— 参数化路由只能靠 `bind()` 手工改绑，返回也不 `onCleared()`。装饰器顺序：**SaveableState 最外、ViewModelStore 次之、转场动画最内**。
- **转场档位在页面首次组合时冻结。** 档位经 `LocalPageTransitionStyle` 下发，装饰器实例不随档位重建；`PageMotionSurface` 的入场 effect 用 `LaunchedEffect(Unit)`。两条同时成立才不会「改一下设置，当前页自己重播一次入场」。
- **返回与预测式返回**：`NavDisplay.onBack` 已走 `androidx.navigationevent` 的 `NavigationBackHandler`。页面内只有「根页面上的特殊返回」（例如巡礼页沉浸时唤出导航栏）才额外挂 `BackHandler`，且**必须带 `enabled` 条件**，避免与 `NavDisplay` 重复消费同一个返回事件。
- 对话框 / BottomSheet 用 Navigation 3 Scene 定义；同一个 `NavigationEventState` 只能绑**一个** `NavigationBackHandler`（条件分支写在同一 handler 内）。

### 3.6 依赖注入

- 统一 **Hilt**：ViewModel 用 `@HiltViewModel` + `hiltViewModel()`（导入 `androidx.hilt.lifecycle.viewmodel.compose`，不是旧包）。禁止在 Composable 里构造依赖或访问容器。
- **作用域**：`@Singleton` 只给全局单例（OkHttp、数据库、DataStore）；`@ActivityRetainedScoped` / `@ViewModelScoped` 给随屏幕存活的依赖。避免滥用 `@Singleton` 让一切常驻。
- **Module 按职责拆**（`NetworkModule`、`DatabaseModule`、`AniListModule` / `BangumiModule` 等）；接口用 `@Binds`，第三方构造用 `@Provides`。

---

## 4. 数据层

### 4.1 离线优先

Mikomi 默认**离线优先**：

- **结构化数据**（番剧、剧集、收藏、历史、排行、时间表）→ Room 作为 SSOT
- **非结构化偏好**（用户偏好、搜索历史）→ DataStore
- **网络数据** → Retrofit + OkHttp 取回后**写回本地**，UI 只观察本地 `Flow`。不要出现「网络直出 UI」的第二条路径。

### 4.2 Repository 规范

- 即使只有一个数据源，也必须有 Repository。
- 三层模型独立：**Network DTO、Room Entity、Domain Model**，由 `mapper/` 转换。禁止把 DTO 当领域模型一路传进 UI。
- Repository 暴露 `Flow` 供上层观察；`suspend` 函数用于一次性刷新。
- **DataStore**：同一文件不得创建多个实例；泛型类型必须不可变；不得对同一文件混用 `SingleProcessDataStore` 与 `MultiProcessDataStore`。

### 4.3 错误处理

不得静默忽略网络、解析、缓存或数据库错误。

- Repository 抛领域异常或返回 `Result` / sealed 类型，**不返回用户文案字符串**。
- UI 经 `UiState.Error` 集中映射成文案。**文案只说结果，不说实现**：失败页不显示 URL、XPath、异常类名（这些进日志，不进屏幕）。
- 重试必须有明确条件和次数上限，考虑幂等与退避；失败最终要落到一个用户能看懂的终止态，不能一直转圈。

### 4.4 数据库迁移

- 配置 `room.schemaLocation` 并提交 schema JSON，作为迁移契约。
- 任何 `@Database` 版本号提升**必须**有对应 `Migration`；生产环境**禁止** `fallbackToDestructiveMigration()`。
- 简单可推断的变更用 `autoMigrations`；重命名 / 类型转换 / 默认值这类有歧义的变更手写 `Migration`。
- DataStore 的 schema 变更要显式处理旧值和默认值，避免破坏性清空。

### 4.5 视频源插件（`data:video`）

这是本项目最特殊的一块：抓取逻辑以「插件规则 JSON」的形式外置，宿主只提供契约与策略链。

- **契约类型住在模块根包**（`VdoStream` / `VdoStreamPlan` / `VdoStreamSniffer` / `VdoCookieJar` / `VdoSearchHit` / `VdoEpisode` / `VdoFailure` / `VdoLineRef` + capability store）。**新源只写抓取**，不要自己发明一套流类型。
- **只接 `PLUGIN` 大源。** `CYK` / `RPB` 是占位枚举（`remote/vdocyk`、`remote/vdorpb` 下只有 README）—— 不要把占位当实现，也不要在 UI 上给它们建 Tab。
- **出流策略链顺序固定**：`CachedDirect(10)` → `StaticHtml(20)` → `EncryptedConfig(30)` → `WebRender(100)`。加策略是往链上加一档优先级，不是改顺序。
- **规则语言 = Kazumi 的 XPath 1.0。** jsoup 转 DOM 必须 `W3CDom().namespaceAware(false)`；相对选择器 `/` 要转 `./`。
- **线路名自己编号。** 不要读站点那一列 `text()` —— 它会把整列的 `<a>` 拼成 `"54321"`。
- **播放形态由出流计划决定，`useWebview` 只是 hint。**
- **UA 必须是桌面 UA。** 移动 UA 会被 302 到「下载 App」页。
- **WebView 不可见的写法是 `alpha = 0` + `isEnabled = false`**，不能用 `INVISIBLE`（Chromium 会停止渲染）；12s 超时判 `NoStream`。
- **换集判断比「自己请求过的地址」**，不比 `getUrl()`（同一个播放器实例的 `getUrl()` 可能在换集后仍返回旧值）。
- 内置插件 JSON 放在插件包根部的 `plugin/` 下，由模块的 `assets` 源集登记打包到 assets 根；落盘目录是 `filesDir/vdoplugin/plugin/`，`.seeded` 标记必须放在该目录**内部**。

---

## 5. 设计系统与资源

### 5.1 字符串

用户可见文案**必须**走 `strings.xml`，禁止在 Kotlin / Compose 里硬编码业务文案。带参数用格式化资源（`%1$s`），不要用拼接改变语序。

### 5.2 主题与设计系统

颜色、字号、间距、形状一律从设计系统取，不在业务页面散落常量。

入口在 `core:designsystem`，分三层 —— **token**（`theme`：布局的呼吸与语义色）、**规格**（`spec`：单个控件的物理尺寸）、**组件**（`core:ui` 的实现）。判别方法只有一个：**改这个数，是整页的节奏变了，还是只有这一个控件变了？** 前者是 token，后者是 spec。

| 要什么 | 从哪里取 | 别名 |
| --- | --- | --- |
| 语义色（主色、表面、描边、错误） | `MaterialTheme.colorScheme` | — |
| M3 装不下的语义色（评分金、标签色板、名次色、骨架墨色） | `MikomiTheme.colors` | 标签 `colors.tag(hue)`、名次 `colors.rank(n)`；局部覆写 `LocalMikomiColors` |
| 压在图像 / 视频上的前景色（封面白字、遮罩、信箱区、HUD、进度条） | `MikomiTheme.colors.media` | 非组合位置用顶层 `MikomiMedia` |
| 绘制阴影的恒定黑 | `MikomiShadowColor` | — |
| 禁用态淡化（容器 / 内容两级） | `MikomiAlpha.DisabledContainer` / `.DisabledContent` | 组件在自己 `enabled` 分支里用；**别**用 `Modifier.alpha()` 整体压暗，那会连带压掉投影与描边 |
| 字号 | `MaterialTheme.typography`（15 槽位全部显式声明） | M3 之外的样式见 `MikomiTextStyles` |
| 间距 / 圆角 / 尺寸 / 投影 | `MikomiSpacing` / `MikomiRadius` / `MikomiSizes` / `MikomiElevation` | 每组的全量档位见 `.steps` |
| 线宽（描边、分隔线、进度） | `MikomiStroke` | — |
| 字体族 | `MikomiFontFamily`（换字体只改这一处） | 等宽用 `FontFamily.Monospace` |
| 形状 | `MaterialTheme.shapes`（由 `MikomiRadius` 派生） | 胶囊用 `MikomiPillShape` |
| 时长 / 缓动 / 按压规格 | `MikomiMotion.Duration` / `.Curve` / `.Press` | — |
| **单个控件有多大**（开关轨道、标签胶囊、底栏、播放器控制条） | `spec/ComponentSpec.kt`（`MikomiSwitchSpec` 等） | 这些**不是** token，见规则 3 |
| 浮层遮罩深度（底部面板、侧栏共用） | `MikomiScrimSpec.Alpha` | 遮罩本身只由 `SheetScrim`（core:ui，internal）画，不要在浮层里各写 `drawBehind` |
| 按压反馈 | 主题下发到 `LocalIndication`，调用点不用管 | 沉浸子树用 `LocalMikomiRippleColor` 覆写 |
| 页面转场档位（推进 / 淡入 / 无） | `PageTransitionStyle`（`core:model`）→ `MikomiNavHost(pageTransitionId)` → `LocalPageTransitionStyle` | 推进 150ms、淡入 200ms；与「封面动效」是两条独立偏好，可任意组合。档位在页面**首次组合时冻结** |
| 系统是否允许动画 | `Context.animationsEnabled()`（`MotionPolicy.kt`） | 夜间圆形揭示与页面转场共用这一条判据，关掉就什么都不播 |

七条硬规则：

1. **页面里不写色值、不写字号、不写圆角、不写线宽、不写节奏类间距。** 需要新颜色就往 `MikomiColors` / `MikomiMediaColors` 加字段；需要新字号就往 `Typography` / `MikomiTextStyles` 加档。
2. **唯一允许绕过设计系统的位置是 `feature:animap`。** 巡礼页是嵌在 WebView 里的站点 UI，配色由站点决定、且固定浅色（深色浮层压在浅色地图上读不出来）。**新代码不许再新增第二个例外** —— 需要特殊视觉时用 `LocalMikomiColors` 做子树覆写，而不是就地写死色值。
3. **实测规格进 `spec` 层，不上提成 token。** 从参考实现逐像素量出来的控件尺寸（底栏 56dp、开关轨道 37×20dp、标签胶囊内边距 18dp、播放器控制钮 40dp）写在 `spec` 里，**不写在组件私有常量里** —— 那份尺寸只能靠翻代码知道，改代码不改注释，下一个人就按注释理解界面。骨架块的 `width × height` 是版式实测值，只有它的**圆角**该走 token。
4. **token 变更必须同步目录，且目录读 `.steps` 而不是手抄。** `TokenGallery.kt`（token + 规格）与 `ComponentGallery.kt`（组件）是设计系统的可视化验收面。尺寸 object 自带 `steps: List<MikomiStep>`，目录从它渲染、审计拿它对账 —— 手抄的清单漏过 `None` 与 `Xxxl` 两档，漏掉的刻度等于没人会用。
5. **绕过的字面量用 `./gradlew tokenAudit` 查，对比度用 `./gradlew contrastAudit` 查。** 前者扫描 `designsystem/theme` 之外的全部 Kotlin，把字面量分成 `[!]` 布局位置（必须换成 token）与 `[·]` 控件体格（规则允许），并打印每档使用次数（某档 0 次要么删、要么还没用上；某档 1 次而邻档 200 次，说明有人在按手感挑值）。后者直接读 `Colors.kt` 算 WCAG 比值，标签 18 对与名次 6 对全部要过 4.5:1，解析不到预期条数会 fail 而不是静默通过。两条默认**只报告不失败**（评审入口）；`-Pstrict` 或 `./gradlew verifyDesign` 才 fail（闸门）。默认不 fail 是因为「布局位置」与「控件体格」的区分靠调用点名字猜，让它直接阻断构建会逼人往 `design-ok` 里塞理由 —— 那正好是把闸门变成后门。
6. **确实该留的字面量写 `// design-ok: 理由`。** 行内（或前后三行内）带这个标记的字面量会被归入「已评审的组件规格」并从 `[!]` 里摘出去，同时**在末尾打印总数**。故意做成「可数」的：豁免一旦不可见，就会退化成新的后门。没有理由的 `design-ok` 不是豁免，是欠账。
7. **浮层类组件共用同一套骨架。** 底部面板与侧栏的遮罩都由 `SheetScrim` 画：深度取 `MikomiScrimSpec.Alpha`、点击关闭接同一个 `onDismiss`、进度以 `() -> Float` 传入以便只在绘制阶段读取。此前两个浮层各写一份逐字相同的 `drawBehind + detectTapGestures`，连 `ScrimAlpha` 都是各存一份 0.32 —— 改一处忘另一处时，两个浮层的深度就会分叉，而这种分叉肉眼几乎看不出来（0.28 与 0.32）。**判据：同一段代码在第二个组件里出现时，就该问它是不是这一族组件的共同骨架。**

**审计有三个盲区**，改审计代码时要一起看：① 分行写的 `padding(` 要先把多行压平再匹配；② `PaddingValues` 是 `padding` 的同级入口，不在 `padding(` 的正则里；③ `design-ok` 必须打印总数，否则豁免变成后门。

使用约定：

- **间距只用于「呼吸」**：`padding(...)`、`contentPadding = PaddingValues(...)`、`spacedBy(...)`、`Spacer` 走 `MikomiSpacing`；`size(...)` / `height(...)` / `width(...)` 属于控件体格，保持字面值（除非它正好是 `MikomiSizes` 的某一档）。
- **间距 token 分两族。** t-shirt 名（`Xxs` `Xs` `Sm` `Md` `Lg` `Xl` `Xxl` `Xxxl`）是**节拍**，回答「留多长」，相邻比值 ≥1.5，随便选不会错；单词名（`Tight` 6 / `Compact` 10 / `Gutter` 14 / `Room` 20）是**角色**，回答「这处空白在表达什么」。页面级节奏从节拍档里选；`Compact` 只给控件内部与紧凑列表行，**10 是页面级禁区**。
- **`Gutter`(14) 与 `Lg`(16) 不互换**：内容贴边、列表行左右内边距用 `Gutter`；两个区块之间用 `Lg`。「贴边」和「分隔」是两种关系，混用会让页面出现两条差 2dp 的水槽。
- **线宽走 `MikomiStroke`**：`BorderStroke(...)`、`Divider(thickness = ...)`、`CircularProgressIndicator(strokeWidth = ...)` 的第一个参数是线宽，不是间距。它曾经和间距混在一张表里选，结果同一屏出现过 0.5 / 1 / 1.5 / 2 四种描边。
- **圆角一律走 `MikomiRadius`**，且「高度的一半」改用 `MikomiPillShape`（百分比圆角，任何高度下都是完整半圆）。骨架块此前散落着 7/9/13dp 的「半高圆角」，本质都是胶囊。

### 5.3 数值基准：对齐 Telegram

结构数值以 Telegram Android 的实测值为基准 —— 它是少数把「信息密度」做到极致、又能在几百个页面间保持一致实现的项目。**同类 token 一律取 TG 的值**，不自己发明：

| 类别 | 基准 | 实际取值 |
| --- | --- | --- |
| 动效时长 | TG | 60（按压）/ 150 / 200 / 250 / 300 / 350 |
| 圆角 | TG | 4 / 6 / 10 / 12 / 16 / 20 / 28（`MikomiRadius`），胶囊用百分比 |
| 图标 | TG | **三档**：`IconXs`=16（文字行内：评分星标、行尾 chevron、状态对勾）/ `IconSm`=20（次级动作：顶栏、搜索结果、播放器控制条）/ `IconMd`=24（标准：菜单项、返回箭头、按钮前导）。收敛前是 11/13/14/15/16/18/20/22/24 八种混用，16 与 20 已是双峰；大于 24 的（播放键 32、HUD 32、开关 28、空状态 56）是单个组件的物理尺寸，留组件内 `private val` |
| 行高 | TG | 56（双行，`MikomiSizes.RowDouble`）；单行 48 由触控目标保证 |
| 触控目标 | 一致 | 48 |
| 按压反馈 | TG | 中性墨色 9.8%（`0x19`），不是主色 12% |
| 媒体前景 | TG | 白字三档 100% / 76% / 45%（清理前有 6 种白） |
| 字号骨架 | TG | 20 标题 / 18 区块 / 16 正文 / 15 次级正文 / 14 副文 / 13 辅助 |
| 间距 | 部分 | 节拍 2/4/6/8/12/16/20/24/32/48，另收三个**角色档**：`Tight`=6、`Compact`=10（全仓布局字面量里 45% 是它）、`Gutter`=14（页面水槽，TG `dp(14)` 694 次）；18 不收（原来那些全是图标尺寸） |
| 线宽 | TG | 1 / 1.5 / 2（TG 分隔线与描边用 1dp，`dp(1)` 764 次） |

**例外（不跟 TG）：**

- **品牌主色**：`ThemePalette` 的五套主色与日夜表面阶梯是本项目的标识，与 TG 的蓝无关。
- **AniMap 浮层**：地图上的搜索框、浮层固定浅色，不随日夜切换。
- **中文行高**：保留 Material 的 1.4~1.5 倍。TG 的紧凑行高是西文值，中文方块字上下有留白，照搬会让整站文字发挤。
- **实测控件规格**：底栏 56dp、开关轨道 37×20dp、Tab 5dp 是量出来的，属于「这个控件」而不是「这套节奏」。

判据：**新数值先问「TG 有没有同类」。** 有就取它的值并登记到上表；确实需要不同（语言、可读性、产品标识）才另立，且必须写进例外清单 —— 否则半年后没人说得清这个数是哪来的。

### 5.4 变更记录：为什么这些数是这些数

token 表最怕的不是「值不好」，而是「没人知道它为什么是这个值」，于是下一个人凭手感再改一版。这一节只记**决策**，不记每次数值微调。

| 轮次 | 做了什么 | 触发它的事实 |
| --- | --- | --- |
| 1 | 建起 token 层（尺寸 / 动效 / 颜色 / 形状 / 字阶），读取点 101 → 533 | 原来是「配色枚举 → ColorScheme → 组件」三层直落，没有中间刻度，每个页面自己决定 8 还是 12 |
| 2 | 删 `Color.kt`、补 `MikomiStroke` 与 `MikomiFontFamily`、收 `Gutter`(14)、修名次 `ink` | M3 默认方案本身带紫（`secondaryContainer` 等）；线宽同一屏出现四档；`padding(…=16.dp)` 全仓 0 处，水槽一直是 14；名次文字只有 1.61:1 |
| 3 | 收 `Compact`(10)、清掉 107 处布局字面量、审计补两处盲区 | 10dp 占全部布局字面量的 45%，说明缺的是**命名**不是该收紧阶梯；按行匹配看不见分行写的 `padding(`，`PaddingValues` 更是完全在扫描范围外 |
| 4 | 建 `spec` 层、`MikomiColors` 按族分组、`Contrast.kt` + `contrastAudit`、`steps` 单一来源 | 组件规格只能靠翻代码知道；`MikomiColors` 22 个平铺字段随新语义色一直涨；对比度「验算过」只是注释；目录手抄漏了 `None` / `Xxxl` / `Elevation.Overlay` 三档 |
| 5 | 收拾日 / 夜这条链：系统关掉动画时直接换色、揭示原点缺失时回退内容中心；删 `darkPrimary` 死字段、色卡首色重复与 8 处 `if (darkTheme)` 三元，方案缓存改定长数组 | 动画时长 0 时遮罩等于不存在，但 10MB 快照 + 全窗回读 + 整窗布局一分不少；原点没上报时圆从左上角斜扫进来；`darkPrimary` 五个色值从没被任何路径读过 |
| 6 | 「动态效果」拆成动漫封面动效与页面动效两条独立偏好（页面动效三档接进 `NavDisplay`）；页面转场尊重系统「减少动画」；底栏显隐与三处孤值（140 / 180 / 260）收口到 `MikomiMotion` 档位 | 两类动效被塞进同一个选项里，选封面就选不了页面；页面转场写死在 `NavHost` 里、用户无从选择；底栏用默认 spring，收尾比全站慢一截；三个值都不在档位上，调档时必漏 |
| 7 | 页面动效档位改由 `LocalPageTransitionStyle` 下发、每个页面首次组合时冻结一份；装饰器实例不再随档位重建 | 在设置页点「淡入 / 无」时，当前页自己重播了一次入场：装饰链换实例等于把整页重新挂载，`LaunchedEffect(style)` 又把进度从 1 拉回 0 |
| 8 | 组件库对齐 TG 的三条：禁用态从 `MikomiMotion.Disabled` 搬到 `MikomiAlpha`（颜色层）；两份 `ScrimAlpha` 合成 `MikomiScrimSpec`；浮层遮罩抽成 `SheetScrim` 由 `BottomPop` / `SideBarPop` 共用 | 0.12 / 0.38 一次都不动，却被放进「动效」命名空间，找禁用态的人不会去那里翻；两个浮层各写一份遮罩，深度各存一份 0.32，一处改动两处手感分叉 |
| 9 | 图标收进 `MikomiIcons`（69 个 `ImageVector`，统一 Rounded + 语义命名 + `autoMirror` 标记），删 `material-icons-extended`；图片收成 `MikomiImageLoader` 加三个请求入口，`feature` 不再依赖 coil | 图标库 84 MB / 11106 类，实际只用 69 个图形，且同一语义混着三种粗细（`Search` 两份、`Star` 三份）；图片没有全局配置，8 处各拼 `ImageRequest`，`crossfade` 与 `memoryCacheKey` 的口径已经分叉（`RankAnimeCard` 开淡入、`HomeBanner` 没开） |
| ~~10~~（已回退） | 曾把 69 个 `ImageVector` 换成 lucide v1.70.0 的 `res/drawable/ic_*.xml` + 88 行语义表；**整轮撤销**，图形语言回到 Material `ImageVector` | 换的是图形语言，不是实现：Material 实心 → lucide `stroke 2` 描边，**同一 dp 下墨水面积掉一半**，且描边随档位线性缩（18dp → 1.5、13 → 1.08、11 → 0.92，不足 1 像素被抗锯齿成灰）。§5.3 的 11/13/16 是围着实心量的，换语言后刻度全歪 —— 见 §5.6 末尾的校准要求。这一轮把「改尺寸」和「换形状」混在同一次变更里，也是它必须整体撤回的原因 |
| 11 | 实心/描边按语义重选：12 个语义标识（日历、排行榜、分类、设置、外观、插件、播放设置、历史、收藏、邮箱、编辑、离线）换成 `Outlined`，新增 4 个导航未选中态（`HomeOutline` / `MapOutline` / `ForumOutline` / `PersonOutline`），底栏改成 `icon`=描边 + `selectedIcon`=实心 | 69 个里 67 个是实心：`MikomiIcons` 当初只从 `Rounded` 一个家族提取，判据只有「有没有被引用」，没有「该实心还是该描边」这一维。零星几个空心的（`StarBorder`、`ChatBubbleOutline`）是名字里自带 Border/Outline 的巧合。底栏两个字段填了同一个图标，选中与未选中只靠颜色区分 |
| 12 | 尺寸从「八种分散值」收成三档（`IconXs`16 / `IconSm`20 / `IconMd`24）并登记进 `MikomiSizes.steps`；新增 `MikomiIcon` 作为唯一图标入口（`size` 默认 `IconMd`），72 处调用全部迁移；3 个弹幕图标从 `res/drawable` 收进 `MikomiIcons`；`tools/icon-gen/` 入库；图片补默认占位/失败底色与列表封面预取 | 尺寸以前**没有负责人**：只有 4 处读 token、32 处写字面量（11/13/14/15/16/18/20/22/24）、23 处不写 size 靠 `ImageVector.defaultWidth=24.dp` —— 改 token 只有 4 处会动。`IconSm` 是 18 而事实主档是 20；评分星标同一语义写了 11 和 13 两个值。弹幕 3 个 xml 是图标系统之外的第二条通路。图片的 `placeholder`/`error` 参数存在但全仓没传过，首帧与失败都是透明洞 |

### 5.5 可访问性与对比度

所有交互元素触控目标**至少 48×48dp**。文本对比度：< 18sp 至少 4.5:1；大文本（≥18sp 或 ≥14sp 加粗）至少 3:1。图片按钮、关键状态与错误提示要有无障碍语义。

**「减少动画」是用户设定，不是可以忽略的偏好。** 系统把动画时长拨到 0 时（开发者选项、无障碍、省电模式），日夜切换直接换色：不抓窗口快照、不建遮罩、不跑揭示。省下的是每次 10MB 位图 + 一次全窗 `PixelCopy` + 一次整窗布局，换来的是「确实没有动画」，而不是一段用户看不见的 400ms。

设计系统内的语义色是**上机验算的**，不是「算过一次」：`./gradlew contrastAudit` 直接读 `Colors.kt` 的色值重算（标签九色相 × 日/夜 18 对、名次 `ink` 压页面底色与卡片 6 对，门槛 4.5:1）。改色值后必须跑。

清单（当前值）：标签 18 对全部 ≥ 5.1:1；名次 `ink` 压 `background` 4.82 / 4.83 / 5.85；`outline` 压卡片 4.52；媒体前景三档压在 50% 灰上 ≥ 3.9。两条已知例外，写代码时要守住：

- **评分金 `MikomiTheme.colors.rating`（#FFC107）只允许压在图像上**：它在白底上 1.63:1、在 `background` 上 1.45:1。若将来要在浅色卡片上显示评分，改用 `onSurface` 文字 + 金色图标，或给它补一档 `ratingInk`。
- **主色（`primary`）当文字只用于大号或短词**：五套预设主色在白卡上只有 2.4~3.4:1，够 3:1（大文本 / 图标）但不够 4.5:1（正文）。正文里的强调不靠改色，靠字重与底色块。

> 注：Compose **Styles API**（`androidx.compose.foundation.style.Style`）目前为实验性，**暂不纳入生产基线**。稳定后再评估自定义组件的样式迁移。

### 5.6 图标：一个入口，一套语义，两档家族

**唯一来源是 `MikomiIcons`**（`core:designsystem` 的 `icon/MikomiIcons.kt`，当前 **76 个**：73 个来自 Material Icons + 3 个弹幕图标）。调用点写 `MikomiIcons.Play`，不写 `Icons.Rounded.PlayArrow`。

**唯一入口是 `MikomiIcon`**（`core:ui/component/base/MikomiIcon.kt`），全仓 72 处图标调用都走它：

```kotlin
MikomiIcon(
    icon = MikomiIcons.ChevronRight,
    contentDescription = null,
    size = MikomiSizes.IconXs,   // 不写就是 IconMd
)
```

**为什么必须有这一层**：Material3 的 `Icon` 里有个 `defaultSizeFor(painter)` —— painter 的 intrinsicSize 有限时它**不加**默认尺寸。我们每个 `ImageVector` 的 `defaultWidth` 都是 24dp，于是「不写 size 也正好 24」：看着对，但那是图形自己带来的，不是 token 在管。实测过一次：把 `IconMd` 改成别的值，全仓 23 处没写 size 的调用点**一个都不动**。`MikomiIcon` 把默认值收进 `size: Dp = MikomiSizes.IconMd`，参数名也顺手把尺寸从 `Modifier` 链里挪了出来 —— 调用点的 `modifier` 在外层，显式传的 `.size()` 仍然优先。

四条硬规则：

1. **不引第三方图标库。** 上一版用的是 `material-icons-extended`：API jar **84 MB / 11106 个类**，而全仓实际只用 110 余处调用、去重后 **69 个图形**。D8 在 debug 不裁剪，这 11106 个类每一次构建都要走一遍 dex。现在这些图形以 `ImageVector` 形式落在项目里，dex 里已经没有 `androidx/compose/material/icons` 这个包。
2. **实体/描边由语义定，不按页面拍脑袋**：**实心（`Rounded`）留给「状态与压画面」**——星级、播放控制、勾选、错误、锁；**描边（`Outlined`）留给「语义标识」**——设置项、快捷入口、标签页、空状态、导航未选中项。底栏的 `MikomiNavItem` 因此填两组：`icon`=描边（未选中）、`selectedIcon`=实心（选中），这也是 M3 导航栏的口径。当前 49 / 16 / 8（`AutoMirrored.Rounded`）。
   - 历史教训：这两个家族不是「同一个图标的两个变体」，而是**两个独立的图标名**。迁移前 67/69 全是实心，偶有空心的那几个（`StarBorder`、`ChatBubbleOutline`）是名字里自带 Border/Outline 的巧合，不是选出来的 —— 所以同一屏里会同时出现空心星和实心日历。
3. **RTL 用 `autoMirror = true` 表达，不在调用点挑 `AutoMirrored` 变体。** 返回、列表排序、播放列表这 8 个语义已经标好；调用点只认语义名。
4. **几何来自上游，工具在 `tools/icon-gen/`。** 加图标或换形状用 `python3 tools/icon-gen/extract.py <Name> --family outlined`，它把上游的紧凑 DSL 翻成本文件的展开式；`--verify` 会逐条核对语义表能否追回上游（当前 73/73）。仓库里没有上游源码，缓存被清之后 `--verify` 会整体报「追不回」，那是找不到 jar，不是图标错了。

**自绘新图标时按这套规格画**，否则和新图标摆在一起会「一个粗一个细、一个偏左一个居中」：

- viewport **24×24**，视觉主体落在 **20×20** 安全区内（四边留 2）；
- 描边 **2dp**，圆头圆角（与 Material Symbols 基准一致）；
- **path 里不写颜色**，`fill` 固定给 `SolidColor(Color.Black)`，实际颜色由 `Icon` 的 tint 决定 —— 这样才吃 `LocalContentColor`、夜间与禁用态能自动跟随；
- 显示尺寸走三档 `IconXs`(16) / `IconSm`(20) / `IconMd`(24)，不要再添新档（§5.3）；
- 需要多色或渐变（品牌标、插画）才落 `res/drawable`，走 §1.3 的 `ic_` 前缀。

新增图标只有一个动作：往 `MikomiIcons` 加一个 `val`。不需要改任何调用点 —— 形状变了也同理，文件名/语义名不变，只换 `val` 里的几何。

**换图形语言 ≠ 改 dp，先做光学尺寸校准**（第 10 轮试过整轮替换，已回退）。当时把 69 个 `ImageVector` 换成 lucide v1.70.0 的 `res/drawable/ic_*.xml`，**dp 一个都没改，视觉上却全乱了**，原因有两条：

- **实心 → 描边，墨水面积掉一半。** 同一 24dp 框里，Material 实心星是一块黑，lucide 的 `stroke 2` 星只有一圈线；`Star` 实心/描边那对语义原本还承担「已收藏 / 未收藏」的区分，描边版直接把这一档视觉重量抹平了。
- **描边随档位线性缩。** 24 viewport 的 `stroke 2` 画到 24dp 正好是 2dp，往下降就顶不住：20dp → 1.67、18 → 1.5、13 → 1.08、**11dp → 0.92dp**（不足 1 像素，抗锯齿后成一条灰线）。

§5.3 里 11 / 13 / 14 / 15 / 16 那些值是围着 **Material 实心的光学大小**量的，一旦换语言，这些数字的含义就变了 —— token 没动，刻度却全歪了。真要换，必须先按墨水面积做补偿（放大 viewport 再裁回 24 框），不能「等 dp 直接搬」。

### 5.7 图片：一个入口，一份缓存口径

**唯一入口是 `MikomiImageLoader`**，在 `MikomiApp.onCreate` 里 `install(this)` 装到 Coil 单例上。

- **必须在第一个图片请求之前装。** Coil 的单例一旦被读取就定型，之后的 `setSafe` 静默失效 —— 表现为「缓存上限没生效、UA 也没带上」，但一条日志都不报。
- **缓存上限写死，不按百分比推。** 内存取可用内存的 20%，磁盘 256 MB。Coil 默认按设备比例算，低内存机上会退到极小（同一张封面反复解码），大内存机上又留一大块永不释放；写死绝对值让两种机器上的行为一致，也便于定位「这张图为什么重新下载」。
- **图片用独立的 `OkHttpClient`。** 不是漏了「复用全局 client」：接口要长超时（弱网重传值得等），图片要短超时（10s / 15s，挂了就给占位块）；而且列表滚动时图片请求是接口的几十倍，共用会挤占接口的并发名额。这层取舍抄的是 Telegram —— 它的 `ImageLoader` 也是自己的连接池。

请求一律走 `core:ui` 的三个入口，不要自己拼 `ImageRequest`：

| 场景 | 用 |
| --- | --- |
| 一张图，只管显示 | `MikomiAsyncImage(url = ...)` |
| 要自己画（模糊背景、遮罩、`drawWithContent`） | `rememberMikomiImagePainter(url = ...)`，返回 `Painter` |
| 需要拿请求做别的事 | `rememberCoverRequest(url = ...)` |
| 列表提前铺图 | `rememberCoverPrefetch(urls = ...)` |

三条口径因此只在一处定义：

- **`memoryCacheKey` 固定为 url。** 各写一份时 key 计算路径会分叉，症状是「搜索完退回来，轮播显示的是搜索海报」。
- **`crossfade` 由调用方显式给。** 懒列表里必须关（一屏十张同时淡入是明确的每帧混合开销）；轮播、详情这种一屏一张的可以开。
- **浏览器 UA 只在需要时加**（`browserUserAgent = true`）：AniRpb 的 meituan 图床会拒绝非浏览器 UA。默认不加，因为很多 CDN 会按 UA 换编码格式。

**默认占位与失败底色是骨架色，不是透明。** `MikomiAsyncImage` 的 `placeholder` / `error` 默认取 `rememberMikomiPlaceholder()`（`skeletonBaseColor()`）。以前这两个参数存在但全仓没传过 —— 封面在首帧、失败、被取消三种情况下都是透明的，露出来的是父容器背景；夜间主题下就是一块比周围更黑的洞。用骨架色而不是固定灰，是因为它和 `SkeletonBox` 同色，列表滚过去不会有「先灰一下再黑一下」的跳变。

**列表封面用 `rememberCoverPrefetch(urls)` 预取。** 图片是按可见性触发的：item 组合出来才开始下载，滚动时新出现的卡片永远是「先占位、再淡入」。做法是数据到手的瞬间把整批 url 排进队列（`memoryCachePolicy = DISABLED`，只求落盘、不占内存，因为此时不知道最终显示多大），滚动时命中磁盘缓存直接出图。当前接在排行榜（一次拿全量前 50）。

**`feature` 模块不依赖 coil。** `MikomiAsyncImage` 与 `rememberMikomiImagePainter` 的参数与返回值都不出现 coil 类型 —— 换图片库、加统一占位、改缓存策略时，改动范围停在 `core:ui`，不下发到 10 个 feature。


---

## 6. UI 布局：自适应与边到边

### 6.1 自适应总则

- 所有屏幕**必须**能在手机、折叠屏、平板上正确渲染与交互（桌面 / TV / Auto / XR 不在当前产品范围内，不为它们做设计，但也不要把「手机竖屏」写成硬假设）。
- 用 `currentWindowAdaptiveInfo()` 判断窗口尺寸类（compact / medium / expanded）与输入方式，不要用 `LocalConfiguration.screenWidthDp` 自己判档。触控目标：触屏 ≥48dp，鼠标可更小。

### 6.2 导航区

- **底部导航栏是本 App 私有的**（`app/.../navigation/MikomiNavigationBar.kt`），**不依赖 `core:ui` 的通用 Tab 组件，也不用 `NavigationSuiteScaffold`。** 实测规格在 `MikomiNavBarSpec`（卡片 56 / 圆角 28、条目 48、图标 24、标签 12sp、宽度按文案不等分、整组居中），底栏占位经 `LocalBottomBarSpace` 下发给页面。
- 底栏**不投影**：elevation 在浅色地图上会扩散成灰边。
- 要加宽屏导航形态（rail / drawer）时，按 §6.3 的多窗格一起做，不要单独把底栏换成 `NavigationSuiteScaffold` —— 那会同时改掉视觉规格和手势切换的语义。

### 6.3 多窗格（尚未落地）

当前所有目的地都是单窗格。要做列表-详情时：

- 用 Navigation 3 的 **SceneStrategy**（`ListDetailSceneStrategy` / `SupportingPaneSceneStrategy`，来自 `androidx.compose.material3.adaptive:adaptive-navigation3`），**禁止** `ListDetailPaneScaffold` / `SupportingPaneScaffold`。
- 用 `entry(metadata = ...)` 标注 list/main 与 detail/supporting；`listPane(detailPlaceholder = ...)` 提供未选中占位。
- 详情页在大屏不显示返回箭头、不强制全屏；移动端全屏展示的内容在大屏要解除全屏。
- `adaptive-navigation3` 已在依赖清单里声明，但**尚未接进 `NavDisplay`**。接的时候一并评估返回键语义（大屏上「返回」是退出详情窗格还是退回上一页）。

### 6.4 自适应网格与列表

- 竖向列表按窗口宽度切列数：`LazyColumn` → `LazyVerticalGrid(GridCells.Adaptive(min.dp))`；`LazyVerticalStaggeredGrid` → `StaggeredGridCells.Adaptive(min.dp)`。
- 小而有界集合（约 1-10 项）用 `Row` / `Column` + `for`，别上懒布局（虚拟化与预取的开销比省下的还多）。
- 应用栏滚动行为用 `enterAlwaysScrollBehavior` / `exitUntilCollapsedScrollBehavior`，**各顶级目的地独立管理**。

### 6.5 边到边

- 每个 Activity 在 `setContent` 前 `enableEdgeToEdge()`；含软键盘的 Activity 在 manifest 设 `android:windowSoftInputMode="adjustResize"`。
- **Insets 避免双重 padding**：
  - 首选 `Scaffold`，把 `PaddingValues` 传给内容；`LazyColumn` 用 `contentPadding`（用父容器 `padding` 会裁剪内容，导致滚不到系统栏后面）。
  - Material 3 组件（`TopAppBar` 等）自动处理安全区。
  - 自适应 Scaffold **不**向下传 `PaddingValues`，必须每个屏幕单独处理；**禁止**给父级加 `safeDrawingPadding`。
- **IME**：内容容器首选 `Modifier.fitInside(WindowInsetsRulers.Ime.current)`（比 `imePadding()` 跳动少）；用 `imePadding()` 时必须放在 `verticalScroll()` 之前。父级已经用 `contentWindowInsets` 处理 IME 时不要再加。
- **系统栏对比度**：`ComponentActivity.enableEdgeToEdge` 自动处理；底部栏场景在 Activity 设 `window.isNavigationBarContrastEnforced = false`（SDK 29+）。**不要重复调用 `enableEdgeToEdge`**（日夜切换只翻 insets appearance）。

---

## 7. 依赖与构建

### 7.1 SDK 与工具链基线

`compileSdk 37` / `minSdk 24` / `targetSdk 36`，Java 17；AGP 9.2.1、Kotlin 2.4.21、KSP 2.3.12。升级前按 §7.5 评估，并按 §0.1 跑完整验收。

为关键路径（冷启动、主要滚动流）维护 **Baseline Profile**，配合 R8 获得稳定启动与滚动性能；用 `benchmark/` 的 Macrobenchmark 回归（参见 §10.4）。

### 7.2 版本目录

所有依赖和插件版本**必须**通过 `gradle/libs.versions.toml` 集中管理，禁止在模块 `build.gradle.kts` 里硬编码版本号。依赖名 kebab-case；Compose 相关用 bundle 统一管理。

### 7.3 约定插件

`build-logic` 里的九个插件是唯一的构建配置来源：

| 插件 id | 给谁用 |
| --- | --- |
| `mikomi.android.application` | `app`（R8 / 签名 / 打包） |
| `mikomi.android.library` | 通用 Android Library |
| `mikomi.android.compose` | 任何含 `@Composable` 的模块（**含 `core:navigation` 这种"不是 UI 模块但写了 `remember`"的模块**） |
| `mikomi.android.hilt` | Hilt + KSP |
| `mikomi.android.serialization` | kotlinx.serialization |
| `mikomi.android.room` | Room + KSP + schema 导出 |
| `mikomi.android.feature` | `feature-*` |
| `mikomi.android.core` | `core-*` |
| `mikomi.android.data` | `data-*` |

**含 `@Composable` 就必须挂 `mikomi.android.compose`。** 少了 Compose 编译器插件，`remember` 会被当普通内联函数，Kotlin 后端直接 Internal error —— 报错信息完全指不到真正的原因。

### 7.4 R8 / ProGuard

发布构建**必须**开启 R8 与资源压缩。以 `proguard-android-optimize.txt` 为基础，规则**窄而具体**，避免包级保留；遵循库的 Consumer Rules；禁止 `android.enableR8.fullMode=false`。定期用 R8 配置分析器审查冗余与过度保留的 keep 规则（app 当前已开 `isMinifyEnabled` + `isShrinkResources`）。

### 7.5 依赖引入评估

引入或升级前必须评估：维护状态、许可证、包体积、最低系统版本、隐私影响、安全风险、与现有依赖的冲突。**能写的自己写，不能写的才引**：这个项目的播放器、嗅探、插件解析都是自己实现的，多引一个库就意味着多一处版本约束。

**第三方图标库一律不再引入**（§5.6）：`material-icons-extended` 用 84 MB / 11106 个类换 73 个实际用到的图形，dex 在 debug 不裁剪，每一版都要为没用的 11000 个类付构建时间。要新图标就按 §5.6 的规格加进 `MikomiIcons`。

### 7.6 构建与运行约定

- **编译验收用 `./gradlew :app:assembleDebug --offline`**；需要整包验证才去掉 `--offline`。
- **不要习惯性 `clean`。** 增量构建是对的；只有在怀疑增量缓存坏了（例如改了约定插件不生效）时才 `clean` 一次。
- 单模块失败更快定位：改 `core:designsystem` 就 `./gradlew :core:designsystem:compileDebugKotlin`，别每次拉整包。
- 环境配置经 `buildConfigField` / `manifestPlaceholders` / CI 注入，不在代码里按 flavor 散落判断；密钥不进版本库（§8.1）。

---

## 8. 安全与隐私

### 8.1 凭证与敏感数据

**严禁**在代码或资源里硬编码密码、密钥、令牌。将来接入账号体系时，Token 必须走 EncryptedSharedPreferences / Android Keystore，不得明文落在 SharedPreferences 或普通文件。当前 App 没有账号体系，AniList / Bangumi 都是公开接口 —— **不要因为「规范里有」就去补一层加密存储，那里现在没有要存的东西**。

### 8.2 日志

日志**不得**输出 Token、Cookie、手机号、精确位置、完整请求头。Release 构建里不得存在无条件输出的 `Log` / `println`。网络层已有脱敏日志拦截器，新增拦截器必须复用它的脱敏逻辑。

### 8.3 组件导出与 WebView

- 私有组件**必须**显式 `android:exported="false"`。
- 当前导出的组件只有 `MainActivity` 与四个 `activity-alias`（桌面图标切换所需），它们都带 `LAUNCHER` intent-filter，**这是必要的，不要"为了安全"去掉**。每个 alias 的 `taskAffinity` 必须独立，否则切图标后点新图标会复用指向「已禁用 alias」的旧 task。
- 新增图标的三步写在 manifest 注释里，照做即可（mipmap 资源 → `enabled="false"` 的 alias → `AppIconStyle` 加一项）。
- **WebView**（巡礼页、网页出流）：限制不必要的文件访问与混合内容；`JavascriptInterface` 只暴露必要方法；通过 `evaluateJavascript` 注入的脚本与 URL 跳转必须校验来源与参数，禁止把站点返回的 URL 直接拿去加载。

### 8.4 权限

当前只申请 `INTERNET` 与 `ACCESS_NETWORK_STATE`，**没有运行时权限**。将来需要时按三态流程走：检查授权 → `shouldShowRequestPermissionRationale()` 说明 → `RequestPermission()` → 永久拒绝引导到系统设置。不缓存权限状态，每次 `checkSelfPermission()`，受保护 API 外面套 `try-catch (SecurityException)`。媒体选择用 Photo Picker，不为选图申请 `READ_MEDIA_*`。

---

## 9. 可观测性

### 9.1 埋点

- 事件名 `snake_case`（`screen_view`、`item_click`），schema 集中在 `core:analytics` 的 `AnalyticsEvents`，禁止散落魔法字符串。
- 上报异步、不阻塞主线程，失败重试有上限。
- **懒列表 item 内禁止用 `LaunchedEffect` 埋点**，用 `Modifier.onVisibilityChanged` + 曝光阈值。

### 9.2 崩溃上报

**当前未接入**（没有 Crashlytics / Sentry）。接入时：上报前脱敏（Token、手机号、精确位置、完整请求头），附非敏感上下文（版本、页面），并纳入发布门禁。不要现在为了"规范完整性"引一个 SDK 进来 —— 它先得有人看报表。

### 9.3 隐私

埋点遵循数据最小化，只采必要字段，提供同意与撤回。采集 / 共享 / 留存行为与 Google Play Data Safety 声明保持一致；第三方 SDK 的采集要在声明里披露。调试日志与线上埋点严格区分。

---

## 10. 性能与流畅度（TG 基准）

这一节是 Telegram 那套做法的可执行版。完整拆解与实测数字见 `docs/reference/telegram-smoothness.md`，这里只留规则。

### 10.1 先取证，再优化

- **不要靠猜。** 滚动卡顿先分清是「布局层」还是「重组层」：数一遍 `IntrinsicSize` / `animateItem()` / `BoxWithConstraints` / `onGloballyPositioned` 的用量与**位置**。当前 `IntrinsicSize` 0 处、`animateItem()` 1 处、`BoxWithConstraints` 8 个文件、`onGloballyPositioned` 17 处 —— 但这些数字本身不是结论：底栏、Tab 指示器、Hero 飞行、巡礼页都是「必须知道几何」的场景，`BoxWithConstraints` 多半是响应式分支。**数量只是起点，判断在位置。**
- 帧率、慢帧、启动耗时用 Macrobenchmark / Profiler 实测，不复述「感觉更快了」。
- 一次只改一处，改完测同一路径。**合并三处改动后测出的提升，归因不了任何一处。**

### 10.2 布局：尺寸稳定，变化过死区

- **列表项尺寸不依赖中间态。** 图片给 `aspectRatio` 或占位框，别让高度在滚动中跳变（这是 TG 布局层最核心的一条，比"把高度写死"重要得多）。
- **窗口尺寸变化不要每一 dp 都重排。** 学 TG `checkLayout()` 的 52dp 死区：先取整到栅格（`remember(maxWidthDp / 52)`）再参与计算。
- **条目动画只用于重排，不用于入场。** `animateItem()` 会强制额外的 placement pass；`EpisodeSection` 那一处是「切换正序 / 倒序时卡片按 `key` 平滑移到新位置」，属于正确用法。列表首次出现不要做「逐条飞入」。

### 10.3 动画：重活交给窗口 / 渲染层

- 逐帧变化的值只在 **draw / layout** 阶段读（§2.5），不进组合。
- 大面积动画（日夜揭示）用窗口级 `ViewAnimationUtils.createCircularReveal`，让 RenderThread 去算，不用 Compose `Animatable` 逐帧重组整树。
- **图标别在主线程逐帧画**：Lottie 的 `RenderMode.SOFTWARE` 就是反面教材，预渲染成位图序列再贴。
- 转场时长按**剩余距离折算**（TG 的做法：`max(200 * 剩余距离 / 总宽, 50)`），不写死一个固定值 —— 手势中途接管时手感差别很大。

### 10.4 验收门槛

- **`assembleDebug` 通过是底线**（§0.1）。性能专项才跑 `benchmark/` 里的 Macrobenchmark，且用真机。
- 改动落在「冷启动路径 / 首页滚动 / 详情页首帧」上时，跑一次对应 benchmark 并记录基线；只改视觉 token 时不跑。
- 不主动在模拟器上反复安装、截图验收。模拟器帧率不代表真机，反复安装的时间成本换不来有效结论。

### 10.5 明确不做的

- **全树颜色插值**：让整棵树每帧重组来"平滑换主题"，成本远超收益。
- **系统日夜切换动画**：`uiMode` 变化铺不了遮罩，强做必闪一帧。
- **把 `MaterialTheme.colorScheme` 的读取挪进 draw 阶段**：方向对，改造面覆盖全仓，需要单独评估 —— **不要顺手做**。

---

## 11. 代码审查检查清单

改完自查，按块过一遍。

### 架构

- 分层是否成立？UI 有没有直接碰数据源？Repository 是否门面、Room 是否 SSOT？
- ViewModel 是否只暴露不可变的 `StateFlow<UiState>`？是否用了 `viewModelScope`、调度器是否注入？
- 参数化 Route 是否按 §3.3 四步写全（`LaunchedEffect` 绑定、`flatMapLatest` 承接、`boundXxx` 对账、退出释放）？
- 导航是否类型安全（`data class : NavKey`）？栈的写操作是否只经过 `Navigator`？
- 模块依赖方向对不对？有没有 feature 互相依赖、有没有循环？`core:model` 是否仍然纯 Kotlin？
- 数据库版本变了是否有 `Migration`？有没有出现 `fallbackToDestructiveMigration`？

### 设计系统

- 有没有裸色值 / 裸字号 / 裸圆角 / 裸线宽 / 节奏类裸间距？跑 `verifyDesign` 看有没有新增 `[!]`。
- 新增的颜色 / 字号是否登记到 `MikomiColors` / `Typography`？新增 token 是否在 `TokenGallery` 出现？
- 控件体格是否进了 `spec` 而不是组件私有常量？
- 每个 `design-ok` 是否都写了理由？

### Compose 与性能

- 懒列表是否 `key` + `contentType`？item 参数是否稳定（没有整对象传进卡片）？
- 有没有把逐帧变化的值读进组合阶段？有没有 `onGloballyPositioned` 滥用、`animateItem()`、`IntrinsicSize`？
- 有没有向后写入？`remember` 的 key 里有没有混进"设置值"？
- 列表项高度会不会随图片加载跳变？

### 安全

- 有没有硬编码密钥？日志里有没有敏感信息？
- 新增组件是否 `exported="false"`？WebView 注入的 URL / 脚本有没有校验？

### 可观测性与文案

- 新埋点是否走 `AnalyticsEvents`、异步上报？
- 用户可见文案是否进 `strings.xml`？**失败提示是否只说结果、不暴露 URL / XPath / 异常类名？**

### 构建

- 依赖是否走版本目录？新模块是否挂了正确的约定插件（含 `@Composable` 必挂 `mikomi.android.compose`）？
- R8 keep 规则是否仍然窄？`assembleDebug` 是否通过？

---

## 12. 版本控制

提交信息格式 `type(scope): description`（`feat(home): add ranking section`），类型用 `feat` / `fix` / `refactor` / `docs` / `chore`。单人开发不强制分支与 PR 流程 —— 但**每个提交必须能编译**（§0.1），不要把「编译通过」留到下一次提交再补。

### 12.1 规范例外

本文档是默认基线。确需偏离时，在对应代码处写清理由（`// design-ok: ...` 或注释），并在本节追加一条：**偏离了什么、为什么、什么时候重新评估**。例外必须有**时效**，不接受"永久豁免"。

本文档随仓库入库（`.gitignore` 只排除 `docs/reference/`），改动有 git 兜底；但 `docs/reference/` 下的参考资料**不在 git 里，删改不可逆** —— 动它们之前先留一份副本。

---

## 附录 A：技术栈基线

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.4.21 / Java 17 |
| UI | Jetpack Compose + Material 3（BOM 2026.02.01） |
| 架构 | MVVM + UDF + Repository（无 UseCase 层） |
| 导航 | Navigation 3（类型安全路由，1.2.0）+ navigationevent |
| 自适应 | Material3 Adaptive 1.3.0（`adaptive-navigation3` 待接入） |
| 边到边 | `enableEdgeToEdge` + WindowInsets |
| 本地存储 | Room 2.8.5（结构化，schema 导出 + 手写 Migration）+ DataStore（偏好） |
| 网络 | Retrofit 2.11.0 + OkHttp + kotlinx.serialization |
| DI | Hilt 2.60.1（KSP） |
| 异步 | Coroutines 1.10.1 + Flow |
| 图片 | Coil 3.6.3 |
| 播放 | Media3 / ExoPlayer 1.11.1 + HLS |
| 毛玻璃 | Haze 1.7.3 |
| 构建 | Gradle Version Catalog + `build-logic` 约定插件（AGP 9.2.1） |
| 性能 | R8 + Baseline Profiles + Macrobenchmark |
| 质量闸门 | `assembleDebug` + `tokenAudit` + `contrastAudit` + `verifyDesign`（**无 ktlint / Detekt / CI**） |

---

## 附录 B：AI 协作约定

**不写测试**（§ 头部），**文案只说结果不说实现**（§4.3），**先取证再改**（§0.2 判据 1），**改完给命令与输出**而不是「已完成」。

省时间的用法：

- **引用章节号**：「按 §2.5 检查这段代码」比重新描述规范省一整轮。
- **指定输出形状**：「只给 diff」「只列违规项，格式：章节号 + 问题 + 修法」。
- **一次一个问题**：堆三个问题会得到三段都浅的回答。
- **大改前先问清**（小改别问）：改一个 `padding` 不需要需求访谈；重写数据层需要。判断标准是「这处改动会不会让用户看到不同的东西」。
- **贴证据**：错误日志、`uiautomator dump`、`grep` 计数、截图，任一种都比描述准。
