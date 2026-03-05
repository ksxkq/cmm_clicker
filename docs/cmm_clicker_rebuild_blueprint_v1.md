# cmm_clicker 重写蓝图 v1

## 0. 当前实现进度（2026-03-02）

1. 已完成 `FlowGraph + Runtime + 插件化动作` 骨架。
2. 已完成辅助服务执行链路、可视化反馈、自动授权后自动启用。
3. 已完成编辑器 MVP v1（动作列表编辑 + 节点基础属性编辑 + undo/redo + 校验面板）。
4. 已交付 jump 目标选择器与 branch 目标编辑（列表点选）。
5. 已交付流程图预览点击选中与边/jump 高亮，参数编辑已接入 schema 驱动。
6. 已交付 schema 迁移骨架（`BundleMigrationEngine` + `v0->v1` step + 单测）。
7. 已交付任务列表真实操作链路（新建/编辑/运行/重命名/复制/删除）与本地持久化。
8. 首页已切换为底部导航（任务/控制台），并接入安全区 insets 适配边到边显示。
9. 页面层完成首轮拆分：`MainActivity` 瘦身为入口编排，任务页/控制台页与公共组件拆分到独立文件。
10. 新增全局“操作面板”浮窗（设置/录制/开始），并复用任务库组件作为浮窗设置页，打通“任意 App 界面启动任务 + 录制手势入库”的闭环。
11. 浮窗层级策略更新：操作面板与任务列表使用两个独立 overlay（两个 `addView`）；任务列表内编辑采用同窗路由栈，不在该链路继续新增 overlay。

## 1. 目标定义

本次不是迁移，是“参考后重写”。

重写目标：

1. 做一个可持续扩展的自动化平台内核，而不是继续堆 if/else。
2. 先把流程语义跑通（jump/branch/folder/subTask），再扩动作数量。
3. 编辑器可用性优先，解决嵌套编辑痛点。
4. 为未来 AI 编排预留清晰的动作能力模型。
5. 支持旧备份导入到新结构，并具备可追踪升级机制。
6. 运行入口形态统一为“全局操作面板 + 全局编辑浮窗”，减少主应用与跨 App 操作割裂。

已确认约束：

1. `closeCurrentUI`：保留动作类型，执行为 no-op。
2. `jump`：重写，不沿用旧逻辑细节。
3. `dupClick`：保留。
4. 初期先做简单动作：`click/swipe/record`，流程跑通后再逐步接入其他动作。

## 2. 产品形态建议

建议做“双视图编辑器”，底层同一份图模型：

1. 列表视图：高效编辑动作参数、批量操作。
2. 流程图视图：可视化 jump/branch/folder/subTask 连线。
3. 两视图实时同步，避免“图和列表两套数据”。

## 3. 架构总览

模块建议：

1. `core-model`：任务图模型、节点/边定义、版本号。
2. `core-runtime`：执行引擎、运行上下文、调试与 trace。
3. `core-actions`：动作插件接口与内置插件。
4. `feature-editor`：列表编辑、流程图编辑、undo/redo。
5. `feature-import-export`：旧备份导入、新备份导出、导入报告。
6. `feature-migration`：schema 升级链。
7. `feature-ai-plan`：预留 Plan IR 与编译入口（先不做模型接入）。
8. `infra-storage`：当前为文件 JSON 仓库（已落地），后续切换到 Room/SqlDelight。
9. `infra-android`：Accessibility、截图、前台服务、权限。

分层原则：

1. `feature-*` 不直接调用 native，统一走 `core-actions` 提供的能力接口。
2. 运行时不依赖 UI；编辑器不依赖执行线程。
3. 动作插件只关心“输入 -> 执行 -> 输出”，不关心流程跳转。

## 4. 数据模型（核心）

建议统一成 Flow Graph：

1. `TaskFlow`
2. `FlowNode`
3. `FlowEdge`
4. `TaskBundle`（任务入口 + 多 flow 集合）

### 4.1 建议结构

`TaskBundle`

1. `bundleId`
2. `name`
3. `schemaVersion`
4. `entryFlowId`
5. `flows: List<TaskFlow>`
6. `metadata`（作者、更新时间、来源等）

`TaskFlow`

1. `flowId`
2. `name`
3. `nodes: List<FlowNode>`
4. `edges: List<FlowEdge>`

`FlowNode`

1. `nodeId`
2. `kind: action | branch | jump | folderRef | subTaskRef | start | end`
3. `actionType`（`kind=action` 时使用）
4. `pluginId`（动作插件标识）
5. `params`（JSON）
6. `ui`（编辑器布局信息）
7. `flags`（enable/active 等）

`FlowEdge`

1. `edgeId`
2. `fromNodeId`
3. `toNodeId`
4. `condition: always | true | false | match(key)`
5. `priority`（同源多边时排序）

### 4.2 jump 与 branch 在新模型中的语义

`jump`

1. 显式保存 `targetFlowId + targetNodeId`。
2. 编辑器直接画边，不再依赖索引。
3. 节点移动不会影响跳转语义。
4. 支持跨 flow 跳转（你明确需要）。

`branch`

1. 不再只是动作特判，而是“条件节点”。
2. 条件节点内部可配置“条件源动作集合”（兼容你当前 checkBranch 思路）。
3. 条件节点输出两条标准边：`true`、`false`。

