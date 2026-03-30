# Execution Overlay OmniCalc-Style Redesign

- Date: 2026-03-27
- Status: approved
- Branch context: `codex/omnicalc-cirrus`

## Goal
在 `CalculatorCirrus/.worktrees/omnicalc-cirrus` 中推进执行器 UI 复刻，风格参考现有已完成的 OmniCalc 树图与里程碑风格；重点强化执行状态、进度、调速、错误观测；主体验入口改为独立临时 Screen，避免依赖工作台打开流程。

## Scope
- 重构执行器 Overlay 组件层（widget/render/controls/sandbox 入口）。
- 执行层做 UI 无关的增量改造，不做整层推翻。
- 激进清理 UI 侧过期实现与重复分支。
- 提供 `/cirrus exec_overlay` 入口用于直接打开临时 Screen。
- 保留工作台 Overlay 能力，但不作为主调试路径。

## Non-Goals
- 不进行执行核心 API 的全量重写。
- 不做与执行器无关的全局 UI 主题改造。
- 不使用 hotswap 工具。

## Constraints
- 在 `omnicalc-cirrus` worktree 上继续推进。
- 视觉语言优先复用 Cirrus 现有素材与已完成树图/里程碑风格。
- 布局空间对齐 OmniCalc web 版执行器结构比例（主画布优先，信息卡窄列，路径条底部固定）。
- 最终验证通过 moddev usage 流程导出渲染并迭代修正。

## Architecture

### 1) Execution Layer (UI-agnostic, incremental)
- 保留现有 `api/recipe/execution` 主干（`ExecutionSession` / `ExecutionRuntime` / `ExecutionTranslator`）。
- 增量补齐统一执行观测快照（例如 telemetry/snapshot 语义）：
  - session/status/progress
  - current/next/parent step context
  - warnings/errors
  - speed/auto advance state
- 执行层不依赖 UI 组件类型。

### 2) UI Projection Layer
- 由 `ui/execution` 投影层将执行快照转换为 Overlay 所需 ViewModel。
- Overlay 仅消费 ViewModel，不读取 runtime 细节。

### 3) Overlay Component Layer (aggressive refactor)
- 重点组件：
  - `ExecutionOverlayWidget`（主画布）
  - `ExecutionOverlayComponentRenderer`（共享渲染器）
  - `ExecutionOverlayControlsWidget`（控制条 + 连续调速）
  - `ExecutionOverlaySandboxScreen`（主体验入口）
- 风格锚点：现有 OmniCalc 树图 + 里程碑语义，避免引入割裂视觉语言。

### 4) Entry Layer
- 主入口：`/cirrus exec_overlay`。
- 开发态可提供低冲突快捷键兜底（不与现有快捷键冲突）。
- 工作台 Overlay 作为兼容与回归场景保留。

## Component & Interaction Design

### Layout
- 主体为树图执行画布。
- 右上角固定执行信息卡（宽度比例约 28%~31%，有 min/max 限制）。
- 底部固定路径条（Path Context）。
- 顶部/底部摘要区域展示状态、进度和告警。

### Execution Visualization
- 节点状态：`pending/running/paused/done/failed`。
- 当前执行分支高亮，非焦点分支弱化。
- 顶部展示 `completed/total + percent + status`。

### Speed & Controls
- 保留 `step/burst/auto/reset`。
- 新增连续调速滑条 `0~10 次/秒`，`0` 代表暂停。
- 展示紧凑速率读数（例如 `5.0/s`）。

### Error Observation
- 信息卡展示：
  - 当前执行
  - 下一步
  - 上一级
  - 当前目标路径
- 错误态展示短摘要与可展开详情（guidance）。
- Footer 保持 warning/paused/failed 的清晰视觉区分。

## Data Flow
1. 执行层 tick 推进并产出 UI 无关快照。
2. 投影层将快照映射为 Overlay ViewModel。
3. Overlay 组件渲染 ViewModel。
4. 用户交互通过 `ExecutionOverlayController` 回流：
   - `advanceSteps(n)`
   - `toggleAutoAdvance()`
   - `setRatePerSecond(value)`
   - `reset()`
5. 控制器更新执行状态，下一帧刷新 UI。

## Cleanup Strategy
- 激进删除 UI 侧已过期和重复的执行器分支。
- 删除死代码与未引用组件。
- 执行层仅清理明确废弃路径，避免无必要重写。

## Testing & Validation

### Unit/Contract
- `ExecutionOverlayWidgetContractTest`：布局比例、状态渲染、速度显示、错误展示。
- `ExecutionOverlaySandboxScreenContractTest`：临时 Screen 装配与共享控制器行为。
- 执行观测快照 contract：状态映射、进度一致性、错误字段正确性。

### Integration
- 临时 Screen 下验证：
  - step/burst/auto
  - 连续调速 0~10/s
  - 错误触发与恢复
  - 路径与焦点同步

### ModDev Usage Capture
- 使用 moddev usage 流程导出渲染（不使用 hotswap）。
- 固定窗口尺寸与场景脚本，导出：
  - 初始态
  - 运行态
  - 暂停态
  - 错误态
- 对照后迭代修正，直到可体验。

## Acceptance Criteria
- `/cirrus exec_overlay` 可直接打开临时 Screen。
- 执行状态/进度/调速/错误在单屏可读且稳定。
- 风格与现有树图/里程碑视觉语言一致。
- 过期执行器 UI 路径已清理，不再被入口引用。
