# cmm_clicker Agent Conventions

## Documentation-Driven Workflow

1. This repository is documentation-driven. Every substantial code change must have matching doc updates in the same work cycle.
2. `docs/cmm_clicker_execution_log.md` is the canonical progress log and must be updated with:
   - what was completed
   - what is in progress
   - what is planned next
3. Domain docs must be updated when relevant:
   - UI/Theme changes -> `docs/ui_theme_system.md`
   - Architecture/runtime/data model changes -> `docs/cmm_clicker_rebuild_blueprint_v1.md`
   - Legacy behavior analysis changes -> `docs/liteclicker_task_action_analysis.md`

## Update Timing

1. Update docs immediately after implementation and before final delivery.
2. If design direction changes, update docs first, then implement.
3. If implementation diverges from docs, docs must be corrected in the same turn.

## Definition of Done

1. Code compiles (`assembleDebug`) and tests pass (`testDebugUnitTest`) when applicable.
2. Required docs are updated and consistent with actual behavior.
3. Any known risks/limitations are explicitly recorded in `docs/cmm_clicker_execution_log.md`.