`folder` / `subTask`

1. `folder` 建议实现为 `flowRef`（子 flow），执行时入栈。
2. `subTask` 可实现为跨 bundle 的 `taskRef`（后续支持）。

## 5. 执行引擎设计

## 5.1 运行上下文

`RuntimeContext` 建议包含：

1. `traceId`
2. `bundleId/flowId/nodeId`
3. `variables`
4. `loopCounter/retryCounter`
5. `lastActionResult`
6. `stopReason`
7. `engineFlags`（dry-run、silent）

## 5.2 统一执行结果

`NodeExecutionResult`：

1. `status: success | fail | error | stop`
2. `next: default | jump(targetFlowId,targetNodeId)`
3. `payload`
4. `errorCode/errorMessage`
5. `durationMs`

## 5.3 状态机（建议）

1. `resolveNode`
2. `validateNode`
3. `executePlugin`
4. `resolveNextEdge`
5. `emitTrace`
6. `advance`

## 5.4 停止语义

1. `stopTask(task)`：终止整个 bundle。
2. `stopTask(subTask)`：终止当前子 flow，回到父 flow。
3. 统一通过 `RuntimeContext` 的作用域栈处理，不靠分散布尔变量。

## 6. 动作插件化

建议接口（Kotlin 概念）：

```kotlin
interface ActionPlugin {
    val pluginId: String
    val supportedType: ActionType
    fun validate(params: JsonObject): List<ValidationIssue>
    suspend fun execute(ctx: ActionContext, params: JsonObject): ActionResult
}
```

建议内置插件分组：

1. `GesturePlugins`：click/swipe/record/dupClick
2. `SystemPlugins`：back/home/recent/launchApp/intentUri/lockScreen
3. `RecognizePlugins`：image/button/color
4. `LogicPlugins`：set/check/operate variable、netRequest、checkTime/checkActivity
5. `ControlPlugins`：pauseTask/stopTask/randomWait
6. `NoopPlugins`：closeCurrentUI（按你要求先空实现）

## 7. 编辑器设计重点

## 7.1 必做能力

1. `undo/redo`（命令模式，支持结构与参数编辑）。
2. 节点拖拽、连线、自动对齐。
3. 面包屑导航（主流程 > 子流程 > 识别成功分支 等）。
4. 分支/跳转目标可视化高亮。
5. 静态校验面板（无目标、不可达、死循环风险、变量未定义）。

当前进展：

1. `undo/redo`、分支/跳转目标高亮、静态校验面板已落地（MVP 级）。
2. 拖拽连线、自动对齐、面包屑导航尚未实现。

## 7.2 你关心的 jump 连线

建议交互：

1. 选中 jump 节点时，高亮目标节点和路径。
2. 目标缺失时显示红色虚线并给修复入口。
3. 支持“点击连线直接跳转到目标节点”。

## 8. 导入旧备份与数据升级

## 8.1 版本规则

1. 仅允许高版本应用导入低版本备份。
2. 若备份版本高于应用支持版本，直接拒绝导入并给出原因。

## 8.2 导入流程

1. 解析旧备份（liteclicker 格式）。
2. 映射到中间模型 `LegacyIR`。
3. 从 `LegacyIR -> FlowGraph`。
4. 执行校验与修复（可自动修复和人工修复项分离）。
5. 生成导入报告并落库。

导入报告建议包含：

1. 总任务数、成功数、失败数。
2. 降级动作清单（例如 `closeCurrentUI -> noop`）。
3. 人工处理项（例如无效 jump 目标）。
4. 最终 schemaVersion。

## 8.3 升级机制

1. 每次 schema 变更都新增一个 migration step。
2. 执行升级时记录完整日志（fromVersion -> toVersion）。
3. 升级前自动备份，失败可回滚。

## 9. 调试与可观测性

必须提供：

1. `dry-run`：流程可运行但不触发真实点击/系统动作。
2. `trace log`：每个节点的输入、结果、耗时、下一跳。
3. `execution replay`：可按 trace 重放路径（用于排查跳转/分支问题）。
4. `runtime metrics`：成功率、平均耗时、失败分布。

## 10. MVP 路线图

### Phase 0：骨架（1-2 周）

1. 建立 `FlowGraph` 数据结构。
2. 建立 `core-runtime` 执行循环。
3. 建立 `ActionPlugin` 接口与注册中心。

### Phase 1：可跑通（2-3 周）

1. 动作：`click/swipe/record/dupClick`。
2. 流程：`jump/branch/folder`。
3. 调试：`dry-run + trace log`。

### Phase 2：可编辑（2-3 周）

1. 列表视图 + 流程图视图。
2. jump 连线可视化。
3. undo/redo + 静态校验。

### Phase 3：可落地替换（2-4 周）

1. 变量系统、check 系动作、stop/pause/randomWait。
2. 旧备份导入 + 导入报告。
3. schema 升级链与回滚策略。

### Phase 4：能力补齐（持续）

1. 识别动作（image/button/color）。
2. netRequest、系统动作、更多插件。
3. AI Plan IR 编译入口。

## 11. 关键风险与规避

1. 风险：一次性动作范围过大导致延期。
   - 规避：严格按 Phase 交付，未进 MVP 的动作不抢先做。
