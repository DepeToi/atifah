# atiFah

情绪管理高并发微服务项目（App + 管理后台），基于 Spring Boot / Spring Cloud Alibaba 的多模块工程骨架。

文档分工：本文件提供给项目成员（定位、快速开始、架构设计、中间件、端口）；完整设计思路与数据模型见 [`docs/architecture.md`](docs/architecture.md)。AI 工具的协作方式见下方「AI 协作开发指南」，规则入口见 [`AGENTS.md`](AGENTS.md)。

## 技术基线

| 组件                      | 版本         |
| ----------------------- | ---------- |
| JDK                     | 21         |
| Spring Boot             | 3.5.16     |
| Spring Cloud            | 2025.0.3   |
| Spring Cloud Alibaba    | 2025.0.0.0 |
| MyBatis-Plus            | 3.5.17     |
| Redisson                | 3.52.0     |
| RocketMQ Spring Starter | 2.3.6      |
| JJWT                    | 0.12.7     |
| 管理后台 Web                | Vue 3 + Vite + Element Plus |
| MySQL                   | 8.0        |
| Redis                   | 7.x        |
| RocketMQ                | 5.3.1      |
| Nacos                   | 3.0.3      |

## 模块结构

```
atifah/
├── pom.xml                        # 聚合工程 + 版本管理
├── atifah-common/              # 公共能力
│   ├── common-core                # Result / ErrorCode / BusinessException / 常量
│   ├── common-web                 # 统一异常、TraceId、Sentinel、Actuator
│   ├── common-redis               # RedisTemplate 序列化、缓存 Key、Redisson
│   ├── common-mybatis             # 分页插件、审计字段填充、BaseEntity
│   ├── common-mq                  # Topic / 消费组常量、MessagePublisher
│   └── common-security            # JWT 签发校验、PasswordEncoder
├── atifah-gateway/             # Spring Cloud Gateway
├── atifah-services/            # C 端业务服务
│   ├── auth-service               # 8081 认证授权
│   ├── user-service               # 8082 用户与隐私设置
│   ├── mood-service               # 8083 打卡与情绪日记
│   ├── analysis-service           # 8084 趋势、量表与报告
│   ├── community-service          # 8085 树洞、社区与 Feed
│   ├── content-service            # 8086 冥想、文章与课程
│   ├── message-service            # 8087 站内信、WebSocket、推送
│   ├── growth-service             # 8088 签到、任务、积分
│   └── stats-service              # 8089 埋点与运营统计
├── atifah-admin-web/           # 管理后台
│   ├── frontend/                  # Vue 3 前端（Vite，独立构建，）
│   └── backend/                   # 8090 管理后台 BFF（Spring Boot）
├── atifah-app/                 # Flutter App（占位）
├── deploy/
│   ├── docker-compose/            # 本地中间件一键启动
│   ├── k8s/                       # K8s 部署清单（占位）
│   ├── nacos/                     # Nacos 共享配置源文件 + 导入说明
│   └── sql/                       # 初始化 Schema
```

## 总体架构设计

> 本节为设计摘要，完整分析与容量假设见 [`docs/architecture.md`](docs/architecture.md)。

### 设计目标

- **敏感数据**：情绪日记、量表结果属于敏感个人信息，隐私与加密优先。
- **写入高峰**：打卡集中在早 7–9 点、晚 21–23 点，需要削峰。
- **读多写少**：社区内容、冥想音频、文章以读为主，需要多级缓存。
- **社交与实时**：树洞/社区涉及 Feed 流、点赞计数、实时消息。
- **合规要求高**：内容审核、危机干预、未成年人保护、数据可删除。

容量基线（可按比例缩容）：注册用户 1000 万、DAU 100 万、峰值在线 20 万、打卡峰值 3000 QPS、读峰值 3 万 QPS、IM 长连接 5 万。

总体风格：App 前后端分离 + BFF；管理后台 Vue 3 SPA + `admin-service` BFF；微服务 + 事件驱动 + 读写分离 + 多级缓存 + 最终一致。

### 关键选型

