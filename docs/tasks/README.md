# 任务包规范（docs/tasks）

> 任务包是 **Claude 给 CodeBuddy 的施工图**，也是 Codex 评审与自测试的输入之一。
> 没有任务包，不允许开始功能开发；一个任务包对应一个分支、一个 PR。

## 1. 命名与编号

- 文件名：`T-<三位编号>-<service>-<slug>.md`，例如 `T-001-auth-login.md`。
- 编号全局递增，不复用；跨服务任务拆成多个任务包，不要写“大任务”。
- slug 用小写 kebab-case，描述功能而不是实现方式。

## 2. 状态机与修改权限

> `AGENTS.md` §0 是工作流摘要；本文件 §2 是状态机与修改权限的唯一细则。

| 状态 | 含义 | 进入条件 | 可设置者 |
| --- | --- | --- | --- |
| `draft` | Claude 正在设计，契约可能变化 | 任务包创建 | Claude |
| `ready` | 契约已冻结、验收标准明确，可开发 | 契约冻结 + 基线标签 + 人工确认 | Claude（需人工确认） |
| `in-progress` | CodeBuddy 正在实现 | 已从 `main` 拉任务分支并开始实现 | CodeBuddy |
| `in-review` | 已开 PR，等待 CI / Codex 评审与自测试 | 分支已推送 + PR 已创建 + CI 已触发 | CodeBuddy |
| `done` | 已合并，验收通过 | PR 已合并 + Codex 报告已落盘 + §11/§12 已归档 | 人类维护者 |
| `blocked` | 契约缺失、依赖未就绪或评审阻塞 | 写明阻塞原因与解除条件 | 当前负责人 |

**允许的流转：**

- `draft → ready → in-progress → in-review → done`
- `in-review → in-progress`：Codex 要求修改，CodeBuddy 回退并写明原因
- 任意状态 → `blocked`：当前负责人写明原因；`blocked → 原状态` 需确认解除条件已满足
- 禁止跳级：`draft/ready → done`、`in-progress → done` 等
- `done` 不允许回退；确需回退只能由人类维护者执行并记录原因

**修改规则：**

- Codex **不得**修改任务状态；它只评审、跑自测试、写评审报告。
- CodeBuddy 只能设置 `in-progress` / `in-review`，不得设置 `done`。
- Claude 只能设置 `draft` / `ready`；`ready` 必须有维护者确认记录。
- 人类维护者负责 `done` 与强制回退。
- 每次状态变更必须同时：
  1. 修改 front matter 的 `status`、`updated`、`status_reason`；
  2. 在任务包 `## 12. 状态变更记录` 追加一行（日期、从、到、操作人、依据）；
  3. 随任务分支或独立 docs 提交进入 Git。
- 聊天里说“完成了”不算状态变更；没有 PR 证据不得标 `in-review`，没有合并/报告证据不得标 `done`（DOC-04）。

## 3. 写作与执行约束

- 任务包由 Claude 编写，必须引用**已冻结的契约路径**，不能写“接口待定”。
- 必须列出“允许修改路径”和“禁止修改路径”；CodeBuddy 只能改允许路径。
- 验收标准必须可验证（命令、断言、接口行为），不能写“体验良好”。
- 单任务单服务、diff ≤400 行；超出请拆分。
- 任务包不复制契约内容，只引用路径与锚点，避免双写漂移；已完成的历史任务包不回改。

## 4. Definition of Ready（进入开发前）

- [ ] 关联 ADR 已确认（如有架构取舍）。
- [ ] 接口 / DDL / 事件契约已冻结，并记录基线版本。
- [ ] 允许修改路径明确，且不与进行中的其他任务冲突。
- [ ] 验收标准与测试要求可执行。
- [ ] 依赖项（其他服务、中间件、前端接口）已就绪或明确 mock 方案。

## 5. Definition of Done（可合并前）

- [ ] 实现符合任务包与契约，未越界修改。
- [ ] 新增/修改代码有对应测试；核心链路覆盖成功与失败路径。
- [ ] 后端 `mvn -B -ntp verify` 通过；前端 `npm ci && npm run build` 通过。
- [ ] CI 全绿，且 Codex 在独立工作区复跑自测试通过。
- [ ] Codex 评审报告已落盘到 `docs/review/reports/`，任务包 §11 已归档报告路径与 Verdict。
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
gh pr create --base main --title "feat(auth): T-001 登录与 Token 签发" \
  --body-file docs/tasks/T-001-auth-login.md
```

Codex 在独立 worktree / 干净检出中执行自测试（`mvn -B -ntp verify`、前端 `npm ci && npm run build`），完整报告写入 `docs/review/reports/T-xxx-<head-short-sha>.md`；不能只信 CI。CodeBuddy 把报告路径与 Verdict 写入任务包 §11。

- `Blocker` / `Major` 或自测试失败 → CodeBuddy 修复，重新跑 CI，Codex 复审。
- 架构 / 契约问题 → 标记 `[Arch]`，回到 Claude，先更新 ADR 与契约，再让 CodeBuddy 跟进。
- `Minor` / `Question` → 可在本 PR 或后续任务处理；涉及契约/安全的问题必须先回答。

## 7. 模板

复制 [`_TEMPLATE.md`](_TEMPLATE.md) 开始新任务。