2. 风险：导入映射失真导致用户数据损坏。
   - 规避：导入前备份 + 导入报告 + 可回滚。
3. 风险：编辑器复杂度爆炸。
   - 规避：先列表后图，图只做核心交互。
4. 风险：运行时行为不可追踪。
   - 规避：强制 trace 事件 schema，测试覆盖关键流程。

## 12. 下一步执行建议

1. 先冻结 `MVP 动作和流程语义`（一个小文档即可）。
2. 再落地 `FlowGraph + Runtime + 4 个基础插件(click/swipe/record/jump)`。
3. 同步做最小编辑器（列表 + jump 目标选择 + 连线预览）。

## 13. 近期实现补充（2026-03-02）

1. 操作面板录制从“单手势即时入库”升级为“会话式录制”：
   - 录制中支持暂停/继续
   - 停止后进入保存弹窗
   - 保存时创建新任务并按录制顺序追加动作
2. `TaskRepository.params` 的持久化实现已升级为递归结构编解码：
   - 支持 `Map/List/Number/Boolean/String`
   - 为 `record` 动作的路径点位（`points`）提供稳定存储，避免被字符串化后回放失真
3. 录制会话增加“采集后即时回放”：
   - 每采集一步手势都会做一次真实回放（并显示轨迹反馈）
   - 回放时临时隐藏录制层，结束后恢复录制会话继续采集
4. 录制采集与执行协议扩展为多指 stroke：
   - 采集侧按 `pointerId` 记录 `points + timestamps + down/up`
   - 运行侧 `record` 支持 `strokes`（含 `startDelayMs/durationMs`）并兼容旧 `points`
   - 支持多指并发与“长按后拖动”回放
   - Android O+ 下单指时间戳回放使用 `continueStroke` 分段执行停顿与移动，避免长按语义丢失
5. 操作面板录制体验细化：
   - 录制浮层从全透明改为轻量半透明遮罩（`#12000000`）
   - `NORMAL <-> RECORDING` 切换动效提速，减少状态切换迟滞感
6. 动作执行时序模型补齐：
   - 节点新增统一 `postDelayMs`（执行后延迟）参数
   - 运行时在节点执行后（`NodeOutcome.CONTINUE`）应用该延迟（dry-run 跳过）
   - 编辑层与列表层已同步显示 `durationMs + postDelayMs` 摘要
7. 编辑器数据一致性补齐：
   - `TaskGraphEditorStore` 在初始化、reset、flow 更新时会统一执行主链归一化
   - 归一化策略：仅重建 `ALWAYS` 顺序边（`START -> ... -> END`），保留有效的条件边（`TRUE/FALSE`）
   - 目标是让流程图与动作列表的“线性主流程”语义保持一致，避免出现孤立节点导致预览与执行理解偏差
8. 运行可观测性基线补齐：
   - 新增结构化运行报告模型 `RuntimeRunReport`（包含 traceId、source、errorCode、validationIssues、traceEvents）
   - 新增本地报告仓库 `RuntimeRunReportRepository`，以 NDJSON 持久化最近运行记录（默认保留 120 条）
   - 首页运行与浮窗运行统一写入同一报告仓库，控制台支持“一键复制最近运行报告 JSON”
9. 调试历史可视化补齐：
   - 控制台新增“运行历史（调试）”列表，直接展示最近运行的关键摘要（状态、错误码、来源、时长、step）
   - 支持按 `reportId` 精确复制单条 JSON，减少“只能复制最近一条”导致的定位摩擦
10. 动作级 trace 细节补齐：
   - `RuntimeTraceEvent` 新增 `details` 字段，承载动作级上下文（参数摘要、下一跳、postDelay、actionResult 状态/错误码）
   - 导出的运行报告 JSON 会原样携带 `traceEvents.details`，后续排查可直接还原“每一步发生了什么、为何跳到下一步”
11. 首页状态层拆分（阶段一）：
   - 新增 `MainUiState + MainViewModel`，把主题、权限、任务运行、运行报告历史等状态从 `MainActivity` 剥离
   - `MainActivity` 收敛为“Compose 容器 + 系统跳转入口”，避免业务状态继续堆叠在 Activity 生命周期内
   - 该拆分为后续 `TaskControlPanelGlobalOverlay` 进一步组件化与状态抽离提供统一模式
12. 浮窗编辑器组件化（阶段二-子模块）：
   - 新增 `TaskControlPanelGraphPreview.kt`，承载流程预览与动作列表连线绘制能力（`FlowPreviewPanel/JumpConnectionCanvas` 及其 lane 布局算法）
   - `TaskControlPanelGlobalOverlay` 保留路由与业务状态，图形渲染逻辑解耦为独立组件，降低单文件复杂度与后续改动冲突概率
   - 连线参数继续由 overlay 传入（例如最大可见 lane），确保拆分后视觉行为保持一致
13. 浮窗编辑器组件化（阶段二-编辑页）：
   - 新增 `TaskControlPanelEditorPages.kt`，承载 `ActionList/NodeEditor` 的主要 Compose UI 结构与编辑辅助函数
   - `TaskControlPanelGlobalOverlay` 中对应页面函数降级为“状态/路由桥接层”，仅绑定回调，不再承载大段 UI 细节
   - 通过“桥接层 + 组件层”分工，为下一步状态容器化（菜单态/草稿态/删除确认态）做准备
