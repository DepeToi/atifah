<!--
标题格式：type(scope): T-xxx 摘要 / type(scope): 摘要
例：feat(auth): T-001 登录与 Token 签发；ops(deploy): Nacos 配置持久化
一个 PR 只做一件事；契约变更必须单独开 arch/* PR。
-->

## 1. 关联

- 变更类型：`Feature` / `Arch` / `Ops` / `Docs`（混合变更按最高风险填写）
- 任务包（仅 Feature）：`docs/tasks/T-xxx-<slug>.md`
- ADR（Arch / 有决策的 Ops）：`docs/adr/NNNN-*.md`
- 任务状态（仅 Feature）：`in-review`（PR 创建后；状态机见 `docs/tasks/README.md` §2）
- 契约基线：`arch-baseline-vX.Y`（无契约变更填“无”）
- 关联 Issue：

## 2. 变更内容

- 改了什么：
- 为什么：
- 影响面（服务 / 接口 / 表 / 前端页面 / 部署）：

## 3. 契约与变更类型

- [ ] 无契约变更
- [ ] 有契约变更（必须是单独的 `arch/*` PR，链接：）
- [ ] 本 PR 未夹带其他类型的改动（Feature 不含 Arch/Ops 路径）

## 4. 验证证据

```text
# 命令与真实结果，不要只写“已通过”
mvn -B -ntp verify
cd atifah-admin-web/frontend && npm run build
node .github/scripts/workflow-check.mjs --base origin/main --head HEAD --branch "$(git branch --show-current)" --local-precheck
```

- [ ] CI 构建/测试/密钥扫描通过；`workflow-check` 在评审归档并加 `review:approved` 后通过
- [ ] 自测试级别：A / B / C；新增/修改测试已运行，失败路径有覆盖
- [ ] 手动验证路径（如适用）：

> 本地预检加 `--local-precheck`：允许 `ready`/`in-review`，跳过 `review:approved` 与归档报告校验，仅用于 PR 创建前。PR 门禁只接受 `in-review`；缺少标签或归档报告会失败，这是预期行为。

## 5. 自检清单

> 细则见 `AGENTS.md` §0/§4 与 `docs/rules/*`；此处只保留提交前确认项。

- [ ] 变更类型与 diff 一致，只修改允许路径
- [ ] 规模符合阈值：Feature ≤600 行非测试代码（总计 ≤1000）；Arch/Ops ≤500 行非文档代码
- [ ] 未修改 `docs/contracts/**`、`docs/rules/**`、根 `pom.xml`、受保护部署文件（除非属于对应类型且已获批准）
- [ ] 接口符合 `Result<T>` / `PageResult<T>` 与 `ErrorCode`
- [ ] 写接口支持 `Idempotency-Key`
- [ ] 无跨库 JOIN / 跨服务直连数据库
- [ ] MQ 消费幂等、重试/死信处理完整（如涉及）
- [ ] 日志、异常、MQ、URL 无敏感明文
- [ ] 无硬编码密钥/密码/Token
- [ ] 新增依赖已确认，版本只在根 `pom.xml`
- [ ] 任务状态按四状态机更新，并在任务包 §12 记录（无 PR 不标 `in-review`）
- [ ] 文档、常量登记、前端类型已同步

## 6. 风险与回滚

- 风险：
- 回滚方式：
- 数据迁移 / 兼容性影响：

## 7. Codex 评审记录

> 完整格式、自测试分级与归档要求见 `docs/review/standards.md` §6.1；此处只记录摘要。

- 结论：`APPROVE` / `REQUEST_CHANGES`
- Reviewed SHA：
- 评审报告：`docs/review/reports/T-xxx-rN-<reviewed-sha>.md`（非 Feature：`PR-<n>-<reviewed-sha>.md`）
- Blocker / Major：
- 处理说明：
- [ ] 维护者已添加 `review:approved` 标签（Feature/Arch/Ops 必需；推送新代码后需移除并复审）

## 8. 给评审者的提示

- 重点看：
- 已知限制：
- 不需要评审的文件：
