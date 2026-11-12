---
id: T-000
title: <服务> <功能>
# 契约已冻结且维护者确认时可直接改为 ready；draft 仅用于契约/范围仍可能变化的例外
status: draft
implementer: codebuddy
created: YYYY-MM-DD
updated: YYYY-MM-DD
status_reason: <为什么处于当前状态>
contract_baseline: arch-baseline-vX.Y
related_adr: []
---

# T-000 <服务> <功能>

## 1. 目标与非目标

- 一句话目标：
- 业务背景：
- 关联需求 / Issue：
- 关联 ADR：
- 明确不做：

## 2. 上下文与必读（只列路径）

- 规则：`AGENTS.md`、`docs/rules/*`、目标模块 `AGENTS.md`
- 契约：`docs/contracts/openapi/<service>.yaml#<anchor>`、`docs/contracts/db/<service>.sql#<table>`、`docs/contracts/events/<event>.md`
- 现有实现 / 测试：
- 关联 ADR：

## 3. 变更范围

### 允许修改

```text
atifah-services/<service>/**
atifah-services/<service>/src/test/**
docs/tasks/T-000-<slug>.md              # 仅 front matter
```

> 只列实际需要的路径；不要写整个仓库。只允许改上述路径，其他一律禁止。

### 禁止修改

```text
docs/contracts/**
docs/rules/**
docs/adr/**
根 pom.xml
atifah-common/**
其他服务/**
```

## 4. 实现要点

- 入口 / 分层：
- 关键流程：
- 错误处理：
- 幂等 / 并发：
- 缓存 / 消息（如涉及）：

## 5. 验收标准（可验证）

- [ ] 接口行为与 OpenAPI 契约一致（列出关键请求/响应）。
- [ ] 正常路径：
- [ ] 失败路径：
- [ ] 权限 / 脱敏：
- [ ] 幂等：
- [ ] 性能 / 分页：
- [ ] 日志不含敏感明文。

## 6. 测试与自检

- 验证级别：A / B / C（定义见 `docs/review/standards.md` §4）
- [ ] 单元测试覆盖：<类/方法>
- [ ] 成功 / 失败 / 边界用例：
- [ ] 如涉及 MQ：重复消费、乱序、重试用例。
- [ ] 如涉及缓存：穿透 / 击穿 / 一致性用例。
- [ ] 如涉及前端：`npm run type-check`、`npm run build`。
- 自检命令：

```bash
mvn -pl atifah-services/<service> -am verify
# 或全量
mvn -B -ntp verify
```

## 7. 风险与回滚

- 风险：
- 回滚方式：
- 数据迁移影响：
