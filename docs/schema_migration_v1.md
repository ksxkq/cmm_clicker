# Schema Migration v1（骨架）

更新时间：2026-02-26

## 1. 目标

1. 统一管理 `TaskBundle.schemaVersion`。
2. 提供可测试的迁移执行链，避免未来结构升级时“散点改逻辑”。
3. 为“旧备份导入”提供可复用的版本升级入口。

## 2. 当前实现

文件：

1. `app/src/main/java/com/ksxkq/cmm_clicker/core/model/BundleSchema.kt`
2. `app/src/main/java/com/ksxkq/cmm_clicker/core/migration/BundleMigrationEngine.kt`
3. `app/src/test/java/com/ksxkq/cmm_clicker/core/migration/BundleMigrationEngineTest.kt`

能力：

1. `BundleSchema.CURRENT_VERSION`：统一当前版本常量。
2. `BundleMigrationEngine`：按版本链执行迁移，输出状态和日志。
3. `BundleMigrationStatus`：
   - `UP_TO_DATE`
   - `MIGRATED`
   - `REJECTED_VERSION_TOO_NEW`
   - `FAILED_MISSING_STEP`
4. 默认迁移链入口：`BundleMigrations.default()`。
5. 示例迁移 step：`SchemaV0ToV1Migration`。

## 3. 当前规则

1. 当备份版本高于应用支持版本时，直接拒绝（`REJECTED_VERSION_TOO_NEW`）。
2. 当迁移链缺 step 时，直接失败（`FAILED_MISSING_STEP`），并返回已执行日志。
3. 迁移引擎会确保每个 step 后写入目标 `schemaVersion`。

## 4. 下一步

1. 增加 `v1 -> v2` 的真实迁移示例（伴随模型变更落地）。
2. 迁移日志落盘并接入导入报告。
3. 导入链接入：`LegacyBackup -> LegacyIR -> TaskBundle(v0/v1) -> migrateToLatest()`。
