# 评审报告归档（docs/review/reports）

> Codex 的完整评审报告归档到这里；PR 评论放 Verdict + Self-test 摘要与报告路径。
> 归档步骤与校验见 [`../standards.md`](../standards.md) §6.1；模板见 [`../report-template.md`](../report-template.md)。

## 命名

- Feature：`T-xxx-rN-<reviewed-sha>.md`，例如 `T-001-r1-7ae8964.md`。
- Arch/Ops/Docs：`PR-<n>-<reviewed-sha>.md`，例如 `PR-10-00db983.md`。
- `<reviewed-sha>` 是 Codex **实际测试的代码提交**（7–40 位），不是归档提交或最终 head SHA。
- 同一任务多轮评审递增 `r1`、`r2`，不要覆盖历史报告；历史报告保持原文件名，不追溯改名。

## 写入权限

- **Codex**：可写本目录，仅限评审报告；不得修改代码、契约、规则、任务实现或测试；不得提交。
- **实现者**（Feature：CodeBuddy；Arch/Ops/Docs：Claude）：APPROVE 后把报告原文作为 docs-only 提交归档；Feature 同时更新任务包 §8.1。不得修改报告正文。
- **人类维护者**：只读；Feature 在任务关闭前确认报告与 §8.1 齐全，Arch/Ops/Docs 在合并前确认报告已归档。

## 归档规则

归档步骤、docs-only 提交范围与校验以 [`../standards.md`](../standards.md) §6.1 为唯一来源；本文件只定义命名、写入权限与保留策略。

## 保留

- 报告随任务长期保留；不删除历史轮次。
- 报告中的手机号、token、密钥等敏感信息必须脱敏。

## 合并与关闭门禁

- Feature：合并前至少有 Codex 的 `APPROVE` 评论或已归档报告；任务关闭前必须已归档并在 §8.1 记录 Reviewed SHA、报告路径与 Verdict，否则不得置为 `done`（DOC-03）。
- Arch/Ops/Docs（若 Codex 评审）：合并前报告必须已归档；非 Feature 没有任务关闭动作，不得以 APPROVE 评论替代归档。
