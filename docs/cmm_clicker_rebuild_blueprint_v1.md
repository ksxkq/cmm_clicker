# cmm_clicker 重写蓝图 v1

## 1. 目标定义

本次不是迁移，是“参考后重写”。

重写目标：

1. 做一个可持续扩展的自动化平台内核，而不是继续堆 if/else。
2. 先把流程语义跑通（jump/branch/folder/subTask），再扩动作数量。
3. 编辑器可用性优先，解决嵌套编辑痛点。
4. 为未来 AI 编排预留清晰的动作能力模型。
5. 支持旧备份导入到新结构，并具备可追踪升级机制。

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
8. `infra-storage`：Room/SqlDelight 持久化（建议 Room 起步）。
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

