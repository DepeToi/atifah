# 代码评审规则（Codex）

> 本文件是 **Codex 只读评审 + 独立自测试 + 报告归档**的流程、分级与规则编号判据；具体规则正文与阈值以 `docs/rules/*`、`docs/contracts/*` 为准，本表只保留检查点与默认级别。规则编号同时被 `AGENTS.md`、`docs/rules/*` 与任务包引用。
> 目标：让评审结果稳定、可追踪、可自动化；不把风格偏好伪装成缺陷。变更分类与工作流见 `AGENTS.md`。

## 1. 评审输入（按变更类型）

| 变更类型 | 必需输入 |
| --- | --- |
| **Feature** | 任务包 `docs/tasks/T-xxx-<slug>.md`（目标、允许修改路径、验收标准） |
| **Arch** | ADR（`docs/adr/NNNN-*.md`）+ 涉及契约时的契约 diff |
| **Ops** | PR 描述中的变更说明、影响面、验证命令与回滚方式 |
| **Docs** | PR 描述与链接/格式检查结果 |

所有类型还必须提供：

1. 相关契约：`docs/contracts/` 中与变更相关的 OpenAPI / DDL / 事件文件。
2. diff：`git diff <base>...<head>`，只评审本次变更及直接受影响的调用方。
3. CI 结果：`.github/workflows/ci.yml` 必须通过。
4. 规则：本文件 + `docs/rules/*.md` + 目标模块 `AGENTS.md`。
5. 自测试环境：独立 worktree / 干净检出，JDK 21 + Maven 3.9+，Node 22（前端）。

缺少该类型的必需产物，或变更类型与实际 diff 不符时，直接 `REQUEST_CHANGES`（SCOPE-01），不要开始逐行评审。

Docs 类默认由 CI + 人工完成，Codex 评审可选；维护者要求复审时按 C 级执行。Ops 类由 Codex 做轻评审（安全/配置/回滚），按 B 级自测。

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
- CI 失败、Codex 自测试未执行或失败、该类型必需产物缺失、任务状态与证据不符 → `REQUEST_CHANGES`。
- 报告归档发生在 APPROVE 之后：Codex 只需保证报告内容完整、命名正确；归档结果由 DOC-03 校验（Feature 在任务关闭时，Arch/Ops/Docs 在合并时），不作为 Codex 的 APPROVE 前置条件。
- 未检查到的文件必须在报告中写明“未覆盖”，不得默认认为没问题。

## 3. 评审流程

1. **分类与产物**：确认 PR 属于 Feature / Arch / Ops / Docs；按 §1 检查该类型的必需产物。混合变更按最高风险类型处理。
2. **范围与状态**：确认 Feature 只改任务包允许路径，Arch/Ops/Docs 只改 PR 描述列出的路径；Feature 任务状态是否由授权角色设置、是否与 PR/合并/报告证据一致（四状态：`draft` / `ready` / `in-review` / `done`）。
3. **契约一致性**：接口路径、字段、错误码、DDL、事件 payload 是否与冻结契约一致。
4. **架构与数据**：服务边界、跨库访问、同步/异步方式、公共模块依赖。
5. **安全**：敏感字段、日志、密钥、鉴权、幂等。
6. **正确性**：空值/边界/并发/事务/MQ 重复消费；错误处理是否吞异常。
7. **测试质量**：是否有真实断言；是否覆盖成功、失败、幂等/重复消费等关键路径。
8. **自测试**：按 §4 的 A/B/C 级执行，记录命令、退出码、关键输出，并与 CI 结果交叉核对。
9. **输出**：每条问题带规则编号、文件行号、证据与修复建议；给出最终结论、自测试结果与报告归档路径。

评审只看 diff 和直接受影响的代码，**不要通读整个仓库**；生成文件、`node_modules/`、`target/` 不评审。

## 4. 自测试要求

> Codex 不能只读 diff、只看 CI；必须按变更风险分级执行自测试，用真实结果支撑结论。

### 4.1 分级

| 级别 | 适用范围 | 必跑 |
| --- | --- | --- |
| **A** | Feature、Arch、`common`、`gateway`、契约、安全相关 | 独立 worktree；`mvn -B -ntp verify`；涉及前端时 `npm ci && npm run build` |
| **B** | Ops：`deploy`、配置、依赖升级、构建脚本、Dockerfile | 受影响模块 `verify` + `docker compose config -q` / YAML 解析（如适用）；独立干净检出 |
| **C** | Docs：纯文档/规则（不含契约） | 链接/格式检查；可免构建，报告中说明理由 |

