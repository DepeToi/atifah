# 任务包规范（docs/tasks）

> 任务包是 **Claude 给 CodeBuddy 的 Feature 施工图**，也是 Codex 评审与自测试的输入之一。
> 只有 **Feature** 变更需要任务包；Arch 用 ADR + 契约 diff，Ops 用变更说明 + 验证命令，Docs 用 PR 描述（见 `AGENTS.md` §0）。
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
| `draft` | Claude 正在设计，契约可能变化 | 任务包创建 | Claude |
| `ready` | 契约已冻结、验收标准明确，可开发 | 维护者批准任务包 + 契约基线已存在 | Claude |
| `in-review` | 已开 PR，等待 CI / Codex 评审与自测试 | 分支已推送 + PR 已创建 | CodeBuddy |
| `done` | 已合并，报告已归档 | PR 已合并 + Codex 报告已归档 | 人类维护者（每周批量） |
| `blocked` | 契约缺失、依赖未就绪或评审阻塞 | 写明阻塞原因与解除条件 | 当前负责人 |

**允许的流转：**

- `draft → ready → in-review → done`
- `in-review → ready`：范围或契约发生变化，需重新批准；写明原因
- 任意状态 → `blocked`：当前负责人写明原因；`blocked → 原状态` 需确认解除条件已满足
- 禁止跳级：`draft/ready → done` 等；`done` 不允许回退，确需回退只能由维护者执行并记录

**修改规则：**

- Codex **不得**修改任务状态；它只评审、跑自测试、写评审报告。
- CodeBuddy 只能设置 `in-review`，不得设置 `done`；任意当前负责人可将状态置为 `blocked`，解除后回到原状态。
- Claude 只能设置 `draft` / `ready`；`ready` 以维护者批准任务包/契约为准。
- 人类维护者负责 `done` 与强制回退；合并即完成，维护者每周批量收口，不要求每个 PR 后立即提交。
- 每次状态变更必须同时：
  1. 修改 front matter 的 `status`、`updated`、`status_reason`；
  2. 在任务包 `## 12. 状态变更记录` 追加一行（日期、从、到、操作人、依据）；
  3. 随任务分支或独立 docs 提交进入 Git。
- 聊天里说“完成了”不算状态变更；没有 PR 证据不得标 `in-review`，没有合并与报告证据不得标 `done`（DOC-04）。

## 3. 写作与执行约束

- 任务包由 Claude 编写，必须引用**已冻结的契约路径**，不能写“接口待定”。
- 必须列出“允许修改路径”和“禁止修改路径”；CodeBuddy 只能改允许路径。
- “允许修改”必须包含任务包自身（仅 front matter / §11 / §12）与报告归档路径 `docs/review/reports/**`；其他路径一律禁止。
- 验收标准必须可验证（命令、断言、接口行为），不能写“体验良好”。
- 单任务单服务：≤600 行非测试代码，总计 ≤1000 行；测试不计入。超限拆任务，或由维护者加 `size-exception` 标签。
- 任务包不复制契约内容，只引用路径与锚点，避免双写漂移；已完成的历史任务包不回改。

## 4. Definition of Ready（进入开发前）

- [ ] 变更类型为 Feature，且不属于 Arch/Ops/Docs。
- [ ] 关联 ADR 已确认（如有架构取舍）。
- [ ] 接口 / DDL / 事件契约已冻结，`contract_baseline` 标签存在且是 `main` 的祖先。
- [ ] 允许修改路径明确，且不与进行中的其他任务冲突。
- [ ] 验收标准与测试要求可执行，测试层级（A/B/C）已写明。
- [ ] 依赖项（其他服务、中间件、前端接口）已就绪或明确 mock 方案。

## 5. Definition of Done（可合并前）

- [ ] 实现符合任务包与契约，未越界修改。
- [ ] 新增/修改代码有对应测试；核心链路覆盖成功与失败路径。
- [ ] 后端 `mvn -B -ntp verify` 通过；前端 `npm ci && npm run build` 通过。
- [ ] CI 全绿，`workflow-check` 通过；Codex 在独立工作区按 A/B 级要求复跑自测试通过。
- [ ] Codex 评审无 `Blocker` / `Major`，或已有维护者明确豁免记录。
- [ ] Codex 报告已作为 PR 最后一个 docs-only 提交归档，任务包 §11 已写入报告路径、Verdict、reviewed SHA 与归档 SHA。
- [ ] 维护者已添加 `review:approved` 标签；推送新代码后已移除并重新评审。
- [ ] 相关文档、契约引用、常量登记已同步。
- [ ] PR 描述包含验证证据与风险/回滚说明。

## 6. 交接步骤

```bash
# CodeBuddy：从 main 拉任务分支
git checkout main && git pull
git checkout -b feat/T-001-auth-login

# 实现并自检
mvn -pl atifah-services/auth-service -am verify
cd atifah-admin-web/frontend && npm run type-check   # 如涉及前端

# 开 PR（以下 push / gh 命令仅在用户明确要求时执行）
git push -u origin feat/T-001-auth-login
gh pr create --base main --title "feat(auth): T-001 登录与 Token 签发" \
  --body-file docs/tasks/T-001-auth-login.md
```

- 创建 PR 的同一个提交里把任务状态改为 `in-review`，并在 §12 记录 PR 链接。
- Codex 在独立 worktree / 干净检出中按 A/B/C 级执行自测试；A 级跑 `mvn -B -ntp verify` 与前端 `npm ci && npm run build`，不能只信 CI。
- 报告命名为 `docs/review/reports/T-xxx-rN-<reviewed-sha>.md`；APPROVE 后 CodeBuddy 原样复制为 PR 最后一个 docs-only 提交，并更新 §11。该提交免语义复审，由 `workflow-check` 校验只改了报告与任务包。
- 维护者加 `review:approved` 标签后，`workflow-check` 重跑通过再合并（Feature 必需；推送新代码后移除并复审）；合并后由维护者每周批量置 `done`。

- `Blocker` / `Major` 或自测试失败 → CodeBuddy 修复，重新跑 CI，Codex 复审；同一 PR 最多 2 轮，仍阻塞则升级到维护者 + Claude 重新界定范围。
- 架构 / 契约问题 → 标记 `[Arch]`，回到 Claude，先更新 ADR 与契约，再让 CodeBuddy 跟进。
- `Minor` / `Question` → 可在本 PR 或后续任务处理；涉及契约/安全的问题必须先回答。

## 7. 模板

复制 [`_TEMPLATE.md`](_TEMPLATE.md) 开始新的 Feature 任务。
