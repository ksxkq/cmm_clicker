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
7. Flow 管理：`addFlow/renameSelectedFlow/deleteSelectedFlow/setSelectedFlowEntryNode`（含删除保护与入口节点切换）；删除接口返回结构化结果，便于 UI 呈现精确失败原因。

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
22. 参数 schema 升级到 v1.1：字段支持 `required/default/min/max/helperText`，主页面与浮窗编辑器统一展示校验错误提示、辅助文案、数值键盘，并提供“填充默认值”快捷操作。
23. 动作类型切换会清理旧类型遗留参数并补齐新类型默认参数，避免 `click` 参数残留到 `swipe` 等类型。
24. 全局浮窗中“动作列表 -> 编辑详情”改为堆叠弹窗转场：底层列表保留并缩放/变暗，上层详情弹窗上滑叠加，返回时反向过渡，强化“进入次级弹窗”感受。
25. 全局浮窗参数输入接入键盘避让：容器 `imePadding` + 输入框聚焦自动 `bringIntoView`，降低键盘覆盖输入项概率。
26. 全局浮窗新增路由栈与面包屑展示（v1 只读）：路径会反映当前编辑层级，为后续 folder/子流程等深层页面导航预留统一机制。
27. 面包屑升级为 v2：支持点击上层路径直接回跳到对应编辑层级（路由栈裁剪）。
28. 顶部头部布局优化：返回/标题/关闭与 header actions 分行展示，actions 行支持横向滚动，减少按钮拥挤与标题压缩。
29. 全局浮窗新增“流程管理”堆叠页：支持 flow 切换、入口节点设置、新增流程、重命名流程、删除当前流程，并复用面包屑回跳。
30. 遮罩点击行为与返回语义统一：若当前处于可返回层级，点弹窗外部先返回上一级；只有根页面才关闭浮窗。
31. Jump/Folder/SubTask 节点新增目标失效提示与“一键修复为当前流程入口”按钮，提升引用修复效率。
32. 动作列表中的 jump 连线支持点击跳转：点击虚线可直接打开目标动作编辑页，便于在长列表中快速定位目标节点。
33. 修复“返回时详情层直接消失”的动效问题：详情层在路由回退时会保留到退场动画结束后再卸载，返回手感与入场动画保持一致。
34. 详情页转场参数优化：进/退场切换为 spring 主导、并同步底层回弹，整体观感更接近“堆叠弹窗”而非硬切换。

## 2.3 测试

文件：

1. `app/src/test/java/com/ksxkq/cmm_clicker/feature/editor/TaskGraphEditorStoreTest.kt`
2. `app/src/test/java/com/ksxkq/cmm_clicker/feature/editor/EditorParamSchemaTest.kt`

覆盖：

1. 新增节点 + undo/redo。
2. 入口节点删除保护。
3. kind/actionType/params 编辑链路。
4. branch `variableKey` 与 TRUE/FALSE 目标边编辑。
5. 参数 schema 默认值合并与字段校验（必填/数值/枚举）。
6. 动作类型切换时参数清理（去除旧类型字段，保留公共字段）。
7. Flow 管理链路（新增/重命名/入口节点切换/删除保护/删除失败结果分类）。
8. 校验器对 jump 目标参数做 `toString().trim()` 兼容处理，减少非字符串存档导致的误报。

## 3. 已知限制

1. 流程图预览仅支持查看和选中，尚未支持拖拽布局与连线编辑。
2. 参数 schema 已接入默认值补齐与 UI 级校验，但运行时尚未做“插件声明级强校验失败即拒绝执行”。
3. 节点 ID 暂未开放编辑，避免牵扯边和引用联动迁移。
4. 面包屑已支持回跳，但当前未加入“未保存提示后再回跳”的保护流程（后续补充）。
5. Flow 管理目前先落在全局浮窗，主页面编辑器还未同步接入同等能力。

## 4. 下一步（v2）

1. Branch 条件节点编辑器（条件源动作集合）。
2. 流程图交互增强：拖拽、连线点击跳转、缺失目标修复入口。
3. 参数 schema 第二版（按动作插件提供字段定义、默认值、校验）。
4. 编辑器调试能力：节点级单步执行与 trace 定位。
