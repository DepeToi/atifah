# 评审报告归档（docs/review/reports）

> Codex 的完整评审报告必须归档到这里；PR 评论放 Verdict + Self-test 摘要与报告路径。
> 规则见 [`../standards.md`](../standards.md) §6.1；模板见 [`../report-template.md`](../report-template.md)。

## 命名

- Feature：`T-xxx-rN-<reviewed-sha>.md`，其中 `<reviewed-sha>` 是本次评审的最后一个代码提交（7–40 位）。
  - 示例：`T-001-r1-7ae8964.md`、`T-001-r2-8641fca.md`。
- 非 Feature（Arch / Ops / Docs）：`PR-<n>-<reviewed-sha>.md`，不使用 `-r<n>`；同一 PR 复审时 Reviewed SHA 变化，报告自然形成新文件。
  - 示例：`PR-10-00db983.md`、`PR-7-c098f5a.md`。
- 同一 PR 多轮评审递增 `r1`、`r2`，不要覆盖历史报告；历史报告保持原文件名，不追溯改名。

## 写入权限

- **Codex**：可写本目录，仅限评审报告；不得修改代码、契约、规则、任务实现或测试；不得提交。
- **CodeBuddy**：在 APPROVE 后按 [`../standards.md`](../standards.md) §6.1 归档 Codex 报告并更新任务包 §11；不得修改报告正文。
- **人类维护者**：只读；合并前确认报告、§11 与 `review:approved` 标签齐全。

## 归档规则

归档步骤、免复审规则与 `Reviewed SHA..HEAD` 校验以 [`../standards.md`](../standards.md) §6.1 为唯一来源；本文件只定义命名、写入权限与保留策略。

## 保留

- 报告随任务长期保留；不删除历史轮次。
- 报告中的手机号、token、密钥等敏感信息必须脱敏。

## 合并门禁

未归档报告、任务包 §11 未记录，或 Feature/Arch/Ops 缺少 `review:approved` → 不得合并；细则见 [`../standards.md`](../standards.md) §6.1。
