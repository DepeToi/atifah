# AGENTS.md

> 本项目所有 AI 工具（Claude Code / CodeBuddy / Codex / Cursor）的**唯一规则入口**。
>
> 读取顺序与优先级（冲突时自上而下）：
>
> 1. 本文件：项目硬约束、角色分工、索引，每次必读，全文 ≤200 行。
> 2. `docs/rules/*.md`：安全、编码、数据、Git 细则，执行具体任务时按需加载。
> 3. `docs/contracts/`：已冻结的接口 / DDL / 事件契约，**优先级高于实现代码**。
> 4. `docs/architecture.md` 与 `docs/adr/`：设计与决策背景。
> 5. 模块级 `AGENTS.md`：就近生效，冲突时覆盖上层。
>
> 规则只维护一份，禁止在各层重复抄写。

## 0. 三角色工作流（每次必读）

| 角色 | 职责 | 可写范围 | 禁止 |
| --- | --- | --- | --- |
| Claude（架构） | 架构设计、接口/DDL/事件契约、ADR、任务拆解、公共能力与脚手架 | `docs/`、根 `pom.xml`、`atifah-common/`、模块骨架、`.github/`、根规则文件 | 不实现业务 CRUD |
| CodeBuddy（开发） | 按任务包实现功能与测试 | 任务包“允许修改”列出的目录，通常为单个服务；任务包 `status` / §12 状态记录（仅此字段） | 不改契约、规则、父 pom、其他服务 |
| Codex（评审） | 只读评审 diff、合规检查与**独立自测试** | PR 评论 + `docs/review/reports/**`（只写评审报告；可运行构建/测试） | 不改代码/契约/规则/任务实现 |
| 人类（维护者） | 确认架构、合并 PR、处理升级 | 全部 | — |

**交接只通过仓库完成**，不依赖对话记忆：

1. Claude 产出 ADR + 契约 + `docs/tasks/T-xxx.md`；人工确认后在 `main` 打 `arch-baseline-vX.Y` 标签，契约冻结。
2. CodeBuddy 从 `main` 拉 `feat/T-xxx-<slug>`，把任务状态改为 `in-progress`；只按任务包实现，提交并推送、创建 PR 后改为 `in-review`。
3. CI（`.github/workflows/ci.yml`）必须通过。
4. Codex 按 `docs/review/standards.md` 只读评审 PR，并在独立工作区执行自测试（`mvn -B -ntp verify`、前端 `npm ci && npm run build`）；完整报告落盘到 `docs/review/reports/T-xxx-<sha>.md`，PR 评论只放摘要与链接。
5. `Blocker` / `Major` 或自测试失败由 CodeBuddy 修复；架构或契约问题回到 Claude，先改 ADR/契约再改代码。
6. 人类在 PR 合并且报告归档后把任务状态改为 `done`；合并前任务包 §11 必须归档评审报告路径与 Verdict。AI 不得自动 `git commit` / `git push`，除非用户明确要求。

**并发约束**：同一工作区同一时刻只允许一个角色写文件。并行任务用独立分支或 `git worktree`，禁止两个工具同时改同一批文件。

**任务状态权限**：Claude 只设 `draft` / `ready`（`ready` 需人工确认）；CodeBuddy 只设 `in-progress` / `in-review`；人类维护者设 `done` 与强制回退；Codex 不得改状态。每次变更必须更新 front matter 的 `status` / `updated` / `status_reason`，并在任务包 §12 追加记录；没有 PR 证据不得标 `in-review`，没有合并与报告证据不得标 `done`（DOC-04）。

**Codex 自测试与报告**：在独立 worktree / 干净检出中执行，记录 commit、命令、结果；完整报告写入 `docs/review/reports/`；不得只信 CI，不得修改代码/测试，不得提交构建产物。

## 1. 项目是什么

- 情绪管理产品：C 端 App（Flutter，`atifah-app/` 占位）+ 管理后台（Vue 3）+ Spring Cloud Alibaba 微服务。
- 当前处于骨架阶段：公共能力与网关已实现，9 个业务服务只有启动类，尚无 Controller / Service / Mapper / 表结构（以仓库现状为准）。
- 业务域：账号、情绪打卡与日记、趋势与量表报告、社区树洞 Feed、冥想内容、消息推送、签到积分、运营统计。
- 情绪日记、量表结果、手机号属于敏感个人信息，**隐私与加密优先于性能优化**。

