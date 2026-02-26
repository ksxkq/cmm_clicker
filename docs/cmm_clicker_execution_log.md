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

1. 新增仓库级 `AGENTS.md`，固化“文档驱动开发 + 同步更新文档”规则。
2. 明确“实现完成后必须同步执行日志与领域文档”的完成标准。

## 3. 正在进行

1. 基于新主题系统，继续抽离可复用组件（状态条、动作按钮、配置项行）。
2. 把“运行 trace 与错误码”沉淀为可导出日志结构，为调试面板打基础。

## 4. 下一步计划

1. MVP 编辑器数据层：先做“动作列表编辑”与“节点基础属性编辑”。
2. Jump/Branch 体验重做：jump 目标选择器（支持跨 flow）与 branch 条件节点模型落地。
3. 流程可视化第一版：显示 jump 连线、目标高亮、缺失目标提示。
4. 导入能力第一版：从 liteclicker 备份解析到 `LegacyIR`，再映射到新 `FlowGraph`。
5. 版本升级链第一版：定义 `schemaVersion` 与迁移 step 骨架。

## 5. 风险与注意事项

1. 不同 Android ROM 对 `WRITE_SECURE_SETTINGS` 下的辅助服务自动开启策略存在差异，需实机回归。
2. 手势反馈层仅代表“尝试执行”，最终以手势分发统计 `success/failed` 为准。
3. 后续新增主题风格时，要继续通过 `AppThemeMode + CmmClickerTheme` 管理，避免页面写死颜色回退成散乱样式。
