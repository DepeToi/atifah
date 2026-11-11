# TODO

> 用途：记录 AI 协作流程的优化项与执行顺序。本文是工作清单，不是契约；条目完成后在关联 PR 中勾选，不在本文件维护长日志。
> 背景：ADR 0001 已完成第一轮减负（去双份状态日志、去强制全量复测、报告可选）；本清单对应第二轮优化。
> 创建：2026-11-07
> 状态：draft

## 0. 使用约定

- **P0 未完成前，不启动 P1–P4 的规则修改**；先用一次真实任务验证流程，再按实际摩擦调整。
- 每项只标注执行角色；实际 `git commit` / `git push` 仍由人工授权（GIT-01）。
- 当前工作区已有未提交的 ADR 0001 + 11 个文档修改；先按 P0-1 落盘，再开始后续事项。
- 当前仓库状态：`main` 分支，无 `arch-baseline-*` 标签，`docs/contracts/` 只有模板与 `api-conventions.md`。

## P0 流程冒烟（auth 纵向切片）

**目标**：让 Claude → CodeBuddy → Codex → 人工 的一次完整闭环真实跑通；在此之前不新增流程规则。

| # | 事项 | 执行 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P0-1 | 提交当前规则基线（ADR 0001 + 11 个文档） | 人工 | 无 | git 历史包含当前规则；工作区干净 |
| P0-2 | auth 契约：`docs/contracts/openapi/auth-service.yaml` | Claude | P0-1 | register/login/refresh/logout；错误码、幂等、脱敏符合 `api-conventions.md` |
| P0-3 | auth DDL：`docs/contracts/db/auth-service.sql` | Claude | P0-1 | 主键、唯一索引、审计字段、`*_cipher`、注释齐全 |
| P0-4 | （可选）ADR 0003：Token 与会话模型 | Claude | P0-2 | 记录 HS256/RS256、刷新轮换、网关鉴权开启条件 |
| P0-5 | 契约 PR 合并 | 人工 | P0-2..P0-4 | Codex 评审通过；CI 绿 |
| P0-6 | 打 `arch-baseline-v0.1` | 人工 | P0-5 | 标签在 `main` 上，仅覆盖 auth |
| P0-7 | T-001 任务包 | Claude | P0-6 | `status: ready`；允许/禁止路径、验收命令、测试要求齐全 |
| P0-8 | 实现 T-001 | CodeBuddy | P0-7 | 按任务包实现；自检通过；PR 置 `in-review` |
| P0-9 | 评审 T-001 | Codex | P0-8 | PR 评论含 Reviewed SHA、CI、补测、未覆盖范围、结论 |
| P0-10 | 合并 + `done` | 人工 | P0-9 | PR 合并；任务包 `status: done` |
| P0-11 | 流程复盘 | 全体 | P0-10 | 记录 3 个真实摩擦点，决定是否调整 P1–P4 |

**P0 明确不做**：9 个服务全量契约、MQ 事件链路、前端页面、管理端鉴权。

### P0 建议顺序

1. 人工提交当前规则基线（P0-1）。
2. Claude 产出 auth 契约 + DDL +（可选）ADR（P0-2..P0-4）。
3. 人工合并、打 `arch-baseline-v0.1`（P0-5..P0-6）。
4. Claude 产出 T-001；CodeBuddy 实现；Codex 评审；人工合并（P0-7..P0-10）。
5. 复盘并记录摩擦点（P0-11）。

### P0 选 auth 的理由

- 网关鉴权当前关闭（`atifah.gateway.auth.enabled=false`），auth 是解锁后续链路的前置。
- 单服务、边界清晰、无跨服务事件，适合第一次跑流程。
- 涉及密码/手机号，可同时验证 SEC 规则是否可执行。

## P1 规则去重（P0 后）

- [ ] P1-1 `AGENTS.md` §0 作为交接序列唯一来源；`README.md` 的 AI 指南删除 5 步序列，改为链接。
- [ ] P1-2 `docs/tasks/README.md` §6 只留分支/自检命令；评审要求指向 `docs/review/standards.md` §4。
- [ ] P1-3 `docs/rules/git-workflow.md` GIT-07 只留 Git 侧约束；交接顺序指向 `AGENTS.md` §0。
- [ ] P1-4 `docs/tasks/_TEMPLATE.md` 的 `owner` 改为 `implementer`。
- [ ] P1-5 PR 模板增加“Feature 变更路径 ⊆ 任务包允许路径”检查项。

**验收**：同一交接步骤不再在 4 处重复描述；相关章节只保留一处正文 + 链接。

## P2 契约 ADR 分级（P0 后）

- [ ] P2-1 修改 `docs/review/standards.md` DOC-01：ADR 仅对首次冻结、破坏性变更、跨服务语义、新组件强制。
- [ ] P2-2 修改 `docs/contracts/README.md` §3：兼容新增走契约 PR + 冻结清单 + 新基线。
- [ ] P2-3 修改 `docs/contracts/api-conventions.md` §10：同步 ADR 分级。
- [ ] P2-4 修改 `AGENTS.md` §0 的 Arch 必需产物。
- [ ] P2-5 新增 ADR（按当时下一个可用编号）记录本次流程规则变更；更新 `docs/adr/README.md` 索引。

**验收**：兼容新增不需要 ADR；破坏性变更仍被 DOC-01 拦截。

## P3 状态机减写（P0 后）

- [ ] P3-1 `docs/tasks/README.md` §2：允许直接创建 `ready`（契约已冻结 + 维护者确认）；`draft` 仅用于例外。
- [ ] P3-2 `in-review` 与开 PR 放在同一提交；`done` 合并后批量处理。
- [ ] P3-3 增加“禁止 status-only PR”规则。
- [ ] P3-4 同步 `AGENTS.md` §0 与 `docs/tasks/_TEMPLATE.md`。

**验收**：正常任务状态提交 ≤2 次；权限模型不变。

## P4 试点后再定（P0-11 复盘后）

- [ ] P4-1 分支保护：`Backend / JDK 21`、`Frontend / Node 22`、`Secret scan / gitleaks` 设为必需 check；Codex 不再手工核验 CI。
- [ ] P4-2 （可选）CI scope check：同一 PR 混改 `docs/contracts/**` 与 `atifah-services/**` 直接失败。
- [ ] P4-3 低风险 Feature 的 Codex 评审抽样（至少积累 3–5 个 PR 后再定）。

## 明确不做

- 不新增角色、状态或流程文档。
- 不做 9 服务大爆炸式契约冻结。
- 不要求 Codex 重复执行 CI 已覆盖的全量测试。
- 不引入新的流程工具/依赖（除非先人工确认）。

## 关联

- ADR: `docs/adr/0001-lightweight-ai-workflow.md`
- 规则入口: `AGENTS.md`
- 任务规范: `docs/tasks/README.md`
- 评审标准: `docs/review/standards.md`
- 契约规范: `docs/contracts/README.md`
