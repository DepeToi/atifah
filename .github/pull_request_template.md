<!--
标题格式：Feature 用 type(scope): T-xxx 摘要；Arch/Ops/Docs 用 type(scope): 摘要
例：feat(auth): T-001 登录与 Token 签发 / docs(workflow): 工作流 v1.1
一个 PR 只对应一个变更；Feature 一个 PR 对应一个任务包；契约变更必须单独开 PR。
-->

## 0. 变更类型（必选）

- [ ] Feature（任务包）
- [ ] Arch（ADR + 契约 diff）
- [ ] Ops（变更说明 + 验证命令）
- [ ] Docs（PR 描述）

## 1. 关联

- 任务包（Feature）：`docs/tasks/T-xxx-<slug>.md`
- 任务状态（Feature）：`in-review`（PR 创建后；状态机见 `docs/tasks/README.md` §2，合并后由维护者批量改为 `done`）
- 契约基线（Feature/Arch）：`arch-baseline-vX.Y`
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

> 细则见 `AGENTS.md` §4 与 `docs/rules/*`；此处只保留提交前确认项。

- [ ] 变更类型与 diff 范围一致（Feature 未夹带 Arch/Ops 路径）
- [ ] Feature 只修改任务包“允许修改”的路径；Arch/Ops/Docs 只修改 PR 描述列出的路径
- [ ] 未修改 `docs/contracts/**`、`docs/rules/**`、根 `pom.xml`、受保护部署文件（Arch 变更除外）
- [ ] 接口符合 `Result<T>` / `PageResult<T>` 与 `ErrorCode`
- [ ] 写接口支持 `Idempotency-Key`
- [ ] 无跨库 JOIN / 跨服务直连数据库
- [ ] MQ 消费幂等、重试/死信处理完整（如涉及）
- [ ] 日志、异常、MQ、URL 无敏感明文
- [ ] 无硬编码密钥/密码/Token
- [ ] 新增依赖已确认，版本只在根 `pom.xml`
- [ ] 任务状态按状态机更新，并在任务包 §8.2 记录（仅 Feature；无 PR 不标 `in-review`，未合并不标 `done`）
- [ ] 文档、常量登记、前端类型已同步

## 6. 风险与回滚

- 风险：
- 回滚方式：
- 数据迁移 / 兼容性影响：

## 7. Codex 评审记录

> 完整格式、自测试记录与归档要求见 `docs/review/standards.md` §6.1；此处只记录摘要。

- 结论：`APPROVE` / `REQUEST_CHANGES`
- Reviewed SHA（Codex 实际测试的代码提交）：
- 评审报告：`docs/review/reports/<T-xxx-rN|PR-n>-<reviewed-sha>.md`
- 归档提交（如已归档）：
- Blocker / Major：
- 处理说明：

## 8. 给评审者的提示

- 重点看：
- 已知限制：
- 不需要评审的文件：