14. 编辑页状态容器化（阶段二-首轮）：
   - 在 `TaskControlPanelEditorPages.kt` 增加 `ActionListUiState` 与 `JumpTargetPickerUiState`，集中管理动作列表和 Jump 选择交互状态
   - 通过 `remember...UiState(key)` 机制将状态生命周期与 `task/flow/node` 路由键对齐，减少分散 `mutableStateOf` 带来的状态漂移风险
   - 下一步继续下沉参数草稿态（输入框草稿、坐标草稿）并补充单元测试/快照测试
15. 编辑页状态容器化（阶段二-草稿态）：
   - 新增 `NodeEditorDraftUiState`，统一管理 `CLICK` 坐标草稿与参数输入草稿，减少节点编辑页字段内重复 `remember` 逻辑
   - 草稿通过 `LaunchedEffect(nodeId + rawValue)` 与模型同步，保持“草稿可暂存无效值、模型只接收校验通过值”的写回约束
   - 参数 key 生命周期增加清理机制（`retainParamDraftKeys`），避免切换节点/参数集合时旧草稿泄漏
16. 编辑状态模型可测试化（阶段二-测试补齐）：
   - 新增 `TaskControlPanelEditorUiState.kt`，将编辑状态模型与页面组合层解耦
   - 新增 `TaskControlPanelEditorUiStateTest`，验证 `NodeEditorDraftUiState` 的草稿更新与清理规则
   - 目标是先稳定“状态层正确性”，后续再补视图层回归测试，降低 UI 重构风险
17. 参数编辑规则可测试化（阶段二-视图规则）：
   - 新增 `TaskControlPanelEditorParamSections.kt`，承载参数过滤与分组规则（例如 `CLICK` 隐藏 `x/y`，`JUMP` 隐藏 `targetFlowId/targetNodeId`）
   - 新增 `TaskControlPanelEditorParamSectionsTest`，覆盖过滤与分组映射的关键路径，避免页面重构时规则回归
   - 页面层改为调用规则函数，形成“规则层 + 组合层”分工
18. 编辑交互规则可测试化（阶段二-交互规则）：
   - 新增 `TaskControlPanelEditorInteractionLogic.kt`，沉淀跳转目标默认解析与坐标输入边界换算规则
   - 新增 `TaskControlPanelEditorInteractionLogicTest`，覆盖“跳转联动默认值”和“像素输入边界钳制”回归
   - 页面层改为复用交互规则函数，进一步减少 UI 组合层中隐式业务判断
19. 动作新增预设可测试化（阶段二-菜单逻辑）：
   - 新增 `TaskControlPanelActionPresets.kt`，把“添加动作”菜单行为统一建模为 `AddActionPreset` 与应用函数
   - 新增 `TaskControlPanelActionPresetsTest`，验证五类动作新增后的节点类型与动作类型一致性
   - 页面层改为按预设遍历渲染菜单，减少重复分支与文案/行为不一致风险
20. 任务列表页组件化补齐（阶段二-TaskList）：
   - `SettingsTaskListPage` 的 UI 迁移为 `TaskControlSettingsTaskListPage`
   - `TaskControlPanelGlobalOverlay` 保留任务 CRUD 的异步调度方法，通过回调注入 UI 组件
   - 形成 `TaskList/ActionList/NodeEditor` 三页统一的“组件层 + 路由桥接层”结构
21. 顺序主链兼容修复（阶段二-运行稳定性）：
   - 编辑器 `TaskGraphEditorStore` 的顺序 `ALWAYS` 链重建改为 `START + 非 START/END + END`，避免 `END` 位于中间时提前终止主链
   - 运行时 `FlowRuntimeEngine` 增加“仅在 `END` 后仍有可执行节点时”的执行前兜底规范化，修复存量异常任务的“只执行首动作”问题
   - 录制追加动作时改为插入到 `END` 前，避免继续生成 `END` 后挂动作节点的结构性隐患
22. 操作面板运行态分层（阶段二-执行可观测）：
   - `PanelMode` 新增 `RUNNING`，在浮窗内区分“运行中”与“待机态”交互
   - 新增 `OverlayRuntimeTraceCollector`，将运行时 trace 事件实时映射到面板状态（当前节点、步数、错误码、状态文案）
   - 运行态核心字段收敛到 `RunningPanelState`，统一处理初始化、trace/result 更新、暂停切换，降低 `TaskControlPanelGlobalOverlay` 状态分散度
   - `RUNNING` 面板控制收敛为“停止 / 暂停(继续) / 最小化”，移除运行态卡片中的历史入口，降低运行中误跳转概率
   - `RUNNING_MINI` 与 FULL 控制语义对齐：最小化状态可直接暂停/继续、停止，并提供恢复到 FULL 的快捷入口
   - 运行引擎新增暂停轮询能力（节点边界 + `postDelayMs` 阶段），暂停后保持当前执行上下文，恢复后继续线性执行
   - 用户控制事件（暂停/继续）写入当前运行会话事件流（`uiEvent=panel_control`），用于执行历史排查
   - 运行结束后自动回落 `NORMAL`，并保留最终执行摘要写入状态栏文本
