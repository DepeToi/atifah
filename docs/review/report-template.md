# 评审报告

> 本文件只定义报告版式；必填内容、判定规则与归档要求见 [`standards.md`](standards.md) §6/§6.1，不在此重复。
> 报告路径：Feature `docs/review/reports/T-xxx-rN-<reviewed-sha>.md`；Arch/Ops/Docs `docs/review/reports/PR-<n>-<reviewed-sha>.md`
> 评审对象：<PR 链接 / 分支 / commit 范围>
> 变更类型：Feature / Arch / Ops / Docs
> 任务包（Feature）：<docs/tasks/T-xxx-xxx.md>
> 契约基线：<arch-baseline-vX.Y>
> Reviewed SHA：<Codex 实际测试的代码提交>
> 评审人：Codex
> 日期：YYYY-MM-DD

## 结论

`APPROVE` / `REQUEST_CHANGES`

- Blocker：0
- Major：0
- Minor：0
- Question：0
- 自测试：PASS / FAIL / 未涉及

## 范围检查

- [ ] 变更类型与 diff 范围一致，且具备该类型必需产物
- [ ] Feature 只改任务包允许路径；Arch/Ops/Docs 只改 PR 描述列出的路径
- [ ] 未修改契约 / 规则 / 父 pom / 受保护文件（Arch 变更除外）
- [ ] 单任务/单变更、单服务、语义 diff ≤400 行（docs-only 报告归档提交不计入）
- [ ] CI 通过
- [ ] 已在独立 worktree / 干净检出执行自测试并记录
- [ ] 报告已写入 `docs/review/reports/`（或已按 §6.1 在 PR 评论输出完整内容；Feature 可合并后补归档，非 Feature 须合并前归档）
- [ ] 任务状态与证据一致（仅 Feature；`in-review` 有 PR，`done` 已合并且报告已归档；§8.2 有记录）

## 自测试记录

- Reviewed SHA：
- 工作区：独立 worktree / 干净检出（路径：）
- 自测试级别：A / B / C
- 环境：JDK 21 / Maven 3.9.x / Node 22（按实际填写）

| 命令 | 结果 | 耗时 | 备注 |
| --- | --- | --- | --- |
| `mvn -B -ntp verify` | PASS / FAIL / 未涉及 |  |  |
| `npm ci && npm run build` | PASS / FAIL / 未涉及 |  |  |

- 失败用例：
- 未覆盖范围（如 MySQL/Redis 集成测试）：
- 与 CI 结果是否一致：

## 发现

### Blocker

- 无

### Major

- 无

### Minor

- 无

### Question

- 无

## 发现详情

```text
[级别] file:line — 规则编号 — 标题
  证据：
  影响：
  建议：
```

## 未覆盖 / 不确定

- 未能检查的文件或行为：
- 需要人工确认的产品/架构问题：

## 建议回归

- [ ] 需要补充的测试：
- [ ] 需要手动验证的路径：
