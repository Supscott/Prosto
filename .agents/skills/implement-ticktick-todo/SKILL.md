---
name: implement-ticktick-todo
description: Orchestrates end-to-end task implementation from TickTick Kanban boards. Retrieves tasks from the 'todo' column, groups them, validates plans via a reviewer subagent, coordinates parallel subagents, runs QA on cumulative diffs, and transitions tasks through 'in progress' and 'test' columns.
---

# Implement TickTick Todo Orchestrator

You are the **Lead Orchestrator Agent**. Your mission is to pull actionable tasks from the user's TickTick Kanban board, group and plan their implementation, validate the plan with a reviewer subagent, coordinate implementation across parallel subagents, run QA over the resulting diff, and manage the task lifecycle in TickTick.

---

## 1. TickTick Kanban Discovery & Retrieval

1. **Locate Project:**
   - Call `list_projects` to find the target project ID (default to `hori` unless specified otherwise by the user).
2. **Resolve Column IDs:**
   - Call `list_columns(project_id)` to map column names to IDs:
     - `todo_column_id`: Column named `todo` (or case-insensitive equivalent).
     - `in_progress_column_id`: Column named `in progress`.
     - `test_column_id`: Column named `test`.
3. **Fetch & Filter Tasks:**
   - Call `get_project_with_undone_tasks(project_id)`.
   - Filter tasks in memory: keep only tasks where `task.columnId == todo_column_id`.
   - Extract title, description/content, tags, and checklist items.
   - If there are many tasks, select a cohesive batch (recommended: 3–6 related tasks) for the current run rather than overloading a single execution.

---

## 2. Analysis, Grouping & Conflict Detection

1. **Codebase Impact Mapping:**
   - Inspect files and architecture relevant to the selected tasks.
   - Group related or small tasks together so they can be assigned to a single subagent without redundant overhead.
2. **Dependency & Concurrency Analysis:**
   - **Disjoint Tasks:** Tasks modifying completely independent directories or files are marked for **Parallel Waves**.
   - **Coupled Tasks:** Tasks sharing common state, shared types, schemas, or critical core files must run in **Sequential Waves** (Wave 1 → Wave 2).

---

## 3. Plan Generation & Storage

Create an implementation plan and write it to `docs/plans/todo-<timestamp-or-topic>.md` (or update existing plan file in `docs/`):
- **Selected Tasks:** List of TickTick tasks with IDs and goals.
- **Grouping & Ownership:** Which subagent handles which tasks.
- **Affected Files:** Precise list of files to be created or modified per subagent.
- **Execution Waves:** Detailed sequence of parallel and sequential waves.
- **Definition of Done (DoD):**
  - Code changes complete.
  - Automated unit/integration tests written and passing.
  - For UI changes: visual verification using the `compose-preview` / `compose-ui-preview` skill.

---

## 4. Reviewer Subagent Gate (Max 2 Iterations)

1. **Invoke Reviewer:**
   - Spawn a reviewer subagent (`invoke_subagent` with `TypeName: "research"` or specialized role `Architecture Reviewer`).
   - Pass the contents of the generated plan file in `docs/`.
   - Reviewer checks for: missing edge cases, architectural violations, potential merge conflicts, missing test coverage, or unrealistic scope.
2. **Feedback Loop:**
   - If the reviewer suggests adjustments, update the plan in `docs/`.
   - **Safety Limit:** Maximum **2 rounds** of revision.
   - Once the reviewer returns `APPROVED` (or after 2 rounds), proceed to execution.

---

## 5. Move Tasks to "In Progress"

Before starting implementation:
- Update the status of the selected tasks in TickTick by setting their `columnId` to `in_progress_column_id` via `update_task` (or `batch_update_tasks`).
- (Optional) Post an initial comment (`add_comment`) referencing the plan file path.

---

## 6. Subagent Dispatch & Implementation

1. **Dispatch Waves:**
   - Spawn implementation subagents using `invoke_subagent`.
   - Use `Workspace: "share"` or assign dedicated branches via GitButler (`but`).
   - Pass explicit scope, target files, and DoD to each subagent.
2. **Subagent Execution Rules:**
   - Each subagent writes production code and tests for their assigned scope.
   - UI tasks must render and visually verify components via Compose preview tools.
   - Run local tests/linters to ensure no regressions.
3. **Wait for Subagent Completion:**
   - Collect completion reports and summaries from all subagents in the wave before starting dependent waves.

---

## 7. QA Review on Cumulative Diff (Direct Fix Pattern)

1. **Inspect Diff:**
   - Orchestrator (or a dedicated QA subagent) inspects the full cumulative diff across all changes (`but diff` or `git diff`).
2. **Direct Fixes (No Subagent Ping-Pong):**
   - Check for: formatting inconsistencies, leftover debug prints, typos, missing edge-case assertions.
   - **Crucial Rule:** The QA reviewer / Orchestrator **directly applies minor fixes and cleanup to the diff** instead of respawning or badgering previous subagents.

---

## 8. TickTick Transition & Completion

1. **Success Path:**
   - When tests pass and QA verifies the diff:
     - Update the completed tasks to `test_column_id` via `update_task` / `batch_update_tasks` for human sign-off.
     - Post a comment on the tasks or log a summary of changes, touched files, and test results.
2. **Failure / Blocker Fallback:**
   - If a task could not be completed or hit an unresolvable blocker:
     - Revert its `columnId` back to `todo_column_id`.
     - Call `add_comment` on that task explaining the exact reason, error log, or blocker.
3. **Final Report:**
   - Output a clean summary for the user detailing:
     - Tasks moved to `test`.
     - Any tasks reverted to `todo` with reasons.
     - Link to the plan in `docs/`.
     - Summary of changes made and automated verification results.
