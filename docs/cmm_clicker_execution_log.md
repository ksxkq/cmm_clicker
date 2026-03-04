# cmm_clicker 执行日志（持续维护）

更新时间：2026-03-02

## 1. 当前目标

1. 完成 Kotlin + Compose 重写的最小可运行闭环（权限 -> 动作执行 -> 可观测）。
2. 保留旧项目关键语义（如 `dupClick`、`closeCurrentUI` no-op、跨 flow jump）并用新架构实现。
3. 构建长期可演进基础：图模型、插件化动作、导入升级链、编辑器可用性。

## 2. 已完成

### 2.1 运行内核与动作体系

1. 建立 `FlowGraph` 数据模型：`TaskBundle / TaskFlow / FlowNode / FlowEdge`。
2. 建立运行时引擎：支持 `start/end/action/branch/jump/folderRef/subTaskRef`。
3. 完成基础动作插件：`click/swipe/record/dupClick`，并保留 `closeCurrentUI` no-op 插件。
4. 修复子流程语义：`folderRef/subTaskRef` 走调用栈返回，不是简单平跳。

### 2.2 Android 辅助能力

1. 接入 AccessibilityService 并打通真实手势执行。
2. 接入点击/滑动/路径的可视化反馈层（黄色反馈）。
3. 增加手势分发诊断：`dispatch/success/failed/last reason`。
4. 修复流程“卡在运行中”问题（超时保护、异常保护、UI finally 收敛）。

### 2.3 自动授权与自动启用

1. 增加 `WRITE_SECURE_SETTINGS` 权限声明。
2. 实现“已授权后自动开启辅助服务”逻辑。
3. 在主页面提供：授权状态、自动开启按钮、自动开启结果反馈。

### 2.4 UI 主题系统（本次新增）

1. 新增统一主题模式：`Mono Light / Mono Dark`。
2. 新增全局主题入口 `CmmClickerTheme`，统一颜色、字体、圆角。
3. 主页面重构为黑白极简卡片风格，操作控件统一边框和间距。
4. 页面提供主题切换入口，后续可继续扩展新风格而不改业务页面逻辑。
5. 主题模式已接入 DataStore 持久化，重启应用后可恢复上次选择。
6. 修复 `Switch` 关闭态可见性，显式设置未选中轨道/边框/滑块颜色。

### 2.5 开发流程治理（本次新增）

1. 新增仓库级 `AGENTS.md`（并补充 `AGENT.md` 入口文件），固化“文档驱动开发 + 同步更新文档”规则。
2. 明确“实现完成后必须同步执行日志与领域文档”的完成标准。

### 2.6 编辑器 MVP（本次新增）

1. 新增 `TaskGraphEditorStore`，落地编辑器状态管理与 undo/redo。
2. 页面增加 `控制台/编辑器` 双模式切换，编辑器可完成 flow/node 选择与基础属性编辑。
3. 支持节点新增/删除/上下移动、kind/actionType/pluginId/flags/params 编辑。
4. 新增校验面板，实时展示图结构问题。
5. Jump 类节点新增目标选择器（flow/node 点选）。
6. 节点列表新增 jump 目标摘要展示（`-> flow/node`）。
7. Branch 节点支持 `variableKey`/`operator`/`expectedValue` 与 TRUE/FALSE 目标边编辑。
8. 新增流程图预览（第一版），支持节点点击选中、相关边高亮、jump 虚线连线高亮。
9. 参数编辑改为 schema 驱动：按节点类型显示字段，支持枚举按钮与数值键盘输入。
10. 连线文本预览（边与 jump 引用）与运行按钮已接入当前编辑后的 bundle。
11. 新增编辑器与 runtime 相关单测，覆盖新增节点、undo/redo、入口节点保护、branch/属性编辑链路。

### 2.7 数据升级基础设施（本次新增）

1. 新增 `BundleSchema.CURRENT_VERSION` 统一管理 schema 版本常量。
2. 新增 `BundleMigrationEngine` 与迁移结果模型（状态、日志、错误信息）。
3. 新增默认迁移链入口 `BundleMigrations.default()` 与 `v0 -> v1` 示例迁移 step。
4. 新增迁移单测，覆盖：无需迁移、正常迁移、未来版本拒绝、迁移链缺失失败。

### 2.8 任务真实操作体验（本次新增）

