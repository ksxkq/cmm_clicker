# liteclicker 任务动作理解文档（迁移到 Kotlin + Compose 前对齐）

## 1. 我对 liteclicker 的整体理解

`liteclicker` 本质是一个「任务编排 + 动作执行引擎」：

- Flutter 侧负责任务编排、循环/跳转/子任务/变量/历史记录/结果判定。
- Android Native 侧（Accessibility + Screenshot + FunctionManager）负责多数系统动作与识别动作落地。
- 两侧通过 `performAction`（MethodChannel）交互，返回 `FlutterEventInfo` 或 `bool`。

主执行链路：

1. `TaskManager.startTask` 初始化上下文、校验、任务状态。
2. `_doActionList` 循环动作，处理 `jump/folder/subTask/break` 传播。
3. `_doAction` 执行动作 + 成功/失败后处理 + 重试/延迟。
4. `_getDoActionResult` 根据动作类型分发到 Flutter 或 Native。
5. 写入 `ActionHistoryInfo` / `TaskHistoryInfo`，更新运行面板状态。

关键入口：

- `lib/managers/task_manager.dart`
- `lib/managers/task/task_action_executor.dart`
- `android/app/src/main/java/com/ksxkq/common/ks_common/manager/FunctionManager.java`

## 2. 动作执行语义（跨类型通用）

### 2.1 动作执行域与执行器

- 当前所有动作都声明为 `mainOnly`（主引擎执行）。
- 执行器分两类：
  - `flutter`：逻辑/流程/容器类动作。
  - `native`：手势/系统能力/识别类动作。

参考：

- `lib/managers/task/task_action_runtime_semantic.dart`
- `lib/managers/task/task_action_semantic.dart`

### 2.2 识别/检查动作成功失败链

- `recognize/check` 成功后可继续执行后置链（`click -> folder -> jump`）。
- 失败后可重试（含重试前 folder），最终可走失败链（folder/jump）。
- 图片识别相似度为 `0` 时会触发“重启截图服务 + 重新授权”额外流程。

参考：

- `lib/managers/task_manager.dart`（`_doAction`, `_doRetryResult`, `_doRecognizeSuccess`）
- `lib/managers/task/task_recognize_success_order.dart`

### 2.3 跳转 / 文件夹 / 子任务传播

- `jump` 动作本身只产出 `DoActionResult.jump`，真正跳转在外层循环处理。
- `folder` 通过 `folderInfo.key` 与子动作 `folderKey` 关联。
- `subTask` 在独立 loop 里执行，可被 `stopTask(type=subTask)` 中断。
- 现状保留了一个历史语义：外层跳转时 `preIndex == 0` 不视为 outer jump。

参考：

- `lib/managers/task/task_jump_resolver.dart`
- `lib/managers/task/task_execution_loop_runner.dart`
- `lib/managers/task/task_jump_dispatch_decider.dart`
- `lib/managers/task/task_loop_break_decider.dart`
- `lib/managers/task/task_break_subtask_decider.dart`

## 3. 39 种动作类型对齐（我当前理解）

说明：`执行器` 指实际执行入口；`实现` 是核心行为摘要。

