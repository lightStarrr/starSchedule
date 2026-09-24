# starSchedule 开发规范

本文件适用于整个仓库。后续开发、修复和 UI 改造都必须遵循以下约定。

## 一、基本要求

- 所有新增和修改的文本文件必须使用 UTF-8 编码。
- 修改前先执行 `git status --short`，检查工作区已有改动。
- 开始新的修改前，必须先检查、整理并提交已有改动；提交信息使用中文。
- 提交前必须删除本次工作产生的临时文件、调试文件和无用生成物。
- 不得擅自丢弃、覆盖或回滚用户已有改动；遇到来源不明或无法安全合并的改动时先说明情况。
- 每次提交只包含一个清晰、完整的修改主题，禁止把无关改动混入同一提交。
- 认为任务完成前，必须逐项检查需求，并提供编译、测试、静态检查、Git 差异或实际运行结果等事实依据。
- 不允许只因为代码可以编译就声称功能完成；涉及设备、ROM、通知、Provider、Widget 或系统设置跳转时，必须区分静态验证和真机验证。

## 二、项目架构目标

项目采用“UI / 状态管理 / 业务逻辑 / 数据与平台实现”分层，依赖方向必须保持单向：

```text
Compose UI
    ↓
ViewModel / UiState / UiAction
    ↓
UseCase / Repository
    ↓
Room DAO / 网络客户端 / 通知 / Widget / Provider
```

项目建设的首要目标是拆分职责和稳定接口，不是单纯移动文件或追求类的数量。

- UI 不得直接访问 DAO、数据库单例、OkHttp、通知管理器或系统 Service。
- DAO 只负责数据库读写和数据库事务，不得持有通知管理器、Context 或 UI 状态。
- 跨数据源、跨表或“写入后刷新提醒”等流程放入 Repository 或 UseCase。
- Android 系统能力集中放在 `platform` 层，通过接口供业务层调用。
- 可独立计算的逻辑必须写成不依赖 Android、Compose 和 Room 的纯 Kotlin 方法，并优先添加单元测试。
- 不为了形式增加空洞的接口、UseCase 或 Gradle 模块；只有存在明确职责边界或替换需求时才抽象。

## 三、目标目录结构

在边界稳定前，优先在 `app` 模块内按 package 拆分，不急于创建大量 Gradle 子模块。

```text
app/src/main/java/com/star/schedule/
├─ app/
│  ├─ MainActivity.kt
│  ├─ AppRoot.kt
│  └─ navigation/
├─ core/
│  ├─ common/
│  ├─ designsystem/
│  ├─ model/
│  ├─ database/
│  │  ├─ AppDatabase.kt
│  │  ├─ dao/
│  │  ├─ entity/
│  │  └─ converter/
│  └─ preferences/
├─ feature/
│  ├─ schedule/
│  │  ├─ data/
│  │  ├─ domain/
│  │  └─ presentation/
│  ├─ timetable/
│  │  ├─ data/
│  │  ├─ domain/
│  │  └─ presentation/
│  ├─ importing/
│  │  ├─ wakeup/
│  │  ├─ qiangzhi/
│  │  └─ xuexitong/
│  └─ settings/
│     ├─ data/
│     ├─ domain/
│     └─ presentation/
├─ integration/
│  └─ wakeup/
└─ platform/
   ├─ notification/
   │  └─ receiver/
   ├─ provider/
   └─ widget/
```

`wakeup-proxy` 保持为独立模块。除非任务明确要求，不改变它与主应用之间的 Provider 协议。

## 四、方法和文件拆分规范

- 一个文件只承载一个主要职责。大页面中的独立 Sheet、Dialog、编辑器和导入流程应拆到独立文件。
- 不以固定行数机械拆分，但文件超过约 500 行时必须检查是否混合了多个职责；超过约 1000 行通常应优先拆分职责。
- Composable 只负责渲染状态和发送事件，不在函数体内实现网络请求、复杂解析、数据库事务或提醒调度。
- 页面入口统一采用 `Route` 与无状态 `Screen` 两层：

```kotlin
@Composable
fun TimetableSettingsRoute(viewModel: TimetableSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimetableSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}
```