1. 新增本地任务仓库 `LocalFileTaskRepository`，支持 JSON 持久化（任务重启可恢复）。
2. 新增任务列表页：新建、运行入口、浮窗编辑、重命名、复制、删除。
3. 新建任务默认模板为 `start -> click -> end`（用于当前研发阶段快速起步）。
4. 新增任务运行历史展示：`最近运行时间/状态/摘要`。
5. 任务编辑改为浮窗链路：动作列表浮窗（默认简版）-> 动作编辑浮窗（参数详情）。
6. 编辑器接入当前活动任务，支持“保存任务”按钮与编辑自动保存。
7. 控制台运行入口由“运行测试流程”改为“运行当前任务”。
8. 修复 kind 切换参数污染：`action -> jump/branch` 不再保留 click 参数，参数区与类型一致。
9. 已落地“全局浮窗编辑器”（`TYPE_ACCESSIBILITY_OVERLAY`）：可在其它 App 页面直接编辑当前任务动作。
10. 编辑器动作类型收敛为已实现集合（`click/dupClick/swipe/record/closeCurrentUI`），`jump` 统一通过 `kind=JUMP + targetFlowId/targetNodeId` 配置，避免误配。
11. 全局浮窗新增公共弹窗容器（Dialog Scaffold）：统一头部标题/菜单 action/底部操作区，并支持按页面控制显示。
12. 全局浮窗按钮统一使用 Material3 Compose 组件（黑白体系，主动作 `Button`、普通动作 `OutlinedButton`），样式与交互反馈复用应用主题。
13. 全局浮窗预览新增图形化连线（普通边 + jump 虚线），并保留文本连线列表。
14. 运行默认模式改为 `REAL`（`dryRun=false`），运行摘要增加“模式=REAL/DRY_RUN”字段，避免“完成但无动作”误判。
15. 全局浮窗接入统一主题：读取 `ThemePreferenceStore` 的 `AppThemeMode`，通过 `CmmClickerTheme` 直接渲染 Compose UI。
16. 主题令牌改为单一来源：新增 `AppThemeTokens`，Compose 首页与全局浮窗都从同一份 palette 取色。
17. 动作列表新增 jump 连线预览（列表内虚线连接源动作与目标动作，限同 flow）。
18. 修复手势反馈偏移：执行坐标改用真实屏幕尺寸，反馈层补齐系统栏偏移与 cutout 布局。
19. 全局浮窗编辑器完成技术栈收敛：`TaskEditorGlobalOverlay` 从 View 控件实现迁移为 Compose 实现（WindowManager 仅作为系统窗口宿主）。
20. 修复全局浮窗“可触摸但不可见”问题：浮窗窗口 `addView` 后再 `setContent`，补充硬件加速与窗口位置重置，降低 MIUI 设备上透明拦截层风险。
21. 全局浮窗增加手动 `Recomposer` 驱动（`ComposeView.setParentCompositionContext(recomposer)`），规避部分 ROM 下自动 window recomposer 不启动导致的“窗口有触摸、无 Compose 内容”问题。
22. 修复 `Recomposer` 崩溃：手动 recomposer 协程上下文改为 `Dispatchers.Main.immediate + AndroidUiDispatcher.Main`，补齐 `MonotonicFrameClock`，解决点击编辑时 `IllegalStateException`。
23. 增加浮窗组合启动诊断：补充 `recomposer` 生命周期日志与 `createComposition()` 显式触发日志，用于定位“窗口已显示但 Compose 内容未渲染”的 ROM 兼容问题。
24. 调整手动 `Recomposer` 线程上下文为 `AndroidUiDispatcher.CurrentThread`（并使用 `UNDISPATCHED` 启动）以匹配 Compose UI 推荐模型，新增 `compose monitor` 日志输出 `recomposerActive/currentState`。
25. 新增组合进入诊断日志：在 `setContent` 顶层输出 `compose lambda entered` 与 `SideEffect` 提交日志，区分“组合未执行”和“组合执行但未绘制”两类问题。
26. 修复服务浮窗生命周期事件顺序：不再提前设置 `RESUMED`，改为 `setContent/createComposition` 后再发送 `ON_START/ON_RESUME`，并将 `OverlayComposeOwner` 改为标准 lifecycle event 驱动（含 pause/stop/destroy）。
27. 修复浮窗偶发崩溃 `no event up from DESTROYED`：在 `compose.post` 回调中增加 overlay/owner 存活校验，并为 `OverlayComposeOwner.start/resume/destroy` 增加 destroyed 防重入保护。
28. 修复浮窗 owner 误销毁：移除 `ComposeView.doOnDetach` 里的立即 `owner.destroy()`，统一由 `removeOverlay()` 负责销毁，避免“窗口仍在但组合启动被误判为 disposed”。
29. 浮窗交互升级为“弹窗化体验”：窗口改为全屏 overlay，禁用拖动；新增背景渐暗动画（scrim fade）与内容自底部上滑动画（sheet slide+fade）。
30. “查看预览”按钮改为黑白统一样式（激活态主按钮、未激活态描边按钮），消除紫色风格漂移。
31. 浮窗遮罩层修正：覆盖状态栏/刘海区域（`FLAG_LAYOUT_NO_LIMITS + SHORT_EDGES`），并移除调试灰底，改为真正半透明 scrim。
32. 浮窗支持“点击内容外区域关闭”（遮罩点击关闭），符合常规弹窗交互预期。
33. 浮窗关闭改为动画驱动：先执行背景淡出 + 面板下滑退出，再延迟 `removeView`，避免突兀消失。
34. 修复遮罩点击实现对内容交互的副作用：内容面板改为“空白区消费点击但不拦截子按钮”，恢复“添加动作/编辑”等按钮可点击性。
35. 修复弹窗入场动画丢失：恢复独立 `sheetVisible` 进场状态（初始 `false` -> 延迟切换 `true`），确保每次打开都执行上滑/淡入动画。
36. 参数 schema 进入 v1.1：`EditorParamSchema` 增加 `required/default/min/max/helperText`，`TaskGraphEditorStore` 接入默认值合并；主页面编辑器与全局浮窗编辑器都新增“填充默认值”入口，并统一显示参数校验错误（必填/枚举/数值范围）与辅助文案。
37. 动作类型切换体验优化：`TaskGraphEditorStore.updateSelectedNodeActionType` 在切换时自动清理旧类型遗留参数（如 `click` 的 `x/y`），保留公共字段并补齐新类型默认值；新增编辑器单测覆盖 schema 默认值/校验与参数清理行为。
38. 全局浮窗编辑页交互升级为“堆叠弹窗”：动作列表作为底层保留，动作详情作为上层卡片叠加进场；底层同步缩放+轻微变暗、上层自底部上滑淡入，返回时反向回退，替代硬切换。
39. 修复浮窗参数编辑键盘遮挡：根容器增加 `imePadding`，参数输入框增加 `BringIntoViewRequester` 聚焦自动滚动到可见区域，减少输入时被软键盘覆盖的问题。
40. 全局浮窗新增“路由栈 + 面包屑”基础：编辑流程从单一 `editingNodeId` 升级为 `OverlayRoute` 栈模型，弹窗头部显示只读路径（任务/动作列表/当前节点），为后续 folder/子流程等多级编辑场景预留深层导航能力。
41. 面包屑升级到 v2：支持点击任意上层路径直接回跳（`popToRouteDepth`），并保持当前堆叠弹窗层级动画一致性。
42. 顶部拥挤优化：弹窗头部改为“两段式布局”——标题行只保留返回/标题/关闭，头部操作按钮迁移到独立横向滚动 action 行，避免标题被挤压和按钮拥堵。
43. Flow 管理能力落地（Store）：新增 `addFlow/renameSelectedFlow/deleteSelectedFlow/setSelectedFlowEntryNode`，支持流程创建、重命名、删除、入口节点切换；删除增加保护（仅剩一个 flow 或被 jump/folder/subTask 引用时拒绝）。
44. Flow 管理能力接入全局浮窗：动作列表页新增“流程”入口，新增“流程管理”堆叠页（流程切换、入口节点设置、新增流程、删除当前流程），并复用路由栈/面包屑回跳。
45. 新增 Flow 管理相关单测：覆盖新增流程、重命名、入口节点切换、删除保护与可删除路径。
46. Flow 删除反馈升级：`deleteSelectedFlow` 返回结构化结果（成功/仅剩一个流程/目标不存在/被引用），浮窗可显示更明确失败原因并附引用来源预览（`flowId/nodeId`）。
47. 浮窗遮罩点击交互修正：当存在可返回页面层级时，点击弹窗外部优先执行“返回上一级”（`popRoute`）；仅在根页面时才关闭整个浮窗，统一与左上角返回语义。
48. Jump/Folder/SubTask 目标可用性优化：在节点编辑页对无效目标给出错误提示，并提供“一键修复为当前流程入口”操作，降低引用失效时的手动修复成本。
49. 校验器兼容性增强：`FlowGraphValidator` 解析 `targetFlowId/targetNodeId` 改为 `toString().trim()`，避免参数类型不是 `String` 时误报 `jump_target_missing`/`jump_target_invalid`；补充对应单测。
50. 任务列表 v2 第一批：新增“搜索 + 排序 + 删除确认”能力。任务列表支持按名称/ID 搜索，支持按最近更新/最近运行/名称排序，并新增删除任务二次确认弹窗，降低误删与任务数量增长后的定位成本。
51. 动作列表 jump 连线交互增强：连线从“仅展示”升级为“可点击跳转”，点击虚线后会直接定位到目标动作编辑页，并给出状态提示，提升跨动作定位效率。
52. 修复详情页返回退场动画丢失：详情层新增“活动路由快照”与“退场延迟卸载”机制，返回到上一级时先播放完整退出动画，再清理详情层内容，避免视觉上像直接移除。
53. 浮窗堆叠转场动效调优：详情层进/退场改为更柔和的 spring 方案，并同步底层列表回弹与变暗恢复时序，降低“硬切”观感，提升返回动画丝滑度。
54. 浮窗预览图交互增强（第一版）：支持节点拖拽布局（会话内）、点击节点/连线直达目标动作编辑，并新增“缺失目标修复”列表（预览区可一键修复 jump/folder/subTask 的无效目标）。
55. 修复预览图拖拽误触：拖拽手势不再触发节点点击进入编辑页；拖拽结束后会抑制一次 tap，避免“拖拽后立刻误跳转”的冲突体验。
56. 修复预览图“拖拽无反应”：移除点位缓存的错误 `remember` 用法，改为实时合并 `pointOverrides + defaultPoints` 并通过 `rememberUpdatedState` 供手势协程读取，确保拖拽时节点位置实时重绘。
57. 主页面落地“Shortcuts 风格 V1 骨架（任务库侧）”：任务页改为“快捷指令库”卡片体验（搜索 + 分段 + 卡片操作），并新增卡片化任务操作结构。
58. 根据体验反馈收敛交互：移除主页面普通编辑入口，任务编辑统一走全局浮窗；去掉“选择/普通编辑”按钮，并重排卡片按钮布局（浮窗编辑/运行主操作 + 复制/重命名/删除次操作）以解决按钮拥挤问题。
59. 首页结构升级：主界面改为底部导航（任务/控制台），并接入 `Scaffold` 安全区内边距，修复 Android 14 边到边场景下内容被状态栏覆盖；任务卡片移除“选中态”视觉与点击选中交互，保留内部“当前任务”上下文用于控制台运行。
60. MainActivity 结构拆分落地：将任务页、控制台页与通用 UI 组件拆到独立文件（`MainTabsRoute.kt`/`TaskTabScreen.kt`/`ConsoleTabScreen.kt`/`MainUiComponents.kt`），`MainActivity` 缩减为状态编排与回调分发入口（约 2438 行 -> 354 行）。
61. 底部导航风格升级：移除 Material `NavigationBar`，改为自定义 iOS 风格悬浮胶囊 Tab Bar（选中态胶囊高亮 + 自绘图标），并保留统一主题令牌与安全区适配。
62. 任务卡片交互收敛：卡片点击即打开浮窗编辑（移除显式编辑按钮）；`复制/重命名/删除` 收纳到右上角溢出菜单；运行入口改为播放 icon 按钮。
63. 任务卡片动作样式升级：运行与更多按钮统一为圆形 icon 按钮（运行固定右下角悬浮位）；新增全局 `AppDropdownMenu/AppDropdownMenuItem` 与 `CircleActionIconButton` 组件，统一菜单边框/圆角/文本风格，减少页面内手写样式分叉。
64. 图标体系升级：接入 `material-icons-extended`，任务卡片运行/更多按钮改用标准库图标（不再使用手绘 icon）；菜单按钮锚定到卡片绝对右上角，保证布局稳定。
65. 圆形按钮交互修正：`CircleActionIconButton` 增加 `clip(CircleShape)`，按压/hover/ripple 反馈与按钮轮廓一致，不再出现长方形反馈区域。
66. 新增“全局操作面板”浮窗（`设置/录制/开始` 三按钮）：以 `TYPE_ACCESSIBILITY_OVERLAY` 常驻任意 App 界面，支持一键打开任务设置、开始执行、进入录制态。
67. 任务列表组件抽象为可复用 `TaskLibraryPanel`，主页面任务库与浮窗“设置”页复用同一套任务管理 UI（新建/搜索/筛选/重命名/复制/删除）。
68. 操作面板“开始”接入“最近启动任务”语义：优先执行 `lastStartedTaskId`，回退到当前选中任务或首个任务，并回写最近运行状态摘要。
69. 操作面板“录制”打通手势采集闭环（MVP）：在全屏录制层采集点击/轨迹，自动映射为 `CLICK/RECORD` 动作并插入到任务入口 flow 的 end 之前，随后保存任务。
70. 控制台新增操作面板入口（打开/关闭），用于从应用内启动全局操作面板后切到任意 App 进行操作。
71. 操作面板视觉与交互重构：改为“可拖动矩形工具条 + 3 个 icon（设置/录制/开始）”，移除文字按钮形态；新增面板与图标内容入场动画（淡入 + 轻微缩放/上浮）。
72. 操作面板支持拖拽定位：悬浮条可在屏幕内自由拖动（边界约束），切到设置页再返回后保持拖拽位置，不重置到默认点位。
73. 修复操作面板尺寸异常：面板模式移除根层 `fillMaxSize`，窗口按内容 `wrap_content` 渲染，避免出现“看起来像弹窗”以及拖动后高度被撑满屏幕的问题。
74. 操作面板新增右侧“移除”icon，并接入退出动效（先淡出/缩放再执行 `removeView`），关闭体验与弹窗体系一致。
75. 移除操作面板入场时的“半透明灰底感”：面板改为纯主题背景 + 零阴影卡片，入场动画由整体轻弹与 icon 分段显现承担，不再出现额外灰色矩形层。
76. 修复“设置弹窗关闭后重复播放面板入场动画”：新增 `closeSettingsPanel()` 状态收敛，返回面板时保持当前形态，避免每次从设置返回都像重新 add。
77. 操作面板动效升级：入场为 4 个 icon 依次出现；移除为动作 icon 依次消失 -> 面板收缩到关闭按钮宽度 -> 最后整体淡出移除，交互节奏更灵动。
78. 操作面板主动画改为 spring 驱动：面板 alpha/scale/translate 与 icon 进退场都切换到 spring 节奏，替换此前偏硬的 tween 动效。
79. 修复关闭收缩锚点错误：面板收缩改为以右侧关闭按钮为锚点（`TransformOrigin(1f, 0.5f)`），不再向左侧设置按钮位置收缩。
80. 操作面板动效按当前产品要求简化：取消“icon 逐个显隐 + 收缩锚点”复杂动效，统一为“打开/关闭都采用渐隐 + 轻微缩放 + 轻微位移”，并保留“设置页返回不重播入场动画”。
81. 修复面板关闭时的“闪一下”：关闭中不再把 icon 切到禁用态（避免先发生一次颜色态切换），改为在点击逻辑层拦截交互，仅保留纯退场动画。
82. 设置页补齐过渡动画：从操作面板进入任务列表时增加 scrim 淡入 + 底部面板轻微上浮/缩放过渡；关闭设置页时执行反向动画后再切回小面板窗口。
83. 修复“设置里点任务后详情没打开且弹窗直接关掉”：任务卡片点击改为在设置浮窗内直接进入编辑路由（不再关闭设置页后开新编辑浮窗）。
84. 按交互约束重构浮窗层级：操作面板与任务列表改为两个独立 `addView`（两个 overlay 窗口）；不再通过同一窗口切换 `WRAP_CONTENT/MATCH_PARENT`。
85. 设置浮窗内编辑改为同窗路由堆叠：`任务列表 -> 动作列表 -> 动作详情` 全部在同一个“设置浮窗 addView”内完成，避免“编辑再开第三层 addView”导致动效断裂。
86. 修复设置浮窗编辑返回能力：动作列表与动作详情页新增顶部“返回”链路，支持回到上一层直至任务列表，再关闭设置浮窗。
87. 设置浮窗转场统一：设置面板入场改为底部上滑 + 淡入；设置内路由改为堆叠动画（任务列表作为底层，动作列表/详情层逐层上滑），与全局浮窗动效语言一致。
88. 圆角触摸反馈一致性修复（第一批）：任务卡片改为 `Card(onClick)`（替代 `modifier.clickable`），底部导航项增加 `clip(RoundedCornerShape)`，避免按压反馈出现直角矩形。
89. 修复“任务列表弹窗关闭时面板闪烁”：操作面板改为常驻组合树，仅通过 `alpha/scale/translate` 控制显隐，不再在 `settingsVisible` 切换时卸载/重建面板节点。
90. 设置弹窗内容区点击消费修正：设置面板容器改为显式消费内部空白点击（`indication = null`），避免内容区空白点击冒泡到遮罩触发返回/关闭。
91. 设置浮窗编辑转场收敛为“整页层”动画：`任务列表 -> 动作列表 -> 动作详情` 三层都包含完整标题栏与内容区，确保堆叠动画作用于整页，而非仅内容区。
92. 修复“面板打开的任务列表无法滑动”：设置页遮罩改为独立底层可点击层（与内容层分离），恢复列表滚动与拖拽手势传递。
93. 增强堆叠视觉层级：动作层与详情层改为带 inset 的独立卡片层（不同内边距），并增加底层缩放/位移/变暗幅度，堆叠关系更明显。
94. 设置浮窗堆叠动画参数与首页编辑器对齐：动作层/详情层切换改为 spring（低刚度）上滑 + fade，退场改为 spring 回落，减少“和首页不是同一套动效”的割裂感。
95. 设置浮窗任务列表补齐独立滚动容器：在“任务设置”页头部下方使用 `weight + verticalScroll` 承载 `TaskLibraryPanel`，确保大量任务时可连续滑动浏览。
96. 修复“固定外壳内堆叠”的结构问题：移除设置浮窗内固定外层卡片壳，改为 `TaskList/ActionList/NodeEditor` 三层各自独立卡片参与动画，避免“轮廓不变、仅内容切换”的违和感。
97. 修复首页编辑浮窗“卡片套卡片”观感：`OverlayDialogScaffold` 边框从 `modifier.border` 改为 `Card(shape + border)` 同源绘制，统一圆角与边框轮廓，消除双层壳错觉。
98. 强化设置浮窗三层堆叠（2->3）反馈：当第 3 层出现时，第 1 层继续退让（额外缩放/上移/变暗），第 2 层也同步缩放与上移，并加大第 3 层 inset，避免“新层直接覆盖上一层”。
99. 设置浮窗堆叠动效按 iOS 语义收敛：顶部新页面不做缩小，改为下层页面缩小/上移/变暗；并取消“越上层越窄”的过度横向 inset，避免误感知为“上层在变小”。
100. 堆叠动画参数统一化：新增 `OverlayStackMotion` 作为首页编辑浮窗与操作面板设置浮窗的共享规范（scale/translate/inset/enter-exit ratio），避免两处参数继续分叉。
101. 调整三层堆叠露出高度：第 3 层顶部 inset 从过大值回调到统一规范，降低“最后一层露出过多”的视觉突兀。
102. 修复首页编辑堆叠时底层灰蒙层：移除底层卡片额外 scrim 叠色，改为仅通过缩放与位移表达层级，消除“半透明灰背景”。
103. 修复“3 层看不到第 2 层露出”：增大 `SECOND/THIRD` 顶部 inset 差值，并调整下层退让位移方向（向下退让），恢复第 2 层在第 3 层出现时的可见露出带。
104. 可见堆叠深度限制为 2 层（设置浮窗）：当进入第 3 级页面时隐藏最底层任务列表，仅保留“上一层 + 当前层”可见，逻辑路由仍可继续入栈。
105. 首页编辑浮窗与设置浮窗的外层样式继续统一：sheet `maxWidth/heightFraction/scrimAlpha` 抽到 `OverlayStackMotion` 共用常量，修复“一层堆叠观感不一致”。
106. 堆叠参数微调（统一常量）：`1->2` 增强底层退让（更容易看到第一层露出），同时降低第二层固定 inset；`2->3` 取消中间层缩放（`THIRD_LAYER_PREVIOUS_SCALE=1f`），修复“第二层看起来被压矮”的问题。
107. 修复“1->2 露出带消失”：回调退让参数为“轻缩放 + 轻微上移”并恢复第二层顶部 inset（`SECOND_LAYER_TOP_INSET_DP=18`），确保打开下一页时首层顶部仍可见一条露出内容。
108. 迭代堆叠映射修正：`2->3` 时“当前卡片取代上一层位置”，上一层退到后层位置（通过交换 Action/Node 的 top inset 角色），符合连续压栈预期。
109. 底部露出收敛：统一将堆叠缩放幅度调轻（`SECOND_LAYER_SCALE/THIRD_LAYER_PREVIOUS_SCALE=0.99`），减少底部缩窄与空隙感。
110. 按产品反馈再次收敛堆叠强度：下层缩放恢复到更明显的 `0.965`；并将“后层位置”回调为 `topInset=0`，确保 `2->3` 时第 2 层准确回到第 1 层原位、第 3 层占据第 2 层原位。
111. 进一步增强“1->2 露出带”：前景层 top inset 调整为 `24dp`，让下层顶部可见内容更明显。
112. 修复“2->3 时第二层往上顶一下”：第二层 top inset 切换改为 `animateDpAsState + spring` 平滑过渡，消除位置瞬时跳变。
113. `OverlayStackMotion` 参数命名清理：移除 `THIRD_*`/`SECOND_*` 旧命名，改为“两层可见堆叠”语义（`PREVIOUS_LAYER_* / FOREGROUND_LAYER_* / BACKGROUND_LAYER_*`），与当前可见层策略一致。
114. 修复打开第 3 级页面崩溃（`Padding must be non-negative`）：对 `actionTopInset` 应用 `coerceAtLeast(0.dp)` 防护，避免 spring 回弹瞬时负值触发非法 padding。
115. 操作面板录制能力升级为“会话模式”：`录制 -> 暂停/继续 -> 停止`，支持一次录制中连续采集多步手势（点击/轨迹），不再“单次手势即自动落库”。
116. 录制停止后新增保存弹窗（全局浮窗内）：可输入任务名，支持“保存为新任务”或“丢弃”；保存逻辑改为新建空任务并按录制顺序追加动作节点。
117. 任务参数持久化升级为递归结构：`TaskRepository` 的 `params` 编解码支持 `Map/List/Number/Boolean/String`，修复录制轨迹点位被序列化成字符串后无法正确回放的问题。
118. 录制采集新增“逐步回放确认”：每次手势采集完成后，临时隐藏录制面板与录制浮层，立即执行一次真实手势回放（复用轨迹反馈层），完成后恢复录制界面继续采集。
119. 录制层级修正：录制浮层改为不覆盖面板视觉区域，并在面板区域透传触摸，保证录制态可随时操作暂停/停止。
120. 录制面板状态切换动画补齐：`NORMAL <-> RECORDING` 增加宽度弹簧过渡与内容 Crossfade，避免状态切换生硬。
121. 录制回放滑动无效修复：回放前将录制浮层临时切换为 `FLAG_NOT_TOUCHABLE` 并延迟一帧执行手势，回放后恢复可触摸，解决“录制态回放不生效但任务执行生效”的不一致。
122. 手势轨迹反馈升级为进度动画：`click/swipe/path` 从静态一次性绘制改为按时长逐步绘制（含动态终点），更接近 liteclicker 的视觉反馈语义。
123. 录制面板交互收敛：录制态移除“关闭按钮”，仅保留“暂停/停止”；退出录制统一走“停止”，若未录制任何动作则直接恢复普通面板，不弹保存弹窗。
124. 录制面板新增时长计时：右上角实时显示当前录制持续时间（暂停期间不计时）。
125. 修复录制态切换动画闪烁：移除录制开始时对面板窗口的 `remove/addView` 提层重挂，避免 `NORMAL -> RECORDING` 动画中途被窗口重建打断。
126. 轨迹反馈时长对齐：`AccessibilityGestureExecutor` 将 click/swipe/path 的反馈 `duration` 与实际 `dispatchGesture` 使用的 `duration` 统一，修复“轨迹动画快慢与真实执行不一致”。
127. 录制层级与交互可用性修正：录制浮层 add 完成后立即重排窗口顺序（将控制面板重新 add 到最上层），并在叠层完成后切换到录制态动画，恢复录制态下“拖动面板/暂停/停止”可操作性。
128. 录制浮层视觉改为轻量半透明（不再全透明）：录制层背景改为 `#12000000`，保留录制态可感知遮罩，同时不干扰目标界面操作观察。
129. 录制面板切换提速：`NORMAL <-> RECORDING` 的宽度与内容切换改为中等刚度弹簧 + 120ms Crossfade，显著减少“状态切换过慢”体感。
130. 录制采集模型升级为 pointer track：按 `pointerId` 记录 `points/timestamps/down/up`，替换旧单指 `capturePath`，支持多指并发轨迹与“长按后拖动”采样。
131. `record` 执行协议升级：新增 `strokes` 参数结构（`points + timestampsMs + startDelayMs + durationMs`），运行时优先执行 `strokes` 并向后兼容旧 `points`。
132. 多轨迹反馈升级：反馈层新增 `MULTI_PATH`，按每条 stroke 的起始延迟与时长逐步绘制，录制回放可视化与真实执行时序一致。
133. 本轮可交付性校验通过：`compileDebugKotlin`、`assembleDebug`、`testDebugUnitTest` 全部成功。
134. 录制面板高度切换动画收敛：面板容器从弹簧自适应高度改为固定时长 `animateContentSize(tween 170ms)`，修复“展开慢、收起快”的非对称观感。
135. 长按后拖动回放修复（参考 liteclicker 的时间段思路）：对“单指 + timestamps”手势在 Android O+ 改为 `continueStroke` 分段回放（停顿段+移动段），不再只依赖单条 path 的长度近似。
136. 长时录制回放稳定性修复：手势分发等待超时由固定 2.5s 改为按手势时长动态计算（`duration + buffer`），避免长按/长轨迹被误判为 timeout。
137. 回归验证通过：本轮修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
138. 录制面板转场进一步收敛为“纯渐隐”：移除录制态切换时的宽高动画，保留不同状态面板尺寸但改为瞬时切换，内容仅通过 Crossfade 过渡，消除“展开慢收起快”的尺寸形变观感。
139. 录制层级切换时序修正：按“先提层（panel re-stack）再触发录制态动画”执行，避免 `remove/addView` 打断转场并导致“切换无动画”。
140. 录制实时触摸轨迹可视化：录制浮层新增 `RecordingTrailOverlayView`，在录制过程中实时绘制手指轨迹与触点（含多指并发、抬手短暂淡出），提升录制反馈可见性。
141. 录制轨迹与回放轨迹配色区分：录制触摸轨迹采用青色系（`#00B8FF/#00C2FF`），回放继续沿用橙色系，避免“用户触摸”和“自动回放”视觉混淆。
142. 稳定性验证：新增录制实时轨迹层后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
143. 轨迹配色按交互反馈收敛：录制实时轨迹调整为“回放同色系但更高透明度”（橙色系低 alpha），在统一视觉语言下保留录制/回放可辨识度。
144. 回放轨迹时序对齐修复：多 stroke 反馈从“按点数等速推进”改为“按 `timestampsMs` 时间轴推进”，解决“轨迹先跑完但手势还在执行”的速度错位。
145. 面板高度包裹修复：移除面板最小高度硬编码，恢复按内容自适应高度，修复录制态和普通态底部多余留白。
146. 回归验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
147. 长按分段回放场景下的轨迹节奏补偿：针对 `continueStroke` 多段执行的回调开销，对反馈时长增加按段数的补偿时间，进一步收敛“轨迹先走完”的体感偏差。
148. 轨迹节奏补偿参数回调：将分段补偿从 `22ms/段` 下调至 `10ms/段`，修正“补偿后轨迹反而慢于实际手势”的问题。
149. 面板闪烁治理（重挂场景）：主面板 `ComposeView` 改为 `DisposeOnViewTreeLifecycleDestroyed`，并移除 re-stack 的冗余 `updateViewLayout`，降低“普通态切录制态”时的窗口重挂闪烁。
150. 稳定性验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
151. 轨迹时序再次收敛：移除单指分段回放反馈时长的“按段补偿”逻辑，反馈总时长回归录制原始 `duration/timestamps`，避免时间轴被拉长导致“轨迹比实际慢”。
152. 录制面板切换闪烁修复：录制启动时将 `panelMode=RECORDING` 与状态文案切换提前到窗口重排前，移除 `post` 延迟切换，解决“播放/关闭按钮残留一拍再消失”。
153. 转场细节微调与验证：面板模式内容切换时长从 `120ms` 收敛到 `80ms`，并完成 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug` 全量校验。
154. 回放实际执行慢定位与修复：确认慢点在 `dispatchTimedStroke` 实际分发链路（非轨迹动画），原实现按采样点逐段 `continueStroke` 导致段间回调开销叠加，改为“连续移动段合并分发，仅在停顿边界拆段”。
155. 回放诊断日志补齐：为 timed 回放新增 `expected(ms) vs actual(ms)` 打点（含分段数），用于后续机型侧校准与阈值调优。
156. 稳定性验证：本轮“分段合并”优化后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
157. 长按停顿识别升级：`buildTimedSegments` 从“单步阈值拆段”改为“静止 run 检测 + 最小时长判定（350ms）”，并以 `pause radius=20px` 识别长按区间，修复“长按后移动被当成连续移动导致停留不足”。
158. 分段模型收敛：新增 `PrimitiveTimedRun`，将短静止并入移动段，只有满足阈值的静止段才生成为 `pause`；同时保留移动段合并策略，兼顾停顿准确性与执行性能。
159. 稳定性验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
160. 按 `liteclicker` 方案重写单指时间戳回放分段：`dispatchTimedStroke` 改为 `FunctionManager` 同款段落模型（`startIndex/endIndex/duration/isPause`）与 `continueStroke` 链式执行，保留 pause 段 `+1px` 语义。
161. 停顿判定改为 `liteclicker` 同款归一化阈值：`maxPauseMove=0.02`、`pauseEntryMove=0.01`、`minPauseDuration=350ms`，停顿与移动段拆分逻辑改为状态机实现。
162. 回放动画同步对齐 `liteclicker`：轨迹时间轴改为“按 timestamp 跨点推进（无段内插值）”，与原项目 `MultiSwipeDisplayView` 行为一致。
163. 稳定性验证：以上重构后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
164. 说明：157/158 的中间方案已废弃，当前生效实现以 160-162 为准。
165. 录制采样策略对齐 `liteclicker`：移除录制轨迹的点位抽样/去抖（仅过滤同事件同坐标重复点），`ACTION_MOVE` 采样尽量全量保留，避免长按阶段微抖动点被吞掉后导致 pause 时长被低估。
166. 录制序列保真度提升：`toRecordedStroke` 取消二次 downsample，直接持久化原始 `points/timestamps`（单点补齐仍保留），确保停顿判定和回放时序基于完整录制数据。
167. 稳定性验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
168. 长按时序保真修复：录制态新增 `32ms` 周期 hold 采样器，对活跃指针持续补点（复用当前位置+新时间戳），解决“系统未派发静止 MOVE 导致长按秒数被压短”的问题。
169. 录制采样器生命周期接入：录制开始启动、录制结束自动取消，避免非录制态额外采样。
170. 稳定性验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
171. 新增端到端诊断日志（录制/回放）：记录 `track -> stroke -> replay` 三阶段关键时序（点数、duration、timestamps 末值、pause hints），用于定位“录制 5s 长按回放不足”。
172. 新增执行器分段诊断：`performRecordStrokes/buildTimedSegments/dispatchTimedStroke` 输出输入 stroke 摘要、分段结构（pause/move、index、duration）及 `expected vs actual` 执行耗时。
173. 稳定性验证：以上日志增强后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
174. 基于实测日志定位到 pause 段被系统提前完成：案例中 `expected=4732ms`、`actual=1830ms`，长按段 `4262ms` 未被完整执行。
175. 新增长按段执行兼容：`dispatchTimedStroke` 的 pause 段从“单条 1px 线”改为“微小环形轨迹 + 1px 收尾”，提升系统对长时 pause duration 的遵循度（同 pre-O `addStrokeWithHold` 思路）。
176. 稳定性验证：以上兼容修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
177. 按需求回归 `liteclicker` 逻辑：移除录制态 `32ms` 周期 hold 采样器，不再主动补充静止点时间戳。
178. 按需求回归 `liteclicker` 逻辑：pause 段执行从“微环轨迹”恢复为 `+1px` 单段语义，保持与 `FunctionManager.executeGestureSegments` 一致。
179. 稳定性验证：以上回归后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
180. 针对实机“长按段被提前完成”增加兼容分支：单指时间戳手势若检测到有效长按段（`>=350ms`），回放从 `timed_continueStroke` 自动切到 `legacy_hold_path`（单条带停顿路径 + 固定总时长），避免 ROM 对 pause 段 duration 的提前截断。
181. 诊断日志新增模式标识：`performRecordStrokes singleStroke mode=timed_continueStroke|legacy_hold_path`，便于区分当前走的是标准分段链还是兼容分支。
182. 稳定性验证：以上兼容分支后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
183. 按最新实机反馈回退兼容分支：移除 `legacy_hold_path` 自动切换，恢复单指时间戳手势统一走 `timed_continueStroke`。
184. 按最新实机反馈恢复 pause 段“原地微动”实现：`dispatchTimedStroke` pause 段重新启用微环轨迹 + 1px 收尾，以保障长按体感时长。
185. 稳定性验证：以上回退后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
186. 多指长按回放修复：新增 `dispatchTimedMultiStroke`，对“多 stroke + timestamps”在 Android O+ 采用统一时间边界分段，并在每个时间片内同步构建所有活跃指针的 `continueStroke`，避免旧方案因单条 path 等速导致长按时长被压缩。
187. 多指时间片执行新增日志：输出 `timed multi stroke done intervals/expected/actual/strokes`，用于后续机型侧比对“预期时长 vs 实际分发时长”。
188. 文档收敛：`task_list_mvp_v1` 同步更新为“单指/多指时间戳手势均走 timed 分段回放；pause 段使用微环 + 1px 收尾；不再使用 `legacy_hold_path`”。
189. 稳定性验证：本轮多指时序修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
190. 修复“错峰多指（先按第一指，再按第二指）回放被取消”：多指 timed 分段模式新增起点对齐约束（`startDelay` 差值 <= `24ms` 才启用），错峰多指自动回退到单次 multi-stroke 分发，避免 `continueStroke` 中途引入新指针导致整段手势被系统取消。
191. 多指回放日志补充模式识别：`multiStroke mode=timed_multi_continueStroke|single_dispatch_paths aligned=...`，用于快速区分当前是否走错峰回退路径。
192. 稳定性验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
193. 新增双指回放失败诊断日志（执行器级）：`dispatchGestureRaw` 增加 `dispatch start/completed/cancelled/failed` 全链路日志，包含 `tag/timeout/strokeCount/elapsed/detail`，用于区分 `cancelled`、`timeout`、`dispatch_returned_false`。
194. 新增分段失败上下文透传：`dispatchTimedStroke` 与 `dispatchTimedMultiStroke` 失败时附带 `index/start/end/duration/active/reason` 到 detail，便于直接定位失败区间与失败模式。
195. 新增回放入口日志（控制面板级）：多指回放开始日志补充 `startSpread` 与每条 stroke 的绝对时间区间 `abs=[start,end]`；回放结束日志附带 `TaskAccessibilityService.gestureStatsText()`。
196. 稳定性验证：以上日志增强后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
197. 基于实测日志定位本轮失败根因：样例 `startSpread=15ms`，`record_timed_multi_seg_1`（从单指切到双指）在 `8ms` 即 `cancelled`，证明错峰起点在 timed multi 分段链中途引入新指针会触发系统取消。
198. 多指 timed 启用条件再次收紧：`MULTI_TIMED_START_ALIGNMENT_TOLERANCE_MS` 从 `24ms` 调整为 `0ms`，仅完全同起点才走 timed multi；任何错峰起点都回退 `single_dispatch_paths`，优先稳定性。
199. 多指模式日志补充 `startSpread`，用于确认是否命中“错峰回退”分支。
200. 稳定性验证：以上收敛后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
201. 修复错峰多指回退路径“长按时长不足”问题：`buildStrokePath` 的长按段改为“按时间加权路径长度”生成（基于移动段参考速度估算目标路径长度），不再使用固定 `delta/step` 步数，避免长按在单次分发中被路径等速压缩。
202. 长按微动建模参数升级：引入 `HOLD_WIGGLE_STEPS_PER_LOOP/MAX_STEPS/BASE_SPEED/MIN_SPEED/MAX_SPEED/PATH_SPEED_SCALE`，并将长按目标路径长度最小值收敛为 `24px`，兼顾长按时长保真与路径规模可控。
203. 稳定性验证：以上修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
204. 新增“动画 vs 执行”统一追踪链路：`performRecordStrokes` 生成 `traceId`（`record-<uptime>`），并透传到反馈层与执行层日志，统一关联一次回放会话。
205. 反馈层新增里程碑日志：记录 `overlay request->addView` 延迟、`view attached/detached` 生命周期、`25/50/75/100%` 进度时间戳；执行层分发日志保留 `start/completed/cancelled/failed` 与 `elapsed`，可直接对比两条时间线。
206. 稳定性验证：以上日志增强后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
207. 动画起点对齐修复：反馈层进度起点从“View 构造时”改为“`onAttachedToWindow` 时”，消除 addView/attach 期间的隐性计时损耗。
208. 执行起点对齐修复：`performRecordStrokes` 在发起分发前增加 `16ms` 同步延迟（约一帧），并打点 `feedbackExecSyncDelay`，减少“执行先动、动画后到”的体感偏差。
209. 稳定性验证：以上对齐修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
210. 修复“停止录制后保存弹窗无入场动画”：新增 `recordingSaveDialogOpenToken`，保存弹窗改为“打开 token 触发 + 首帧后进入”（`18ms`）以确保 `AnimatedVisibility` 发生 `false -> true` 转换，即使 settings overlay 是刚 `addView`。
211. 修复“保存弹窗无退场动画”：新增 `scheduleSettingsOverlayRemovalIfIdle()`，在弹窗关闭时延迟 `180ms` 再移除 settings overlay，避免 `removeView` 抢占 exit 动画。
212. 生命周期收敛：补充 `deferredSettingsRemovalJob` 取消逻辑，`show/hide/remove` 与录制状态重置时统一清理 `recordingSaveDialogOpenToken`，避免残留状态影响下一次动画。
213. 稳定性验证：以上保存弹窗动画修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
214. 修复“关闭仍无动画”的根因：`SettingsOverlayContent` 顶部早退条件会在 `recordingSaveDialogVisible=false` 且 `settingsVisible=false` 时立即移除整棵 UI，导致 exit 动画无法执行；新增 `recordingSaveDialogAnimatingOut` 维持关闭过渡态，直到退场时长结束再清理。
215. 关闭路径统一：新增 `dismissRecordingSaveDialogWithAnimation()`，`discard/confirm` 均改为先触发退场动画，再按状态决定是否移除 settings overlay，避免分支里直接 `visible=false + removeView` 抢动画。
216. 状态机细节修正：关闭时不再立即重置 `recordingSaveDialogOpenToken`，避免 `remember(token)` 重建后丢失退出过渡。
217. 稳定性验证：以上二次修复后再次通过 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`。
218. 录制面板按钮顺序调整：录制态主控按钮从“暂停 + 停止”改为“停止 + 暂停”，减少误触暂停导致未保存录制会话的问题。
219. 操作面板 `开始` 按钮新增二次确认：点击后先弹“确认开始任务”对话框，明确展示目标任务名，用户确认后才触发执行；取消则不启动任务。
220. 交互对齐与稳定性验证：本轮改动后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
221. 修复“开始任务确认弹窗无入场动画”：新增 `startTaskConfirmDialogOpenToken`，确认弹窗打开改为“token 触发 + 首帧后进入”（与保存弹窗一致），避免 settings overlay 刚 `addView` 时直接处于 visible 状态导致无入场过渡。
222. 修复“开始任务确认弹窗无退场动画”：新增 `startTaskConfirmDialogAnimatingOut`，并将关闭路径统一为 `dismissStartTaskConfirmDialogWithAnimation()`；退出期间保持 overlay 内容存活，延迟移除 `addView` 容器，避免 `removeView` 抢占动画。
223. 弹窗样式统一化：新增 `OverlayDialogCardScaffold` 作为中心弹窗壳，`录制保存弹窗` 与 `开始确认弹窗` 复用同一套圆角/边框/间距与标题说明排版。
224. 稳定性验证：以上修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
225. 弹窗移除策略从“固定延迟”改为“动画状态驱动”：去掉 `scheduleSettingsOverlayRemovalIfIdle` 的定时移除，新增 `removeSettingsOverlayIfIdle` + 退出动画完成回调，只有在 `AnimatedVisibility` 过渡真正结束后才触发 `removeView`。
226. 退出收敛点新增：`onRecordingSaveDialogExitAnimationSettled` / `onStartTaskConfirmDialogExitAnimationSettled` 在关闭动画结束时统一清理 `animatingOut` 与 overlay 移除判定；若中途反向打开弹窗（动画被取消），则不会误移除背景层。
227. `SettingsOverlayContent` 接入 `MutableTransitionState`：两个中心弹窗改为 `visibleState` 驱动，确保可检测到“退出完成”这一准确时机，再执行 overlay 回收。
228. 稳定性验证：本轮“动画结束后移除”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
229. 背景层观感统一：settings sheet 与中心确认弹窗的 scrim 参数收敛为统一配置（透明度/淡入淡出时长一致），减少“任务设置 vs 确认弹窗”背景半透明体感差异。
230. 背景层回收时机再收敛：新增 `pendingSettingsOverlayRemoval`，只在“全部内容不可见 + scrim alpha 衰减到接近 0”后才执行 `removeSettingsOverlayIfIdle`，避免 scrim 尚未淡完即 `removeView` 导致的突兀闪断。
231. 清理与打开路径同步：在打开设置页/确认弹窗时显式清空 pending 移除标记，防止关闭动画被取消后出现误回收。
232. 稳定性验证：以上背景层统一与回收时序优化后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
233. 录制保存弹窗新增“步骤编辑”能力：在保存前展示本次录制步骤清单，支持单步 `上移/下移/删除`，允许先整理顺序再落库。
234. 录制会话收敛：当保存弹窗内步骤被删到 0 条时，自动关闭保存弹窗并回到普通面板，同时重置录制会话状态，避免空录制任务被保存。
235. 稳定性验证：以上“录制步骤可编辑”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
236. 按产品要求回退“保存弹窗内动作编辑”：移除录制保存弹窗中的步骤列表与 `上移/下移/删除` 控件，恢复为仅做“命名 + 保存/丢弃”的确认弹窗。
237. 编辑职责收敛：动作修改入口统一保留在任务编辑页（动作列表/动作详情），录制保存阶段不再承担动作编辑。
238. 稳定性验证：以上回退后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
239. 动作列表语义收敛：任务编辑页列表不再展示 `START/END` 控制节点，避免将流程控制节点误解为可编辑动作；当前只展示可编辑节点集合。
240. 稳定性验证：以上动作列表过滤后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
241. `CLICK` 编辑交互升级：节点详情新增“屏幕拖动选点”模式（全屏拾点层 + 可拖动圆点 + 应用/取消），支持在真实屏幕背景上直接选取点击坐标。
242. `CLICK` 手动输入语义收敛为像素坐标：详情页新增 `X(px)/Y(px)` 输入并实时回写；`x/y` 百分比字段从通用参数区隐藏，避免用户在编辑层看到比例值。
243. 参数存储兼容保持：编辑层使用像素坐标交互，落库前统一转换为运行时比例值（`0~1`）写入节点参数，不影响现有执行器协议。
244. 稳定性验证：以上 `CLICK` 可视化拾点与像素输入改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
245. `CLICK` 拾点交互补齐：拾点层支持“任意区域滑动/点击”更新准星位置，不再要求用户必须按住准星拖拽。
246. 拾点准星样式优化：由实心圆改为空心十字准星（中心点 + 十字线 + 环形边框），降低对底层目标内容的遮挡。
247. 边界行为保持：准星中心点仍限制在屏幕范围内，准星图形允许部分超出屏幕边缘，兼顾边缘点位可达与视觉可读性。
248. 稳定性验证：以上拾点交互细化后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
249. 拾点提示文案增强：顶部文案明确补充“任意位置滑动/点击也可移动准星”，避免用户误以为只能拖准星本体。
250. 拾点底部操作区可读性修复：`取消/应用` 按钮容器改为实体卡片背景（非透明），在复杂底图上保持稳定对比度。
251. 稳定性验证：以上提示与可读性优化后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
252. 按产品要求收敛拾点手势：移除“点击任意位置跳点”，保留“任意位置滑动 + 拖动准星本体”两种移动方式，避免误触导致点位瞬时跳变。
253. 提示文案同步收敛：拾点标题与说明均改为“任意位置滑动可移动准星”，与实际行为一致。
254. 稳定性验证：以上拾点手势收敛后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
255. `CLICK` 拾点入口可见性增强：节点详情中的入口按钮改为整行宽度，并将文案调整为“屏幕拖动调整点击位置”，降低入口被忽略概率。
256. 按产品要求移除“填充默认值”：浮窗节点编辑页仅保留“删除动作”，不再提供默认值一键填充按钮。
257. 稳定性验证：以上入口与操作区精简后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
258. 编辑入口统一：操作面板“任务设置”中的任务卡片点击不再走面板内嵌编辑路由，改为关闭设置页后直接打开全局任务编辑浮窗（`TaskEditorGlobalOverlay`），与首页任务编辑入口保持一致。
259. 入口统一后可见性修复：面板入口与首页入口现在复用同一编辑器能力集，`CLICK` 的“屏幕拖动调整点击位置”入口在两侧路径表现一致。
260. 稳定性验证：以上入口统一改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
261. 修复“入口统一后 `CLICK` 拾点能力缺失”：全局任务编辑浮窗（`TaskEditorGlobalOverlay`）补齐 `CLICK` 的像素坐标编辑（`X(px)/Y(px)`）与“屏幕拖动调整点击位置”全屏拾点层。
262. 全局编辑器参数展示收敛：`CLICK` 节点在参数区隐藏比例值 `x/y`，避免与像素输入双语义并存；拾点结果仍按比例值存储，兼容运行时协议。
263. 统一交互细节：全局编辑浮窗节点页移除“填充默认值”按钮，和近期操作面板编辑体验保持一致。
264. 稳定性验证：以上全局编辑器补齐改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
265. 修复“任务设置 -> 编辑任务”过渡断层：切换编辑时改为先请求打开全局任务编辑浮窗，再在检测到编辑浮窗已显示后关闭设置浮窗，避免“设置页先消失，再出现编辑页”的空窗感。
266. 服务能力补齐：`TaskAccessibilityService` 新增 `isTaskEditorOverlayShowing()`，供操作面板切换链路判断编辑浮窗是否已挂载。
267. 稳定性验证：以上过渡修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
268. 按产品反馈将“切换式过渡”改为“真实堆叠”：从操作面板任务设置进入编辑时不再关闭设置浮窗，直接在其上层打开全局任务编辑浮窗；关闭编辑后可回到原任务设置层。
269. 文档与实现收敛：撤销 `isTaskEditorOverlayShowing()` 检测链路，避免“先开再关”逻辑继续导致下层消失。
270. 稳定性验证：以上堆叠语义修正后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
271. 堆叠可见性增强：全局编辑浮窗新增“来自任务设置”的堆叠模式（更轻 scrim + 顶部 inset 露出），避免上层完全遮住下层导致“看不到堆叠”。
272. 风格一致性修复：统一“任务设置/编辑任务”主弹窗圆角到同一规格（18dp），消除两套弹窗圆角不一致。
273. 稳定性验证：以上堆叠视觉与样式统一后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
274. 任务设置三层页面改为复用共享对话框壳：新增 `OverlayDialogScaffoldShared` 组件（标题区/面包屑/头尾 actions/圆角边框样式），`TaskControlPanel` 的 `任务设置/动作列表/编辑动作` 三层统一接入，移除面板侧自定义 `SettingsPageHeader`。
275. 堆叠结构收敛：设置页堆叠层不再各自包一层手写 `Card`，改为“动画容器 + 共享 Scaffold”组合，和编辑浮窗的卡片语义保持一致。
276. 稳定性验证：以上共享组件复用改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
277. 修复“能堆叠但无堆叠动画”：新增跨窗口联动状态（`settingsExternalStacked`），当编辑浮窗在任务设置上层打开时，下层任务设置页同步执行退后动画（缩放 + 轻微位移）；编辑浮窗关闭后下层自动回弹。
278. 跨窗口状态检测恢复：`TaskAccessibilityService` 重新提供 `isTaskEditorOverlayShowing()`，操作面板通过轮询观察编辑浮窗显示态并驱动下层动画状态收敛。
279. 稳定性验证：以上跨窗口堆叠动画联动后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
280. 参数一致性修复（跨窗口 vs 同窗堆叠）：移除跨窗口临时动画参数（独立 scrim/额外位移），改为直接复用 `OverlayStackMotion` 同套参数与 spring 进退场曲线，保证首层堆叠与后续层级动画节奏一致。
281. 跨窗口联动采样频率优化：编辑浮窗显示态监听轮询从 `120ms` 收敛到 `32ms`，减少“下层退后动画滞后一拍”的体感。
282. 稳定性验证：以上参数统一与监听时序优化后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
283. 恢复“全局最多两层可见”规则：跨窗口堆叠场景下新增编辑浮窗路由深度检测；当编辑浮窗进入二级页面（如动作详情）时，下层任务设置 sheet 自动隐藏，避免总可见层数达到 3 层。
284. 下层动画触发条件收敛：仅在“编辑浮窗处于一级页”时启用 `settingsExternalStacked` 的退后动画，进入二级页后不再对任务设置层施加退后态。
285. 稳定性验证：以上“最多两层可见”修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
286. 修复“二级页切换瞬间仍可见 3 层”问题：当外部编辑浮窗从一级进入二级时，任务设置层退场改为即时移除（`snap` 退场），不再保留退出过渡帧，保证任意时刻最多两层可见。
287. 首层视觉参数再统一：任务设置 sheet 的 scrim 透明度改为复用 `OverlayStackMotion.SHEET_SCRIM_ALPHA`，与后续编辑层保持同一套堆叠基线；高遮罩仅保留给中心确认类对话框。
288. 稳定性验证：以上“即时两层约束 + scrim 参数统一”修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
289. 修复跨窗口联动监听竞态：`watchExternalEditorStackState` 不再在编辑浮窗尚未真正挂载时提前退出，改为先等待编辑浮窗出现（最多 2s）后再进入稳定监听。
290. 打开编辑入口状态收敛：移除“打开即强制设置 `settingsExternalStacked=true`”的提前置位，改为由真实编辑浮窗显示态驱动下层堆叠/隐藏，避免“下层像固定在底部但不随详情联动”。
291. 稳定性验证：以上跨窗口监听竞态修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
292. 详情页前景位替换修复：移除全局编辑浮窗中 `FlowManager/NodeEditor` 的额外顶部偏移（`padding(top=20.dp)`），使新进入页面占用当前前景位，符合“当前页退后、新页接管其位置”的堆叠语义。
293. 稳定性验证：以上前景位替换修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
294. 堆叠遮挡修复（面板来源路径）：进入 `动作详情/流程管理` 时，下层“编辑任务”页新增向上退让位移（`-FOREGROUND_LAYER_TOP_INSET_DP`）并保留缩放，避免被上层整页完全遮挡。
295. 语义保持：上层详情页继续占据原前景位，下层退到背景位，满足“新页接管当前位置、旧页后退可见”的堆叠预期。
296. 稳定性验证：以上遮挡修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
297. 首层堆叠语义对齐：面板来源的“任务设置 -> 编辑任务”阶段，下层 `任务设置` 由“仅缩放”改为“缩放 + 向上退让到背景位”（`-FOREGROUND_LAYER_TOP_INSET_DP`），与后续层级转场规则一致。
298. 目标收敛：保证跨窗口首层与同窗口后续层级都遵循“新页占前景位、旧页退到背景位”的统一动画语义。
299. 稳定性验证：以上首层语义对齐修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
300. 面板路径切回同窗口编辑：`任务设置` 中任务卡片点击不再 `addView` 打开 `TaskEditorGlobalOverlay`，改为在当前 settings overlay 内加载 `TaskGraphEditorStore` 并进入 `SettingsRoute.ActionList`。
301. 跨窗口链路收敛：面板进入编辑前会主动关闭可能残留的全局编辑浮窗，避免同任务出现双编辑窗口；后续堆叠动画完全由 settings overlay 单窗口路由驱动。
302. 稳定性验证：以上“同窗口编辑入口恢复”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
303. 修复同窗口编辑崩溃：`SettingsActionListPage` 与 `SettingsNodeEditorPage` 移除内层 `verticalScroll`，避免与 `SharedOverlayDialogScaffold` 内容区外层滚动叠加导致 “scrollable measured with infinity max height constraints” 异常。
304. 约束策略收敛：settings overlay 内统一由共享 scaffold 承担纵向滚动，子页面仅输出普通内容布局（`Column` 不再声明独立滚动容器）。
305. 稳定性验证：以上滚动约束修复后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
306. 首页编辑入口收敛：任务页卡片点击不再调用 `showTaskEditorOverlay` 直开全局编辑器，改为调用 `showTaskListOverlay` 打开“浮窗任务列表（任务设置页）”。
307. 控制面板新增能力：`TaskControlPanelGlobalOverlay` 增加 `showSettingsPanel(preferredTaskId)`，支持从首页直接打开任务设置浮窗并携带预选任务；`TaskAccessibilityService` 对外新增 `showTaskListOverlay(...)`。
308. 入口语义统一：首页仅保留“进入浮窗任务列表再编辑”路径，浮窗面板与首页的编辑链路收敛到同一套 settings overlay。
309. 稳定性验证：以上首页入口收敛改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
310. 编辑架构进一步收敛：删除 `TaskEditorGlobalOverlay` 与 `TaskAccessibilityService` 中对应的 show/hide/query 接口，移除跨窗口编辑能力。
311. 状态与动画链路清理：`TaskControlPanelGlobalOverlay` 中 `settingsExternalStacked/settingsExternalDetailVisible/watchExternalEditorStackState` 等跨窗口联动状态与分支全部移除，设置页仅保留同窗口堆叠动画。
312. 结果一致性：编辑入口统一为“首页/面板 -> 浮窗任务列表 -> 动作列表/动作详情”，运行时仅存在一套编辑 UI 承载。
313. 稳定性验证：以上“删除全局编辑浮窗 + 清理跨窗口联动”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
314. 首页任务页职责收敛：`TaskTabScreen` 改为仅保留一个“任务列表”入口卡片，不再在首页内联展示任务 CRUD、搜索、筛选与运行卡片。
315. 入口行为统一：首页“打开任务列表”按钮直接调用 `showTaskListOverlay`，与浮窗面板入口一致，任务创建/编辑/运行统一在浮窗任务列表链路内完成。
316. 稳定性验证：以上首页入口精简改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
317. 动作列表交互升级：每个动作卡片右上角新增菜单按钮，统一提供 `编辑/复制/禁用(启用)/删除` 操作；列表主点击保持“进入动作详情”单一主路径。
318. 删除与状态管理收敛：删除动作改为菜单触发并二次确认，节点详情页移除“删除动作”按钮；禁用状态在动作卡片中直接可见（“已禁用”标记 + 灰化）。
319. Store 能力补齐：`TaskGraphEditorStore` 新增节点级接口 `duplicateNode(nodeId)`、`updateNodeEnabled(nodeId, enabled)`、`removeNode(nodeId)`，用于动作列表菜单直接按节点操作。
320. 稳定性验证：以上动作菜单与节点级编辑接口改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
321. 动作新增入口升级：动作列表“添加动作”改为下拉菜单，支持按类型直接新增 `点击/滑动/录制/双击/跳转`，其中“添加跳转动作”不再依赖后续改类型步骤。
322. 动作信息可读性增强：动作列表每项新增“持续时间 + 执行后延迟”展示（`持续 xxms | 延迟 xxms`），禁用项继续保留灰化与状态标记。
323. 编辑规则收敛：节点详情页动作类型改为只读（创建后固定）；若需换类型，改为在列表中新增目标类型动作后再迁移参数/删除旧动作。
324. 运行时能力补齐：节点级 `postDelayMs` 参数在 `NodeOutcome.CONTINUE` 后统一生效（非 dry-run），因此 `ACTION/JUMP/BRANCH` 等流程节点可共享“执行后延迟”语义；`EditorParamSchema` 已为各动作类型补齐该参数定义。
325. 稳定性验证：以上“按类型新增 + 时长/延迟展示 + 类型固定 + postDelay 执行”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
326. 运行中断能力补齐：面板“开始”按钮在运行中切换为“停止”，点击后会取消当前任务协程，支持在长延迟（如 `postDelayMs=60000`）等待中提前停止。
327. 跳转编辑可视化：`JUMP` 节点新增“目标流程/目标动作”下拉选择器，替代手输 `targetFlowId/targetNodeId`；并在编辑页增加字段语义说明文案。
328. 参数区去重：`JUMP` 节点的 `targetFlowId/targetNodeId` 从通用参数文本区隐藏，避免“下拉选择 + 文本输入”双入口冲突。
329. 稳定性验证：以上“运行可中断 + 跳转目标可视化选择”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
330. 动作列表新增“流程预览”入口：在动作列表顶部加入 `流程预览/隐藏预览` 切换，可查看当前 flow 的文本化连线（Edge）与跳转关系（Jump）。
331. 列表态 jump 可见性增强：`JUMP/FOLDER_REF/SUB_TASK_REF` 动作卡片内直接显示“跳转 -> 目标流程/目标动作”摘要，减少必须进入详情页才能判断跳转目标的问题。
332. 预览交互补齐：流程预览中的连线条目支持点击并直接定位到目标动作编辑页（跨 flow 目标同样可跳转）。
333. 稳定性验证：以上“流程预览入口 + jump 关系可视化”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
334. 流程预览升级为图形化画布：`FlowPreviewPanel` 从文本列表切换为流程图样式，普通 `edge` 以实线显示，`jump/folderRef/subTaskRef` 以虚线显示，节点支持点选定位到动作编辑页。
335. 动作列表连线落地：动作卡片列表右侧新增同 flow 的 jump 虚线连接层（`JumpConnectionCanvas`），可直接观察“源动作 -> 目标动作”关系，并支持点线后快速定位到目标动作。
336. 跨 flow 跳转在预览区保留独立入口：图形预览下方补充“跨流程跳转”按钮列表，点击后直接切换到目标 flow/node 的动作编辑页。
337. 稳定性验证：以上“流程图预览 + 列表连线可视化”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
338. 动作列表连线重构：引入“右侧连线走廊（gutter）”+“lane 分配”策略，按区间重叠为 jump 连线分配独立车道，避免多跳转时虚线互相覆盖难以辨认。
339. 列表宽度策略收敛：动作卡片右侧改为固定 gutter，不再按连线数量继续缩窄卡片宽度，避免“线越多内容越挤”。
340. 连线交互升级：连线点击命中改为三段折线（出发横线-车道纵线-到达横线）几何检测，点击线体可稳定定位到目标动作。
341. 连线方向可读性修复：动作列表 jump 连线终点从圆点改为箭头（target 端），起点保留弱化圆点，明确显示 `source -> target` 方向语义。
342. 连线方向显著性增强：箭头尺寸放大，目标端最后一段改为实线加粗（前两段仍虚线），并区分正向/回跳色彩（`primary/secondary`），提升密集场景下流向辨识度。
343. 修复“动作菜单无法触发”：列表连线层取消点击命中（`onConnectionTap=null`），避免覆盖层抢占触摸事件导致卡片右上角菜单不可点击；后续若恢复点线跳转，需改为不阻塞下层交互的命中方案。
344. 多连线拥挤治理：动作列表连线增加可见车道上限（当前 `max=3`），超限连线折叠到最后车道并弱化显示，同时展示“已折叠 +N”提示，兼顾可读性与布局稳定性。
345. 流程预览可读性升级：预览图中每个节点旁新增名称标签（优先显示动作名，其次 `actionType + nodeId`），便于直接识别动作含义而非只看点位。
346. `START/END` 节点差异化：`START` 改为绿色胶囊节点，`END` 改为红色菱形节点；普通节点保持圆形，且选中态统一描边高亮，强化“起止节点 vs 普通动作”视觉区分。
347. 流程预览交互收敛为只读：预览区移除节点点击与拖拽编辑能力，仅用于查看整条流程与跳转关系，不再承载编辑入口。
348. 主链展示收敛：预览图按“开始 -> 中间动作（按当前列表顺序）-> 结束”串联绘制主流程线，避免出现动作点位孤立导致的理解断层。
349. 跳转线分离渲染：`JUMP/FOLDER_REF/SUB_TASK_REF` 连线改为右侧独立车道折线，并附带“来源 → 目标”文字标记，减少与主流程线重叠造成的可读性问题。
350. 功能节奏调整：动作列表中的“流程预览”入口按钮已暂时下线（软隐藏），当前编辑链路聚焦动作列表/动作详情；预览实现代码保留，待后续 V2 重构后再恢复入口。
351. 动作详情参数区交互收口：参数编辑改为分组展示（位置/轨迹、时序、行为、其它），并以卡片分段，降低长表单阅读与定位成本。
352. 字段级校验提示接入：动作详情参数输入统一复用 `EditorParamValidator`（必填、数字、范围、枚举），在输入框下展示错误/辅助文案，仅在校验通过时写回节点参数。
353. 稳定性验证：以上“参数分组 + 字段校验提示”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
354. 编辑器主链一致性修复：`TaskGraphEditorStore` 在 `init/reset/updateFlow` 时统一执行 flow 归一化，自动重建 `ALWAYS` 边为“当前节点顺序的串联链”（`START -> ... -> END`），防止出现未挂到主链的孤立动作。
355. 条件边保留策略：归一化仅重建 `ALWAYS` 边；`TRUE/FALSE` 等条件边在来源/目标节点仍存在时会原样保留，避免分支逻辑被误删。
356. 回归测试补齐：`TaskGraphEditorStoreTest` 新增主链归一化断言与初始化归一化测试，并在新增/复制/删除/分支编辑用例里校验 `ALWAYS` 串联；本轮再次通过 `testDebugUnitTest` 与 `assembleDebug`。
357. Lint 阻断项清零：`TaskControlPanelGlobalOverlay` 的窗口参数与屏幕尺寸读取补齐 API 版本保护（`layoutInDisplayCutoutMode` 仅在 API 28+ 设置，`currentWindowMetrics` 仅在 API 30+ 使用并提供低版本回退），消除 `NewApi` 错误。
358. 主题样式兼容修复：`themes.xml` 中按钮样式由 `android:paddingHorizontal` 改为 `android:paddingStart/paddingEnd`，兼容 `minSdk=24` 并保持同等视觉内边距语义。
359. Manifest Lint 策略显式化：`WRITE_SECURE_SETTINGS` 权限声明补充 `tools:ignore="ProtectedPermissions"`，明确该权限仅用于 ADB/系统签名调试链路，避免 lint 作为构建错误阻断。
360. 稳定性验证：以上 lint 修复后再次通过 `./gradlew lintDebug`（`BUILD SUCCESSFUL`）。
361. 运行报告结构化落地：新增 `RuntimeRunReport`（schema v1），统一封装 `traceId/source/taskId/taskName/status/stepCount/message/errorCode/validationIssues/traceEvents`，并提供 JSON 编码能力用于调试导出。
362. 运行报告持久化仓库落地：新增 `RuntimeRunReportRepository`，按 NDJSON 方式落盘 `runtime_run_reports_v1.ndjson`（保留最近 120 条），支持读取最近一条 JSON 供外部复制与排查。
363. 运行入口接入统一报告链路：`MainActivity` 的“运行当前任务”与 `TaskControlPanelGlobalOverlay` 的“开始任务”均在执行完成后写入结构化运行报告，覆盖首页与浮窗两条执行路径。
364. 控制台导出入口补齐：控制台“流程运行”区域新增“复制最近运行报告(JSON)”按钮，支持把最近一次结构化报告复制到系统剪贴板；用于后续调试面板和问题回传。
365. 稳定性验证：以上“运行报告结构化 + 导出入口”改造后再次通过 `testDebugUnitTest`、`assembleDebug` 与 `lintDebug`。
366. 运行历史可视化补齐：控制台新增“运行历史（调试）”区块，展示最近结构化报告列表（时间、任务、状态、错误码、来源、摘要、step/耗时），可直观看到多次运行记录而不必手读 JSON。
367. 调试交互补齐：历史区新增“刷新运行历史”“复制最近一条”及“按条复制该条 JSON”操作，便于快速复现与问题回传。
368. 报告仓库读能力补齐：`RuntimeRunReportRepository` 新增按 `reportId` 查询原始 JSON 与最近摘要列表查询，支持 UI 历史展示与按条复制。
369. 主页面状态同步补齐：`MainActivity` 新增运行报告历史 state，并在 `onCreate/onResume/执行完成后` 自动刷新；浮窗写入报告后回到首页也可看到最新历史。
370. 稳定性验证：以上“运行历史可视化 + 仓库查询能力”改造后再次通过 `testDebugUnitTest`、`assembleDebug` 与 `lintDebug`。
371. 动作级排查信息加密度：`RuntimeTraceEvent` 新增结构化 `details` 字段，记录每步动作的关键调试信息（`actionType/params 摘要/outcome/nextFlowId/nextNodeId/postDelayMs/actionStatus/actionErrorCode/actionMessage`），不再只依赖 `message` 字符串。
372. 失败事件错误码标准化：运行失败事件会在 `details.errorCode` 写入归一化错误码（从 message 提取），并保留 `rawMessage` 与 action 执行结果摘要，便于快速定位失败类型。
373. 报告导出同步升级：`RuntimeRunReport` 的 `traceEvents` 导出已包含 `details`，按条复制 JSON 后可直接看到动作级参数与跳转信息。
374. 测试补齐：`FlowRuntimeEngineTest` 新增 trace details 断言，`RuntimeRunReportTest` 新增 `details` JSON 序列化断言。
375. 稳定性验证：以上“动作级 details 扩展”改造后再次通过 `testDebugUnitTest`、`assembleDebug` 与 `lintDebug`。
376. 页面状态层拆分落地（第一阶段）：新增 `MainUiState` 与 `MainViewModel`，将首页主题切换、权限状态刷新、任务运行、运行报告复制/刷新等状态与业务逻辑从 `MainActivity` 迁出。
377. Activity 职责收敛：`MainActivity` 仅保留 UI 绑定与系统页面跳转（无障碍设置页），其余任务入口与控制台操作统一委托给 `MainViewModel`。
378. 路由参数瘦身：`MainTabsRoute` 移除首页不再使用的任务 CRUD 回调参数，保留“打开浮窗任务列表 + 控制台运行/调试”所需最小接口。
379. 稳定性验证：以上“MainActivity 状态层拆分”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
380. 浮窗大文件拆分（第二阶段-子模块）：新增 `TaskControlPanelGraphPreview.kt`，将动作列表连线与流程预览相关的绘制组件/布局计算（`JumpConnectionCanvas`、`buildJumpConnectionLayout`、`connectionGutterWidthDp` 等）从 `TaskControlPanelGlobalOverlay` 中抽离。
381. 责任边界收敛：`TaskControlPanelGlobalOverlay` 保留业务状态与页面路由，图形连线与预览算法迁移为同包内独立组件，降低单文件耦合和回归影响面。
382. 兼容策略：连线 lane 上限继续由 overlay 常量传入（`ACTION_LIST_MAX_VISIBLE_JUMP_LANES`），确保拆分前后的显示与交互参数一致。
383. 稳定性验证：以上“图形预览组件拆分”改造后通过 `:app:compileDebugKotlin`（后续继续执行全量 `testDebugUnitTest/assembleDebug` 回归）。
384. 编辑页面组件化继续推进：新增 `TaskControlPanelEditorPages.kt`，将 `SettingsActionListPage` 与 `SettingsNodeEditorPage` 的 Compose UI 主体迁移为独立组件（`TaskControlSettingsActionListPage`、`TaskControlSettingsNodeEditorPage`）。
385. Overlay 逻辑收敛：`TaskControlPanelGlobalOverlay` 中对应页面函数改为薄封装，仅负责路由跳转与回调绑定（`mutate/persist/openNodeEditor/openClickPositionPicker`），不再承载大段 UI 绘制细节。
386. 编辑工具函数复用：节点摘要、跳转摘要、时序摘要、参数解析与参数分组逻辑同步迁移到 `TaskControlPanelEditorPages.kt`，动作列表与节点编辑页共享同一套显示规则。
387. 文件体积变化：`TaskControlPanelGlobalOverlay.kt` 从 4354 行降至 2851 行，图形与编辑 UI 分别下沉到 `TaskControlPanelGraphPreview.kt`（782 行）与 `TaskControlPanelEditorPages.kt`（763 行）。
388. 稳定性验证：以上“编辑页组件抽离”改造后再次通过 `testDebugUnitTest` 与 `assembleDebug`。
389. 编辑页状态容器化（首轮）：`TaskControlPanelEditorPages.kt` 新增 `ActionListUiState` 与 `JumpTargetPickerUiState`，统一承载动作列表菜单态（新增菜单/行内菜单/删除确认）与 Jump 目标选择菜单态。
390. 状态挂载方式收敛：通过 `rememberActionListUiState(taskId, flowId)` 与 `rememberJumpTargetPickerUiState(nodeId)` 将页面局部状态与路由键绑定，避免状态变量在组件体内分散定义。
391. 行为保持：状态容器化不改业务回调协议（仍由 overlay 桥接 `mutate/persist/openNodeEditor/openClickPositionPicker`），仅做 UI 状态组织重构。
392. 稳定性验证：以上“状态容器化首轮”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
393. 参数草稿状态容器化：`TaskControlPanelEditorPages.kt` 新增 `NodeEditorDraftUiState`，集中管理节点编辑页的 `click X/Y` 输入草稿与通用参数输入草稿，替换字段内分散 `remember` 状态。
394. 模型同步策略收敛：草稿值通过 `LaunchedEffect(nodeId + rawValue)` 与模型参数同步，保留“输入时可临时无效、模型仅在校验通过后写回”的原有交互语义。
395. 草稿生命周期管理：新增 `retainParamDraftKeys`，在参数集合变化时清理无效草稿 key，避免跨动作/跨参数残留导致的状态污染。
396. 稳定性验证：以上“参数草稿状态容器化”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
397. 状态模型文件化：新增 `TaskControlPanelEditorUiState.kt`，将 `ActionListUiState/JumpTargetPickerUiState/NodeEditorDraftUiState` 从页面文件中分离，形成可复用、可测试的编辑状态模型层。
398. 组件测试补齐：新增 `TaskControlPanelEditorUiStateTest`，覆盖 `NodeEditorDraftUiState` 的关键行为（坐标草稿更新、参数草稿回退、草稿 key 保留/清理）。
399. 可维护性提升：`TaskControlPanelEditorPages.kt` 仅保留页面组合与 `remember...UiState` 绑定，状态实现细节下沉到独立文件，减少 UI 文件职责混杂。
400. 稳定性验证：以上“状态模型文件化 + 单测补齐”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
401. 参数分组逻辑可测试化：新增 `TaskControlPanelEditorParamSections.kt`，将节点编辑页的“参数过滤 + 分组映射”提炼为独立函数（`buildNodeEditorParamsSnapshot/groupNodeEditorParams/paramEditorGroupForKey`）。
402. 视图层回归测试补齐：新增 `TaskControlPanelEditorParamSectionsTest`，覆盖 `CLICK` 坐标字段过滤、`JUMP` 目标字段过滤、参数分组映射（`POSITION/TIMING/TARGET/BEHAVIOR/ADVANCED`）等核心规则。
403. 页面层调用收敛：`TaskControlPanelEditorPages.kt` 改为复用上述参数函数，不再内联参数过滤/分组细节，降低页面重构时的逻辑漂移风险。
404. 稳定性验证：以上“参数分组逻辑提取 + 视图层回归测试”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
405. 交互逻辑可测试化：新增 `TaskControlPanelEditorInteractionLogic.kt`，提炼跳转目标解析与坐标换算逻辑（`resolveJumpTargetFlowId/resolveJumpTargetNodeId/defaultJumpTargetNodeId/ratioToPixel/pixelInputToRatioOrNull`）。
406. 页面交互调用收敛：`TaskControlPanelEditorPages.kt` 中 Jump 目标选择与 CLICK 像素输入写回，统一调用交互逻辑函数，移除页面内联计算分支。
407. 交互回归测试补齐：新增 `TaskControlPanelEditorInteractionLogicTest`，覆盖“跳转目标默认解析”和“像素输入边界钳制/非法输入”关键路径。
408. 稳定性验证：以上“交互逻辑提取 + 回归测试”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
409. 添加动作逻辑可测试化：新增 `TaskControlPanelActionPresets.kt`，将动作菜单的 5 个新增分支抽象为 `AddActionPreset` + `applyAddActionPreset`，统一“菜单文案/成功文案/编辑器变更”映射。
410. 页面菜单调用收敛：`TaskControlPanelEditorPages.kt` 中“添加动作”下拉菜单改为遍历 `AddActionPreset.entries`，避免重复分支与文案漂移。
411. 交互单测补齐：新增 `TaskControlPanelActionPresetsTest`，覆盖 `CLICK/SWIPE/RECORD/DUP_CLICK/JUMP` 五类新增动作后节点 `kind/actionType` 的预期结果。
412. 稳定性验证：以上“添加动作预设提取 + 单测”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
413. 任务列表页组件化继续推进：`SettingsTaskListPage` UI 迁移为 `TaskControlSettingsTaskListPage`（位于 `TaskControlPanelEditorPages.kt`），`TaskControlPanelGlobalOverlay` 保留任务 CRUD 调度方法（`create/rename/duplicate/deleteTaskFromSettings`）并通过回调绑定。
414. Overlay 职责继续收敛：任务列表页从“UI + 异步仓库操作混排”改为“UI 组件 + 独立操作方法”，降低页面函数复杂度并保持行为一致。
415. 质量门禁补齐：本轮重构后再次通过 `lintDebug`（HTML 报告：`app/build/reports/lint-results-debug.html`），确认未引入新的 lint 阻断项。
416. 稳定性验证：以上“任务列表页组件化 + lint 回归”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug` 与 `lintDebug`。
417. 执行链路问题修复：定位并修复“任务执行只跑首动作”的高频场景，根因是历史任务出现 `END` 节点不在末尾时，顺序 `ALWAYS` 主链在编辑保存后被提前截断。
418. 编辑器主链归一化增强：`TaskGraphEditorStore` 的 `normalizeFlowSequentialAlwaysEdges` 改为按“`START + 非 START/END 节点 + END`”构建顺序链，避免 `END` 位于中间时吞掉后续动作。
419. 运行时兜底兼容：`FlowRuntimeEngine` 增加执行前规范化，仅在检测到“`END` 后仍有可执行节点”时重建顺序 `ALWAYS` 边，兼容已存量的异常链路数据且不干扰正常分支图。
420. 录制入库顺序修复：`appendRecordedGesture` 新增动作改为插入到 `END` 之前，不再把录制动作追加到 `END` 之后，减少后续编辑时链路被重写截断的风险。
421. 回归测试补齐：新增/调整 `TaskGraphEditorStoreTest` 与 `FlowRuntimeEngineTest` 覆盖“`END` 中间节点链路修复”场景；并完成 `:app:compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug` 验证通过。
422. 操作面板模式扩展：`PanelMode` 新增 `RUNNING`，用于承载任务执行中的独立状态层，不再与 `NORMAL/RECORDING` 复用同一布局语义。
423. 运行态可视化首版：浮窗运行中面板新增“任务名、当前 flow/node、已执行步数、状态文案、错误码”展示，并保留一键停止操作入口。
424. 运行追踪接线：浮窗执行链路接入 `OverlayRuntimeTraceCollector`，运行时 `RuntimeTraceEvent` 会实时回写面板状态（步骤计数、当前节点、错误码），结束后再回落至 `NORMAL`。
425. 稳定性验证：以上“RUNNING 状态层 + trace 实时回写”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
426. 运行面板防抖优化：`RUNNING` 卡片改为固定高度并统一 5 行信息槽位（含错误码占位），避免信息行出现/消失引发卡片高度跳变。
427. 文本稳定性优化：运行态关键字段（任务名/当前节点/状态/错误码）全部限制为单行省略，消除长文案换行导致的布局闪烁。
428. 稳定性验证：以上“运行面板高度稳定化”改造后再次通过 `:app:compileDebugKotlin`。
429. 本次执行历史页（MVP）落地：新增 `TaskControlRunHistoryPage`，在浮窗内展示“当前/最近一次执行”的摘要与逐步事件时间线（step、phase、flow/node、message、details）。
430. 路由扩展：`SettingsRoute` 新增 `RunHistory`，并接入设置弹窗层级导航；支持从“任务设置”页顶部入口打开本次执行历史。
431. 运行面板快捷入口：`RUNNING` 状态新增“本次执行历史”快捷按钮（`...`），执行中可直接跳转到历史页查看步骤细节。
432. 会话态追踪补齐：浮窗内新增本次执行会话状态缓存（traceId/task/status/step/startedAt/finishedAt/events），运行结束后仍可在历史页查看本次完整轨迹。
433. 稳定性验证：以上“本次执行历史页 + 路由扩展 + 会话缓存”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
434. 任务菜单新增“历史记录”入口：`TaskControlPanelGlobalOverlay` 的 `SettingsTaskListLayer` 头部操作改为内置“历史记录”按钮（常驻）+“本次执行历史”（有会话时显示），统一由设置弹窗路由承接。
435. 历史记录列表页接入：设置路由新增 `SettingsRoute.ReportHistory`，并复用 `TaskControlRuntimeReportHistoryPage` 展示最近运行摘要列表，支持刷新、复制单条 JSON、删除单条记录。
436. 数据链路统一：浮窗层新增 `runtimeReportHistory/runtimeReportHistoryMessage` 统一状态源；`startLastTask` 在运行报告落库后自动刷新历史列表，确保“每次执行都出现在历史记录入口”。
437. 稳定性验证：以上“任务菜单历史记录入口 + 列表可删”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
438. 任务 item 更多菜单增强：`TaskLibraryPanel` 新增 `onTaskHistory(taskId)` 回调，并在每个任务卡片的“更多”菜单中加入【历史记录】入口（位置：重命名与删除之间）。
439. 历史记录按任务过滤：浮窗层新增 `runtimeReportHistoryTaskId/runtimeReportHistoryTaskName` 过滤状态；从任务 item 打开历史时仅查询并展示该任务记录（顶部入口仍可看全部历史）。
440. 稳定性验证：以上“任务菜单按任务查看历史”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
441. 历史记录页能力补全：`TaskControlRuntimeReportHistoryPage` 新增【详情】入口并透传 `onOpenDetail(reportId)`，恢复“从历史列表进入历史详情”链路。
442. 历史详情组件化：新增独立页面组件 `TaskControlRuntimeReportDetailPage`（摘要、校验问题、步骤事件、复制 JSON），与历史列表组件分离，避免列表页承担详情渲染职责。
443. 浮窗路由扩展：`SettingsRoute` 新增 `ReportHistoryDetail(reportId)`，支持“历史列表 -> 历史详情 -> 返回历史列表”的层级导航与遮罩返回逻辑。
444. 仓库解析能力补齐：`RuntimeRunReportRepository` 新增 `findDetailByReportId(reportId)`，并提供结构化详情模型（含 validationIssues/traceEvents），避免 UI 直接解析原始 JSON。
445. 稳定性验证：以上“历史详情回归 + 组件拆分”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
446. 历史详情交互增强：在历史详情页新增【上一条 / 下一条】切换按钮，支持在当前筛选集合内连续排查记录，无需返回列表重复点选。
447. 操作收敛：按需求移除历史列表与历史详情中的【复制 JSON】入口，避免暴露不需要的操作。
448. 删除崩溃修复：`SharedOverlayDialogScaffold` 内容区滚动改为“仅在高度有限时启用 `verticalScroll`”，规避删除后重组阶段出现无限高度约束导致的 `IllegalStateException`。
449. 稳定性验证：以上“去除复制 JSON + 详情上下条 + 删除崩溃修复”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
450. BadToken 崩溃修复（浮窗环境）：将历史记录删除确认、动作删除确认从 `AlertDialog` 改为同层内嵌确认卡片，避免在 `TYPE_ACCESSIBILITY_OVERLAY` 下创建应用窗口导致 `WindowManager$BadTokenException`。
451. 任务管理弹窗兼容修复：`TaskLibraryPanel` 的“重命名任务/删除任务”由 `Dialog` 改为同层卡片确认，统一采用 overlay-safe 交互模型，避免后续同类 token 崩溃。
452. 稳定性验证：以上“overlay-safe 确认弹层收敛”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
453. 历史删除确认弹层重做：删除确认从“列表底部内联 item”改为“同层居中模态卡片 + 遮罩点击取消”，视觉回到弹窗语义且继续保持 overlay-safe。
454. 状态上提：历史删除确认状态由页面局部状态提升到 `TaskControlPanelGlobalOverlay`，在 `SettingsActionListLayer` 顶层统一渲染，避免随滚动流布局漂移到底部。
455. 稳定性验证：以上“历史删除确认弹层重做”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
456. 模态层范围修正：历史删除确认弹层从 `SettingsActionListLayer`（卡片内部范围）迁移到 `SettingsOverlayContent` 顶层，半透明背景改为覆盖整个 addView 全屏范围。
457. 动画补齐：历史删除确认弹层接入与其它弹层一致的 `AnimatedVisibility` 进入/退出（fade + vertical slide），消除“无动画”问题。
458. 稳定性验证：以上“全屏模态范围 + 弹层动画”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
459. 统一模态管理升级：新增 `TaskControlPanelModalHost`（`TaskControlModalModel/Action/Tone`），将删除确认、开始任务确认统一收敛到同一套 modal 状态与渲染管线，支持后续扩展成功/失败提示弹窗。
460. 开始任务确认重构：移除 `startTaskConfirmDialog*` 独立状态机与专用卡片，改为 `SettingsModal.ConfirmStartTask`，统一走全屏遮罩 + 居中卡片 + 进出动画；确认时按弹窗绑定 `taskId` 执行，避免状态漂移启动错误任务。
461. Overlay 全屏遮罩一致性修复：`SettingsOverlayContent` 仅保留一套全屏 scrim 计算与 modal 过渡状态，关闭流程改为“等待退出动画完成后移除 addView”，解决半透明背景提前消失与层级割裂问题。
462. 稳定性验证：以上“开始确认弹窗并入统一 ModalHost + 全屏遮罩一致化”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
463. 模态退出残留修复：`TaskControlModalHost` 增加“退出阶段模型保留”机制（`retainedModel`），避免 `model=null` 时提前 uncompose 导致 `MutableTransitionState` 卡在过渡态，进而阻塞 overlay 移除。
464. 交互残留修复：在模态过渡结束后才清空保留模型，确保 scrim 与卡片退出动画正常收敛，不再出现“页面已关闭但仍有透明/半透明遮罩拦截触摸”。
465. 稳定性验证：以上“模态退出残留修复”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
466. 浮层残留回收补丁：`TaskControlModalHost` 的 `onDismissRequest` 统一改为 `dismissSettingsModal(removeOverlayWhenIdle = !settingsVisible)`，确保“无遮罩页面”场景也会进入 overlay 回收条件，不再留下透明拦截层。
467. 删除确认动效连续性优化：确认动作改为“先关闭 modal，等待 `MODAL_EXIT_DELAY_MS` 后执行删除/启动任务”，避免数据刷新与路由变更抢占退出过渡，修复“消失动画被打断”的观感。
468. 稳定性验证：以上“浮层回收 + 动效连续性”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
469. 双层 scrim 叠加修复：`SettingsOverlayContent` 在 `settingsModal` 场景不再驱动基础 scrim 动画，仅保留 `TaskControlModalHost` 的全屏 scrim，避免“先亮一下/停顿/再继续”式非连贯背景过渡。
470. 背景动效职责拆分：基础 scrim 仅服务于设置页 sheet 与录制保存弹层；确认类 modal 的背景动画完全由 `ModalHost` 承担，统一时序并减少叠加闪烁。
471. 稳定性验证：以上“确认弹窗背景动画连贯性”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
472. 弹窗导航交互重排：`SharedOverlayDialogScaffold` 改为“底部左侧导航按钮（根页=关闭，子页=返回）”，顶部不再放返回/关闭，减少标题栏拥挤并统一单手操作区域。
473. 子页最小化入口落地：动作列表/历史页/编辑动作页顶部右侧固定【最小化】按钮；任务设置根页不显示最小化，符合“根页关闭、子页回退/最小化”规则。
474. 浮层显示状态机增强：`TaskControlPanelGlobalOverlay` 新增 `PanelDisplayMode(FULL/MINI)` 与 `PanelHideReason` 集合（`SETTINGS_OPEN/RECORDING_INTERACTION/RUNNING_TEMP`），由原因驱动主面板显示，避免分散 show/hide 调用冲突。
475. 最小化恢复链路：新增 `minimizeSettingsOverlay()/restoreSettingsOverlayFromMini()`；最小化时仅收起设置层并保留当前路由状态，恢复时回到原子页面，不强制重置到任务列表。
476. 录制交互隐藏接管：录制回放期间不再直接操作 `View.VISIBLE/INVISIBLE`，改为设置 `PanelHideReason.RECORDING_INTERACTION`，统一纳入面板可见性计算，避免与最小化/设置遮挡逻辑相互覆盖。
477. 稳定性验证：以上“底部导航 + 子页最小化 + 原因驱动显示状态机”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
478. 最小化恢复路由修复：修正“编辑动作页最小化后恢复回到任务设置根页”问题；最小化不再触发 settings overlay 移除重置，而是切换为 `FLAG_NOT_TOUCHABLE + INVISIBLE` 保持当前路由与编辑上下文。
479. 设置层交互开关：新增 `settingsLayoutParams` 与 `setSettingsOverlayInteractionEnabled()`，在最小化/恢复与重开设置时显式切换可触摸状态，避免全屏空 overlay 残留拦截触摸。
480. 稳定性验证：以上“最小化恢复路由修复”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。
481. 面板可见性追踪增强：新增结构化 trace 记录（`panelVisibilityTrace`，限制最近 180 条），统一输出 `event/reason/panelMode/panelDisplayMode/hideReasons/settingsVisible/overlayAttached`，便于复盘隐藏与移除链路。
482. 原因链路日志收敛：`setPanelDisplayMode`、`setPanelHideReason`、`setPendingSettingsOverlayRemoval`、`removeSettingsOverlay`、`removeOverlay`、`settings modal show/dismiss/action`、`settings open/close/minimize/restore` 全部接入 `panel_trace` 日志。
483. 移除阻塞可见化：`removeSettingsOverlayIfIdle` 增加阻塞签名日志（并去重），可直接看到为何未满足移除条件（如 `settingsVisible` / `recordingDialog` / `settingsModal`）。
484. 稳定性验证：以上“隐藏/移除原因可回溯”改造后再次通过 `:app:compileDebugKotlin`、`testDebugUnitTest` 与 `assembleDebug`。

## 3. 正在进行

1. 全局操作面板实机交互打磨（录制态按钮间距/状态文案/误触控制、运行态信息密度与按钮排布、录制提示层反馈）。
2. 调试面板前置能力推进：已打通“任务菜单历史记录入口 + 删除能力”，下一步继续补齐筛选、搜索、分页与分享链路。
3. 继续推进页面状态层拆分第二阶段：把 `TaskControlPanelGlobalOverlay` 的编辑/运行状态进一步拆成可复用状态模型与控制器，降低超长文件维护成本（已先完成图形预览子模块抽离）。
4. 编辑页组件化：`TaskList/ActionList/NodeEditor`、状态容器、规则逻辑与单测已完成；当前进入实机联调与体验回归阶段。
5. 录制多指会话实机打磨（手指数上限、停顿阈值、不同机型采样密度）。
6. 首页任务入口已收敛为单按钮，后续观察是否需要在首页增加“最近任务摘要”只读信息（不引入第二条编辑路径）。

## 4. 下一步计划

1. 操作面板 v1 稳定化：录制动作编辑入口、录制取消与异常提示完善。
2. 任务库第二版：批量操作、收藏分组、空态与上下文菜单优化。
3. Branch 条件模型扩展（支持更多条件源配置）。
4. 流程图交互增强：预览布局持久化（当前为会话内）与连线信息密度优化。
5. 导入/迁移链在任务体验稳定后接入（`LegacyIR -> migrateToLatest`）。
6. UI 架构继续分层：评估引入 `RouteState`/`UIIntent`，统一首页与浮窗编辑链路的状态意图表达。
7. 录制动作编辑二期：多指 stroke 可视化编辑与参数微调（统一在任务编辑页承载）。

## 5. 风险与注意事项

1. 不同 Android ROM 对 `WRITE_SECURE_SETTINGS` 下的辅助服务自动开启策略存在差异，需实机回归。
2. 手势反馈层仅代表“尝试执行”，最终以手势分发统计 `success/failed` 为准。
3. 后续新增主题风格时，要继续通过 `AppThemeMode + CmmClickerTheme` 管理，避免页面写死颜色回退成散乱样式。
4. 录制功能已支持连续会话与停止后保存；后续仍需补齐“录制结果可视化回放校对”和“更细粒度动作编辑（例如单步删除/重排）”。
5. 多指录制单次手势受 `AccessibilityService` stroke 数量限制（当前安全上限为 18 条），极端场景会进行裁剪。
6. 多指 timed 分段回放在高采样密度下会增加分发次数（按时间边界切片）；若后续出现低端机卡顿，需要继续做时间片合并策略。
7. 错峰多指当前走“单次 multi-stroke”回放路径以优先保证不取消；后续若需进一步提升错峰长按精度，可再做“预注册占位指针”或“分阶段手势重建”专项优化。
8. 目前编辑链路集中在 `TaskControlPanelGlobalOverlay` 单文件内，后续重构需确保录制控制与任务编辑状态拆分后仍保持单向数据流，避免状态互相污染。
9. 运行时“执行前顺序边兜底”当前只在检测到 `END` 节点后仍存在可执行节点时触发；若后续引入更复杂的非线性流程模板，需要评估是否扩展为更通用的迁移/修复策略。
10. 运行态面板当前逐条消费 trace 事件并触发 UI 刷新；若后续出现高频动作导致面板刷新过密，需要引入节流或批量更新策略。
11. 本次执行历史页当前以会话内缓存展示（重启/隐藏后不保留当前会话态）；若后续需要跨会话回看，可考虑增加按 `reportId` 的详情解析与固定入口。
