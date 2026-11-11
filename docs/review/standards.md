# 代码评审规则（Codex）

> 本文件是 **Codex 只读评审 + 风险匹配验证**的流程、分级与规则编号判据；具体规则正文与阈值以 `docs/rules/*`、`docs/contracts/*` 为准，本表只保留检查点与默认级别。规则编号同时被 `AGENTS.md`、`docs/rules/*` 与任务包引用。
> 目标：让评审结果稳定、可追踪、可自动化；不把风格偏好伪装成缺陷。变更分类与工作流见 `AGENTS.md`。

## 1. 评审输入（按变更类型）

| 变更类型 | 必需输入 |
| --- | --- |
| **Feature** | 任务包 `docs/tasks/T-xxx-<slug>.md`（目标、允许修改路径、验收标准） |
| **Arch** | ADR（`docs/adr/NNNN-*.md`）+ 涉及契约时的契约 diff |
| **Ops** | PR 描述中的变更说明、影响面、验证命令与回滚方式 |
| **Docs** | PR 描述与变更内容 |

所有类型还必须提供：

1. 相关契约：`docs/contracts/` 中与变更相关的 OpenAPI / DDL / 事件文件。
2. diff：`git diff <base>...<head>`，只评审本次变更及直接受影响的调用方。
3. CI 结果：`.github/workflows/ci.yml` 必须通过。
4. 规则：本文件 + `docs/rules/*.md` + 目标模块 `AGENTS.md`。
5. 验证证据：Reviewed SHA 对应的 CI 结果；如需补测，记录命令与结果。

缺少该类型的必需产物，或变更类型与实际 diff 不符时，直接 `REQUEST_CHANGES`（SCOPE-01），不要开始逐行评审。

Docs 类默认由 CI + 人工完成，Codex 评审可选；维护者要求复审时按 C 级执行。Ops 类由 Codex 做轻评审（安全/配置/回滚），按 B 级验证。

## 2. 结论与分级

| 级别 | 定义 | 合并影响 |
| --- | --- | --- |
| `Blocker` | 违反硬约束；安全/数据/契约/架构边界被破坏；会导致生产故障或数据错误 | 必须修复 |
| `Major` | 高概率缺陷；核心链路缺测试；明显维护性/性能风险 | 必须修复或由维护者书面豁免 |
| `Minor` | 规则内的命名、注释、可读性问题 | 可不阻塞，记录跟进 |
| `Question` | 信息不足，无法确认；涉及契约/安全时必须回答 | 不阻塞，但需在合并前澄清 |

**结论规则：**

- 任一 `Blocker` 或 `Major` → `REQUEST_CHANGES`。
- 零 `Blocker` / `Major` → `APPROVE`，可附带 `Minor` / `Question`。
- CI 未通过、要求的风险测试未执行或失败、该类型必需产物缺失、任务状态与证据不符 → `REQUEST_CHANGES`。
- PR 评论是默认评审记录；报告文件可选归档，不作为 APPROVE、合并或任务关闭的前置条件。
- 未检查到的文件或行为必须在 PR 评论中写明“未覆盖”，不得默认认为没问题。

## 3. 评审流程

1. **分类与产物**：确认 PR 属于 Feature / Arch / Ops / Docs；按 §1 检查该类型的必需产物。混合变更按最高风险类型处理。
2. **范围与状态**：确认 Feature 只改任务包允许路径，Arch/Ops/Docs 只改 PR 描述列出的路径；Feature 任务状态是否由授权角色设置、是否与 PR/合并证据一致（四状态：`draft` / `ready` / `in-review` / `done`）。
3. **契约一致性**：接口路径、字段、错误码、DDL、事件 payload 是否与冻结契约一致。
4. **架构与数据**：服务边界、跨库访问、同步/异步方式、公共模块依赖。
5. **安全**：敏感字段、日志、密钥、鉴权、幂等。
6. **正确性**：空值/边界/并发/事务/MQ 重复消费；错误处理是否吞异常。
7. **测试质量**：是否有真实断言；是否覆盖成功、失败、幂等/重复消费等关键路径。
8. **验证**：按 §4 核验 Reviewed SHA 的 CI 结果；仅在覆盖不足或风险较高时补跑测试，并记录命令、结果与未覆盖范围。
9. **输出**：每条问题带规则编号、文件行号、证据与修复建议；在 PR 评论给出结论、Reviewed SHA 与验证证据。

评审只看 diff 和直接受影响的代码，**不要通读整个仓库**；生成文件、`node_modules/`、`target/` 不评审。

## 4. 验证要求（A/B/C）

