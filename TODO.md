# TODO

> 用途：记录 AI 协作流程的优化项与执行顺序。本文是工作清单，不是契约；条目完成后在关联 PR 中勾选，不在本文件维护长日志。
> 背景：ADR 0001 已完成第一轮减负（去双份状态日志、去强制全量复测、报告可选）；本清单对应第二轮优化。
> 创建：2026-11-07
> 状态：draft

## 0. 使用约定

- **P0 已完成（2026-11-11）**；P1–P4 可按计划启动，规则修改仍需按变更类型走 PR。
- 每项只标注执行角色；实际 `git commit` / `git push` 仍由人工授权（GIT-01）。
- P0-1..P0-11 已完成：规则基线、auth 契约 + DDL + ADR 0003、T-001 注册切片均已合并；复盘见下方。
- 当前仓库状态：`main` 分支；`arch-baseline-v0.1` 已打标签；`docs/contracts/` 已包含 auth 契约。

## P0 流程冒烟（auth 纵向切片）

**目标**：让 Claude → CodeBuddy → Codex → 人工 的一次完整闭环真实跑通；在此之前不新增流程规则。

| # | 事项 | 执行 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| P0-1 | 提交当前规则基线（ADR 0001 + 11 个文档） | 人工 | 无 | ✅ 已完成（`c9a0fba` 已合并）；git 历史包含当前规则 |
| P0-2 | auth 契约：`docs/contracts/openapi/auth-service.yaml` | Claude | P0-1 | register/login/refresh/logout；错误码、幂等、脱敏符合 `api-conventions.md` |
| P0-3 | auth DDL：`docs/contracts/db/auth-service.sql` | Claude | P0-1 | 主键、唯一索引、审计字段、敏感字段处理（ADR 0003）、注释齐全 |
| P0-4 | （可选）ADR 0003：Token、会话与手机号 | Claude | P0-2 | 记录 HS256/RS256、刷新轮换、手机号存储、网关鉴权开启条件 |
| P0-5 | 契约 PR 合并 | 人工 | P0-2..P0-4 | Codex 评审通过；CI 绿 |
| P0-6 | 打 `arch-baseline-v0.1` | 人工 | P0-5 | 标签在 `main` 上，仅覆盖 auth |
| P0-7 | T-001 任务包 | Claude | P0-6 | `status: ready`；允许/禁止路径、验收命令、测试要求齐全 |
| P0-8 | 实现 T-001 | CodeBuddy | P0-7 | 按任务包实现；自检通过；PR 置 `in-review` |
| P0-9 | 评审 T-001 | Codex | P0-8 | PR 评论含 Reviewed SHA、CI、补测、未覆盖范围、结论 |
| P0-10 | 合并 + `done` | 人工 | P0-9 | PR 合并；任务包 `status: done` |
| P0-11 | 流程复盘 | 全体 | P0-10 | ✅ 已完成（见下方复盘） |

**P0 明确不做**：9 个服务全量契约、MQ 事件链路、前端页面、管理端鉴权。

> 实现拆分（2026-11-11 维护者确认）：为满足 SCOPE-02 ≤400 行，auth 实现拆为 T-001 注册 / T-002 登录 / T-003 刷新与登出；本表 P0-7..P0-10 先以 T-001 跑通流程，T-002/T-003 在 P0-11 复盘后继续。

### P0-11 复盘（2026-11-11）

| # | 摩擦点 | 处理 | 证据 |
| --- | --- | --- | --- |
| 1 | SCOPE-02 未说明测试是否计入 400 行 | ADR 0004：只统计生产语义行 | PR #9 |
| 2 | PR 描述复用旧 Docs 内容，类型与 diff 不符 | PR 模板增加变更类型/范围自检 | PR #10 |
| 3 | common-web 框架级 400 未映射，服务内临时兜底 | common-web 统一映射 + 测试 | PR #11 |

结论：

- P1-5 已完成（PR #10）；P1–P4 其余项不因本次复盘调整。
- auth-service 的临时 `AuthExceptionHandler` 在 T-002 中删除。
- gitleaks 对 JWT 示例的误报已用低熵占位符规避；`BusinessException(ErrorCode, String)` 构造器缺失留待后续公共 API 评估，不阻塞流程。

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

- [x] P1-1 `AGENTS.md` §0 作为交接序列唯一来源；`README.md` 的 AI 指南删除 5 步序列，改为链接。
- [x] P1-2 `docs/tasks/README.md` §6 只留分支/自检命令；评审要求指向 `docs/review/standards.md` §4。
- [x] P1-3 `docs/rules/git-workflow.md` GIT-07 只留 Git 侧约束；交接顺序指向 `AGENTS.md` §0。
- [x] P1-4 `docs/tasks/_TEMPLATE.md` 的 `owner` 改为 `implementer`。
- [x] P1-5 PR 模板增加“Feature 变更路径 ⊆ 任务包允许路径”检查项（PR #10）。

**验收**：同一交接步骤不再在 4 处重复描述；相关章节只保留一处正文 + 链接。

## P2 契约 ADR 分级（P0 后）

- [x] P2-1 修改 `docs/review/standards.md` DOC-01：ADR 仅对首次冻结、破坏性变更、跨服务语义、新组件强制。
- [x] P2-2 修改 `docs/contracts/README.md` §3：兼容新增走契约 PR + 冻结清单 + 新基线。
- [x] P2-3 修改 `docs/contracts/api-conventions.md` §10：同步 ADR 分级。
- [x] P2-4 修改 `AGENTS.md` §0 的 Arch 必需产物。
- [x] P2-5 新增 ADR（按当时下一个可用编号）记录本次流程规则变更；更新 `docs/adr/README.md` 索引。

**验收**：兼容新增不需要 ADR；破坏性变更仍被 DOC-01 拦截。

## P3 状态机减写（P0 后）

- [x] P3-1 `docs/tasks/README.md` §2：允许直接创建 `ready`（契约已冻结 + 维护者确认）；`draft` 仅用于例外。
- [x] P3-2 `in-review` 与开 PR 放在同一提交；`done` 合并后批量处理。
- [x] P3-3 增加“禁止 status-only PR”规则。
- [x] P3-4 同步 `AGENTS.md` §0 与 `docs/tasks/_TEMPLATE.md`。

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
