# 0001. AI 规则文档分层

- 状态：已采纳
- 日期：2026-10-18
- 决策人：项目维护者

## 背景

项目同时使用 Claude Code / CodeBuddy / Cursor / Codex 等工具。此前规则散落在 `AGENTS.md`、`CODEBUDDY.md`、`docs/backend-development.md`、模块说明中，存在三个问题：

1. 同一规则多处抄写，修改后容易漂移；
2. 根规则文件越来越长，每次对话全量加载，浪费上下文；
3. 模块级差异与全局规则混在一起，AI 容易按通用 Spring 习惯猜。

参考《Java 项目 AI 规则文档分层结构》，需要建立「个人偏好 → 项目入口 → 主题规则 → 专题文档 → 模块规则」的分层。

## 决策

采用五层结构：

| 层 | 位置 | 内容 | 加载方式 |
| --- | --- | --- | --- |
| 1 | `~/.claude/CLAUDE.md`（项目内副本 `.claude/CLAUDE.md`） | 个人习惯（中文回复、不自动 commit、遵循阿里手册） | 不进 Git，个人维护 |
| 2 | `AGENTS.md` + `CLAUDE.md` | 项目唯一入口：硬约束与索引，≤200 行；`CLAUDE.md` 只写 `@AGENTS.md` | 每次必读 |
| 3 | `.claude/rules/*.md` | `security` / `code-style` / `database` / `git` / `backend-javadoc` | 全量加载 |
| 4 | `docs/*.md` + `docs/adr/` | `architecture` / `domain` / `api-contract` / `db` / `pitfalls` 专题 + ADR | 按需读取 |
| 5 | 各模块 `AGENTS.md` | 模块级补充规则 | 就近生效，冲突时覆盖上层 |

配套约定：

- 规则只维护一份；`CLAUDE.md` 不重复规则，`CODEBUDDY.md` 不再单独维护。
- `docs/backend-development.md` 的内容拆分到 `.claude/rules/` 与 `docs/` 后删除，避免与规则文件双写。
- `atifah-admin-web/frontend/DEVELOPMENT.md` 的硬规则并入 `frontend/AGENTS.md`，人看的工程说明保留在 `README.md`。
- 重要架构决策新增 ADR，不再只留在对话或提交信息里。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 单一超长 `AGENTS.md` | 只需维护一处 | 每次全量加载，模块差异无法就近覆盖 | 否决 |
| 每个工具各维护一份规则 | 工具适配灵活 | 多处抄写，必然漂移 | 否决 |
| 五层分层（本 ADR） | 入口短、规则全量加载、专题按需读、模块可就近覆盖 | 需要一次性整理并持续约束 | 采纳 |

## 影响

- 正面：`AGENTS.md` 保持精简；规则与文档职责清晰；新模块可以就近补充规则。
- 代价：需要维护 `.claude/rules/` 与 `docs/` 的边界；改规则时必须判断应放哪一层。
- 已同步：
  - 新增 `CLAUDE.md`（仅 `@AGENTS.md`）；
  - 项目内 `.claude/CLAUDE.md` 作为个人偏好副本，已加入 `.gitignore`，不进入版本库；
  - 新增 `.claude/rules/security.md`、`code-style.md`、`database.md`、`git.md`、`backend-javadoc.md`；
  - 新增 `docs/domain.md`、`docs/db.md`、`docs/pitfalls.md`、`docs/adr/`；
  - 新增 `atifah-common/AGENTS.md`、`atifah-admin-web/frontend/AGENTS.md`；
  - 删除 `CODEBUDDY.md`、`docs/backend-development.md`、`atifah-admin-web/frontend/DEVELOPMENT.md`。
