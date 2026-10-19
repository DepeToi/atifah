---
id: T-000
title: <服务> <功能> 
status: draft
owner: codebuddy
created: YYYY-MM-DD
contract_baseline: arch-baseline-v0.1
related_adr: []
---

# T-000 <服务> <功能>

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
```

> 只列实际需要的路径；不要写整个仓库。

### 禁止修改

```text
docs/contracts/**
docs/rules/**
docs/adr/**
根 pom.xml
atifah-common/**
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

## 7. 测试要求

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
```

## 10. 风险与回滚

- 风险：
- 回滚方式：
- 数据迁移影响：

## 11. 评审与自测试记录

| 轮次 | 评审人 | 自测试命令/结果 | 结论 | Blocker/Major | 处理 |
| --- | --- | --- | --- | --- | --- |
| 1 | Codex |  |  |  |  |