## 2. 技术栈（禁止替换、禁止擅自新增）

| 用途 | 选型 | 版本 |
| --- | --- | --- |
| 语言 / 构建 | Java + Maven | 21 / 3.9+ |
| 框架 | Spring Boot | 3.5.16 |
| 微服务 | Spring Cloud / SCA | 2025.0.3 / 2025.0.0.0 |
| 持久层 | MyBatis-Plus + MySQL | 3.5.17 / 8.0 |
| 缓存 | Redis 7 + Redisson | 3.52.0 |
| 消息 | RocketMQ Spring Starter | 2.3.6 |
| 认证 | JJWT（HS256，生产建议 RS256） | 0.12.7 |
| 管理后台 | Vue 3 + Vite 8 + Element Plus 2.14 | - |

- 新增依赖必须先人工确认；不得为一个工具方法引入新库。
- 禁止改根 `pom.xml` 的 parent 与 BOM 版本；禁止引入第二套 JSON / 日志 / HTTP 客户端。

## 3. 模块、端口与包边界

```text
pom.xml (parent: spring-boot-starter-parent)
├── atifah-common/      core · web · redis · mybatis · mq · security
├── atifah-gateway/     响应式网关，8080
├── atifah-services/    9 个业务服务的真实父 POM
└── atifah-admin-web/   frontend（Vue 3，独立 npm）+ backend（admin-service，8090）
```

端口：`gateway 8080 · auth 8081 · user 8082 · mood 8083 · analysis 8084 · community 8085 · content 8086 · message 8087 · growth 8088 · stats 8089 · admin 8090`。

三条单看文件看不出的继承关系：

- `atifah-services/pom.xml` 是业务服务的真实父 POM，已声明 common×5、Nacos、OpenFeign、LoadBalancer；**子服务 pom 不要再重复声明**。
- `atifah-admin-web/backend` 复用该父 POM，但聚合在根 pom；artifactId 是 `admin-service`，不是目录名。
- `atifah-gateway` 独立、响应式，只依赖 `common-core` + `common-security`；**禁止**引入 `common-web` / `common-mybatis` / `spring-boot-starter-web`，禁止开启虚拟线程。

包约定：包根 `com.atifah.<service>`；公共代码只在 `com.atifah.common.*`；公共能力通过 `META-INF/spring/...AutoConfiguration.imports` 装配，**不要也不要加 `@ComponentScan("com.atifah")`**。

## 4. 硬约束速查（规则编号与 `docs/review/standards.md` 一致）

- **安全 SEC**：敏感字段加密存储；日志、异常、MQ、URL 不得出现明文；密钥只走环境变量；唯一入口是网关（见 `docs/rules/security.md`）。
- **接口 API**：统一 `Result<T>` / `PageResult<T>`；错误码只取 `ErrorCode`；业务写接口必须支持 `Idempotency-Key`（登录/刷新/登出例外，注册必须）；C 端 `/api/v1/**`，管理端 `/admin/api/**`（见 `docs/contracts/api-conventions.md`）。
- **数据 DB**：一服务一库，禁止跨库 JOIN、`SELECT *`、拼接 SQL、循环查库；实体继承 `BaseEntity`；Flyway 管理 DDL（见 `docs/rules/database.md`）。
- **消息 MQ**：Topic / 消费组先登记 `MqTopics` / `MqConsumerGroups`；只用 `MessagePublisher`；消费端必须幂等并配重试/死信（见 `docs/rules/database.md`）。
- **编码 CODE**：构造注入、禁止字段 `@Autowired`；统一异常；`@Valid` 校验；日志用 `@Slf4j` 占位符（见 `docs/rules/code-style.md`）。
- **依赖 DEPS**：版本只在根 `pom.xml` 管理；新增依赖先确认；禁止第二套 JSON / 日志 / HTTP 客户端。
- **变更边界 SCOPE**：只改任务包允许路径；一个 PR 只做一件事，diff ≤400 行。

## 5. 契约、任务包与评审