| Type | 执行器 | 实现（当前代码事实） |
|---|---|---|
| `unknown` | native | 下发到 native 后走默认分支，基本等价 no-op success。 |
| `dupClick` | native | 连续点击（旧实现，线程循环 dispatchGesture）。 |
| `click` | native | 普通点击；识别成功场景可按识别结果 rect/button/color 二次点击。 |
| `swipe` | native | 单指滑动手势（支持随机偏移、轨迹缓存、必要时隐藏浮窗）。 |
| `record` | native | 录制手势回放（单/多指，支持时间戳段落与停顿）。 |
| `back` | native | 全局返回。 |
| `home` | native | 全局 Home。 |
| `recent` | native | 全局最近任务。 |
| `folder` | flutter | 容器动作；`actionCount != 0` 才 success，后续进入 `_doFolderActionInfo`。 |
| `jump` | flutter | 仅构造 jump 结果；目标解析与 index 应用在 loop 层。 |
| `launchApp` | native+flutter后处理 | native 先拉起应用；Flutter 再做前台包名复核、重试、双开处理。 |
| `imageRecognize` | native（执行）+flutter（后处理） | 截图后图像匹配（本地/可选网络）；返回识别结果列表。 |
| `buttonRecognize` | native（执行）+flutter（后处理） | 遍历/系统 API 查控件，按 text/desc/id 匹配并返回识别结果。 |
| `colorRecognize` | native（执行）+flutter（后处理） | 截图后按颜色点位比对，返回识别结果。 |
| `setTxtToInput` | native | 找输入框并填值（剪贴板/预设/弹窗输入）。 |
| `requestInput` | native + flutter后处理 | 弹输入框拿文本，Flutter 成功后再尝试写入当前焦点输入框。 |
| `pauseTask` | flutter | 弹“暂停任务”对话框；主要是流程控制，不是业务判断动作。 |
| `randomWait` | flutter主导 + native空执行 | 先随机改写 `actionInfo.delay`，后续走统一 delay 机制实现等待。 |
| `copyText` | native + flutter补充 | 复制到剪贴板，或将文本粘贴到输入框（支持按索引/识别区域）。 |
| `checkTime` | flutter | 判断当前时间是否命中设定周/时段。 |
| `netRequest` | flutter | 执行 HTTP 请求，按 success/errorInfo 产出结果。 |
| `alert` | native | 声音/震动/通知（可组合）。 |
| `subTask` | flutter | 先校验引用有效，再进入 `_doSubTask` 执行子任务动作列表。 |
| `lockScreen` | native | API 28+ 全局锁屏。 |
| `intentUri` | native | `Intent.parseUri` 拉起 URI。 |
| `screenshot` | flutter | 请求截图并保存历史图；可触发系统截图。 |
| `stopTask` | flutter | `task` 模式停整个任务；`subTask` 模式仅设置 `isBreakSubTask=true`。 |
| `areaClick` | native | 在矩形区域内批量/随机点击（多点手势分批执行）。 |
| `checkActivity` | flutter | 检查当前前台包名/类名是否匹配。 |
| `miniProgram` | flutter | 调 `WechatKitPlatform.launchMiniProgram` 启动小程序。 |
| `setVariable` | flutter | 单变量赋值。 |
| `checkVariable` | flutter | 变量比较判断（字符串/数字/布尔）。 |
| `operateVariable` | flutter | 按规则批量操作变量并输出结果说明。 |
| `setVariableChoiceDialog` | flutter | 弹窗让用户选动作，再把对应变量写入。 |
| `setVariableBatch` | flutter | 三模式：`auto/dialog/net`，用于批量设置变量。 |
| `checkBranch` | flutter | 依次执行候选“检查动作”，首个 success 即成功并沿用其结果。 |
| `closeCurrentUI` | flutter | 截图->上传->服务端识别关闭按钮->可执行点击；日上限 50 次。 |
| `createVariableValue` | flutter | 生成随机数并写入目标变量。 |
| `scroll` | native | 调辅助服务滚动（forward/backward）。 |

## 4. 我认为对迁移最关键的“行为约束”

1. `jump` 不是立即跳转，而是“返回结果给 loop 层”。
2. `folder/subTask` 都是容器语义，真正执行在后续分支（不是在动作本体内一次性做完）。
3. 识别动作是“native识别 + flutter编排成功/失败链 + retry”三段式。
4. `randomWait` 的本质是“改 delay + 统一延迟机制”，不是 native 里的独立等待实现。
5. `stopTask(subTask)` 依赖 `isBreakSubTask` 传播，不等于直接停整个任务。

## 5. 发现的兼容/历史点（迁移时要明确）

1. `TaskActionType.fromString` 未覆盖 `closeCurrentUI`，会回退到 `unknown`（当前行为已被冻结）。
2. 外层跳转 `index=0` 的处理是历史兼容语义，不是直觉语义。
3. `ActionInfoType.other` 实际用于“识别结果动作”集合（命名与语义不一致）。
4. `closeCurrentUI` 在输入表里标注“已废弃”，但代码仍完整可执行。

## 6. 请你确认（用于下一步 Kotlin 重构边界）

1. `closeCurrentUI` 在新项目中是要保留还是删除？
2. 外层跳转 `index=0` 的历史行为是否继续兼容？
3. `dupClick` 是否继续保留（当前已接近 legacy）？
4. `randomWait` 是否维持现状（delay 注入方式）还是改成显式动作实现？
5. 39 个动作里，第一阶段你希望优先迁移哪一组（建议：`click/swipe/record + recognize + folder/jump/subTask + set/check variable`）？

