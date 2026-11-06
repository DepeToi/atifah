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
- **CodeBuddy**：在 APPROVE 后将 Codex 报告**原样复制**为 PR 最后一个 docs-only 提交，并更新任务包 §11；不得修改报告正文。
- **人类维护者**：只读；合并前确认报告、§11 与 `review:approved` 标签齐全。

## 归档顺序

1. Codex 在 PR 评论给出 Verdict + Self-test 摘要，并产出完整报告。
2. CodeBuddy 将报告原样复制到 `docs/review/reports/`（Feature：`T-xxx-rN-<reviewed-sha>.md`；非 Feature：`PR-<n>-<reviewed-sha>.md`）；Feature 同一提交更新任务包 §11，非 Feature 填写报告归档记录。该提交只允许改报告与任务包 §11/§12。
3. `workflow-check` 解析报告中的 Reviewed SHA，要求至少一份当前轮次报告的 `Reviewed SHA..HEAD` 只含报告与任务包 §11/§12、HEAD 最后提交为 docs-only；历史轮次报告可同时归档，但旧报告不能单独作为审批依据。该提交免语义复审。报告不记录归档提交自身的 SHA。
4. 维护者加 `review:approved` 标签，CI 全绿后合并。
5. 合并后维护者每周批量把任务置为 `done`。

## 保留

- 报告随任务长期保留；不删除历史轮次。
- 报告中的手机号、token、密钥等敏感信息必须脱敏。

## 合并门禁

未归档报告、任务包 §11 未记录报告路径/Verdict、Feature/Arch/Ops 缺少 `review:approved`，或 CI / `workflow-check` 未通过 → 不得合并（DOC-03，细则见 `../standards.md` §6.1）。推送新代码后必须移除标签并复审。
