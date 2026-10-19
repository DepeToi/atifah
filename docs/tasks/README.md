# 任务包规范（docs/tasks）

> 任务包是 **Claude 给 CodeBuddy 的施工图**，也是 Codex 评审与自测试的输入之一。
> 没有任务包，不允许开始功能开发；一个任务包对应一个分支、一个 PR。

## 1. 命名与编号

- 文件名：`T-<三位编号>-<service>-<slug>.md`，例如 `T-001-auth-login.md`。
- 编号全局递增，不复用；跨服务任务拆成多个任务包，不要写“大任务”。
- slug 用小写 kebab-case，描述功能而不是实现方式。

## 2. 状态流转

| 状态 | 含义 | 负责人 |
| --- | --- | --- |
| `draft` | Claude 正在设计，契约可能变化 | Claude |
| `ready` | 契约已冻结、验收标准明确，可开发 | Claude → 人工确认 |
| `in-progress` | CodeBuddy 正在实现 | CodeBuddy |
| `in-review` | 已开 PR，等待 CI / Codex 评审与自测试 | Codex |
| `done` | 已合并，验收通过 | 人工 |
| `blocked` | 契约缺失、依赖未就绪或评审阻塞 | 当前负责人 |

状态写在任务包文件的 front matter 中；变更状态时同步更新文件，不靠聊天记录。

## 3. 写作与执行约束

- 任务包由 Claude 编写，必须引用**已冻结的契约路径**，不能写“接口待定”。
- 必须列出“允许修改路径”和“禁止修改路径”；CodeBuddy 只能改允许路径。
- 验收标准必须可验证（命令、断言、接口行为），不能写“体验良好”。
- 单任务单服务、diff ≤400 行；超出请拆分。
- 任务包不复制契约内容，只引用路径与锚点，避免双写漂移。

## 4. Definition of Ready（进入开发前）

- [ ] 关联 ADR 已确认（如有架构取舍）。
- [ ] 接口 / DDL / 事件契约已冻结，并记录基线版本。
- [ ] 允许修改路径明确，且不与进行中的其他任务冲突。
- [ ] 验收标准与测试要求可执行。
- [ ] 依赖项（其他服务、中间件、前端接口）已就绪或明确 mock 方案。

## 5. Definition of Done（可合并前）

- [ ] 实现符合任务包与契约，未越界修改。
- [ ] 新增/修改代码有对应测试；核心链路覆盖成功与失败路径。
- [ ] 后端 `mvn -B -ntp verify` 通过；前端 `npm run build` 通过。
- [ ] CI 全绿，且 Codex 在独立工作区复跑自测试通过。
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

# 开 PR（不要自动 push，除非用户明确要求）
git push -u origin feat/T-001-auth-login
gh pr create --base main --title "feat(auth): T-001 登录与 Token 签发" \
  --body-file docs/tasks/T-001-auth-login.md
```

Codex 在独立 worktree / 干净检出中执行自测试（`mvn -B -ntp verify`、前端 `npm ci && npm run build`），把结果写入评审报告；不能只信 CI。

- `Blocker` / `Major` 或自测试失败 → CodeBuddy 修复，重新跑 CI，Codex 复审。
- 架构 / 契约问题 → 标记 `[Arch]`，回到 Claude，先更新 ADR 与契约，再让 CodeBuddy 跟进。
- `Minor` / `Question` → 可在本 PR 或后续任务处理；涉及契约/安全的问题必须先回答。

## 7. 模板

复制 [`_TEMPLATE.md`](_TEMPLATE.md) 开始新任务。