- **契约冻结**：`docs/contracts/` 是接口唯一依据；架构基线通过 `main` 上的 `arch-baseline-vX.Y` 标签冻结。
- **先改契约再改代码**：任何接口/DDL/事件变更必须先由 Claude 出 ADR 并单独提交契约 PR；CodeBuddy 与 Codex 不得修改 `docs/contracts/**`。
- **任务包**：每个实现任务必须有 `docs/tasks/T-xxx-<slug>.md`，包含目标、必读文件、允许/禁止修改路径、验收标准与测试要求。
- **评审**：Codex 只读评审并独立执行自测试，按 `docs/review/standards.md` 输出固定格式；报告落盘到 `docs/review/reports/`，任务包 §11 归档路径与 Verdict；校验任务状态与 PR/合并证据一致；`Blocker` / `Major` 未清零、自测试失败、报告未归档或状态证据不符不得合并。
- **PR 上限**：单服务、单任务、diff ≤400 行；超出请拆任务，不要提交大杂烩。

## 6. 禁止事项（违反即打回）

- 禁止硬编码密码、密钥、Token、AK/SK、内部域名；`JWT_SECRET` 不得写死真实值。
- 禁止把情绪正文、量表结果、手机号、密码打印到日志或控制台；不得明文存储敏感字段。
- 禁止为让编译/测试通过而删除测试、关掉校验、加 `@SuppressWarnings` / `@ts-ignore`。
- 禁止擅自改：根 `pom.xml` 的 parent/版本、`deploy/nacos/config/*`、`deploy/docker-compose` 端口映射、`deploy/sql` 已执行脚本、`.gitattributes` 换行设置。
- 禁止绕过网关直连业务服务；管理端不得直连业务库。
- 禁止自动 `git commit` / `git push`。

## 7. 文档索引（按需读，禁止一次性全文加载）

| 文档 | 内容 |
| --- | --- |
| `docs/rules/security.md` | 敏感数据、密钥、鉴权、幂等 |
| `docs/rules/code-style.md` | Java 命名/分层/响应/异常/实体/日志 |
| `docs/rules/database.md` | 数据库、事务、缓存、MQ、分库分表 |
| `docs/rules/git-workflow.md` | 分支、提交、变更边界、文档同步 |
| `docs/contracts/README.md` | 契约目录、冻结与变更流程 |
| `docs/contracts/api-conventions.md` | 接口总纲（响应、错误码、鉴权、分页） |
| `docs/tasks/README.md` | 任务包规范与状态流转 |
| `docs/review/standards.md` | Codex 评审规则、自测试要求、分级与结论 |
| `docs/review/reports/` | Codex 评审报告归档（按任务 + head SHA 命名） |
| `docs/adr/` | 架构决策记录 |
| `docs/architecture.md` | 设计目标、核心链路、高并发与一致性 |
| `docs/domain.md` | 业务域、服务归属、领域边界 |
| `docs/db.md` | 数据模型、分片、加密、缓存与一致性 |
| `README.md` | 给人看：快速开始、中间件地址与端口 |

模块级规则（就近生效）：`atifah-common/AGENTS.md`、`atifah-gateway/AGENTS.md`、`atifah-services/AGENTS.md`、`atifah-admin-web/AGENTS.md`、`atifah-admin-web/frontend/AGENTS.md`。

## 8. 工作方式

1. 动手前先读任务包、模块级 `AGENTS.md` 与相关契约；确认现有写法，不要凭 Spring 通用习惯猜。
2. 只改与任务相关的文件，不顺手重构，不新增未要求的功能。
3. 改配置时保持“一份真相”：共享配置改 `deploy/nacos/config/common-application.yaml` 并提醒导入 Nacos；本地 `application.yml` 只留端口、应用名、Nacos 连接、数据源 URL、生产者组。
4. 改路由、端口、服务名时，同步 `README.md` 端口表与相关契约/文档。
5. 新增业务代码顺序：实体（继承 `BaseEntity`）→ Mapper → Service → Controller；先登记 `CacheKeys` / `MqTopics` / `MqConsumerGroups` 再引用。
6. 完成后自检：后端 `mvn -o -q -DskipTests compile`；前端 `npm run type-check`；**不通过不得声称完成**。
7. 有设计分歧先给两个方案与取舍，等确认再写；架构决策写 `docs/adr/`，不要只留在对话里。

## 9. 常用命令

```bash
mvn clean package -DskipTests                    # 全量构建
mvn -o -q -DskipTests compile                    # 快速编译（改完代码先跑这个）
mvn -pl atifah-admin-web/backend -am package -DskipTests   # 只构建 admin BFF 及其上游
docker compose -f deploy/docker-compose/docker-compose.yml up -d   # 本地中间件
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
cd atifah-admin-web/frontend && npm run dev      # 管理后台前端 :5173
```
