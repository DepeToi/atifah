# 0007. 工作流 v2.2：门禁语义拆分、报告结论校验与 CI 触发收敛

- 状态：已采纳
- 日期：2026-11-07
- 决策人：项目维护者（Claude 起草）

> 本 ADR 修订 [0006](0006-workflow-v2.1.md) 的门禁执行细节；四分类、四状态、任务包、契约基线、报告归档机制保持不变。

## 背景

v2.1 合并后，执行层仍有五处可机械修复的摩擦：

1. **审批前门禁必然失败，且与评审规则冲突**。`workflow-check` 对 Feature/Arch/Ops 在缺少 `review:approved` 时直接失败；`docs/review/standards.md` §2 又要求“CI 失败即 `REQUEST_CHANGES`”。Codex 无法在 APPROVE 前看到门禁全绿，评审报告只能写“预期失败/CI 无法核对”。
2. **测试代码被计入规模**。`isSizeExcluded` 只排除 `src/test/`。PR #12 中 371 行 `.github/scripts/workflow-check.test.mjs` 被当作生产代码，最终 999 行超过 Arch 500 行上限，触发两轮评审与升级；前端 `*.test.ts` / `*.spec.ts` 同样会被误计。
3. **门禁不校验报告结论**。`workflow-check` 只校验报告文件名、Reviewed SHA 与归档范围，不解析 `Verdict`、`Blocker`、`Major` 与自测试结果。一份 `REQUEST_CHANGES` 或自测试 FAIL 的报告，只要归档并加标签仍可通过。
4. **label 事件触发全量构建**。`ci.yml` 订阅 `labeled` / `unlabeled`，backend、frontend、secret-scan 均无事件条件；每次加/摘 `review:approved`、`size-exception` 都会重跑 JDK 构建、前端构建与密钥扫描。
5. **标签摘除与合并策略依赖人工**。规则要求推送新代码后移除 `review:approved`，但仓库没有自动摘除；若仓库允许 squash/rebase 合并，报告中的 Reviewed SHA 将不在 `main` 历史中，审计链失效。

另有两处文档表述需要收敛：任务包“允许修改”包含 front matter / §11 / §12，但归档提交只允许 §11 / §12；`done` 批量收口要求与 `main` 受保护、所有改动走 PR 的规则存在张力。

## 决策

### 1. 门禁拆分为 structure 与 approval 两段

- `workflow-check --mode=structure`：变更分类、任务包与状态、允许路径、规模、分支命名、契约/ADR 配套、`contract_baseline`、`git diff --check`。缺少 `review:approved` 只警告，不失败。
- `workflow-check --mode=approval`：要求 `review:approved`，并校验报告结论、归档范围、`Reviewed SHA..HEAD`、HEAD 最后提交为 docs-only、Feature 任务包 §11 引用。
- CI 拆为 `workflow-check`（structure）与 `approval-gate`（approval）两个 job；分支保护要求两者都通过。
- 本地预检 `--local-precheck` 默认使用 structure 模式，允许 `ready` / `in-review`；PR 门禁只接受 `in-review`。
- `approval-gate` 在添加标签前失败是**预期状态**，不作为 Codex 的 `REQUEST_CHANGES` 依据；structure 失败、构建/测试/密钥扫描失败才是。

### 2. 规模统计排除测试文件

以下路径不计入 Feature/Arch/Ops 的**非测试代码上限**（Feature 600 行、Arch/Ops 500 行）：

- `src/test/**`
- `**/*.test.*`、`**/*.spec.*`
- `**/__tests__/**`

Feature 的“含测试总计 ≤1000 行”仍包含测试文件，用于约束 PR 总体积。不放宽 600/1000/500 阈值。工作流类变更按“Arch（ADR + 规则）→ Ops（门禁脚本 + CI）”拆成两个 PR；PR #12 即使排除测试仍有 628 行，说明拆分是必要的，而不是阈值过低。

### 3. approval 模式机械校验报告结论

归档报告必须能被解析出：

- `Verdict: APPROVE`
- `Blocker：0`
- `Major：0`
- `自测试：PASS`

支持中英文冒号、大小写不敏感；`REQUEST_CHANGES`、`Major > 0`、`自测试：FAIL` 或字段缺失均失败。Feature 任务包 §11 的 `Verdict` 必须与报告一致。C 级文档变更不进入 approval 门禁，不受此条约束。