- A 级不得只跑单模块；跨模块编译/测试必须覆盖。测试耗时过长时，至少执行受影响模块 `verify` + 全量 `mvn -B -ntp -DskipTests package`，并写清未跑的测试。
- B 级无法起 Docker/中间件时，在报告中写“未覆盖”，不得默认通过。
- C 级不得跳过链接/格式检查；涉及契约的文档变更按 A 级处理。

### 4.2 执行环境

- 使用独立 worktree / 干净检出，指向 PR 的 head commit；不要复用实现者的脏工作区。
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

- 报告必须包含：**Reviewed SHA**（本次实际测试的代码提交）、执行环境、每条命令、退出码、关键输出、失败用例。
- 自测试失败 → `REQUEST_CHANGES`；若确认是环境问题（如缺少 MySQL），标 `[Question]` 并说明未覆盖范围，不得直接 `APPROVE`。
- 自测试通过但 CI 失败 → 以 CI 为准，`REQUEST_CHANGES`；分析差异并记录。
- 自测试通过但 CI 未跑 → 不能代替 CI；提醒维护者等 CI 全绿。
- 归档提交或后续 docs-only 提交不改变 Reviewed SHA；复审以 Reviewed SHA 为准。

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
| SCOPE-02 | 一个 PR 只对应一个任务/变更；语义 diff ≤400 行（docs-only 报告归档提交不计入），归档提交自身 ≤400 行 | Major |
| SCOPE-03 | 未授权修改契约/规则/父 pom/受保护文件 | Blocker |
| ARCH-01 | 一服务一库；禁止跨库 JOIN、访问其他服务表 | Blocker |
| ARCH-02 | 跨服务同步走 OpenFeign、异步走 RocketMQ；禁止共享库绕过 | Blocker |
| ARCH-03 | `common-*` 不依赖 service；网关不引入 `common-web` / `common-mybatis` / servlet 栈 | Blocker |
| ARCH-04 | 公共能力走 `AutoConfiguration.imports`；业务服务不得加 `@ComponentScan("com.atifah")` | Major |

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
| DB-02 | 禁止 `SELECT *`、拼接 SQL、N+1、循环内查库；唯一业务键有唯一索引 | Major（拼接 SQL/注入风险为 Blocker） |
| DB-03 | 实体继承 `BaseEntity`；雪花 ID；逻辑删除；审计字段；Flyway 与 `docs/contracts/db/` 一致，已执行脚本未修改 | Major（Schema/迁移与契约不一致为 Blocker） |
| DB-04 | `@Transactional` 只在 Service；事务内不做 RPC/MQ/文件 IO | Major |
| DB-05 | 缓存 Key 登记 `CacheKeys`；Cache Aside；缓存不是唯一数据源 | Major |
| DB-06 | 分片键命中查询；无跨分片聚合 | Major |
| MQ-01 | Topic / 消费组登记常量；只用 `MessagePublisher` | Major |
| MQ-02 | 消费幂等（`msgId` + 业务唯一键 + 状态机）；重试/死信/告警；不吞异常 | Blocker |
| MQ-03 | 消息 key 用业务键；消息体无敏感明文；与事件契约一致 | Blocker（敏感明文/契约不一致） |

### 5.4 安全

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| SEC-01 | 敏感字段加密；日志、异常、MQ、URL 无明文 | Blocker |
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
| CODE-06 | 实体/Mapper 规范（与 DB-03 同一根因时合并报告） | Major |
| CODE-07 | 不手拼 TraceId；异常日志保留堆栈；业务异常不重复打印 | Minor |
| CODE-08 | 删除测试、关闭校验、`@SuppressWarnings` / `@ts-ignore` 掩盖问题 | Blocker |
| DEPS-01 | 新增依赖先确认；版本只在根 pom；无第二套 JSON/日志/HTTP 客户端 | Blocker |
| DEPS-02 | 修改 parent / BOM 版本 | Blocker |
| TEST-01 | 核心链路有测试，且断言行为而非只编译 | Major |
| TEST-02 | 测试可重复，不依赖真实外部中间件（或明确标记集成测试） | Major |
| TEST-03 | Codex 未按 A/B/C 级执行自测试，或自测试失败仍给出 `APPROVE` | Blocker（流程检查） |

