# 0001. 三工具协作与仓库契约工作流

- 状态：已采纳（部分被 [0004](0004-workflow-v2.md) 取代）
- 日期：2026-10-19
- 决策人：项目维护者

> 本文记录决策与理由，不作为工作流正文；角色边界、状态机与交接步骤以 `AGENTS.md` §0 和 `docs/tasks/README.md` 为准。
>
> 2026-11-06：角色边界、状态机、报告归档与规模阈值已由 ADR 0004 调整；本文保留历史决策，冲突时以 ADR 0004 为准。

## 背景

项目同时使用三个 AI 工具：Claude 做基础架构设计，CodeBuddy 做基础功能开发，Codex 做代码评审、合规检查与独立自测试。

此前的 AI 规则体系（`AGENTS.md`、`.claude/rules/*`、`docs/adr/` 等）在提交 `db6d457` 中被整体删除。重新搭建时面临三个问题：

1. 三个工具之间没有自动通信机制，靠对话转述会丢失上下文、产生漂移。
2. 架构设计、功能实现、代码评审由不同工具执行，必须明确“谁能写哪些文件”，否则会互相覆盖。
3. 评审若只靠临时提示词，结论不稳定；必须把规则编号化、契约化，并尽量交给 CI 自动检查。

## 决策

采用“**仓库为唯一交接面**”的工作流：

### 1. 角色分工

| 角色 | 职责 | 可写范围 |
| --- | --- | --- |
| Claude | 架构、契约、ADR、任务拆解、公共能力、脚手架 | `docs/`、根 `pom.xml`、`atifah-common/`、模块骨架、`.github/` |
| CodeBuddy | 按任务包实现功能与测试 | 任务包允许的单个服务目录 |
| Codex | 只读评审、合规检查与独立自测试 | PR 评论 + `docs/review/reports/**`（只写评审报告；可运行构建/测试） |
| 人类 | 确认架构、合并 PR、处理升级 | 全部 |

### 2. 仓库契约

- `AGENTS.md` 是所有工具的规则入口；`CLAUDE.md` 只导入 `AGENTS.md` 与细则，不重复规则。
- 规则细则放 `docs/rules/*.md`（工具中立），不在 `.claude/rules` 等工具专属目录维护唯一副本。
- 接口/DDL/事件契约放 `docs/contracts/`；冻结后在 `main` 打 `arch-baseline-vX.Y` 标签。
- 每个实现任务必须有 `docs/tasks/T-xxx-<slug>.md` 任务包；一个任务对应一个分支、一个 PR。
- 评审判据放 `docs/review/standards.md`，规则编号化（SCOPE/ARCH/API/DB/MQ/SEC/CODE/DEPS/TEST/DOC/OPS/GIT）。
- 评审报告落盘到 `docs/review/reports/T-xxx-<head-short-sha>.md`；任务包 §11 归档路径与 Verdict。
- 任务状态由授权角色维护：Claude `draft`/`ready`、CodeBuddy `in-progress`/`in-review`、人类 `done`；Codex 只读。每次变更写 front matter 与任务包 §12（DOC-04）。

### 3. 串行交接

1. Claude 产出 ADR + 契约 + 任务包 → 人工确认 → 冻结基线。
2. CodeBuddy 从 `main` 拉 `feat/T-xxx-<slug>` → 实现 → 自检 → PR。
3. CI 通过。
4. Codex 只读评审 + 在独立工作区执行自测试，完整报告落盘到 `docs/review/reports/` → `Blocker`/`Major` 或自测试失败回 CodeBuddy，架构/契约问题回 Claude。
5. 人类合并。

同一工作区同一时刻只允许一个角色写文件；并行任务使用独立分支或 `git worktree`。

### 4. 门禁

- CI：`mvn -B -ntp verify`、前端 `npm ci && npm run build`、密钥扫描。
- 评审：任一 `Blocker` / `Major` 未清零、Codex 自测试未执行/失败、报告未落盘/任务包 §11 未归档，或任务状态与 PR/合并证据不符，不得合并。
- 契约：功能 PR 不得夹带契约修改；契约变更必须单独 PR + ADR。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 单一工具包办架构、开发、评审 | 无需交接，上下文连续 | 缺少独立评审视角；架构与实现容易互相妥协 | 否决 |
| 靠对话把架构结论转述给开发工具 | 上手快 | 上下文丢失、不可追溯、无法审计 | 否决 |
| 三个工具同时改同一工作区 | 并行速度快 | 冲突、覆盖、责任不清 | 否决 |
| 仓库契约 + 串行交接 + 只读评审与独立自测试（本 ADR） | 可追溯、可审计、规则稳定，评审不脱离实际运行 | 需要维护契约与任务包，前期有成本；Codex 需独立跑构建/测试 | 采纳 |

## 影响

- 正面：
  - 架构、实现、评审的输入输出都落在仓库里，可追溯、可回滚。
  - CodeBuddy 不需要猜设计；Codex 有稳定判据；Claude 的决策有 ADR 承载。
  - 规则可逐步自动化（ArchUnit、CI、静态扫描），减少重复口头提醒。
- 负面/代价：
  - 前期需要维护契约、任务包与规则，架构阶段耗时增加。
  - 单任务拆小后，PR 数量增加。
  - 需要人工维护角色边界，避免工具越界修改。
  - Codex 需要独立执行构建/测试，评审时间与资源增加。
  - 评审报告需要额外归档，增加少量维护成本。
- 需要同步修改的文档与代码：
  - `AGENTS.md`、`CLAUDE.md`
  - `docs/rules/*.md`
  - `docs/contracts/**`
  - `docs/tasks/**`
  - `docs/review/**`
  - `.github/pull_request_template.md`、`.github/workflows/ci.yml`
  - 模块级 `AGENTS.md`
- 回滚方式：
  - 保留本 ADR；若流程成本过高，可简化为“Claude + CodeBuddy 两角色”，但仍保留契约与评审门禁。

## 关联

- 相关契约：`docs/contracts/README.md`
- 相关任务包规范：`docs/tasks/README.md`
- 相关评审规则：`docs/review/standards.md`
- 替代：`db6d457^` 中的 `0001-ai-rules-layering.md`（已在 `db6d457` 删除）。本 ADR 沿用其分层思路，但把工具专属的 `.claude/rules` 改为中立的 `docs/rules`，并增加契约、任务包、评审门禁与 CI。
