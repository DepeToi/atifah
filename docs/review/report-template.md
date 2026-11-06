# 评审报告

> 本文件只定义报告版式；必填内容、判定规则与归档要求见 [`standards.md`](standards.md) §6/§6.1，不在此重复。
> 报告路径：`docs/review/reports/T-xxx-rN-<reviewed-sha>.md`（非 Feature 用 `PR-<n>-<reviewed-sha>.md`，不使用 `-r<n>`）
> 变更类型：Feature / Arch / Ops / Docs
> 评审对象：<PR 链接 / 分支 / commit 范围>
> 任务包 / ADR：<docs/tasks/T-xxx-xxx.md 或 docs/adr/NNNN-*.md>
> 契约基线：<arch-baseline-vX.Y>
> Reviewed SHA：<被评审的最后一个代码提交>
> 评审人：Codex
> 日期：YYYY-MM-DD

## 结论

Verdict：`APPROVE` / `REQUEST_CHANGES`

- Blocker：0
- Major：0
- Minor：0
- Question：0
- 自测试：PASS / FAIL / 未涉及
- 自测试级别：A / B / C

> `Verdict`、`Blocker`、`Major` 与 `自测试` 字段会被 `approval-gate` 解析；填写实际值，不要保留模板占位符。

## 范围检查

- [ ] 变更类型与实际 diff 一致（Feature / Arch / Ops / Docs）
- [ ] 必需产物齐全：Feature 任务包 / Arch ADR + 契约 diff / Ops 变更说明 / Docs PR 描述
- [ ] diff 在允许范围内（Feature 对照任务包；Arch/Ops/Docs 对照各自边界）
- [ ] 未修改契约 / 规则 / 父 pom / 受保护文件（除非属于对应变更类型且已获批准）
- [ ] 单关注点；规模符合类型阈值
- [ ] CI 通过，`workflow-check` 通过
- [ ] 已在独立 worktree / 干净检出按 A/B/C 级执行自测试并记录
- [ ] 报告已按 §6.1 归档（或已明确待归档提交）
- [ ] 任务状态与证据一致（`in-review` 有 PR；`done` 已合并且报告已归档；状态变更在 §12 有记录）

## 自测试记录

- 级别：A / B / C
- commit：<reviewed-sha>
- 工作区：独立 worktree / 干净检出（路径：）
- 环境：JDK 21 / Maven 3.9.x / Node 22（按实际填写）

| 命令 | 结果 | 耗时 | 备注 |
| --- | --- | --- | --- |
| `mvn -B -ntp verify` | PASS / FAIL / 未涉及 |  |  |
| `npm ci && npm run build` | PASS / FAIL / 未涉及 |  |  |
| 配置 / Compose / YAML 校验 | PASS / FAIL / 未涉及 |  |  |

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

## 归档记录

- Reviewed SHA：
- 归档提交范围：`docs/review/reports/**` + 任务包 §11/§12（仅此范围）
- 归档提交：由 `workflow-check` 按 `Reviewed SHA..HEAD` 识别，报告不记录归档提交自身 SHA
- `review:approved` 标签：待维护者添加
