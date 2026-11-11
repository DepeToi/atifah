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
| Claude（架构） | 架构设计、接口/DDL/事件契约、ADR、任务拆解、公共能力、网关/部署/CI 脚手架 | `docs/`、根 `pom.xml`、`atifah-common/`、`atifah-gateway/`、`deploy/`、`.github/`、模块骨架、根规则文件 | 不实现业务 CRUD |
| CodeBuddy（开发） | 按 Feature 任务包实现功能与测试 | 任务包“允许修改”列出的目录，通常为单个服务；任务包 front matter（仅状态字段） | 不改契约、规则、父 pom、其他服务 |
| Codex（评审） | 只读评审 diff、合规检查与风险匹配的验证 | PR 评审评论；按需写 `docs/review/reports/**` | 不改代码/契约/规则/任务实现 |
| 人类（维护者） | 确认架构、合并 PR、处理升级 | 全部 | — |

**变更分四类**（混合变更按最高风险类型处理；Feature 不得夹带 Arch/Ops 路径；契约变更必须单独 PR）：

| 类型 | 范围 | 必需产物 | 评审 |
| --- | --- | --- | --- |
| Feature | 业务服务、管理端业务代码 | 任务包 `docs/tasks/T-xxx-*.md` | Codex 语义评审；按风险核验 CI 与补充测试 |
| Arch | `atifah-common/**`、`atifah-gateway/**`、根/父 `pom.xml`、`docs/contracts/**`、`docs/rules/**`、`docs/adr/**`、根/模块 `AGENTS.md`、`CLAUDE.md`、`docs/tasks/README.md`、`docs/tasks/_TEMPLATE.md`、`docs/review/standards.md`、`docs/review/report-template.md`、`docs/review/reports/README.md` | ADR（涉及契约时附契约 diff） | Codex 语义评审；高风险变更针对性验证 |
| Ops | `deploy/**`、`.github/**`、Dockerfile、构建/部署脚本 | PR 描述中的变更说明、影响面、验证命令与回滚方式 | Codex 轻评审（安全/配置）+ 相关配置验证 |
| Docs | `docs/**`（非契约/ADR/rules/治理入口）、`README.md`、`TODO.md`、单个任务包 `docs/tasks/T-*.md`、已归档报告 `docs/review/reports/*.md` | PR 描述 | CI + 人工；Codex 可免 |

- 治理入口文件（根/模块 `AGENTS.md`、`CLAUDE.md`、`docs/tasks/README.md`、`docs/tasks/_TEMPLATE.md`、`docs/review/standards.md`、`docs/review/report-template.md`、`docs/review/reports/README.md`）一律归 Arch，避免被 Docs 兜底跳过语义评审。
- 分类表覆盖所有可提交路径；未列出的根级文件默认按 Ops 处理，新增根级文件必须同步更新本表。
- 提交标题：Feature 用 `type(scope): T-xxx 摘要`；Arch/Ops/Docs 用 `type(scope): 摘要`，不得伪造任务编号。

**交接只通过仓库完成**，不依赖对话记忆：

1. Claude 产出 ADR + 契约 +（Feature）任务包；人工确认后在 `main` 打 `arch-baseline-vX.Y` 标签，契约冻结。
2. Feature：CodeBuddy 从 `main` 拉 `feat/T-xxx-<slug>`，只按任务包实现，创建 PR 后把状态改为 `in-review`。Arch/Ops/Docs：由 Claude 按分类在 `arch/`、`ops/`、`docs/` 分支实现并开 PR。
3. CI（`.github/workflows/ci.yml`）必须通过。
4. Codex 按 `docs/review/standards.md` 只读评审 PR，核对 Reviewed SHA 对应的 CI，并按风险补充测试；在 PR 评论记录结论、验证证据与未覆盖范围。报告文件可选。
5. `Blocker` / `Major` 或验证失败由对应实现者修复；架构或契约问题回到 Claude，先改 ADR/契约再改代码。
6. 人类合并；维护者批量把 Feature 任务状态改为 `done`。AI 不得自动 `git commit` / `git push`，除非用户明确要求。