> CI 已覆盖的全量测试不要求 Codex 重复执行。Codex 必须确认 CI 针对 Reviewed SHA 通过，并按风险验证 CI 未覆盖的关键路径。

### 4.1 分级

| 级别 | 适用范围 | 必跑 |
| --- | --- | --- |
| **A** | Feature、Arch、`common`、`gateway`、契约、安全相关 | 确认对应 SHA 的全量 CI 通过；对 CI 未覆盖的受影响模块或高风险路径补跑针对性测试 |
| **B** | Ops：`deploy`、配置、依赖升级、构建脚本、Dockerfile | 确认 CI 通过；按需验证受影响配置，例如 `docker compose config -q` 或 YAML 解析 |
| **C** | Docs：纯文档/规则（不含契约） | 检查本次修改的链接、格式与内容；可免构建 |

- CI 已覆盖全量构建和测试时，不要求再执行全量 `mvn verify` 或前端构建。
- CI 未运行、未覆盖变更模块或失败时，Codex 执行能覆盖缺口的最小测试；环境限制必须说明，不得将未覆盖写成通过。
- 涉及契约的文档变更按 A 级处理。

### 4.2 执行环境

- 本地补测应从干净检出或 worktree 执行，并确认检出的提交是 PR 的 Reviewed SHA；无需仅为复用 CI 创建额外工作区。
- 后端：JDK 21 + Maven 3.9+；前端：Node 22 + npm（与 CI 一致）。
- 不修改代码、测试、配置来“让测试通过”；不提交 `target/`、`dist/`、`node_modules/`。

示例（Git Bash）：

```bash
git fetch origin
git worktree add ../atifah-codex-review <head-sha>
cd ../atifah-codex-review
mvn -B -ntp verify
cd atifah-admin-web/frontend && npm ci && npm run build
```

### 4.3 记录与判定

- PR 评论必须记录：**Reviewed SHA**、CI 状态、实际补跑的命令与结果、失败用例及未覆盖范围。
- 补测失败 → `REQUEST_CHANGES`；若确认是环境问题（如缺少 MySQL），标 `[Question]` 并说明未覆盖范围，不得将其视为通过。
- CI 失败或未针对 Reviewed SHA 运行 → `REQUEST_CHANGES`；分析差异或等待 CI 全绿。
- 复审以实际评审和验证的 Reviewed SHA 为准。

## 5. 规则目录

### 5.0 规则编号别名

同一根因只报一个 canonical ID；别名仅用于索引，不重复计数。

| canonical | alias |
| --- | --- |
| SCOPE-01 | GIT-03 |
| ARCH-01 | DB-01 |
| API-04 | SEC-04 |
| DB-03 | CODE-06 |
| DOC-02 | GIT-05 |
| OPS-02 | GIT-04 |

### 5.1 范围与架构

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| SCOPE-01 | 变更类型与 diff 范围一致；具备该类型必需产物；Feature 只改任务包允许路径，Arch/Ops/Docs 只改 PR 描述列出的路径 | Blocker |
| SCOPE-02 | 一个 PR 只对应一个任务/变更；生产语义 diff ≤400 行（测试不计入，口径见本节说明） | Major |
| SCOPE-03 | 未授权修改契约/规则/父 pom/受保护文件 | Blocker |
| ARCH-01 | 一服务一库；禁止跨库 JOIN、访问其他服务表 | Blocker |
| ARCH-02 | 跨服务同步走 OpenFeign、异步走 RocketMQ；禁止共享库绕过 | Blocker |
| ARCH-03 | `common-*` 不依赖 service；网关不引入 `common-web` / `common-mybatis` / servlet 栈 | Blocker |
| ARCH-04 | 公共能力走 `AutoConfiguration.imports`；业务服务不得加 `@ComponentScan("com.atifah")` | Major |

**SCOPE-02 统计口径**：只统计生产代码、SQL、配置、契约与规则的语义新增/删除行；测试代码、纯格式化、生成文件不计入。测试代码规模仍需在 PR 描述中说明，评审关注覆盖质量而非行数。

### 5.2 接口

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| API-01 | 统一 `Result<T>` / `PageResult<T>`，`code=0` 成功，无自定义响应壳 | Blocker |
| API-02 | 错误码只取 `ErrorCode`；业务失败抛 `BusinessException`；Controller 不 try-catch 包装 | Major |
| API-03 | 路径 `/api/v1/**`、`/admin/api/**`；只经网关访问 | Blocker |
| API-04 | 写接口幂等：要求见 `docs/contracts/api-conventions.md` §7（与 SEC-04 同一根因合并报告） | Blocker |
| API-05 | 分页 `page`/`size` 有上限；禁止无界查询 | Major |
| API-06 | ID 以 string 返回；时间 `yyyy-MM-dd HH:mm:ss`；camelCase；变更向后兼容 | Major |