### 5.6 流程与文档

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| DOC-01 | 契约变更先有 ADR，并单独开 PR | Blocker |
| DOC-02 | 接口/路由/端口/配置变更同步文档与常量登记 | Major |
| DOC-03 | Feature 任务关闭前报告未归档或 §8.1 未归档；Arch/Ops/Docs（若评审）合并前报告未归档 | Blocker（流程检查） |
| DOC-04 | Feature 任务状态被未授权角色修改、跳过必经状态，或与 PR/合并/报告证据不符 | Blocker（流程检查） |
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

Self-test:
  Reviewed SHA: <reviewed-sha>
  mvn -B -ntp verify: PASS
  npm ci && npm run build: PASS
  notes: 未运行 MySQL/Redis 集成测试

Verdict: REQUEST_CHANGES
```

要求：

- 每条必须带**规则编号**、`file:line`、**证据**、**影响**、**建议**；没有证据的猜测标 `Question`。
- 同一根因只报一次，选最具体的 canonical 规则编号，不重复计数（别名见 §5.0）。
- 不要写“建议重构”“可以考虑优化”这类无法执行的结论。
- `Minor` 可以合并为一条列表，但仍需带规则编号与位置。
- 必须附 `Self-test` 块：Reviewed SHA、命令、结果、未覆盖范围；没有自测试结果不得给出 `APPROVE`。
- 必须附报告路径：Feature `docs/review/reports/T-xxx-rN-<reviewed-sha>.md`；Arch/Ops/Docs `docs/review/reports/PR-<n>-<reviewed-sha>.md`。

### 6.1 评审记录归档

> 报告命名、写入权限与保留策略见 [`reports/README.md`](reports/README.md)；本节只定义归档步骤与校验。

1. Codex 评审后，在 PR 评论给出 Verdict 与 Self-test 摘要；完整报告按 [`report-template.md`](report-template.md) 写入工作区 `docs/review/reports/<name>.md`。
   - Feature：`T-xxx-rN-<reviewed-sha>.md`
   - Arch/Ops/Docs：`PR-<n>-<reviewed-sha>.md`
   - `reviewed-sha` 是 Codex 实际测试的代码提交，不是归档提交或后续 head SHA。
2. APPROVE 后，由实现者把报告原文作为 **docs-only 提交** 归档：
   - Feature：CodeBuddy 归档，并更新任务包 §8.1；可在合并前归档，也可在任务关闭前补归档。
   - Arch/Ops/Docs（若 Codex 评审）：Claude 归档；**必须在合并前完成**，因为非 Feature 没有任务关闭动作。
   该提交只允许改 `docs/review/reports/**` 与 Feature 任务包 front matter / §8.1 / §8.2；不触发语义复审；不计入 SCOPE-02 的 400 行语义 diff，但归档提交自身 ≤400 行。维护者合并前确认该提交范围，超出范围则按普通变更复审。
3. 如果 Codex 无法写文件或访问 GitHub：在 PR 评论输出完整报告，由实现者/维护者原样保存到上述路径；Feature 归档前不得置 `done`，非 Feature 不得合并。
4. DOC-03 校验点：Feature 在任务关闭时；Arch/Ops/Docs 在合并时。Feature 不再把“合并前落盘”作为合并门禁。
5. Codex 的写入范围仅限 `docs/review/reports/**`；不得修改代码、契约、规则、任务实现或测试。
6. 报告必须包含：Reviewed SHA、Verdict、Blocker/Major/Minor/Question 计数、发现详情、Self-test 块、未覆盖范围。
7. Feature 任务包 §8.1 归档报告路径与 Verdict；非 Feature 不更新任务包，但报告必须在合并前归档（DOC-03）。

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
- 不得只依赖 CI 结果；必须按 §4 的 A/B/C 级独立执行自测试。
- 不得修改代码/测试/配置让自测试通过，不得提交构建产物。
- 不得修改任务包状态或 §8.2 状态记录；状态由授权角色维护。
- 不得在 CI 失败、自测试未执行或失败、该类型必需产物缺失、任务状态与证据不符、契约缺失时给出 `APPROVE`。
- 报告内容必须完整；无法写文件或访问 GitHub 时，在 PR 评论输出完整报告，由实现者归档。

## 9. 报告模板

见 [`report-template.md`](report-template.md)。报告命名与归档见 §6.1；PR 评论只放摘要与链接。
