# AGENTS.md

> 本项目 AI 规则的**唯一入口**：Claude Code / CodeBuddy / Cursor / Codex 都先读本文件。
> 文档分层（越靠下越具体，冲突时就近覆盖）：
>
> 1. /`.claude/CLAUDE.md`：个人习惯（中文回复、不自动 commit、遵循阿里手册），**不进 Git**（项目内副本已加入 `.gitignore`）。
> 2. 本文件：项目硬约束与索引，每次必读，全文 ≤200 行；`CLAUDE.md` 只写 `@AGENTS.md`。
> 3. `.claude/rules/*.md`：`security` / `code-style` / `database` / `git` / `backend-javadoc`，全量加载。
> 4. `docs/*.md` + `docs/adr/`：architecture / domain / api / db / pitfalls，按需读取。
> 5. 模块级 `AGENTS.md`：就近生效，冲突时覆盖上层。
>
> 规则只维护一份，禁止在各层重复抄写。

<!-- 全量加载主题规则；根 CLAUDE.md 仍只导入本文件。 -->
@.claude/rules/security.md
@.claude/rules/code-style.md
@.claude/rules/database.md
@.claude/rules/git.md
@.claude/rules/backend-javadoc.md

## 1. 项目是什么

- 情绪管理产品：C 端 App（Flutter，`atifah-app/` 仅占位）+ 管理后台（Vue 3）+ 后端微服务。
- 后端是 Spring Cloud Alibaba 多模块工程，**当前处于骨架阶段**：公共能力与网关已实现，9 个业务服务只有启动类，尚无 Controller / Service / Mapper / 表结构。
- 业务域：账号、情绪打卡与日记、趋势与量表报告、社区树洞 Feed、冥想内容、消息推送、签到积分、运营统计。
- 数据敏感：情绪日记、量表结果属敏感个人信息，**隐私与加密优先级高于任何性能优化**。

## 2. 技术栈（禁止替换、禁止新增）

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

- 新增依赖必须先问人，确认后再改 pom；不得为一个工具方法引入新库。
- 禁止改根 `pom.xml` 的 parent 与 BOM 版本；禁止引入第二套 JSON / 日志 / HTTP 客户端。

## 3. 模块、端口与包边界

```text
pom.xml (parent: spring-boot-starter-parent)
├── atifah-common/      core · web · redis · mybatis · mq · security（pom 聚合，无依赖）
├── atifah-gateway/     Spring Cloud Gateway，响应式，8080
├── atifah-services/    9 个业务服务（实际父 POM）
└── atifah-admin-web/   frontend（Vue，独立 npm）+ backend（admin-service，8090）
```

端口：`gateway 8080 · auth 8081 · user 8082 · mood 8083 · analysis 8084 · community 8085 · content 8086 · message 8087 · growth 8088 · stats 8089 · admin 8090`

三条单看文件看不出的继承关系（详见 `docs/module-layout.md`）：

- `atifah-services/pom.xml` 才是业务服务的实际父 POM，common×5 + Nacos + OpenFeign + LoadBalancer 已在此声明，**单个服务里不要再加 common 依赖**。
- `atifah-admin-web/backend` 用 `<relativePath>../../atifah-services/pom.xml</relativePath>` 复用该父 POM，但直接聚合在**根** pom 下；其 artifactId 是 `admin-service`，不是目录名。
- `atifah-gateway` 独立，项目内只依赖 `common-core` + `common-security`，跑响应式栈，**不得引入 `common-web` / `common-mybatis`，不得开启虚拟线程**。

包约定：包根 `com.atifah.<service>`；公共代码只在 `com.atifah.common.*`，业务代码不得写进公共包。公共模块通过 `AutoConfiguration.imports` 自动装配，**不需要也不要加 `@ComponentScan`**。

## 4. 硬约束速查

- **安全**：敏感数据加密存储、日志脱敏、密钥只走环境变量；详见 `.claude/rules/security.md`。
- **编码**：`Result<T>` / `PageResult<T>` / `ErrorCode` / `BusinessException` / `@Valid`；构造注入；实体继承 `BaseEntity`；详见 `.claude/rules/code-style.md`。
- **数据**：一服务一库、禁止跨库 JOIN / `SELECT *` / 循环查库 / 拼接 SQL；缓存 Key 与 MQ Topic 先登记；详见 `.claude/rules/database.md`。
- **接口**：C 端 `/api/v1/**`、管理端 `/admin/api/**`、WebSocket `/ws/**`；网关唯一入口；写接口带 `Idempotency-Key`；详见 `docs/api-contract.md`。
- **Git**：不自动 commit / push；提交信息 `type: 描述`；受保护文件不擅改；详见 `.claude/rules/git.md`。

