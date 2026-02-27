# UI 主题系统说明（黑白极简）

更新时间：2026-02-27

## 1. 设计目标

1. 风格接近 shadcn 的黑白极简方向。
2. 主题统一管理，页面不直接写死颜色。
3. 支持快速切换主题模式，便于后续继续扩展风格。

## 2. 文件结构

1. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/AppThemeMode.kt`
2. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/AppThemeTokens.kt`
3. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/CmmClickerTheme.kt`
4. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/ThemePreferenceStore.kt`
5. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainActivity.kt`
6. `app/src/main/java/com/ksxkq/cmm_clicker/accessibility/TaskEditorGlobalOverlay.kt`
7. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainUiComponents.kt`
8. `app/src/main/res/values/themes.xml`

## 3. 主题模式

1. `MONO_LIGHT`
2. `MONO_DARK`

切换方式：`AppThemeMode.next()` 循环切换，并通过 `ThemePreferenceStore` 持久化。

## 4. 统一令牌

在 `AppThemeTokens` 中集中维护：

1. `ColorScheme`：背景、文本、边框、容器色。
2. `CmmClickerTheme` 将 `AppThemeTokens` 映射到 Compose `MaterialTheme`。
3. 浮窗 `TaskEditorGlobalOverlay` 也走 Compose 渲染，并与首页复用同一 `CmmClickerTheme`。
4. `Typography`：标题、正文、辅助文本。
5. `Shapes`：统一圆角规范。

页面组件只使用 `MaterialTheme.colorScheme / typography / shapes`，不直接引用固定色值。

全局浮窗（Accessibility Overlay）主题策略：

1. 从 `ThemePreferenceStore` 读取当前 `AppThemeMode`。
2. 浮窗内部使用 `ComposeView + CmmClickerTheme(themeMode)` 直接渲染 Compose 组件。
3. 首页与浮窗共享同一套 `AppThemeTokens`，按钮/文本/容器/连线视觉保持一致。
4. 浮窗按钮风格收敛到黑白体系：主动作实心、普通动作描边，避免出现与主题不一致的彩色按钮。

说明：WindowManager 负责把浮窗挂到系统窗口层，UI 层仍然可以全部使用 Compose；当前仅宿主是 View（`ComposeView`），视觉样式只维护一份 Compose 主题。

## 5. 页面约束

1. 卡片统一 1dp 边框、0 阴影。
2. 主要操作统一按钮样式（默认 `OutlinedButton`，主动作 `Button`）。
3. 状态信息优先用文本语义表达（如 `[OK]` / `[OFF]`），不依赖彩色提示。
4. `Switch` 显式配置 `checked/unchecked` 颜色，保证关闭状态在浅色和深色主题下都可见。
5. `SwitchRow` 使用垂直居中布局，保证文字与开关对齐。
6. 弹窗动效优先：浮窗默认使用背景渐暗 + 内容上滑的组合动效，后续交互沿用同一动画节奏。
7. 浮窗遮罩保持半透明并覆盖状态栏区域，避免出现不透明灰底或顶部漏光。
8. 弹窗支持点击遮罩关闭，并使用退出动效后再移除窗口，避免交互突兀。
9. 首页使用 `Scaffold` 管理安全区内边距，保证边到边模式下内容不被状态栏覆盖。
10. 首页导航采用底部导航栏（任务/控制台），导航组件与页面内容共享同一 `CmmClickerTheme` 令牌。
11. 任务页/控制台页拆分后仍复用同一套 `SectionCard/ActionButton/SwitchRow` 组件，避免样式分叉。
12. 首页底部导航已替换为自定义 iOS 风格浮动胶囊 Tab Bar（非 Material 默认样式），图标使用 Compose 自绘，仍遵循主题色令牌。
13. 菜单样式通过 `AppDropdownMenu` 统一（圆角、边框、低阴影、文本色），任务卡片/后续页面复用，避免 Material 默认菜单风格漂移。
14. 任务卡片操作图标已接入标准库 `material-icons-extended`（运行/更多），统一由 `CircleActionIconButton + Material Icon` 组合渲染。
15. 圆形 icon 按钮统一启用 `clip(CircleShape)`，保证触摸反馈区域与视觉形状一致。

## 6. 后续扩展建议

1. 主题模式已持久化到本地（DataStore）。
2. 增加更多风格包时，仅新增 `AppThemeMode` 与主题令牌，不改业务页。
3. 将 `SectionCard/StatusLine/ActionButton/SwitchRow` 下沉到 `ui/components` 复用。
