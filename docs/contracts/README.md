# 契约目录（docs/contracts）

> 这里是 Claude 产出、CodeBuddy 实现、Codex 校验与风险验证的**接口唯一依据**。
> 契约一旦冻结，优先级高于实现代码；实现与契约冲突时以契约为准，改契约必须先走 ADR。

## 1. 目录结构

```text
docs/contracts/
├── api-conventions.md          # 接口总纲：响应、错误码、鉴权、分页、命名
├── openapi/
│   ├── _template.yaml          # OpenAPI 3.1 模板
│   └── <service>.yaml          # 每个服务一份，如 auth-service.yaml
├── db/
│   ├── _template.sql           # 逻辑 Schema 模板
│   └── <service>.sql           # 每个服务一份，如 mood-service.sql
└── events/
    ├── _template.md            # 事件契约模板
    └── <event-name>.md         # 每个事件一份，如 mood-recorded.md
```

- 业务服务名统一用 `atifah-<service>` 去掉 `atifah-` 前缀后的名称，与目录 `atifah-services/<service>` 一致；管理端 BFF 使用 `admin-service`。
- 事件名用 kebab-case，与 `MqTopics` 常量一一对应。
- 模板文件以 `_` 开头，不会被当作已冻结契约。

## 2. 所有权与写入权限

角色与可写范围以 `AGENTS.md` §0 为唯一来源。契约相关约束只有一条：`docs/contracts/**` 由 Claude 写入，CodeBuddy / Codex 只读。

CodeBuddy **禁止**修改契约来迁就实现。发现契约缺失或矛盾时，在 PR 中标注 `[Contract-Question]` 并等待 Claude 更新。

## 3. 冻结与版本

- 架构阶段完成后，由人工确认，在 `main` 上打标签：`arch-baseline-vX.Y`（例如 `arch-baseline-v0.1`）。
- 功能任务包必须声明所依赖的基线版本；CodeBuddy 从对应基线之后的最新 `main` 拉分支。
- 冻结后的契约变更流程：
  1. Claude 新增/更新 ADR，说明变更原因、影响范围与兼容性。
  2. 单独开 `arch/<topic>` PR，只改契约与相关文档。
  3. 人工评审通过后合并，必要时打新基线标签。
  4. CodeBuddy 基于新契约更新实现；不得在同一 PR 中同时改契约和实现。
- 兼容性规则：
  - 新增可选字段、新增接口 = 向后兼容，可留在原版本。
  - 删除/重命名字段、改变语义、收紧必填 = 破坏性变更，必须升级路径版本（如 `/api/v2`）或事件版本。
  - 错误码只允许新增通用码；禁止改变既有码的含义。

## 4. 冻结检查清单

- [ ] 每个接口都有请求/响应示例、错误码、鉴权要求。
- [ ] 所有写接口都声明 `Idempotency-Key`。
- [ ] 分页参数、时间格式、ID 类型符合 `api-conventions.md`。
- [ ] DDL 包含主键、唯一索引、审计字段、字符集与注释。
- [ ] 敏感字段已标注加密或脱敏方式，且不出现在日志/消息明文中。
- [ ] 事件契约包含 Topic、生产者、消费者、key、幂等键、重试/死信策略。
- [ ] 契约与 `docs/architecture.md`、`AGENTS.md` 无矛盾。
- [ ] 变更已记录 ADR（新建或更新）。

## 5. 工具如何消费契约

- **Claude**：写契约、维护模板与总纲；通过 ADR 记录取舍。
- **CodeBuddy**：Feature 任务包“上下文与必读”列出具体契约路径；实现以契约为准，不自行扩展字段。
- **Codex**：评审时对照契约检查 diff；Arch 变更检查 ADR 与契约 diff；CI 核验与风险验证要求见 `docs/review/standards.md` §4/§6。

## 6. 相关文档

- [接口总纲](api-conventions.md)
- [任务包规范](../tasks/README.md)
- [评审规则](../review/standards.md)
- [架构说明](../architecture.md)
- [ADR](../adr/README.md)