- ViewModel 对外暴露不可变 `StateFlow<UiState>`，页面操作使用明确的 `UiAction` 或语义清楚的方法。
- 避免把大量互相关联的布尔值散落在 Composable 中；对话框、加载、错误和选中状态应建模为明确状态。
- 可复用 UI 组件放到对应 feature 的 `presentation/components`；只有跨多个 feature 使用时才放入 `core/designsystem`。
- `utils` 不作为通用收纳目录。新代码必须根据业务含义放入 `domain`、`data`、`platform` 或具体 feature。
- 避免 `Manager`、`Helper`、`Utils` 等含义宽泛的名称，优先使用能够描述职责的名称，例如 `ReminderScheduler`、`ImportTimetableUseCase`、`TimetableRepository`。
- 公共方法默认使用最小可见性；能设为 `private` 或 `internal` 时不要公开。

## 五、UI 重写规范

- 旧 UI 不作为新 UI 的结构模板；只复用经过抽离并验证的业务逻辑和数据接口。
- 先建立 Design System，再逐页面重写，统一颜色、排版、圆角、间距、图标、动画和交互反馈。
- 新页面必须覆盖加载、空数据、正常、错误和不可用状态，不能只实现理想状态。
- 页面不得直接接收 `Activity`、DAO 或具体通知实现；优先接收 `UiState`、事件回调和必要的稳定模型。
- 文案放入字符串资源，禁止在 Composable 中新增硬编码用户可见文本。
- Material 组件的实验性或废弃 API 必须集中管理并记录原因，新增代码不得继续扩散已废弃 API。
- 动画应复用统一规格，并尊重系统减少动态效果设置；避免为了视觉效果引入影响可读性或操作稳定性的动画。
- 每完成一个新页面并确认功能等价后，应删除对应旧 UI，避免长期维护两套实现。
- UI 重写期间保持 Room 数据、偏好键、通知行为、Widget 数据和 Provider 协议兼容，除非当前任务明确要求迁移或破坏性调整。

### Edge-to-edge 与系统栏

- 项目 `targetSdk` 达到 35 及以上时，必须按 Android 15+ Edge-to-edge 规则实现；状态栏默认透明，内容会绘制到状态栏后方。
- 每个 Compose Activity 在 `onCreate` 中调用 `enableEdgeToEdge()`，以兼容低于 Android 15 的设备；状态栏样式使用 `SystemBarStyle.auto` 或与页面主题一致的明确样式。
- 不新增 `window.statusBarColor`、`Window.setStatusBarColor` 或固定 XML `statusBarColor` 作为主要方案；这些 API 在 Android 15 上已废弃且不再影响状态栏颜色。
- 状态栏背景由 Compose 内容绘制。统一背景直接延伸到系统栏区域；Material 3 `TopAppBar` 优先使用默认 `windowInsets`，自定义顶部栏使用 `statusBarsPadding()` 或 `windowInsetsPadding(WindowInsets.statusBars)`，同一层级只能处理一次。
- 不使用固定的 24dp/状态栏高度，也不同时叠加 `Scaffold`、TopAppBar 和自定义 `statusBarsPadding()`，避免重复留白。
- 当图片、课程卡片或渐变背景导致状态栏图标对比度不足时，在 Compose 中使用覆盖状态栏区域的颜色或渐变保护层；不得退回直接设置系统状态栏颜色。
- 自动适配状态栏图标时，只采样应用窗口在状态栏下方的内容；默认每 1500ms 更新一次，并使用带滞回的亮度阈值，禁止每帧截图或在亮度临界值附近反复切换图标。
- 导航栏同样遵循 Edge-to-edge 和 Insets 规则；验证时至少检查手势导航与三键导航的内容遮挡、背景保护和图标对比度。

## 六、数据与业务层规范

- 按领域拆分 DAO，例如 `TimetableDao`、`CourseDao`、`LessonTimeDao`、`PreferenceDao`、`ReminderDao` 和 `DayNoteDao`。
- Entity 是数据库结构，不应直接成为所有 UI 的展示模型；复杂页面使用领域模型或 UI Model。
- Repository 负责组合 DAO、网络和平台接口，向 ViewModel 提供稳定的数据流和操作结果。
- 业务操作使用明确的输入和返回类型，避免只返回 `Boolean` 表示所有失败情况；需要区分原因时使用 sealed result 或具有错误类型的 `Result` 包装。
- 偏好键集中定义并通过 Repository 访问，禁止在页面或 ViewModel 中继续散落字符串键。
- 数据库 Schema、迁移和现有数据兼容属于高风险改动，必须单独提交并增加迁移验证。

## 七、协程和生命周期规范

