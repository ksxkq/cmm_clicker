# cmm_clicker 执行日志（持续维护）

更新时间：2026-02-27

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

## 3. 正在进行

1. 全局操作面板实机交互打磨（按钮尺寸、状态文案、误触控制、录制提示层反馈）。
2. 把“运行 trace 与错误码”沉淀为可导出日志结构，为调试面板打基础。
3. 继续推进页面状态层拆分：`MainActivity` -> `ViewModel + Route state`，让任务/控制台各自拥有独立状态模型。
4. 浮窗编辑器与操作面板的导航协同（后续支持从操作面板直接进入当前任务编辑态）。

## 4. 下一步计划

1. 操作面板 v1 稳定化：录制动作编辑入口、录制取消与异常提示完善。
2. 任务库第二版：批量操作、收藏分组、空态与上下文菜单优化。
3. Branch 条件模型扩展（支持更多条件源配置）。
4. 流程图交互增强：预览布局持久化（当前为会话内）与连线信息密度优化。
5. 导入/迁移链在任务体验稳定后接入（`LegacyIR -> migrateToLatest`）。
6. UI 架构继续分层：引入 `RouteState`/`UIIntent`，减少 Activity 中业务判断。

## 5. 风险与注意事项

1. 不同 Android ROM 对 `WRITE_SECURE_SETTINGS` 下的辅助服务自动开启策略存在差异，需实机回归。
2. 手势反馈层仅代表“尝试执行”，最终以手势分发统计 `success/failed` 为准。
3. 后续新增主题风格时，要继续通过 `AppThemeMode + CmmClickerTheme` 管理，避免页面写死颜色回退成散乱样式。
4. 录制功能当前为 MVP（单次手势追加动作），尚未提供连续录制会话、手势回放确认与动作命名策略。
