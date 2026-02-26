# cmm_clicker 执行日志（持续维护）

更新时间：2026-02-26

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
2. 新增任务列表页：新建、选择、运行入口、重命名、复制、删除。
3. 新建任务默认模板为 `start -> click -> end`（用于当前研发阶段快速起步）。
4. 新增任务运行历史展示：`最近运行时间/状态/摘要`。
5. 任务编辑改为浮窗链路：动作列表浮窗（默认简版）-> 动作编辑浮窗（参数详情）。
6. 编辑器接入当前选中任务，支持“保存任务”按钮与编辑自动保存。
7. 控制台运行入口由“运行测试流程”改为“运行当前选中任务”。
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

## 3. 正在进行

1. 任务体验第一版可用性验证（任务列表、动作浮窗、运行联动）与交互细节打磨（含参数编辑可用性）。
2. 把“运行 trace 与错误码”沉淀为可导出日志结构，为调试面板打基础。
3. 全局浮窗细节优化（更强的预览、布局与可达性，按钮尺寸与密度继续打磨；面包屑回跳的交互手感继续微调）。

## 4. 下一步计划

1. 任务列表第二版：搜索、排序、批量操作、确认弹窗与空态优化。
2. 浮窗交互优化：层级动效、关闭确认、节点编辑快捷操作。
3. Branch 条件模型扩展（支持更多条件源配置）。
4. 流程图交互增强：拖拽布局、连线点击跳转、缺失目标可修复提示。
5. 导入/迁移链在任务体验稳定后接入（`LegacyIR -> migrateToLatest`）。

## 5. 风险与注意事项

1. 不同 Android ROM 对 `WRITE_SECURE_SETTINGS` 下的辅助服务自动开启策略存在差异，需实机回归。
2. 手势反馈层仅代表“尝试执行”，最终以手势分发统计 `success/failed` 为准。
3. 后续新增主题风格时，要继续通过 `AppThemeMode + CmmClickerTheme` 管理，避免页面写死颜色回退成散乱样式。
