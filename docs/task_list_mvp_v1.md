# Task List MVP v1（真实任务体验）

更新时间：2026-02-27

## 1. 目标

1. 让用户可以在应用内管理真实任务，而不是仅运行固定 demo。
2. 打通最小闭环：新建任务 -> 编辑动作 -> 保存 -> 任务列表运行 -> 查看最近运行结果。
3. 为后续导入与迁移落地提供统一任务容器（`TaskBundle`）。

## 2. 当前实现

文件：

1. `app/src/main/java/com/ksxkq/cmm_clicker/feature/task/TaskRepository.kt`
2. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainActivity.kt`
3. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainTabsRoute.kt`
4. `app/src/main/java/com/ksxkq/cmm_clicker/ui/TaskTabScreen.kt`
5. `app/src/main/java/com/ksxkq/cmm_clicker/ui/ConsoleTabScreen.kt`
6. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainUiComponents.kt`

能力：

1. `LocalFileTaskRepository`：本地 JSON 持久化，支持任务 CRUD 与运行信息更新。
2. 任务列表页（`任务` Tab）：
   - Shortcuts 风格“快捷指令库”卡片布局
   - 首页采用 iOS 风格底部导航结构（任务/控制台）
   - 已接入 `Scaffold` 安全区内边距，适配 Android 14 边到边场景
   - 新建任务（默认模板 `start -> click -> end`）
   - 搜索任务（名称/ID）
   - 分段筛选（全部/最近运行/最近编辑）
   - 卡片操作：点击卡片直接进入浮窗编辑（无“选中”交互）
   - 运行使用圆形播放 icon 按钮（固定卡片右下角）
   - 卡片右上角提供圆形更多按钮，`复制/重命名/删除` 收纳到菜单
   - 图标改为标准库（Material Icons Extended），避免手绘图标一致性问题
   - 删除二次确认与重命名弹窗
3. 动作详情浮窗：
   - 由 AccessibilityService 承载的全局浮窗（`TYPE_ACCESSIBILITY_OVERLAY`），可在其它 App 页面编辑
   - 统一使用公共弹窗容器：头部标题/菜单 action/底部操作区可按场景配置
   - 浮窗 UI 全量使用 Compose Material3 组件，并通过 `CmmClickerTheme + AppThemeMode` 与首页保持同一主题效果
   - 浮窗改为“全屏弹窗层 + 底部内容面板”，禁止拖动位置，交互更稳定
   - 显示时带背景渐暗动画与底部上滑动画，强化弹窗语义
   - 点击内容外区域可关闭浮窗，交互行为与常规弹窗一致
   - 关闭时带退出动画（背景淡出 + 面板下滑），再执行窗口移除
   - 背景遮罩覆盖状态栏区域，且保持半透明，不使用不透明灰底
   - 默认仅展示动作列表摘要（不铺满参数）
   - 顶部提供“查看预览/隐藏预览”按钮（黑白统一样式），预览支持图形连线与文本连线
   - 动作列表内支持 jump 连接线（同 flow）用于快速识别跳转目标
   - 点击动作后进入“动作编辑浮窗”
4. Jump 设置规则：
   - 跳转语义来自 `NodeKind.JUMP`，不是 `ActionType=jump`
   - 通过 `targetFlowId + targetNodeId` 指向目标节点（支持跨 flow）
5. 任务列表显示：
   - 更新时间
   - 最近运行时间
   - 最近运行状态
   - 最近运行摘要
   - 搜索（按任务名或 taskId）
   - 分段筛选（全部/最近运行/最近编辑）
   - 删除任务二次确认弹窗
6. 编辑器联动：
   - 进入运行/编辑动作前加载对应 `TaskBundle` 作为当前任务上下文
   - 编辑自动保存（防丢）
   - 手动“保存任务”按钮
7. 控制台联动：
   - 运行按钮语义改为“运行当前任务”。
8. 页面结构：
   - `MainActivity` 仅负责状态与回调分发
   - Tab 页面与 UI 组件拆分到独立文件，便于维护与后续继续 ViewModel 化

## 3. 当前限制

1. 持久化当前为文件 JSON，尚未升级到 Room（后续可平滑替换）。
2. 任务列表当前尚未支持批量操作（批量删除/批量运行）。
3. 任务模板目前仅用于研发阶段，正式上线可关闭默认模板创建。
4. 当前主页面不提供普通编辑入口，任务编辑统一走全局浮窗。
5. 目前仍是 Activity 内状态持有，下一步再下沉到独立 ViewModel/RouteState。

## 4. 下一步

1. 任务库第二版：卡片信息层级优化、上下文菜单与批量操作。
2. 运行结果可展开详情。
3. 任务批量操作（批量选择、批量删除）与空态优化。
4. 任务稳定后再接入旧备份导入和 schema 迁移链。
