# 评审报告归档（docs/review/reports）

> Codex 的完整评审报告必须落盘到这里；PR 评论只放摘要与报告链接。
> 规则见 [`../standards.md`](../standards.md) §6.1；模板见 [`../report-template.md`](../report-template.md)。

## 命名

- `T-xxx-<head-short-sha>.md`，其中 `<head-short-sha>` 是评审时 PR head commit 的 7 位短 SHA。
- 示例：`T-001-7ae8964.md`。
- 同一任务多轮评审：`T-001-r2-<sha>.md`，不要覆盖历史报告。

## 写入权限

- **Codex**：可写本目录，仅限评审报告；不得修改代码、契约、规则、任务实现或测试。
- **CodeBuddy / 人类**：只读报告；把报告路径与 Verdict 归档到任务包 §11，不得修改报告正文。

## 保留

- 报告随任务长期保留；不删除历史轮次。
- 报告中的手机号、token、密钥等敏感信息必须脱敏。

## 内容与格式

报告内容、Self-test 块与判定规则见 [`../standards.md`](../standards.md) §6/§6.1；本文件只规定命名、写入权限与保留策略。

## 合并门禁

未落盘报告，或任务包 §11 未归档报告路径与 Verdict → 不得合并（DOC-03，细则见 `../standards.md` §6.1）。
