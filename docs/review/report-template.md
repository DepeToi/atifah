# 评审报告

> 评审对象：<PR 链接 / 分支 / commit 范围>
> 任务包：<docs/tasks/T-xxx-xxx.md>
> 契约基线：<arch-baseline-vX.Y>
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

- [ ] diff 只在任务包允许路径内
- [ ] 未修改契约 / 规则 / 父 pom / 受保护文件
- [ ] 单任务、单服务、diff ≤400 行
- [ ] CI 通过
- [ ] 已在独立 worktree / 干净检出执行自测试并记录

## 自测试记录

- commit：
- 工作区：独立 worktree / 干净检出（路径：）
- 环境：JDK 21 / Maven 3.9.x / Node 22（按实际填写）

| 命令 | 结果 | 耗时 | 备注 |
| --- | --- | --- | --- |
| `mvn -B -ntp verify` | PASS / FAIL |  |  |
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