**并发约束**：同一工作区同一时刻只允许一个角色写文件。并行任务用独立分支或 `git worktree`，禁止两个工具同时改同一批文件。

**任务状态权限**：仅 Feature 任务包有状态。Claude 只设 `draft` / `ready`（`ready` 需人工确认）；CodeBuddy 只设 `in-review`；人类维护者设 `done` 与强制回退；任意当前负责人可置 `blocked` 并按解除条件恢复；Codex 不得改状态。状态只更新 front matter，Git 历史与 PR 保留流转依据；细则见 `docs/tasks/README.md` §2（DOC-04）。

**Codex 验证与报告**：按 `docs/review/standards.md` §4 的 A/B/C 级核验 CI 与风险对应的测试；不重复执行 CI 已覆盖的全量测试。报告以 PR 评论为准，独立报告文件可选；不得修改代码/测试，不得提交构建产物。

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
- 版本以根 `pom.xml` 为准；`docs/architecture.md` 中的 ShardingSphere、Caffeine、XXL-Job、SkyWalking 等属于规划/可选组件，启用前必须新增 ADR 并人工确认，不视为已批准依赖。

## 3. 模块、端口与包边界

```text
pom.xml (parent: spring-boot-starter-parent)
├── atifah-common/      core · web · redis · mybatis · mq · security
├── atifah-gateway/     响应式网关，8080
├── atifah-services/    9 个业务服务的真实父 POM
└── atifah-admin-web/   frontend（Vue 3，独立 npm）+ backend（admin-service，8090）
```

端口与服务名以 [`README.md`](README.md) 的模块结构（含端口）为准；新增或调整端口必须同步该表与相关契约。

三条单看文件看不出的继承关系：

- `atifah-services/pom.xml` 是业务服务的真实父 POM，已声明 common×5、Nacos、OpenFeign、LoadBalancer；**子服务 pom 不要再重复声明**。
- `atifah-admin-web/backend` 复用该父 POM，但聚合在根 pom；artifactId 是 `admin-service`，不是目录名。
- `atifah-gateway` 独立、响应式，只依赖 `common-core` + `common-security`；**禁止**引入 `common-web` / `common-mybatis` / `spring-boot-starter-web`，禁止开启虚拟线程。

包约定：包根 `com.atifah.<service>`；公共代码只在 `com.atifah.common.*`；公共能力通过 `META-INF/spring/...AutoConfiguration.imports` 装配，**不要也不要加 `@ComponentScan("com.atifah")`**。

## 4. 硬约束速查（规则编号与 `docs/review/standards.md` 一致）

- **安全 SEC**：敏感字段加密存储；日志、异常、MQ、URL 不得出现明文；密钥只走环境变量；唯一入口是网关（见 `docs/rules/security.md`）。
- **接口 API**：统一 `Result<T>` / `PageResult<T>`；错误码只取 `ErrorCode`；业务写接口必须支持 `Idempotency-Key`（例外与细则见 `docs/contracts/api-conventions.md` §7）；C 端 `/api/v1/**`，管理端 `/admin/api/**`。
- **数据 DB**：一服务一库，禁止跨库 JOIN、`SELECT *`、拼接 SQL、循环查库；所有 SQL（含写入）写 `resources/mapper/*.xml`（Mapper 不继承 `BaseMapper`，禁止 `BaseMapper` / `IService` 的 CRUD；XML 显式带 `deleted = 0`）；`id` 由 Service 生成；实体继承 `BaseEntity`；Flyway 管理 DDL（见 `docs/rules/database.md`）。
- **消息 MQ**：Topic / 消费组先登记 `MqTopics` / `MqConsumerGroups`；只用 `MessagePublisher`；消费端必须幂等并配重试/死信（见 `docs/rules/database.md`）。
- **编码 CODE**：构造注入、禁止字段 `@Autowired`；统一异常；`@Valid` 校验；日志用 `@Slf4j` 占位符（见 `docs/rules/code-style.md`）。
- **依赖 DEPS**：版本只在根 `pom.xml` 管理；新增依赖先确认；禁止第二套 JSON / 日志 / HTTP 客户端。
- **变更边界 SCOPE**：Feature 只改任务包允许路径，Arch/Ops/Docs 只改 PR 描述列出的路径；一个 PR 只做一件事，语义 diff ≤400 行。