### 4. 新代码推送自动摘除 `review:approved`

新增 `pull_request: synchronize` 工作流，检测到标签后自动移除。归档提交发生在加标签之前，不会被误删；加标签后再推送任何代码都会撤销批准，维护者需在复审后重新添加。该工作流只改 PR 标签，不改代码。

### 5. CI 事件与路径过滤

- `labeled` / `unlabeled` 事件只运行 `workflow-check` 与 `approval-gate`，跳过 backend、frontend、secret-scan。
- Docs-only PR 跳过 backend 与 frontend；只改 `atifah-admin-web/frontend/**` 的 PR 跳过 backend。
- 路径判断放在 job 级 `if`，不使用 workflow 级 `paths-ignore`，避免必需检查停留在 pending。
- `push: main` 保留全量构建，作为合并后的二次验证；`workflow_dispatch` 保持全量。

### 6. 合并策略与 `done` 收口

- 仓库固定使用 **merge commit**，禁止 squash/rebase，保证 Reviewed SHA 始终是 `main` 的祖先。
- `done` 批量收口通过 docs-only PR 完成，不直接推送 `main`；如确需维护者绕过保护，必须在 ADR 或 PR 中记录理由。
- 归档提交只允许改 `docs/review/reports/**` 与任务包 §11/§12；front matter 只在状态变更提交中修改。任务包模板与规范按此措辞收敛。

### 7. 实施顺序

- PR1 `arch/workflow-v2.2`：本 ADR 与规则文档。
- PR2 `ops/workflow-gate-hardening`：`workflow-check` 脚本、回归测试、CI job 拆分与自动摘标签工作流。
- PR1 合并后开 PR2；PR2 合并前，旧门禁仍按 v2.1 运行。合并 PR2 前需把分支保护的必需检查从旧的 `Workflow / process gates` 更新为 `Workflow / structure` 与 `Workflow / approval`，否则旧检查名不再产生会阻止合并。

### 8. 明确不做

- 不引入 Codex 的 GitHub 身份或原生 Review 审批；`review:approved` 标签机制保留。
- 不自动批准、不自动合并、不自动改任务状态。
- 不调整 Codex 自测试 A/B/C 分级。
- 不引入策略 JSON、只读 CI token 或归档自动化；需要时另立 ADR。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 保持现状，评审时人工区分“预期失败” | 不改脚本 | 门禁漏洞仍在；每次评审重复解释；易误合并 | 否决 |
| 只修规模统计 | 改动最小 | 报告结论、标签摘除、CI 浪费均未解决 | 否决 |
| 门禁拆两段 + 结论校验 + 自动摘标签 + CI 过滤（本 ADR） | 机械项可验证；评审信号清晰；不改变角色模型 | approval-gate 在审批前仍为红；增加两个 CI job | 采纳 |
| 原生 GitHub Review：Codex 用独立身份 `gh pr review --approve` | 彻底移除标签与 approval-gate 红灯 | 单账号仓库无法自批准；需 bot 身份、token 与权限治理 | 暂缓 |

## 影响

- 正面：规模统计不再误伤测试；`REQUEST_CHANGES` 报告无法通过审批门禁；新代码推送自动撤销批准；label 操作不再触发全量构建；Reviewed SHA 的合并历史可追溯。
- 负面/代价：审批前仍有一个 `approval-gate` 红 check（语义明确）；新增自动摘标签工作流需要 `pull-requests: write`；CI 拆 job 后配置更复杂。
- 需要同步修改的文件：`docs/review/standards.md`、`docs/review/report-template.md`、`docs/tasks/README.md`、`docs/rules/git-workflow.md`、`AGENTS.md`、`README.md`、`.github/pull_request_template.md`、`docs/adr/README.md`、`.github/scripts/workflow-check.mjs`、`.github/scripts/workflow-check.test.mjs`、`.github/workflows/ci.yml`、新增 `.github/workflows/revoke-approval.yml`。
- 回滚方式：恢复 v2.1 的单段门禁与手工摘标签；删除 `revoke-approval.yml`；CI 恢复全量触发；文档同步回退。

## 关联

- 相关契约：无
- 相关任务包：无
- 修订/被修订：[0006](0006-workflow-v2.1.md)、[0004](0004-workflow-v2.md)
