<!--
标题格式：type(scope): T-xxx 摘要
例：feat(auth): T-001 登录与 Token 签发
一个 PR 只对应一个任务包；契约变更必须单独开 PR。
-->

## 1. 关联

- 任务包：`docs/tasks/T-xxx-<slug>.md`
- 契约基线：`arch-baseline-vX.Y`
- 关联 ADR / Issue：

## 2. 变更内容

- 改了什么：
- 为什么：
- 影响面（服务 / 接口 / 表 / 前端页面）：

## 3. 契约影响

- [ ] 无契约变更
- [ ] 有契约变更（必须单独 PR，链接：）

## 4. 验证证据

```text
# 命令与真实结果，不要只写“已通过”
mvn -B -ntp verify
cd atifah-admin-web/frontend && npm run build
```

- [ ] CI 全绿
- [ ] 新增/修改测试已运行，失败路径有覆盖
- [ ] 手动验证路径（如适用）：

## 5. 自检清单

- [ ] 只修改任务包“允许修改”的路径
- [ ] 未修改 `docs/contracts/**`、`docs/rules/**`、根 `pom.xml`、受保护部署文件
- [ ] 接口符合 `Result<T>` / `PageResult<T>` 与 `ErrorCode`
- [ ] 写接口支持 `Idempotency-Key`
- [ ] 无跨库 JOIN / 跨服务直连数据库
- [ ] MQ 消费幂等、重试/死信处理完整（如涉及）
- [ ] 日志、异常、MQ、URL 无敏感明文
- [ ] 无硬编码密钥/密码/Token
- [ ] 新增依赖已确认，版本只在根 `pom.xml`
- [ ] 文档、常量登记、前端类型已同步

## 6. 风险与回滚

- 风险：
- 回滚方式：
- 数据迁移 / 兼容性影响：

## 7. Codex 评审记录

- 结论：`APPROVE` / `REQUEST_CHANGES`
- 自测试：`mvn -B -ntp verify` / `npm ci && npm run build` → PASS / FAIL
- 自测试环境：独立 worktree / 干净检出（commit：）
- Blocker：
- Major：
- 处理说明：

## 8. 给评审者的提示

- 重点看：
- 已知限制：
- 不需要评审的文件：
