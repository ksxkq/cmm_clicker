# Editor MVP v1（动作列表 + 节点基础属性）

更新时间：2026-02-26

## 1. 目标

1. 提供可运行的编辑器数据层，不依赖流程图视图也能先完成基础编辑。
2. 先打通核心编辑链路：选择 flow -> 选择节点 -> 修改节点 -> 校验反馈 -> 运行验证。
3. 提前落地 `undo/redo`，为后续复杂交互（连线、拖拽）打基础。

## 2. 当前实现范围

## 2.1 Store 层

文件：

1. `app/src/main/java/com/ksxkq/cmm_clicker/feature/editor/TaskGraphEditorStore.kt`

能力：

1. `TaskGraphEditorState`：输出当前 bundle、选中 flow/node、undo/redo 状态、图校验结果。
2. 选中能力：`selectFlow` / `selectNode`。
3. 节点操作：新增动作节点、删除当前节点、上下移动节点。
4. 节点属性编辑：`kind/actionType/pluginId/enabled/active/params`。
5. 历史能力：`undo` / `redo`。
6. `reset`：重置到示例图数据。

## 2.2 UI 层

文件：

1. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainActivity.kt`

能力：

1. 页面模式切换：`任务` / `控制台`（`编辑器` 保留为开发调试页）。
2. 编辑器操作面板：undo/redo、新增、删除、移动、重置。
3. Flow 列表选择与节点列表选择。
4. 节点属性编辑面板（kind/actionType/pluginId/flags/params）。
5. Jump 类节点目标选择器（flow/node 点选，不再仅靠手输参数）。
6. Branch 节点支持 `variableKey`、`operator`、`expectedValue` 与 TRUE/FALSE 目标边点选。
7. 节点列表显示 jump 目标摘要（`-> targetFlow/targetNode`）。
8. 图校验面板（显示校验问题列表）。
9. 流程图预览（第一版）：节点/边/jump 同 flow 虚线连线、点击节点选中、相关连线高亮。
10. 参数编辑 schema 化：按节点类型渲染字段，`operator` 走枚举按钮，数值字段走数字键盘。
11. 连线文本预览面板（边与 jump 引用文本）。
12. 运行按钮复用当前编辑后的 bundle（不是固定样例）。
13. 任务页新增动作浮窗链路：简版动作列表 + 独立动作编辑浮窗。
14. 编辑器已接入任务仓库：选中任务加载、自动保存与手动保存。
15. kind 切换时参数会按目标类型重置，避免 `jump` 仍显示 `click` 参数。
16. 动作类型按钮只展示当前已实现动作：`click/dupClick/swipe/record/closeCurrentUI`。
17. `jump` 统一通过 `kind=JUMP` 进行配置，目标由 `targetFlowId/targetNodeId` 点选。
18. 全局浮窗编辑界面使用统一 Dialog Scaffold（Compose 实现）与 Material3 按钮体系，支持头部菜单 action 与底部操作按钮分区。
19. 动作列表支持同 flow 的 jump 连线，可在列表态直接观察跳转关系。
20. 浮窗编辑器交互动画升级：背景渐暗 + 底部上滑进场，作为后续编辑/弹窗交互的统一动画基线。
21. 浮窗支持点击遮罩关闭，并带退出动画（背景淡出 + 面板下滑），统一弹窗开合体验。

## 2.3 测试

文件：

1. `app/src/test/java/com/ksxkq/cmm_clicker/feature/editor/TaskGraphEditorStoreTest.kt`

覆盖：

1. 新增节点 + undo/redo。
2. 入口节点删除保护。
3. kind/actionType/params 编辑链路。
4. branch `variableKey` 与 TRUE/FALSE 目标边编辑。

## 3. 已知限制

1. 流程图预览仅支持查看和选中，尚未支持拖拽布局与连线编辑。
2. 参数 schema 目前是 UI 约束，尚未接入插件级强校验和默认值自动补齐。
3. 节点 ID 暂未开放编辑，避免牵扯边和引用联动迁移。
4. Flow 的新增/删除/重命名暂未开放。

## 4. 下一步（v2）

1. Branch 条件节点编辑器（条件源动作集合）。
2. 流程图交互增强：拖拽、连线点击跳转、缺失目标修复入口。
3. 参数 schema 第二版（按动作插件提供字段定义、默认值、校验）。
4. 编辑器调试能力：节点级单步执行与 trace 定位。
