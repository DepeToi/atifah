---
id: T-000
title: <服务> <功能>
change_class: feature
status: draft
owner: codebuddy
created: YYYY-MM-DD
updated: YYYY-MM-DD
status_reason: <为什么处于当前状态>
contract_baseline: arch-baseline-v0.1
related_adr: []
---

# T-000 <服务> <功能>

> 本模板只用于 **Feature** 变更。Arch 用 ADR + 契约 diff，Ops 用变更说明 + 验证命令，Docs 用 PR 描述；见 `AGENTS.md` §0。

## 1. 目标与背景

- 一句话目标：
- 业务背景：
- 关联需求 / Issue：
- 关联 ADR：

## 2. 非目标（明确不做）

- 
- 

## 3. 必读文件（按顺序）

- [ ] `AGENTS.md`
- [ ] `docs/rules/security.md`
- [ ] `docs/rules/code-style.md`
- [ ] `docs/rules/database.md`
- [ ] 目标模块 `AGENTS.md`：
- [ ] 契约：
  - `docs/contracts/openapi/<service>.yaml#<path-anchor>`
  - `docs/contracts/db/<service>.sql#<table>`
  - `docs/contracts/events/<event>.md`

## 4. 变更范围

### 允许修改

```text
atifah-services/<service>/**
atifah-services/<service>/src/test/**
docs/tasks/T-000-<slug>.md            # 仅 front matter / §11 / §12
docs/review/reports/T-000-r*-*.md     # 仅原样归档评审报告
```

> 只列实际需要的路径；不要写整个仓库。报告归档路径只允许原样复制，不得修改报告正文。

### 禁止修改

```text
docs/contracts/**
docs/rules/**
docs/adr/**
根 pom.xml
atifah-common/**
atifah-gateway/**
deploy/**
.github/**
其他服务/**
```

## 5. 实现要点

- 入口 / 分层：
- 关键流程：
- 错误处理：
- 幂等 / 并发：
- 缓存 / 消息（如涉及）：

## 6. 验收标准（可验证）

- [ ] 接口行为与 OpenAPI 契约一致（列出关键请求/响应）。
- [ ] 正常路径：
- [ ] 失败路径：
- [ ] 权限 / 脱敏：
- [ ] 幂等：
- [ ] 性能 / 分页：
- [ ] 日志不含敏感明文。
- [ ] 分支 `feat/T-000-<slug>`；单服务；≤600 行非测试代码、总计 ≤1000 行。
- [ ] 未修改契约、规则、ADR、父 pom、公共模块、网关、deploy 或其他服务。

## 7. 测试要求

- [ ] 测试层级：A / B（按 `docs/review/standards.md` §4 选择）。
- [ ] 单元测试覆盖：<类/方法>
- [ ] 成功 / 失败 / 边界用例：
- [ ] 如涉及 MQ：重复消费、乱序、重试用例。
- [ ] 如涉及缓存：穿透 / 击穿 / 一致性用例。
- [ ] 如涉及前端：`npm run type-check`、`npm run build`。

## 8. 交付物

- [ ] 代码实现
- [ ] 测试
- [ ] PR（关联本任务包）
- [ ] 契约引用更新（仅引用，不修改契约）
- [ ] 必要的 README / 注释

## 9. 自检命令

```bash
mvn -pl atifah-services/<service> -am verify
# 或全量
mvn -B -ntp verify
node .github/scripts/workflow-check.mjs --base origin/main --head HEAD --branch "$(git branch --show-current)"
```

## 10. 风险与回滚

- 风险：
- 回滚方式：
- 数据迁移影响：

## 11. 评审与自测试记录

- 评审报告：`docs/review/reports/T-000-r<n>-<reviewed-sha>.md`
- Reviewed SHA：<被评审的最后一个代码提交>
- 归档 SHA：<报告归档提交>
- Verdict：`APPROVE` / `REQUEST_CHANGES`
- 自测试层级：A / B

| 轮次 | 评审人 | 自测试命令/结果 | 结论 | Blocker/Major | 处理 |
| --- | --- | --- | --- | --- | --- |
| 1 | Codex |  |  |  |  |

## 12. 状态变更记录

> 每次状态变更必须更新 front matter 的 `status` / `updated` / `status_reason`，并在此追加一行；没有记录的状态变更无效（DOC-04）。

| 日期 | 从 | 到 | 操作人 | 依据/证据 |
| --- | --- | --- | --- | --- |
| YYYY-MM-DD | draft | ready | Claude | 维护者批准 + 契约冻结（arch-baseline-vX.Y） |
| YYYY-MM-DD | ready | in-review | CodeBuddy | 分支已推送 + PR 已创建 |
| YYYY-MM-DD | in-review | done | 维护者 | PR 已合并 + 报告已归档（批量收口） |