23. 运行态布局稳定化（阶段二-体验优化）：
   - `RUNNING` 面板改为固定高度 + 固定信息槽位，避免字段动态出现/消失触发布局抖动
   - 运行态文本统一单行省略，控制长任务名与长状态文案导致的换行闪烁
24. 本次执行历史页（阶段二-可排查）：
   - 浮窗设置路由新增 `RunHistory`，支持从任务设置页进入“本次执行历史”
   - 页面展示执行摘要（traceId/status/step/时间）与逐步事件列表（phase、flow/node、message、details）
   - 运行会话引入内存态缓存，确保执行结束后仍可在当前浮窗会话内查看本次完整轨迹
   - 会话缓存模型收敛为 `CurrentRunSessionState`（`begin/reset/updateFromTrace/appendControlEvent/finalize`），减少 overlay 内分散字段并提升可测试性
   - 历史页快照组装改为 `CurrentRunSessionState.toHistorySnapshot(...)` 统一输出，Overlay 仅保留路由与渲染调度职责
25. 调试历史入口统一（阶段二-任务菜单化）：
   - 浮窗设置路由新增 `ReportHistory`，在“任务设置”菜单增加常驻【历史记录】入口，统一承接历史查看
   - 历史记录页展示 `RuntimeRunReportRepository` 的摘要列表，支持详情查看与删除
   - 任务执行完成写入报告后立即刷新该列表，保证“每次执行都可在任务菜单历史记录中看到”
   - 历史记录页支持关键字搜索与状态筛选（全部/完成/失败/停止），并展示“显示数/筛选数/总数”计数，提升排查效率
   - 历史记录页支持本地分页“加载更多”（每次 20 条）与筛选空态提示，避免长列表信息密度过高
   - 搜索/筛选/分页逻辑已抽离为可测试纯函数，并由单测覆盖核心过滤与分页规则，降低 UI 重构回归风险
   - 任务级历史视图提供【查看全部】快捷切换，可直接从任务过滤态回到全量历史列表
26. 任务级历史视图（阶段二-菜单精细化）：
   - `TaskLibraryPanel` 每个任务卡片的“更多”菜单新增【历史记录】操作，入口语义从“全局历史”细化为“该任务历史”
   - 浮窗历史列表支持按 `taskId` 过滤（仅显示当前任务），并在页面内展示“仅显示任务”提示
   - 历史仓库查询支持可选 `taskId` 过滤参数，返回“该任务最近 N 条”摘要，供浮窗与后续调试页复用
27. 历史详情组件化（阶段二-调试可读性）：
   - 历史列表页与历史详情页拆分为独立组件（`TaskControlRuntimeReportHistoryPage` / `TaskControlRuntimeReportDetailPage`）
   - 浮窗路由新增 `ReportHistoryDetail(reportId)`，支持从列表打开详情并返回列表
   - 历史仓库增加 `findDetailByReportId` 结构化解析能力，详情页直接消费模型而非自行解析 JSON
28. 历史排查交互与稳定性（阶段二-体验收敛）：
   - 历史详情页新增“上一条/下一条”切换，按当前筛选结果线性浏览记录
   - 移除历史页面中的“复制 JSON”操作，保留“详情 + 删除”最小动作集
   - 共享弹窗脚手架在无限高度约束下禁用 `verticalScroll`，修复历史删除后偶发滚动测量崩溃
29. Overlay 弹层约束统一（阶段二-稳定性）：
   - 浮窗场景（`TYPE_ACCESSIBILITY_OVERLAY`）禁止使用会创建独立应用窗口 token 的 `Dialog/AlertDialog`
   - 任务库与历史页的确认交互统一改为“同层卡片确认”，由 overlay 容器承载
   - 该约束作为后续浮窗页面新增交互的默认规则，避免 `BadTokenException` 回归
30. 历史删除确认重构（阶段二-交互一致性）：
   - 历史列表删除确认改为 overlay 顶层“居中模态卡片 + 遮罩”，不再作为列表内联节点显示
   - 删除确认状态提升到 `TaskControlPanelGlobalOverlay`，保证在滚动内容区域内仍保持固定模态层表现
   - 保持无系统窗口依赖，兼容 `TYPE_ACCESSIBILITY_OVERLAY` 运行环境
31. 浮窗模态层统一（阶段二-弹层规范）：
   - 模态确认弹层统一在 `SettingsOverlayContent` 顶层渲染，覆盖整个 addView 区域
   - 接入统一的 fade/slide 动画参数，保持与录制保存/开始确认弹层一致的动效体验
   - 继续避免 `Dialog/AlertDialog`，防止 overlay 场景 token 不匹配导致的窗口异常
32. 浮窗弹层宿主抽象（阶段二-可扩展）：
   - 新增 `TaskControlPanelModalHost` 作为统一模态渲染入口，收敛 `title/message/tone/actions` 配置模型
   - “开始任务确认 / 历史删除确认 / 成功失败提示”共享同一状态容器（`SettingsModal`）与关闭策略，避免分散状态机导致动效和层级不一致
   - 模态关闭后仅在过渡状态 idle 时移除 overlay，确保 scrim 与卡片退出动画完整播放，减少“背景突变消失”的割裂感
   - `ModalHost` 在退出动画阶段保留最后一次 `model`，防止过渡中途因 `model` 置空而 uncompose，避免出现“看不见的遮罩层残留”
   - 所有 modal 关闭入口（按钮/遮罩/异常回收）统一传递 `removeOverlayWhenIdle`，保证无遮罩页面可及时回收 `settingsOverlayView`
   - 对确认类动作引入短延迟（`MODAL_EXIT_DELAY_MS`）在退出动画后执行业务副作用，减少“动画被业务刷新抢断”的视觉不连续
   - scrim 动效去重：确认类 modal 只使用 `ModalHost` 的 scrim 过渡，`SettingsOverlayContent` 的基础 scrim 不再在 modal 场景叠加，避免双曲线叠加造成的背景闪停