## 5. 接口契约（摘要）

- 请求头：`Authorization: Bearer <token>`、`Idempotency-Key`（写接口必填）、`X-Trace-Id`（透传）。
- 网关校验 JWT 后写 `X-User-Id` / `X-Username` 下游头，业务服务直接读 `CommonConstants`，**不要重复验签**。
- 分页参数固定 `page`（从 1 起）/ `size`（默认 20，上限 100）；时间 `yyyy-MM-dd HH:mm:ss`（Asia/Shanghai）；ID 一律字符串返回（雪花 ID 超出 JS 精度）。
- 资源用复数名词，动作交给 HTTP 方法；破坏性变更升 `v2`，新增字段保持向后兼容。
- **改接口先改契约与 `docs/api/<service>.md`，再改代码**，并同步 `common-core` 与前端 `src/api/types.ts` 三处。

## 6. 禁止事项（违反即打回）

- 禁止硬编码密码、密钥、Token、AK/SK、内部域名；`JWT_SECRET` 不得写死真实值。
- 禁止把情绪正文、量表结果、手机号、密码打印到日志或控制台；不得明文存储敏感字段。
- 禁止为让编译/测试通过而删除测试、关掉校验、加 `@SuppressWarnings`。
- 禁止擅自改：根 pom 的 parent/版本、`deploy/nacos/config/*`、`deploy/docker-compose` 端口映射、`deploy/sql` 已执行脚本、`.gitattributes` 换行设置。
- 禁止绕过网关直连业务服务；管理端不得直连业务库。
- 禁止自动 `git commit` / `git push`。

## 7. 文档索引（按需读，禁止一次性全文加载）

| 文档 | 内容 |
| --- | --- |
| `.claude/rules/security.md` | 敏感数据、密钥、鉴权、幂等 |
| `.claude/rules/code-style.md` | Java 命名/分层/响应/异常/实体/日志 |
| `.claude/rules/database.md` | 数据库、事务、缓存、MQ、分库分表 |
| `.claude/rules/git.md` | 提交、分支、变更边界、文档同步 |
| `.claude/rules/backend-javadoc.md` | 后端 Java 注释规范与补充/修正注释红线 |
| `docs/architecture.md` | 设计目标、技术架构、核心链路、高并发与一致性 |
| `docs/domain.md` | 业务域、服务归属、边界规则 |
| `docs/api-contract.md` | 接口契约总纲；`docs/api/<service>.md` 为各服务明细（待补） |
| `docs/db.md` | 数据模型、分片、加密字段、缓存与一致性 |
| `docs/module-layout.md` | Maven 反应堆、自动装配、配置分层、网关鉴权 |
| `docs/pitfalls.md` | 已知坑与排查清单 |
| `docs/adr/` | 架构决策记录 |
| `README.md` | 给人看：定位、快速开始、中间件地址与账号 |
| `atifah-admin-web/README.md` | 管理后台工程与部署 |

模块级规则（就近生效）：`atifah-common/AGENTS.md`、`atifah-gateway/AGENTS.md`、`atifah-services/AGENTS.md`、`atifah-admin-web/AGENTS.md`、`atifah-admin-web/frontend/AGENTS.md`。

## 8. 工作方式

1. 动手前先读相关模块的 `AGENTS.md` 与 `docs/` 对应文档，确认现有写法再改；不要凭 Spring 通用习惯猜。
2. 只改与任务相关的文件，不顺手重构，不新增未要求的功能。
3. 改配置时保持「一份真相」：共享配置改 `deploy/nacos/config/common-application.yaml` 并提醒导入 Nacos；本地 `application.yml` 只留端口、应用名、Nacos 连接、数据源 URL、生产者组这些差异化项。
4. 改路由、端口、服务名时，同步 `docs/module-layout.md` 与根 `README.md` 的端口表。
5. 新增业务代码顺序：实体（继承 `BaseEntity`）→ Mapper → Service → Controller；先登记 `CacheKeys` / `MqTopics` 再引用。
6. 完成后自检：`mvn -o -q -DskipTests compile`；前端改动跑 `npm run type-check`。**不通过不得声称完成**。
7. 有设计分歧先给两个方案与取舍，等我确认，不要自行选定就写。

## 9. 常用命令

```bash
mvn clean package -DskipTests                    # 全量构建
mvn -o -q -DskipTests compile                    # 快速编译（改完代码先跑这个）
mvn -pl atifah-admin-web/backend -am package -DskipTests   # 只构建 admin BFF 及其上游
docker compose -f deploy/docker-compose/docker-compose.yml up -d   # 本地中间件
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
cd atifah-admin-web/frontend && npm run dev   # 管理后台前端 :5173
```