| 层次       | 选型                                         |
| -------- | ------------------------------------------ |
| 注册/配置    | Nacos                                      |
| 网关       | Spring Cloud Gateway                        |
| RPC      | OpenFeign（默认）/ Dubbo 3（热点链路可选）               |
| 流控熔断     | Sentinel（规则持久化到 Nacos，网关+服务双层防护）             |
| 分布式事务    | Seata AT（仅少量强一致场景）                           |
| ORM/分库分表 | MyBatis-Plus / ShardingSphere-JDBC           |
| 缓存       | Redis 7 + Redisson + Caffeine（L1+L2 多级缓存）   |
| 消息       | RocketMQ 5.x（事务/延时/顺序消息 + 死信队列）              |
| 定时任务     | XXL-Job（周报生成、对账补偿、批量任务）                     |
| 文件       | OSS/MinIO 预签名直传                              |
| 可观测      | SkyWalking + Prometheus/Grafana + Loki        |
| 容器/发布    | Docker + Kubernetes + Helm/Kustomize，Harbor + ArgoCD |

### 核心链路

- **心情打卡**：Gateway 完成 JWT 验签、`Idempotency-Key` 去重、Sentinel 限流；`mood-service` 校验参数并以唯一索引 `(user_id, record_date)` 本地落库，再发事务消息 `mood-recorded`，由 stats/analysis/growth/message 异步处理统计、趋势、积分与通知。同步只做核心落库，其余全部异步。
- **情绪分析与报告**：打卡事件驱动 `analysis-service` 预计算日统计；周/月报由 XXL-Job 批量生成；AI 情绪摘要异步调用、独立限流与超时降级；量表结果加密存储。
- **社区 Feed**：发布落库（先审后发）后发 `post-published`，分别走审核、粉丝收件箱扩散、统计/热榜；普通用户写扩散（`feed:inbox:{userId}` ZSET 裁剪 1000 条），大 V/热点走读扩散；点赞用 Lua 原子去重 + 计数，RocketMQ 批量回写。
- **消息与实时通信**：在线走 `message-service` 内 WebSocket 推送，离线落库 + 厂商推送；RocketMQ 延时消息实现定时提醒；长连接粘性会话 + 按连接数 HPA 扩缩。

### 高并发与一致性

- **分层抗压**：CDN/Nginx 限流 → 网关 JWT 本地验签 + Sentinel → 无状态服务 HPA → Caffeine + Redis 多级缓存 → MySQL 主从/分库分表 → RocketMQ 削峰。
- **缓存设计**：用户/内容 `Caffeine(3–10s) + Redis(30–60min 随机 TTL)`；打卡状态、连续打卡、点赞去重、Feed 收件箱、热榜分别使用 Redis String/Bitmap/Set/ZSET 方案；穿透、击穿、雪崩均有对应处理；一致性采用 Cache Aside，关键场景加延迟双删或 Binlog 订阅刷新。
- **异步与一致性**：通知、统计、积分、审核、报告生成全部走 MQ；事务消息 + 本地消息表 + 定时对账保证“打卡成功 → 加积分”最终一致；消费端以 `msgId` 去重 + 业务唯一键 + 状态机幂等；同一用户事件使用相同 message key 保证顺序。
- **MQ Topic**：`mood-recorded`、`post-published`、`content-audit-result`、`notification`、`stats-event`、`points-changed`、`user-logoff`；统一重试 16 次 + 指数退避 → 死信队列 → 告警 + 人工补偿。
- **分库分表**：`t_mood_record`、`t_mood_diary`、`t_post`、`t_message` 按 `user_id` 哈希分片，评论按 `post_id` 分片；统一雪花 ID；跨分片统计走 `stats-service` 聚合表。

### 数据与安全

- 一服务一库，禁止跨库 JOIN；跨服务同步查询走 OpenFeign，异步事件走 RocketMQ。
- 日记正文、量表结果字段级加密（AES-256-GCM），密钥由 KMS/Vault 管理；日志禁止打印正文。
- 认证授权采用 OAuth2.1 + JWT：短时 Access Token、Refresh Token 轮换、`jti` 黑名单；管理端独立密钥 + MFA + IP 白名单。
- 内容先审后发、举报闭环；危机干预分级处理；未成年人模式与监护人同意；数据可导出、可注销、可删除（Saga 清理）。
- 防刷：设备指纹、验证码、UID/IP 频控、行为风控、文件类型与大小校验、SSRF 白名单。

