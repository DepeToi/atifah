# 任务包规范（docs/tasks）

> 任务包是 **Claude 给 CodeBuddy 的 Feature 施工图**，也是 Codex 评审与验证的输入之一。
> 只有 **Feature** 变更需要任务包；Arch 用 ADR + 契约 diff，Ops 用 PR 描述 + 验证命令，Docs 用 PR 描述（见 `AGENTS.md` §0）。
> 没有任务包，不允许开始 Feature 开发；一个任务包对应一个分支、一个 PR。

## 1. 命名与编号

- 文件名：`T-<三位编号>-<slug>.md`，例如 `T-001-auth-login.md`；服务名写在标题里，不写进文件名。
- 编号全局递增，不复用；跨服务任务拆成多个任务包，不要写“大任务”。
- slug 用小写 kebab-case，描述功能而不是实现方式。
- 非 Feature 变更不新建任务包，也不占用 `T-xxx` 编号。

## 2. 状态机与修改权限

> `AGENTS.md` §0 是工作流摘要；本文件 §2 是状态机与修改权限的唯一细则。

| 状态 | 含义 | 进入条件 | 可设置者 |
| --- | --- | --- | --- |
| `draft` | Claude 正在设计，契约或范围可能变化 | 任务包创建（例外情况） | Claude |
| `ready` | 契约已冻结、验收标准明确，可开发 | 契约冻结 + 基线标签 + 维护者确认；可直接以此状态创建 | Claude |
| `in-review` | 已开 PR，等待 CI / Codex 评审 | 分支已推送 + PR 已创建 | CodeBuddy |
| `done` | 已合并，验收通过 | PR 已合并 + CI 通过 + 评审结论满足合并要求 | 人类维护者（可批量） |
| `blocked` | 契约缺失、依赖未就绪或评审阻塞 | 写明阻塞原因与解除条件 | 当前负责人 |

**允许的流转：**

- `draft → ready → in-review → done`
- `in-review → ready`：范围或契约发生变化，需重新批准；写明原因
- 任意状态 → `blocked`：当前负责人写明原因；`blocked → 原状态` 需确认解除条件已满足
- 禁止跳级：`draft/ready → done` 等；`done` 不允许回退，确需回退只能由维护者执行并记录

**修改规则：**

- 状态只对 Feature 任务包生效；Arch/Ops/Docs 不新建任务包，也不占用 `T-xxx` 编号。
- Codex **不得**修改任务状态；它只评审 PR，并按风险验证 CI 与补充测试。
- CodeBuddy 正常流转只设 `in-review`，不得设置 `done`；可将状态置为 `blocked`，解除后回到原状态。
- Claude 正常流转只设 `draft` / `ready`；`ready` 以维护者批准任务包/契约为准；可将状态置为 `blocked`。
- 人类维护者负责 `done` 与强制回退；合并后批量收口，不要求每个 PR 后立即提交。
- 状态变更只更新 front matter 的 `status`、`updated`、`status_reason`；Git 历史和关联 PR 即为变更记录，不维护重复日志。
- 没有 PR 证据不得标 `in-review`；没有合并、CI 与评审证据不得标 `done`（DOC-04）。
- **禁止 status-only PR**：仅修改任务包 front matter（`status` / `updated` / `status_reason`）的 PR 不予合并。状态变更必须与对应的实现、契约或评审证据在同一 PR，或由维护者在合并后批量收口 `done`（ADR 0005）。

## 3. 写作与执行约束

- 任务包由 Claude 编写，必须引用**已冻结的契约路径**，不能写“接口待定”。
- 必须列出“允许修改路径”和“禁止修改路径”；CodeBuddy 只能改允许路径。
- “允许修改”必须包含任务包自身（仅 front matter）；其他路径一律禁止。
- 验收标准必须可验证（命令、断言、接口行为），不能写“体验良好”。
- 单任务单服务；生产语义 diff ≤400 行（口径见 `docs/review/standards.md` SCOPE-02，测试不计入）；超出请拆分。
- 任务包不复制契约内容，只引用路径与锚点，避免双写漂移；已完成的历史任务包不回改，历史包保留 §11/§12 编号。

## 4. Definition of Ready（进入开发前）

- [ ] 变更类型为 Feature，且不属于 Arch/Ops/Docs。
- [ ] 关联 ADR 已确认（如有架构取舍）。
- [ ] 接口 / DDL / 事件契约已冻结，`contract_baseline` 标签存在且是 `main` 的祖先。
- [ ] 允许修改路径明确，且不与进行中的其他任务冲突。
- [ ] 验收标准与测试要求可执行，验证级别（A/B/C）已写明。
- [ ] 依赖项（其他服务、中间件、前端接口）已就绪或明确 mock 方案。

## 5. Definition of Done（任务可关闭前）

- [ ] 实现符合任务包与契约，未越界修改。
- [ ] 新增/修改代码有对应测试；核心链路覆盖成功与失败路径。
- [ ] 本地受影响模块测试通过；涉及前端时运行相关类型检查或测试，并在 PR 记录命令与结果。
- [ ] CI 全绿；Codex 按 A/B/C 级核验 Reviewed SHA 的 CI，并按风险补充验证。
- [ ] PR 中有 Codex 评审结论与 Reviewed SHA 对应的验证证据；独立报告文件可选。
- [ ] Codex 评审无 `Blocker` / `Major`，或已有维护者明确豁免记录。
- [ ] 相关文档、契约引用、常量登记已同步。
- [ ] PR 描述包含验证证据与风险/回滚说明。

## 6. 交接步骤

```bash
# CodeBuddy：从 main 拉任务分支
git checkout main && git pull
git checkout -b feat/T-001-auth-login

# 实现并自检
mvn -pl atifah-services/auth-service -am test
cd atifah-admin-web/frontend && npm run type-check   # 如涉及前端

# 开 PR（以下 push / gh 命令仅在用户明确要求时执行）
git push -u origin feat/T-001-auth-login
gh pr create --base main --title "feat(auth): T-001 登录与 Token 签发"
# 描述按 .github/pull_request_template.md 填写，不要用任务包正文代替
```

Codex 按 `docs/review/standards.md` §4 的 A/B/C 级核验 CI 与风险对应的测试；不重复执行 CI 已覆盖的全量测试。Reviewed SHA、CI 结果、补充测试及未覆盖范围记录在 PR 评审评论中。完整报告可按需归档到 `docs/review/reports/`。

- `Blocker` / `Major` 或验证失败 → 实现者修复，重新跑 CI，Codex 复审。
- 架构 / 契约问题 → 标记 `[Arch]`，回到 Claude，先更新 ADR 与契约，再让 CodeBuddy 跟进。
- `Minor` / `Question` → 可在本 PR 或后续任务处理；涉及契约/安全的问题必须先回答。

## 7. 模板

复制 [`_TEMPLATE.md`](_TEMPLATE.md) 开始新任务；历史任务包不追溯改编号。