33. 浮层可见性原因驱动（阶段二-交互稳定）：
   - 新增 `PanelDisplayMode`（`FULL/MINI`）与 `PanelHideReason` 集合（`SETTINGS_OPEN/RECORDING_INTERACTION/RUNNING_TEMP`），主面板可见性改为统一规则计算
   - 设置页子路由支持“顶部右侧最小化”并保留路由现场，恢复后回到原页面；根页保留“关闭”语义，不开放直接最小化
   - 录制回放期间改为写入 `PanelHideReason.RECORDING_INTERACTION`，替代直接改 `View` 可见性，减少多场景（设置/录制/运行）互相覆盖导致的状态冲突
   - 最小化实现调整为“保留 settings overlay 实例 + 关闭触摸 + 设为不可见”，避免 overlay 移除时重置路由状态，保证恢复仍停留在原子页（如编辑动作）
   - 可见性追踪：引入结构化 `panel_trace`（事件 + 原因 + 当前模式/隐藏原因/附着状态），并覆盖“模式切换、隐藏原因变化、待移除标记、overlay 实际移除、modal 显隐/动作”，用于问题复盘
   - 运行中最小化（`RUNNING_MINI`）由 `RUNNING_TEMP` 原因单独驱动：运行态支持“最小化运行面板”，MINI 保留“运行中”动效与恢复/停止操作；任务结束时自动清理 `RUNNING_TEMP` 并恢复 FULL，防止最小化状态残留
34. 弹窗导航布局规范（阶段二-单手操作）：
   - `SharedOverlayDialogScaffold` 的导航动作下沉到底部左侧（根页=关闭，子页=返回），顶部保留标题与可选最小化按钮
   - 子页不再提供关闭动作，必须先回退到根页再关闭，降低误关闭概率并统一层级语义
35. 设置路由决策下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelSettingsNavigation.kt`，承载 `SettingsRoute` 及“标题映射/返回路径/遮罩返回路径”纯函数，避免路由规则散落在多个 Composable 与事件入口
   - `TaskControlPanelGlobalOverlay` 新增 `showSettingsOverlayRoute(route, reason)` 统一设置层展示初始化（可见性、动画、隐藏原因、overlay 挂载）
   - `SettingsActionListLayer` 与 `onAuxOverlayBackdropTap` 改为委托导航函数，Overlay 从“路由规则实现者”收敛为“路由状态调度者”
36. 设置模态决策下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelSettingsModal.kt`，承载 `SettingsModal` 模型与 `resolveSettingsModalAction()` 纯决策函数（`Dismiss/StartTask/DeleteRuntimeReport`）
   - `TaskControlPanelGlobalOverlay.onSettingsModalAction` 改为统一“动作解析 + modal 关闭 + 延迟副作用调度”流程，减少重复分支并保持动效先于业务执行
   - `buildSettingsModalModel(...)` 同步下沉至 modal 模块，统一确认/反馈弹窗的 UI 模型映射逻辑
   - `ConfirmStartTask` 明确禁用背景点击关闭（`dismissOnBackdropTap=false`），开始任务确认必须通过按钮显式选择
   - 对应单测 `TaskControlPanelSettingsModalTest` 覆盖“确认开始/确认删除/反馈弹窗默认关闭/未知动作忽略”，保障后续 modal 增量扩展
37. 设置页数据装配下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelReportHistoryPresentation.kt`，承载历史页作用域标签、详情当前项解析、翻页状态与相邻记录定位纯函数
   - `SettingsReportHistoryPage/SettingsReportHistoryDetailPage` 与详情前后翻页逻辑统一委托 presentation 层，Overlay 不再内联拼装展示数据
   - 对应单测 `TaskControlPanelReportHistoryPresentationTest` 覆盖作用域文案、当前项解析与翻页边界，降低历史页后续迭代回归风险
38. 设置 overlay 生命周期判定下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelSettingsOverlayLifecycle.kt`，统一“设置 overlay 是否需要渲染、是否可空闲移除、阻塞签名”的判定函数
   - `SettingsOverlayContent` 与 `removeSettingsOverlayIfIdle` 共享同一判定口径，避免隐藏/移除条件在不同函数中漂移
   - 对应单测 `TaskControlPanelSettingsOverlayLifecycleTest` 覆盖渲染条件、可移除条件与阻塞签名输出
