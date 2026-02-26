# UI 主题系统说明（黑白极简）

更新时间：2026-02-26

## 1. 设计目标

1. 风格接近 shadcn 的黑白极简方向。
2. 主题统一管理，页面不直接写死颜色。
3. 支持快速切换主题模式，便于后续继续扩展风格。

## 2. 文件结构

1. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/AppThemeMode.kt`
2. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/CmmClickerTheme.kt`
3. `app/src/main/java/com/ksxkq/cmm_clicker/ui/theme/ThemePreferenceStore.kt`
4. `app/src/main/java/com/ksxkq/cmm_clicker/ui/MainActivity.kt`

## 3. 主题模式

1. `MONO_LIGHT`
2. `MONO_DARK`

切换方式：`AppThemeMode.next()` 循环切换，并通过 `ThemePreferenceStore` 持久化。

## 4. 统一令牌

在 `CmmClickerTheme` 中集中维护：

1. `ColorScheme`：背景、文本、边框、容器色。
2. `Typography`：标题、正文、辅助文本。
3. `Shapes`：统一圆角规范。

页面组件只使用 `MaterialTheme.colorScheme / typography / shapes`，不直接引用固定色值。

## 5. 页面约束

1. 卡片统一 1dp 边框、0 阴影。
2. 主要操作统一按钮样式（默认 `OutlinedButton`，主动作 `Button`）。
3. 状态信息优先用文本语义表达（如 `[OK]` / `[OFF]`），不依赖彩色提示。
4. `Switch` 显式配置 `checked/unchecked` 颜色，保证关闭状态在浅色和深色主题下都可见。

## 6. 后续扩展建议

1. 主题模式已持久化到本地（DataStore）。
2. 增加更多风格包时，仅新增 `AppThemeMode` 与主题令牌，不改业务页。
3. 将 `SectionCard/StatusLine/ActionButton/SwitchRow` 下沉到 `ui/components` 复用。