### 部署与可观测性

- K8s：网关 3+ 副本、业务服务 2–N HPA，`message-service` 按连接数扩缩 + 粘性会话；探针、`preStop` 优雅下线、反亲和、NetworkPolicy、容器非 root。
- CI/CD：单测 + SonarQube → Maven 构建 → Jib 镜像 → Harbor → ArgoCD 同步 → Flyway 迁移 → 金丝雀发布 → 指标校验/回滚；管理后台前端独立 `npm ci && npm run build` 发布到 Nginx/CDN。
- 目标：可用性 99.95%，核心读 P99 ≤ 200ms、写 P99 ≤ 300ms，打卡成功率 ≥ 99.9%，RPO ≤ 5min / RTO ≤ 30min。

### 演进路线

- P0 工程骨架/中间件/CI → P1 核心闭环 + 管理后台 MVP → P2 社区/消息/增长 → P3 缓存/Feed/分库分表/限流降级 → P4 K8s 生产化/可观测/压测/安全加固。
- 关键取舍：不全量微服务（低流量服务可合并部署）；强一致范围最小化，优先最终一致 + 对账；缓存和 MQ 必须配套幂等、补偿与监控；情绪数据隐私优先级高于性能优化。

## 环境要求

- JDK 21+（确保 `JAVA_HOME` 指向 JDK 21，`java -version` 输出 21.x）
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0`（管理后台前端，Vite 8 要求）
- Docker / Docker Compose（用于启动 MySQL、Redis、Nacos、RocketMQ 等中间件）
- IDE：IntelliJ IDEA（推荐）

> 当前机器未检测到 Docker。仅编译 Maven 工程不需要 Docker；启动服务前需先准备中间件（本机安装或使用其他机器上的 Docker）。

## 快速开始

### 1. 启动中间件

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d
```

启动后：

| 组件                  | 地址                      | 说明                                     |
| ------------------- | ----------------------- | -------------------------------------- |
| MySQL               | `127.0.0.1:3306`        | 用户 `root` / 密码 `root`，自动创建各服务 Schema   |
| Redis               | `127.0.0.1:6379`        | 无密码                                    |
| Nacos               | `127.0.0.1:8848`        | 注册与配置中心                                |
| Nacos Console       | <http://localhost:8849> | Nacos 3.x 控制台，宿主机映射端口                  |
| RocketMQ NameServer | `127.0.0.1:9876`        |                                        |
| RocketMQ Dashboard  | <http://localhost:8180> |                                        |
| Sentinel Dashboard  | <http://localhost:8858> | 默认账号/密码 `sentinel` / `sentinel`        |
| Seata（可选）           | `127.0.0.1:8091`        | `docker compose --profile seata up -d` |

### 2. 编译

```bash
mvn clean package -DskipTests
```

### 3. 启动后端服务

启动顺序建议：先 `atifah-gateway`，再按需启动业务服务。可在 IDEA 中直接运行各 `*ServiceApplication`，或使用：

```bash
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
java -jar atifah-services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar atifah-admin-web/backend/target/admin-service-1.0.0-SNAPSHOT.jar
```

所有服务通过 Nacos 注册，网关按 `lb://atifah-<service>` 路由。

### 4. 启动管理后台前端

管理后台为独立的 Vue 工程，开发期通过 Vite 代理 `/admin/api` 到网关：

