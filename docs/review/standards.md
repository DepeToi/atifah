# 代码评审规则（Codex）

> 本文件是 **Codex 只读评审 + 独立自测试**的唯一判据。规则编号同时被 `AGENTS.md`、`docs/rules/*` 与任务包引用。
> 目标：让评审结果稳定、可追踪、可自动化；不把风格偏好伪装成缺陷。

## 1. 评审输入（缺一不可）

1. 任务包：`docs/tasks/T-xxx-<slug>.md`（目标、允许修改路径、验收标准）。
2. 契约：`docs/contracts/` 中与任务相关的 OpenAPI / DDL / 事件文件。
3. diff：`git diff <base>...<head>`，只评审本次变更及直接受影响的调用方。
4. CI 结果：`.github/workflows/ci.yml` 必须通过。
5. 规则：本文件 + `docs/rules/*.md` + 目标模块 `AGENTS.md`。
6. 自测试环境：独立 worktree / 干净检出，JDK 21 + Maven 3.9+，Node 22（前端）。

缺少任务包或契约时，直接 `REQUEST_CHANGES`（SCOPE-01 / DOC-01），不要开始逐行评审。

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
- CI 失败、Codex 自测试未执行或失败、任务包/契约缺失 → 一律 `REQUEST_CHANGES`。
- 未检查到的文件必须在报告中写明“未覆盖”，不得默认认为没问题。

## 3. 评审流程

1. **范围与状态**：diff 是否只在任务包允许路径内；是否夹带契约/规则/其他服务修改；任务状态是否由授权角色设置、是否与 PR/合并/报告证据一致。
2. **契约一致性**：接口路径、字段、错误码、DDL、事件 payload 是否与冻结契约一致。
3. **架构与数据**：服务边界、跨库访问、同步/异步方式、公共模块依赖。
4. **安全**：敏感字段、日志、密钥、鉴权、幂等。
5. **正确性**：空值/边界/并发/事务/MQ 重复消费；错误处理是否吞异常。
6. **测试质量**：是否有真实断言；是否覆盖成功、失败、幂等/重复消费等关键路径。
7. **自测试**：在独立工作区按 §4 执行构建与测试，记录命令、退出码、关键输出，并与 CI 结果交叉核对。
8. **输出**：每条问题带规则编号、文件行号、证据与修复建议；给出最终结论与自测试结果。

评审只看 diff 和直接受影响的代码，**不要通读整个仓库**；生成文件、`node_modules/`、`target/` 不评审。

## 4. 自测试要求

> Codex 不能只读 diff、只看 CI；必须在**独立工作区**自行执行构建与测试，用真实结果支撑结论。

### 4.1 执行环境

- 使用独立 worktree / 干净检出，指向 PR 的 head commit；不要复用 CodeBuddy 的脏工作区。
- 后端：JDK 21 + Maven 3.9+；前端：Node 22 + npm（与 CI 一致）。
- 不修改代码、测试、配置来“让测试通过”；不提交 `target/`、`dist/`、`node_modules/`。
- 需要 MySQL/Redis 的集成测试：本地有 Docker 时用 `deploy/docker-compose` 启动；没有时在报告中明确写“未覆盖”，不得默认通过。

示例（Git Bash）：

```bash
git fetch origin
git worktree add ../atifah-codex-review <head-sha>
cd ../atifah-codex-review
mvn -B -ntp verify
cd atifah-admin-web/frontend && npm ci && npm run build
```

### 4.2 必跑项

| 范围 | 命令 | 要求 |
| --- | --- | --- |
| 后端全量 | `mvn -B -ntp verify` | 必须执行；不能只跑单模块，避免跨模块编译/测试被漏掉 |
| 前端 | `npm ci && npm run build` | 涉及前端或共享类型时必须执行 |
| 契约/DDL | 对照 `docs/contracts/` 人工核对 | 静态检查，不能替代构建 |
| 集成测试 | 有中间件时执行；否则说明未覆盖 | Redis/MySQL/MQ 相关 |

- 如果 PR 只改文档/规则，可只跑链接/格式检查，并在报告中说明免跑理由。
- 如果测试耗时过长，至少执行受影响模块的 `verify` + 全量 `mvn -B -ntp -DskipTests package`，并写清未跑的测试。

### 4.3 记录与判定

- 报告必须包含：commit SHA、执行环境、每条命令、退出码、关键输出、失败用例。
- 自测试失败 → `REQUEST_CHANGES`；若确认是环境问题（如缺少 MySQL），标 `[Question]` 并说明未覆盖范围，不得直接 `APPROVE`。
- 自测试通过但 CI 失败 → 以 CI 为准，`REQUEST_CHANGES`；分析差异并记录。
- 自测试通过但 CI 未跑 → 不能代替 CI；提醒维护者等 CI 全绿。

## 5. 规则目录

### 5.1 范围与架构

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| SCOPE-01 | 只修改任务包“允许修改”的路径 | Blocker |
| SCOPE-02 | 一个 PR 只对应一个任务，diff ≤400 行 | Major |
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
| API-04 | 业务写接口校验 `Idempotency-Key`（注册必须；登录/刷新/登出豁免），Redis + 数据库唯一索引双保险 | Blocker |
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
| SEC-04 | 写接口幂等（与 API-04 同一根因时合并报告） | Blocker |
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
| TEST-03 | Codex 未执行自测试，或自测试失败仍给出 `APPROVE` | Blocker（流程检查） |

### 5.6 流程与文档

