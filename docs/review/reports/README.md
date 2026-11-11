# 评审报告（docs/review/reports）

> PR 评论是 Codex 评审结果与验证证据的默认记录。只有需要在仓库中长期索引时，才将完整报告归档到此目录。
> 评审规则见 [`../standards.md`](../standards.md)；可选报告模板见 [`../report-template.md`](../report-template.md)。

## 命名

- Feature：`T-xxx-rN-<reviewed-sha>.md`，例如 `T-001-r1-7ae8964.md`。
- Arch/Ops/Docs：`PR-<n>-<reviewed-sha>.md`，例如 `PR-10-00db983.md`。
- `<reviewed-sha>` 是 Codex **实际评审的代码提交**（7–40 位）；报告应注明该 SHA 对应的 CI 与补充验证结果。
- 同一任务多轮评审递增 `r1`、`r2`，不要覆盖历史报告；历史报告保持原文件名，不追溯改名。

## 写入权限

- **Codex**：如需归档，可写本目录中的评审报告；不得修改代码、契约、规则、任务实现或测试；不得提交。
- **实现者**（Feature：CodeBuddy；Arch/Ops/Docs：Claude）：可按维护需要将 PR 评论中的评审报告原文另行归档。
- **人类维护者**：按 PR 评论中的评审结论、CI 与状态证据决定合并或关闭任务；无需检查报告文件。

## 归档规则

报告文件为可选副本，不要求专门创建 docs-only 提交；PR 评论是唯一必需的评审记录。

## 保留

- 报告随任务长期保留；不删除历史轮次。
- 报告中的手机号、token、密钥等敏感信息必须脱敏。

## 合并与关闭

- Feature：合并前有 Codex 的 `APPROVE` 评论、Reviewed SHA 对应的 CI 通过，且任务包验收完成，方可置为 `done`。
- Arch/Ops/Docs：按 PR 评论与 CI/配置验证证据完成评审；不要求归档报告文件。