## 5. 契约、任务包与评审

- **契约冻结**：`docs/contracts/` 是接口唯一依据；架构基线通过 `main` 上的 `arch-baseline-vX.Y` 标签冻结。冻结与变更流程见 `docs/contracts/README.md` §3。
- **先改契约再改代码**：任何接口/DDL/事件变更必须先由 Claude 出 ADR 并单独提交契约 PR；CodeBuddy 与 Codex 不得修改 `docs/contracts/**`。
- **任务包**：每个 Feature 变更必须有 `docs/tasks/T-xxx-<slug>.md`；Arch 用 ADR + 契约 diff，Ops 用 PR 描述，Docs 用 PR 描述。规范与状态机见 `docs/tasks/README.md`。
- **评审**：Codex 只读评审并按风险核验 CI / 补充测试，判据与输出格式见 `docs/review/standards.md`。
- **PR 上限**：单服务、单任务、语义 diff ≤400 行；口径以 `docs/review/standards.md` SCOPE-02 为准；超出请拆任务，不要提交大杂烩。

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
| `docs/review/standards.md` | Codex 评审规则、风险验证、分级与结论 |
| `docs/review/reports/` | 可选的 Codex 评审报告副本（按任务/PR + Reviewed SHA 命名） |
| `docs/adr/` | 架构决策记录 |
| `docs/architecture.md` | 设计目标、核心链路、高并发与一致性 |
| `docs/domain.md` | 业务域、服务归属、领域边界 |
| `docs/db.md` | 数据模型、分片、加密、缓存与一致性 |
| `README.md` | 给人看：快速开始、中间件地址与端口 |

模块级规则（就近生效）：`atifah-common/AGENTS.md`、`atifah-gateway/AGENTS.md`、`atifah-services/AGENTS.md`、`atifah-admin-web/AGENTS.md`、`atifah-admin-web/frontend/AGENTS.md`。

## 8. 工作方式

1. 动手前先读任务包（Feature）或 ADR/PR 描述（Arch/Ops/Docs）、模块级 `AGENTS.md` 与相关契约；确认现有写法，不要凭 Spring 通用习惯猜。
2. 只改与任务相关的文件，不顺手重构，不新增未要求的功能。
3. 改配置时保持“一份真相”：共享配置改 `deploy/nacos/config/common-application.yaml` 并提醒导入 Nacos；本地 `application.yml` 只留端口、应用名、Nacos 连接、数据源 URL、生产者组、`mybatis-plus.mapper-locations`（与 Nacos 保持同值，Nacos 优先、本地回落）。
4. 改路由、端口、服务名时，同步 `README.md` 端口表与相关契约/文档。
5. 新增业务代码顺序：实体（继承 `BaseEntity`）→ Mapper → Service → Controller；先登记 `CacheKeys` / `MqTopics` / `MqConsumerGroups` 再引用。
6. 完成后自检：后端 `mvn -o -q -DskipTests compile`；前端 `npm run type-check`；**不通过不得声称完成**。
7. 有设计分歧先给两个方案与取舍，等确认再写；架构决策写 `docs/adr/`，不要只留在对话里。
8. 重构/优化代码时：给出完整文件而非局部片段；注释与 Javadoc 用中文；说明改动点与原因；单独列出兼容性风险。

## 9. 常用命令

```bash
mvn clean package -DskipTests                    # 全量构建
mvn -o -q -DskipTests compile                    # 快速编译（改完代码先跑这个）
mvn -pl atifah-admin-web/backend -am package -DskipTests   # 只构建 admin BFF 及其上游
docker compose -f deploy/docker-compose/docker-compose.yml up -d   # 本地中间件
docker compose -f deploy/docker-compose/docker-compose.app.yml up -d --build   # 后端容器（方案 B，需先 mvn package）
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
cd atifah-admin-web/frontend && npm run dev      # 管理后台前端 :5173
```