| 编号 | 检查点 | 默认级别 |
| --- | --- | --- |
| DOC-01 | 契约变更先有 ADR，并单独开 PR | Blocker |
| DOC-02 | 接口/路由/端口/配置变更同步文档与常量登记 | Major |
| DOC-03 | Codex 评审报告未落盘到 `docs/review/reports/`，或任务包 §11 未归档 | Blocker（流程检查） |
| DOC-04 | 任务状态被未授权角色修改、跳过必经状态，或与 PR/合并/报告证据不符 | Blocker（流程检查） |
| OPS-01 | 配置分层正确；共享配置只改 `common-application.yaml` 并提醒导入 Nacos | Major |
| OPS-02 | 修改受保护部署文件、端口映射、已执行 SQL、`.gitattributes` | Blocker |
| GIT-01 | AI 未经用户要求自动 `commit` / `push` | Blocker（流程检查） |
| GIT-02 | 分支命名、PR 模板、单任务单 PR | Major（流程检查） |
| GIT-03 | 变更边界：只改任务包允许路径，不顺手重构（与 SCOPE-01 合并报告） | Major |
| GIT-04 | 修改受保护文件（与 OPS-02 合并报告） | Blocker |
| GIT-05 | 接口/路由/端口/配置变更未同步文档与常量（与 DOC-02 合并报告） | Major |
| GIT-06 | 提交前自检未执行或未通过却声称完成 | Major |
| GIT-07 | 多个工具并发修改同一批文件 | Blocker（流程检查） |

## 6. 输出格式（必须固定）

在 PR 评论中按以下格式输出，一条问题一个条目：

```text
[Blocker] path/to/File.java:42 — SEC-01 — 日志打印了日记正文
  证据：log.info("content={}", diary.getContent());
  影响：敏感个人信息泄露，违反数据分级要求。
  建议：改为 log.info("diaryId={}", diary.getId()); 并补充脱敏测试。

[Major] path/to/Service.java:88 — DB-01 — 循环内逐条查库
  证据：for (Long id : ids) { mapper.selectById(id); }
  影响：N+1，批量场景放大数据库压力。
  建议：改为 selectBatchIds(ids) 或一次 IN 查询。

[Question] path/to/Controller.java:15 — API-06 — 新增字段是否保持向后兼容？
  说明：前端旧版本是否会读取该字段？

Self-test:
  commit: <head-sha>
  mvn -B -ntp verify: PASS
  npm ci && npm run build: PASS
  notes: 未运行 MySQL/Redis 集成测试

Verdict: REQUEST_CHANGES
```

要求：

- 每条必须带**规则编号**、`file:line`、**证据**、**影响**、**建议**；没有证据的猜测标 `Question`。
- 同一根因只报一次，选最具体的规则编号，不重复计数。
- 不要写“建议重构”“可以考虑优化”这类无法执行的结论。
- `Minor` 可以合并为一条列表，但仍需带规则编号与位置。
- 必须附 `Self-test` 块：commit、命令、结果、未覆盖范围；没有自测试结果不得给出 `APPROVE`。
- 必须附报告落盘路径：`docs/review/reports/T-xxx-<head-short-sha>.md`。

### 6.1 评审记录归档（强制）

- Codex 必须把完整评审报告写入 `docs/review/reports/T-xxx-<head-short-sha>.md`，使用 [`report-template.md`](report-template.md)；PR 评论只放摘要与报告链接。
- Codex 的写入范围仅限 `docs/review/reports/**`；不得修改代码、契约、规则、任务实现或测试。
- 报告必须包含：commit SHA、Verdict、Blocker/Major/Minor/Question 计数、发现详情、Self-test 块、未覆盖范围。
- CodeBuddy 在开 PR 或合并前，把报告路径与 Verdict 写入 `docs/tasks/T-xxx.md` §11；不得修改报告正文。
- 未落盘报告或 §11 未归档 → 不得合并（DOC-03）。
- 如果 Codex 所在环境不能写文件：在 PR 评论输出完整报告，由 CodeBuddy/维护者原样保存到上述路径后再合并。

## 7. 分流与升级

- `[Arch]`：架构/契约/跨服务边界问题 → 回到 Claude，先改 ADR 与契约。
- `[Impl]`：实现问题 → 回到 CodeBuddy 修复，Codex 复审。
- `[Question]`：信息不足或产品决策 → 由人类维护者确认。
- 同一规则连续两轮被同一类问题触发 → 建议 Claude 更新规则或增加自动化检查（ArchUnit / CI），而不是反复口头提醒。

## 8. Codex 禁止事项

- 不直接修改代码、不提交修复；评审是只读的，但可以在独立工作区运行构建与测试，并写 `docs/review/reports/**` 评审报告。
- 不评审任务范围外的文件，不提出大规模重构。
- 不发明需求或验收标准；以任务包和契约为准。
- 不因个人风格偏好判 `Blocker` / `Major`；无规则依据的只算 `Minor` 或不报。
- 不得只依赖 CI 结果；必须按 §4 独立执行自测试。
- 不得修改代码/测试/配置让自测试通过，不得提交构建产物。
- 不得只把评审结论留在 PR 评论/聊天而不落盘；必须写入 `docs/review/reports/`。
- 不得修改任务包状态或 §12 状态记录；状态由授权角色维护。
- 不在 CI 失败、自测试未执行或失败、报告未落盘、任务状态与证据不符、契约缺失时给出 `APPROVE`。

## 9. 报告模板

见 [`report-template.md`](report-template.md)。报告必须落盘到 `docs/review/reports/T-xxx-<head-short-sha>.md`；PR 评论只放摘要与链接。