39. 设置 overlay 生命周期调度收敛（阶段二-状态拆分）：
   - `TaskControlPanelGlobalOverlay` 新增 `startSettingsSheetEnterAnimation()` 与 `animateSettingsOverlayDismiss(...)`，统一设置层进入/关闭动画调度
   - `closeSettingsPanel`、`minimizeSettingsOverlay`、`restoreSettingsOverlayFromMini` 与 `showSettingsOverlayRoute` 复用上述调度函数，减少重复延迟逻辑和状态重置分支
   - 保持“动画完成后再执行副作用”的时序约束，降低不同关闭入口造成的状态漂移
40. 录制保存后的主路径收敛（阶段二-交互优化）：
   - 录制保存成功后复用现有“开始任务确认”弹窗，不再追加额外编辑入口
   - 触发时机对齐为“录制保存弹窗退出后再弹确认”，保证弹层过渡连续性
41. 设置 overlay UI 状态机接入（阶段二-状态拆分）：
   - 新增 `TaskControlPanelSettingsOverlayUiStateMachine.kt`，统一管理 `visible/sheetVisible/dismissAnimating` 三元状态迁移
   - `TaskControlPanelGlobalOverlay` 增加 `dispatchSettingsOverlayUiEvent(...)` 作为状态机入口，关键显示/关闭链路改为事件驱动
   - 对应单测 `TaskControlPanelSettingsOverlayUiStateMachineTest` 覆盖 show/show-sheet/dismiss 主迁移路径
42. 主面板可见性状态机接入（阶段二-状态拆分）：
   - 新增 `TaskControlPanelPanelVisibilityStateMachine.kt`，统一管理 `PanelDisplayMode` 与 `PanelHideReason` 的组合状态
   - `TaskControlPanelGlobalOverlay` 将 `panelDisplayMode + hideReason map` 收敛为单一 `panelVisibilityState`，并通过 reducer 统一处理 display/hide/clear 事件
   - 对应单测 `TaskControlPanelPanelVisibilityStateMachineTest` 覆盖 display mode 切换、hide reason 开关与 clear 重置
43. 主面板展示判定下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelPanelVisibilityPresentation.kt`，承载 FULL/MINI 展示条件与 RUNNING_MINI 判定纯函数
   - `TaskControlPanelGlobalOverlay.OverlayContent` 改为读取 presentation 输出，减少 UI 组合层直接拼装可见性条件
   - 对应单测 `TaskControlPanelPanelVisibilityPresentationTest` 覆盖 FULL 展示、RUNNING_MINI 展示与阻断场景
44. 主面板事件分发入口统一（阶段二-状态拆分）：
   - `TaskControlPanelGlobalOverlay` 增加 `dispatchPanelVisibilityEvent(...)`，作为 `PanelVisibilityState` 唯一写入口
   - display mode / hide reason / clear hide reasons 均通过 reducer 分发，降低多处重复 next-state 比较逻辑
45. 主面板布局映射下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelPanelMode.kt` 与 `TaskControlPanelPanelLayout.kt`，收敛 `PanelMode` 类型和卡片宽度映射规则
   - `OverlayContent` 的宽度计算改为 `resolvePanelCardWidthDp(...)` 纯函数调用，降低 UI 组合层分支密度
   - 对应单测 `TaskControlPanelPanelLayoutTest` 覆盖 FULL/MINI 宽度映射
46. Overlay 重复清理逻辑收敛（阶段二-状态拆分）：
   - `TaskControlPanelGlobalOverlay` 新增 `resetRecordingSaveDialogState()` 与 `resetClickPickerState()`，统一录制保存弹层与选点状态重置
   - 初始化与 overlay 移除链路改为复用重置函数，减少多处重复字段赋值
47. 设置层入口阻断规则收敛（阶段二-状态拆分）：
   - `TaskControlPanelGlobalOverlay` 新增 `isSettingsOverlayEntryBlocked()`，统一“设置/历史/本次执行历史”入口的阻断条件
   - 避免不同入口各自维护阻断表达式，降低后续交互改动出现条件漂移的风险
48. 延迟调度规则收敛（阶段二-状态拆分）：
   - `TaskControlPanelGlobalOverlay` 将设置层进入/退出、面板关闭与 modal action 的延迟调度统一收敛到 `launchAfterDelay(...)`
   - 统一定义 `SETTINGS_SHEET_ENTER_DELAY_MS/SETTINGS_DISMISS_DELAY_MS/PANEL_ENTRY_DELAY_MS/PANEL_DISMISS_DELAY_MS`，替代分散硬编码时序常量，便于后续调参与回归定位
49. 副作用调度器抽离（阶段二-状态拆分）：
   - 新增 `TaskControlPanelEffectScheduler.kt`，以 `KeyedEffectScheduler` + `TaskControlPanelEffectKey` 统一承接延迟副作用调度，并支持同 key 新任务覆盖旧任务
   - `TaskControlPanelGlobalOverlay` 接入 keyed 调度后，设置层动画、面板关闭、modal 动作与录制保存后确认的延迟链路不再直接散落 `scope.launch + delay`
   - 在 `hide/removeOverlay` 统一 `cancelAll()`，减少 overlay 生命周期结束后延迟回调命中已销毁 UI 状态的风险
50. WindowManager 操作执行器抽离（阶段二-状态拆分）：
   - 新增 `TaskControlPanelWindowOpsExecutor.kt`，统一承接 `add/remove/update/restack` 的异常兜底与日志分级，降低 `GlobalOverlay` 对 WindowManager 细节的直接耦合
   - `TaskControlPanelGlobalOverlay` 接入 `windowOps` 后，设置层交互切换、overlay attach/detach、拖动布局更新、录制层 add/remove/restack 与 capture touch 更新均走统一执行器
   - 执行器采用泛型签名，单测可使用假对象验证错误短路与日志回调，避免依赖 Android 运行时
