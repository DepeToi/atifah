# 评审报告

> 报告路径：docs/review/reports/T-005-6fcf4be.md
> 评审对象：PR #3（DepeToi/atifah），分支 `fix/T-005-auth-register-idempotency-hardening`，head `6fcf4be`
> 任务包：docs/tasks/T-005-auth-register-idempotency-hardening.md
> 契约基线：arch-baseline-v0.2
> 评审人：Codex
> 日期：2026-10-19

## 结论

`APPROVE`

- Blocker：0
- Major：0
- Minor：0
- Question：0
- 自测试：PASS

## 范围检查

- [x] diff 只在任务包允许路径内（`AuthServiceImpl.java`、`AuthServiceImplTest.java`、T-005 任务包）
- [x] 未修改契约 / 规则 / 父 pom / 受保护文件
- [x] 单任务、单服务、diff ≤400 行（3 文件，+83 / -10）
- [x] CI 通过：维护者确认 PR #3 CI 全绿（2026-10-19），已合并（merge commit `abf315f`）
- [x] 已在独立 worktree 执行自测试并记录
- [x] 报告已落盘到 `docs/review/reports/`
- [x] 任务状态与证据一致：`in-review` 对应 PR #3（head=`6fcf4be`）；§12 有 `ready → in-progress → in-review` 记录

## 自测试记录

- commit：6fcf4be8edc795a25bea85069aea173b009a6f3e
- 工作区：独立 worktree（`D:\Project\atifah-codex-review-T005`，detached @ 6fcf4be，自测后已清理）
- 环境：JDK 21.0.2 / Maven 3.9.12；前端未涉及

| 命令 | 结果 | 耗时 | 备注 |
| --- | --- | --- | --- |
| `mvn -B -ntp verify` | PASS | 01:11 | 20 模块全部 SUCCESS；auth-service 27 tests 通过 |
| `npm ci && npm run build` | 未涉及 | - | 本次仅改 auth-service Java 与文档 |

- 失败用例：无
- 未覆盖范围：auth-service 仅 Mockito 单测，未运行真实 Redis / MySQL 集成；其余 8 个业务服务为骨架无测试
- 与 CI 结果是否一致：一致。本环境无法直接读取私有仓库 CI API，以维护者确认为准（PR #3 CI 全绿）

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
无。逐条核对结论：
- API-04 / SEC-04：UUID 校验（8-4-4-4-12、大小写均可）在写 Redis / 查库之前完成，缺失与格式非法均返回 40000；契约 §7 与 auth-service.yaml 一致。
- 并发有界重读：DuplicateKeyException 分支内最多读取 3 次（含首次立即读取）、间隔 50ms、总等待 ≤100ms，取到返回首次 userId，否则 40900；与 api-conventions.md §7、ADR-0002 §5.1 一致。
- 中断处理：恢复中断标记并 log.warn 后返回 null，符合任务包 5.2 规定。
- SEC-01：日志仅含 userId / 失败类别，未出现 Idempotency-Key、手机号、密码、token。
- 测试：新增 malformed key、冲突重读命中、冲突重读耗尽 3 个用例，断言真实；既有用例同步替换为合法 UUID，无删除测试、无 @SuppressWarnings。
```

## 未覆盖 / 不确定

- 未能检查的文件或行为：无。GitHub CI 运行状态已由维护者确认全绿并完成合并（`abf315f`）。
- 需要人工确认的产品/架构问题：`Idempotency-Key` 大小写是否需归一化。当前正则接受大小写十六进制，但 Redis key 直接使用原始字符串，同一 UUID 以不同大小写重试会被视为两个不同幂等键；契约仅要求“大小写均可”，未要求归一化，且唯一索引兜底保证无数据错误，不阻塞合并。

## 建议回归

- [ ] 如需更强一致性，可在拼 Redis key 前对 `Idempotency-Key` 做 `toLowerCase()` 并补一个大小写归一化单测（可选，需先经维护者确认，不属本任务强制项）。
- [ ] 合并前由维护者确认 CI 全绿；由 CodeBuddy 将本报告路径与 Verdict 写入任务包 §11。