### 5.3 数据库、缓存与消息

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| DB-01 | 数据库边界：一服务一库；禁止跨库 JOIN、访问其他服务表（与 ARCH-01 合并报告） | Blocker |
| DB-02 | 禁止 `SELECT *`、拼接 SQL、N+1、循环内查库；所有 SQL（含写入）落在 `resources/mapper/*.xml`（Mapper 不继承 `BaseMapper`，禁止 `BaseMapper`/`IService` CRUD；XML 显式带 `deleted = 0`、`id` 由 Service 生成）；唯一业务键有唯一索引 | Major（拼接 SQL/注入风险为 Blocker） |
| DB-03 | 实体继承 `BaseEntity`；雪花 ID 由 Service 生成；逻辑删除与时间字段由 XML/数据库默认值显式维护；Flyway 与 `docs/contracts/db/` 一致，已执行脚本未修改 | Major（Schema/迁移与契约不一致为 Blocker） |
| DB-04 | `@Transactional` 只在 Service；事务内不做 RPC/MQ/文件 IO | Major |
| DB-05 | 缓存 Key 登记 `CacheKeys`；Cache Aside；缓存不是唯一数据源 | Major |
| DB-06 | 分片键命中查询；无跨分片聚合 | Major |
| MQ-01 | Topic / 消费组登记常量；只用 `MessagePublisher` | Major |
| MQ-02 | 消费幂等（`msgId` + 业务唯一键 + 状态机）；重试/死信/告警；不吞异常 | Blocker |
| MQ-03 | 消息 key 用业务键；消息体无敏感明文；与事件契约一致 | Blocker（敏感明文/契约不一致） |

### 5.4 安全

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| SEC-01 | 敏感字段按 SEC-01 分级处理（加密或脱敏）；日志、异常、MQ、URL 无明文 | Blocker |
| SEC-02 | 无硬编码密码、密钥、Token、AK/SK | Blocker |
| SEC-03 | 不绕过网关；业务服务不重复验签；管理端不直连业务库 | Blocker |
| SEC-04 | 写接口幂等：执行契约 §7 与 `docs/rules/security.md` SEC-04（与 API-04 合并报告） | Blocker |
| SEC-05 | CORS、上传校验、SSRF 白名单、防刷符合规则 | Major |

### 5.5 编码、依赖与测试

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| CODE-01 | 命名/常量/日志占位符规范；无 `System.out` / `printStackTrace` / `console.log` | Minor（敏感日志按 SEC-01） |
| CODE-02 | Controller 不写业务逻辑；Service 不读 `HttpServletRequest`；分包正确 | Major |
| CODE-03 | 启动类三注解齐全 | Major |
| CODE-04 | 构造注入；禁止字段 `@Autowired`；Lombok 仅用于样板代码 | Major |
| CODE-05 | `@Valid` 校验；统一异常；禁止自定义响应壳 | Major |
| CODE-06 | 实体/Mapper 规范；Mapper 不继承 `BaseMapper`，所有 SQL 在 XML（与 DB-02 同一根因时合并报告） | Major |
| CODE-07 | 不手拼 TraceId；异常日志保留堆栈；业务异常不重复打印 | Minor |
| CODE-08 | 删除测试、关闭校验、`@SuppressWarnings` / `@ts-ignore` 掩盖问题 | Blocker |
| CODE-09 | 可读性：过度嵌套、无必要抽象/包装、压缩写法影响维护；结构调整改变原行为时按正确性规则升级 | Minor |
| DEPS-01 | 新增依赖先确认；版本只在根 pom；无第二套 JSON/日志/HTTP 客户端 | Blocker |
| DEPS-02 | 修改 parent / BOM 版本 | Blocker |
| TEST-01 | 核心链路有测试，且断言行为而非只编译 | Major |
| TEST-02 | 测试可重复，不依赖真实外部中间件（或明确标记集成测试） | Major |
| TEST-03 | Codex 未核验 Reviewed SHA 的 CI，或未执行要求的风险测试仍给出 `APPROVE` | Blocker（流程检查） |