51. 运行执行 UI 映射下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelRunExecutionPresentation.kt`，将运行前提示文案、执行摘要文案与停止/失败终态映射沉淀为纯函数
   - `TaskControlPanelGlobalOverlay.startLastTask()` 改为消费 presentation 模型，流程层仅保留“任务加载 -> 执行 -> 持久化 -> 状态写回”编排逻辑
   - 对应单测 `TaskControlPanelRunExecutionPresentationTest` 覆盖 summary 模板与终态映射回退，确保后续流程重构不破坏用户可见文案
52. 运行持久化 payload 下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelRunExecutionPersistence.kt`，将运行完成后的 `summary/report` 构造统一收敛为 `buildRunExecutionPersistencePayload(...)`
   - `TaskControlPanelGlobalOverlay.startLastTask()` 改为消费 payload，流程层不再内联 `RuntimeRunReport.fromExecution(...)` 参数拼装
   - 对应单测 `TaskControlPanelRunExecutionPersistenceTest` 覆盖 source/task/status/step/duration 映射，降低后续运行链路拆分回归风险
53. 运行引擎配置下沉（阶段二-状态拆分）：
   - 新增 `TaskControlPanelRunExecutionConfig.kt`，将运行引擎参数组装收敛为 `buildRunRuntimeEngineOptions(...)`，统一 `maxSteps/pausePoll/dryRun/stopOnValidationError` 默认值
   - `TaskControlPanelGlobalOverlay.startLastTask()` 改为调用配置构造函数，流程层减少硬编码 options 细节
   - 对应单测 `TaskControlPanelRunExecutionConfigTest` 覆盖配置默认值和 pause 回调透传，保障后续策略调参回归
54. 运行结果写入可观测性增强（阶段二-稳定性）：
   - `TaskControlPanelGlobalOverlay.startLastTask()` 对报告写入与任务运行摘要写入增加失败日志，避免存储异常静默
   - 新增 `buildRunPostPersistStatusText(...)`，将“部分记录写入失败”提示纳入状态文案拼装，提升用户侧问题发现效率
   - 对应测试扩展到 `TaskControlPanelRunExecutionPersistenceTest`，覆盖写入成功/失败状态文案分支
55. 运行作业生命周期修复（阶段二-稳定性）：
   - `TaskControlPanelGlobalOverlay` 新增 `cancelRunTaskJob(...)`，在 `hide/removeOverlay` 链路主动取消运行中的作业
   - 修复浮层销毁后运行任务仍继续执行的风险，保证“浮层生命周期”与“运行作业生命周期”一致收口
   - 增加取消来源日志，便于定位是 `hide` 还是 `removeOverlay` 触发终止
56. 设置层动画竞态修复（阶段二-稳定性）：
   - `TaskControlPanelSettingsOverlayUiStateMachine` 调整 `SHOW_SHEET` 迁移规则：dismiss 动画进行中不再接受进入事件
   - 规避“快速开关设置层”时延迟事件反向覆盖退出状态导致的页面闪动/错位
   - 对应测试 `TaskControlPanelSettingsOverlayUiStateMachineTest` 增加竞态分支覆盖
57. 运行态高频刷新节流（阶段二-稳定性）：
   - `TaskControlPanelGlobalOverlay` 为 trace 回调引入 `touchUiForRunningTrace()` 节流（80ms），避免每条 trace 事件触发 UI 刷新
   - 通过 `resetRunningTraceUiThrottle()` 在运行态开启、结束、取消时统一重置节流状态，防止延迟刷新跨会话串扰
   - 目标是降低运行态面板的刷新抖动与低端机掉帧概率，同时保持关键状态可见性
58. 多指轨迹超限可观测性增强（阶段二-稳定性）：
   - `AccessibilityGestureExecutor.performRecordStrokes(...)` 增加超限裁剪 warning 与有效输入计数日志，提升问题排查可观测性
   - `TaskControlPanelGlobalOverlay` 在录制回放链路增加“超限裁剪中/已裁剪”状态提示，避免用户误判为随机丢动作
   - 对应测试 `TaskControlPanelRecordingReplayPresentationTest` 覆盖裁剪计数与文案分支
59. 高采样回放降级保护（阶段二-稳定性）：
   - 新增 `AccessibilityGestureTimingPolicy.kt`，为 timed 分段派发增加点数预算限制（单 stroke / multi 总量）
   - 当采样密度超预算时自动降级到非 timed 派发，优先保证稳定执行与低端机可用性
   - 对应测试 `AccessibilityGestureTimingPolicyTest` 覆盖预算判定分支；执行器日志同步输出降级原因，便于线上排障
60. 运行态节流尾部竞态收口（阶段二-稳定性）：
   - `TaskControlPanelGlobalOverlay.resetRunningTraceUiThrottle()` 增加对 `RUNNING_TRACE_UI_FLUSH` 的显式取消
   - 防止上一会话延迟刷新任务在下一会话触发，保证节流状态与运行会话边界一致