```bash
cd atifah-admin-web/frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。前端环境变量与生产部署方式见
[`atifah-admin-web/README.md`](atifah-admin-web/README.md)。

### 5. 健康检查

```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8082/actuator/health   # user-service
curl http://localhost:8090/actuator/health   # admin-service（管理后台 BFF）
```

## 配置约定

- 配置优先级：环境变量 > Nacos（`atifah_GROUP`）> 本地 `application.yml`。
- 常用环境变量：

| 变量                                                            | 默认值                                    |
| ------------------------------------------------------------- | -------------------------------------- |
| `NACOS_ADDR`                                                  | `127.0.0.1:8848`                       |
| `NACOS_NAMESPACE`                                             | 空（public）                              |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` | `127.0.0.1` / `3306` / `root` / `root` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`                | `127.0.0.1` / `6379` / 空               |
| `ROCKETMQ_NAMESRV`                                            | `127.0.0.1:9876`                       |
| `SENTINEL_DASHBOARD`                                          | `127.0.0.1:8858`                       |
| `JWT_SECRET`                                                  | 开发默认值（生产必须替换）                          |

- 管理后台前端环境变量（`VITE_API_BASE_URL`、`VITE_API_PROXY_TARGET`）见 `atifah-admin-web/README.md`。
- Servlet 业务服务已开启虚拟线程（`spring.threads.virtual.enabled=true`）；`atifah-gateway` 为响应式 Netty 栈，不适用该配置。
- 网关 JWT 鉴权当前默认关闭（`atifah.gateway.auth.enabled=false`），接入 `auth-service` 签发 Token 后开启。
- 生产环境请将 `JWT_SECRET` 替换为至少 256 位的强随机密钥，或切换为 RS256 公私钥方案。
- `growth-service` 的 Seata 默认关闭（`seata.enabled=false`），需要分布式事务时再启用。

## 已知依赖说明

启动网关时会看到一条 `spring-cloud-starter-gateway` 的 deprecation WARN，属预期现象（SCA 的 Sentinel 网关适配器仍依赖经典 starter）。

## 开发约定
- 一服务一库（`atifah_<service>`），禁止跨库 JOIN；同步查询用 OpenFeign，异步解耦用 RocketMQ。
- 对外统一 `Result<T>` / `PageResult<T>`，业务写接口带 `Idempotency-Key`（登录/刷新/登出例外，注册必须）。
- 管理后台：开发期 Vite 代理 `/admin/api` 到网关，生产由 Nginx 托管静态资源并反代，前端不直连业务服务。

## AI 协作开发指南（Claude / CodeBuddy / Codex）

> 三个工具通过**仓库文件 + Git**交接，不依赖对话记忆。规则唯一入口是 [`AGENTS.md`](AGENTS.md)；
> 本指南是给人看的操作手册，完整规则见 [`docs/rules/`](docs/rules/)、[`docs/contracts/`](docs/contracts/)、[`docs/review/standards.md`](docs/review/standards.md)。

### 1. 角色与写入边界

| 角色 | 负责 | 可写范围 | 禁止 |
| --- | --- | --- | --- |
| **Claude**（架构） | 需求拆解、ADR、接口/DDL/事件契约、公共能力、CI 与规则 | `docs/`、根 `pom.xml`、`atifah-common/`、模块骨架、`.github/` | 不实现业务 CRUD |
| **CodeBuddy**（开发） | 按任务包实现功能与测试 | 任务包“允许修改”的路径，通常为单个服务；任务包 `status` / §12 状态记录（仅此字段） | 不改契约、规则、父 pom、其他服务 |
| **Codex**（评审） | 只读评审 diff、合规检查、**独立自测试** | PR 评论 + `docs/review/reports/**`（只写评审报告；可执行构建/测试） | 不改代码/契约 |
| **人类**（维护者） | 确认架构、合并 PR、导入 Nacos、打基线标签 | 全部 | — |

**并发约束**：同一工作区同一时刻只允许一个角色写文件；并行任务用独立分支或 `git worktree`。

**任务状态权限**：Claude 只设 `draft` / `ready`（`ready` 需人工确认）；CodeBuddy 只设 `in-progress` / `in-review`；`done` 只能由人类维护者在合并且报告归档后设置；Codex 不得改状态。每次变更必须更新 front matter 的 `status` / `updated` / `status_reason`，并在任务包 §12 记录；无 PR 证据不得标 `in-review`，无合并/报告证据不得标 `done`（DOC-04）。

### 2. 启动位置与必读文件

| 工具 | 从哪里启动 | 每次必读 | 执行任务时再读 |
| --- | --- | --- | --- |
| Claude Code | 仓库根目录 | `CLAUDE.md` → `AGENTS.md` → `docs/rules/*` | 相关 ADR、契约、`docs/architecture.md` |
| CodeBuddy | 仓库根目录或目标服务目录 | `AGENTS.md` + 目标模块 `AGENTS.md` + `docs/rules/*` | 任务包及其引用的契约 |
| Codex | 仓库根目录或 PR | `AGENTS.md` + `docs/review/standards.md` | 任务包、契约、`git diff` |

- CodeBuddy 若支持项目规则文件，把根 `AGENTS.md` 设为规则源；不支持时，在每次任务 prompt 中附上 `AGENTS.md` 与任务包路径。
- 不要把 `docs/architecture.md` 整篇粘进 prompt；引用路径，让工具按需读取。
- 不把 `node_modules/`、`target/`、`dist/` 纳入上下文或评审范围。

### 3. 标准操作流程

#### 步骤 1：Claude 做架构设计（产出契约与任务包）

输入：需求 / Issue。输出：

- `docs/adr/NNNN-*.md`：关键取舍；
- `docs/contracts/openapi/<service>.yaml`、`docs/contracts/db/<service>.sql`、`docs/contracts/events/<event>.md`；
- `docs/tasks/T-xxx-<slug>.md`：任务包（目标、允许路径、验收标准、测试要求）。

```text
作为架构师，为 <需求> 做设计：
1) 写 ADR；
2) 更新 OpenAPI / DDL / 事件契约；
3) 写任务包 docs/tasks/T-xxx-<slug>.md。
不要实现业务代码；有取舍先列方案。
```

人工确认契约后，在 `main` 打基线标签（不要在契约未确认时打）：

```bash
git tag arch-baseline-v0.1
git push origin arch-baseline-v0.1
```

#### 步骤 2：CodeBuddy 开发（只按任务包实现）

```bash
git checkout main && git pull
git checkout -b feat/T-001-auth-login
```

开始实现时把任务包状态改为 `in-progress`；提交、推送并创建 PR 后改为 `in-review`。

```text
读取 AGENTS.md、目标模块 AGENTS.md、docs/tasks/T-001-auth-login.md
及其引用的契约。只修改任务包“允许修改”的路径；实现并补充测试；
运行任务包中的自检命令，贴出真实结果；不要修改契约、规则和根 pom。
```

完成自检后开 PR（PR 模板会自动带出；也可用 `gh pr create --web`）：

```bash
mvn -pl atifah-services/auth-service -am verify
cd atifah-admin-web/frontend && npm run type-check   # 涉及前端时
git push -u origin feat/T-001-auth-login
```

#### 步骤 3：CI 自动门禁

- 后端：`mvn -B -ntp verify`
- 前端：`npm ci && npm run build`
- 密钥扫描：gitleaks

CI 不通过不得进入评审；AI 不得为了“修绿”删测试或关校验。
CI 通过不等于 Codex 可以免测：Codex 仍需在独立工作区复跑自测试。

#### 步骤 4：Codex 只读评审 + 自测试

输入：任务包、相关契约、`git diff main...HEAD`、CI 结果。Codex 必须在独立 worktree / 干净检出（PR head commit）中执行：

```bash
mvn -B -ntp verify
cd atifah-admin-web/frontend && npm ci && npm run build
```

完整报告写入 `docs/review/reports/T-xxx-<head-short-sha>.md`；PR 评论只放摘要与链接。

输出固定格式，直接贴 PR 评论：

```text
[Blocker] path/to/File.java:42 — SEC-01 — 日志打印了日记正文
  证据：log.info("content={}", diary.getContent());
  影响：敏感个人信息泄露。
  建议：改为 log.info("diaryId={}", diary.getId()); 并补脱敏测试。

Self-test:
  commit: <head-sha>
  mvn -B -ntp verify: PASS
  npm ci && npm run build: PASS
  notes: 未运行 MySQL/Redis 集成测试

Report: docs/review/reports/T-001-<head-short-sha>.md
Verdict: REQUEST_CHANGES
```

```text
你是只读评审者。读取 AGENTS.md、docs/review/standards.md、
docs/tasks/<任务包>.md 和相关契约，评审 git diff main...HEAD。
在独立 worktree / 干净检出中运行 mvn -B -ntp verify 和前端 npm ci && npm run build，
记录命令、结果与未覆盖范围。按 [级别] file:line — 规则编号 — 问题 输出，
给出证据、影响、建议、Self-test 和 Verdict。不要修改代码或测试。
```

结论规则：任一 `Blocker` / `Major`、自测试失败或未执行、任务状态与 PR/合并/报告证据不符 → `REQUEST_CHANGES`；零 `Blocker` / `Major` 且自测试通过 → `APPROVE`。报告未落盘或任务包 §11 未归档 → 不得合并。

#### 步骤 5：修复、合并与回流

- 实现问题（`[Impl]`）或自测试失败 → CodeBuddy 修复，Codex 复审。
- 架构/契约问题（`[Arch]`）→ 回到 Claude，先改 ADR/契约，再实现。
- 信息不足（`[Question]`）→ 人类维护者确认。
- 人类合并 PR 前，CodeBuddy/维护者把报告路径与 Verdict 归档到任务包 §11；合并后由人类维护者更新 `status: done`（CodeBuddy 不得自行设 `done`）。
- 同一规则反复触发 → 更新 `AGENTS.md` / `docs/rules/`，或做成 CI/ArchUnit 自动检查。

### 4. 分支、提交与 PR

| 类型 | 分支 | 提交信息 |
| --- | --- | --- |
| 架构/契约 | `arch/<topic>` | `docs(arch): ...` |
| 功能 | `feat/T-xxx-<slug>` | `feat(scope): T-xxx ...` |
| 修复 | `fix/T-xxx-<slug>` | `fix(scope): T-xxx ...` |
| 文档/规则 | `docs/<topic>` | `docs: ...` |

- 一个 PR 只对应一个任务包，单服务，diff ≤400 行。
- 契约变更必须单独 PR + ADR，不得混在功能 PR 里。
- AI 不得自动 `git commit` / `git push`，除非用户明确要求。

### 5. 当前第一个任务

- 任务包：[`docs/tasks/T-001-auth-login.md`](docs/tasks/T-001-auth-login.md)
- 设计依据：[`docs/adr/0002-auth-login-token.md`](docs/adr/0002-auth-login-token.md)
- 契约：[`docs/contracts/openapi/auth-service.yaml`](docs/contracts/openapi/auth-service.yaml)、[`docs/contracts/db/auth-service.sql`](docs/contracts/db/auth-service.sql)
- 范围：注册 + 登录 + 签发 access/refresh token；refresh/登出（T-002）、网关鉴权（T-003）、用户资料（T-004）后续推进。
- 当前基线标签尚未创建：先审阅契约，再打 `arch-baseline-v0.1`，然后把任务包交给 CodeBuddy。

### 6. 常见错误

- 让 CodeBuddy 修改 `docs/contracts/` 来“迁就实现” → 契约漂移，Codex 必然打回。
- 让 Codex 直接改代码 → 评审失去独立性；修复一律回 CodeBuddy。
- Codex 只看 diff、只信 CI 不跑自测试 → 结论不可信；必须在独立工作区复跑。
- 只把评审结论留在 PR 评论/聊天，不落盘 → 无审计记录；必须写 `docs/review/reports/` 并在任务包 §11 归档。
- CodeBuddy 自行把任务状态设为 `done`、Codex 改状态、没有 PR 就标 `in-review` → 状态与证据不符，按 DOC-04 打回。
- 三个工具同时改同一工作区 → 冲突与覆盖；必须串行或用独立 worktree。
- 契约未冻结就开工 → 返工成本最高。
- 把架构文档整篇粘贴进 prompt → 上下文浪费且容易过期；引用路径即可。

## 下一步

1. 补充各服务 Controller / Service / Mapper 与核心表 DDL（Flyway）。
2. 按 [`docs/tasks/T-001-auth-login.md`](docs/tasks/T-001-auth-login.md) 实现 auth-service 注册/登录与 Token 签发；随后按 [`docs/adr/0002-auth-login-token.md`](docs/adr/0002-auth-login-token.md) 的 T-002/T-003 推进刷新、登出与网关鉴权。
3. 打通打卡链路（mood → RocketMQ → stats/analysis/growth/message）。
4. 接入 Sentinel 规则持久化、SkyWalking、Prometheus/Grafana。
5. 完善管理后台 Vue 页面与 `admin-service` 接口，接入独立鉴权与 MFA。
6. 迭代 Flutter App。