### 5.6 流程与文档

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| DOC-01 | 契约变更先有 ADR，并单独开 PR | Blocker |
| DOC-02 | 接口/路由/端口/配置变更同步文档与常量登记 | Major |
| DOC-03 | PR 缺少变更类型、验证证据或必要的评审结论 | Major（流程检查） |
| DOC-04 | Feature 任务状态被未授权角色修改、跳过必经状态，或与 PR/合并证据不符 | Blocker（流程检查） |
| OPS-01 | 配置分层正确；共享配置只改 `common-application.yaml` 并提醒导入 Nacos | Major |
| OPS-02 | 修改受保护部署文件、端口映射、已执行 SQL、`.gitattributes` | Blocker |
| GIT-01 | AI 未经用户要求自动 `commit` / `push` | Blocker（流程检查） |
| GIT-02 | 分支命名、PR 模板、单任务/单变更单 PR | Major（流程检查） |
| GIT-03 | 变更边界（与 SCOPE-01 合并报告） | Major（别名） |
| GIT-04 | 修改受保护文件（与 OPS-02 合并报告） | Blocker（别名） |
| GIT-05 | 接口/路由/端口/配置变更未同步文档与常量（与 DOC-02 合并报告） | Major（别名） |
| GIT-06 | 提交前自检未执行或未通过却声称完成 | Major |
| GIT-07 | 多个工具并发修改同一批文件 | Blocker（流程检查） |

## 6. 输出格式（必须固定）

在 PR 评论中按以下格式输出，一条问题一个条目：

```text
[Blocker] path/to/File.java:42 — SEC-01 — 日志打印了日记正文
  证据：log.info("content={}", diary.getContent());
  影响：敏感个人信息泄露，违反数据分级要求。
  建议：改为 log.info("diaryId={}", diary.getId()); 并补充脱敏测试。

[Major] path/to/Service.java:88 — ARCH-01 — 循环内逐条查库
  证据：for (Long id : ids) { mapper.selectById(id); }
  影响：N+1，批量场景放大数据库压力。
  建议：改为 selectBatchIds(ids) 或一次 IN 查询。

[Question] path/to/Controller.java:15 — API-06 — 新增字段是否保持向后兼容？
  说明：前端旧版本是否会读取该字段？

Validation:
  Reviewed SHA: <reviewed-sha>
  CI: PASS
  additional tests: <command/result, or none required>
  notes: 未覆盖 MySQL/Redis 集成测试

Verdict: REQUEST_CHANGES
```

要求：

- 每条必须带**规则编号**、`file:line`、**证据**、**影响**、**建议**；没有证据的猜测标 `Question`。
- 同一根因只报一次，选最具体的 canonical 规则编号，不重复计数（别名见 §5.0）。
- 不要写“建议重构”“可以考虑优化”这类无法执行的结论。
- `Minor` 可以合并为一条列表，但仍需带规则编号与位置。
- 必须附 `Validation` 块：Reviewed SHA、CI 结果、补充测试（如有）及未覆盖范围；没有对应 SHA 的 CI 结果不得给出 `APPROVE`。
- PR 评论即为默认记录；如需归档完整报告，可按 `report-template.md` 写入 `docs/review/reports/`，无需额外归档提交。

### 6.1 评审记录归档

> PR 评论为默认评审记录。完整报告可按需归档；命名与保留规则见 [`reports/README.md`](reports/README.md) 和 [`report-template.md`](report-template.md)。不需要额外 docs-only 提交或更新任务包评审日志。

## 7. 分流与升级

- `[Arch]`：架构/契约/跨服务边界问题 → 回到 Claude，先改 ADR 与契约。
- `[Impl]`：实现问题 → 回到实现者修复，Codex 复审。
- `[Question]`：信息不足或产品决策 → 由人类维护者确认。
- 同一规则连续两轮被同一类问题触发 → 建议 Claude 更新规则或增加自动化检查。

## 8. Codex 禁止事项

- 不直接修改代码、不提交修复；评审是只读的，但可以在独立工作区运行构建与测试，并写 `docs/review/reports/**` 评审报告。
- 不评审任务范围外的文件，不提出大规模重构。
- 不发明需求或验收标准；以任务包、PR 描述和契约为准。
- 不因个人风格偏好判 `Blocker` / `Major`；无规则依据的只算 `Minor` 或不报。
- 必须按 §4 核验 CI，并按需补跑 CI 未覆盖的风险测试；不得重复执行 CI 已覆盖的全量测试。
- 不得修改代码/测试/配置让验证通过，不得提交构建产物。
- 不得修改任务包状态；状态由授权角色维护。
- 不得在 CI 失败、要求的风险测试未执行或失败、该类型必需产物缺失、任务状态与证据不符、契约缺失时给出 `APPROVE`。
- PR 评论需包含评审结论与验证证据；完整报告文件可选。

## 9. 可选报告模板

见 [`report-template.md`](report-template.md)。需要在仓库中保留完整报告副本时，按 [`reports/README.md`](reports/README.md) 命名；PR 评论始终是必需的评审记录。