- 禁止新增 `GlobalScope`。
- 禁止在主线程和生命周期回调中使用 `runBlocking`。
- ViewModel 使用 `viewModelScope`，Compose 副作用使用 `LaunchedEffect` 或受控的 `rememberCoroutineScope`。
- Receiver、Service、Provider 和 Widget 使用与组件生命周期匹配的执行方式；需要延迟完成的广播使用 `goAsync()`，可延期后台任务优先使用 WorkManager。
- 不在普通方法中随意创建无人管理的 `CoroutineScope`。
- Dispatcher 应由边界层统一管理；可测试业务逻辑不得硬编码依赖主线程或 Android Looper。

## 八、兼容性边界

调整项目结构或重写 UI 时，除非任务明确要求，不得改变以下外部契约：

- Room 表结构、已有数据和数据库版本。
- `com.star.schedule.export` Provider authority 及其数据协议。
- WakeUp 代理使用的包名、Provider authority 和字段映射。
- 已存在的偏好键及其值语义。
- 通知 Channel、闹钟 requestCode、广播 Action 和提醒触发语义。
- Widget、系统启动广播、Flyme 灵动通知及 ColorOS/WakeUp 能力判断行为。

涉及厂商 ROM 的能力检测、权限状态和设置跳转时，静态代码检查不能替代目标设备验证。未知状态不得直接当作关闭状态处理。

## 九、代码风格

- 遵循 Kotlin 官方代码风格，使用 4 空格缩进和尾随逗号。
- 建议通过 `.editorconfig` 固定 `charset = utf-8`、`end_of_line = lf`、`insert_final_newline = true` 和合理的最大行宽。
- 类与 Composable 使用清晰的 PascalCase 名称；变量和方法使用 camelCase；常量使用 UPPER_SNAKE_CASE。
- 避免通配符导入、无意义缩写、重复注释和描述代码表面行为的注释。
- 注释说明“为什么这样做”和外部限制，不重复翻译代码本身。
- 依赖版本统一维护在 Version Catalog，不在各模块散落硬编码版本。

## 十、项目建设顺序

项目开发与架构整理原则上按以下顺序推进：

1. 建立可重复运行的编译和测试基线。
2. 为日期、周数、解析、导入映射和提醒计算等纯逻辑补充特征测试。
3. 抽离 Repository、UseCase 和平台接口，解除旧 UI 对 DAO 与系统实现的直接依赖。
4. 将大型旧文件按职责拆分，但不同时改变业务行为。
5. 建立新的 Design System、`UiState`、`UiAction` 和 ViewModel。
6. 按页面重写 UI，并逐个验证功能、状态和交互。
7. 新页面验证完成后删除旧实现和无用兼容代码。
8. 边界稳定后再评估是否拆分为更多 Gradle 模块。

禁止在同一个提交中同时进行大规模文件移动、业务逻辑修改和 UI 重写。确有必要时，必须先拆成可独立验证的提交。

## 十一、验证与提交要求

每次修改后至少完成以下检查：

1. 使用 `git diff --check` 检查空白和补丁问题。
2. 检查 `git diff`，确认没有无关文件、临时文件或意外生成物。
3. 运行与修改相关的单元测试。
4. 涉及 Kotlin、资源、Manifest 或 Gradle 配置时，至少完成对应模块编译；条件允许时运行完整构建。
5. 涉及 UI 时，检查正常、空状态、加载、错误、深色模式和不同屏幕尺寸。
6. 涉及通知、Widget、Provider、系统权限或厂商 ROM 时，记录静态验证与真机验证各自的结果。
7. 再次运行 `git status --short`，确保提交范围清晰。

当任务要求 UI 和现有效果保持不变时，还必须遵循以下要求：

- 修改前在指定模拟器上记录关键页面截图、界面层级和必要的交互路径；修改后使用相同设备尺寸、密度、主题和数据状态复查。
- 涉及动画或状态切换时，不能只比较静态截图，必须实际触发交互并检查开始状态、结束状态和切换过程。
- 模拟器安装、启动和界面回归不能替代程序化测试；已有单元测试必须保留，并为抽离出的纯逻辑补充测试。
- 基线截图、界面树和录屏默认存放在仓库外的临时目录；除非任务明确要求作为测试资源提交，否则提交前不得留在仓库中。
- 模拟器无法证明厂商 ROM 专属能力、通知样式或系统设置跳转正确；这些项目必须明确记录为需要对应设备验证。

提交信息使用中文，并描述实际完成的修改，例如：

```text
整理课表导入业务接口
拆分课表设置页面组件
重写课程表主页界面
补充课程周数计算测试
```
