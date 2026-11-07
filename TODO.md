# TODO — 项目改进项跟踪

> 来源：2026-10-30 架构巡检（Claude）
> 基线：`main` @ `0ff6292` + 当前未提交工作区
> 用途：跟踪巡检发现的问题；每项实施前必须先落到 ADR / 任务包，再按三角色流程推进。
> 状态图例：`[ ]` 待处理 · `[~]` 进行中 · `[x]` 完成 · `[!]` 阻塞/待决策

## 使用说明

- 本文件只是**跟踪索引**，不是任务包；具体实现范围、允许路径、验收标准以 `docs/tasks/T-xxx-*.md` 为准。
- 每项完成后补充：PR 编号、评审报告路径、合并 commit、验证命令结果。
- 禁止直接在 `main` 提交；禁止 AI 自动 `git commit` / `git push`。
- 新增依赖（测试容器、ESLint、Spectral 等）必须先人工确认。
- 当前仓库规则未明确分配 `atifah-gateway/**` 与 `deploy/**` 的 AI 角色，先按 P0-4 决策。

---

## 总览

| 优先级 | 编号 | 事项 | 状态 | 建议任务包 |
| --- | --- | --- | --- | --- |
| P0 | P0-1 | 网关身份头可伪造 | `[ ]` | T-006 |
| P0 | P0-2 | 数据库/Nacos 凭据硬编码 | `[ ]` | T-007 |
| P0 | P0-3 | 方案 B 容器内 RocketMQ 不可用 | `[ ]` | T-007 |
| P0 | P0-4 | 工作区未收口 + 角色/流程缺口 | `[~]` | ADR 0007 + T-006/007/008 |
| P1 | P1-1 | 注册幂等写入事务边界 | `[ ]` | T-009 |
| P1 | P1-2 | 登录时序枚举 + 状态判断 | `[ ]` | T-009 |
| P1 | P1-3 | 登录失败计数 TTL 竞态 | `[ ]` | T-009 |
| P1 | P1-4 | 网关 401 响应不符合 Result 契约 | `[ ]` | T-006 |
| P1 | P1-5 | ErrorCode → HTTP 状态映射硬编码 | `[ ]` | T-010 |
| P1 | P1-6 | 分页缺少统一上限 | `[ ]` | T-010 |
| P1 | P1-7 | TraceId 可注入 + 未跨服务透传 | `[ ]` | T-010 |
| P1 | P1-8 | 文档漂移（AGENTS/README/architecture/db/ADR 索引） | `[ ]` | T-008 |
| P1 | P1-9 | 网关鉴权启用边界未闭环 | `[ ]` | T-003（预留） |
| P1 | P1-10 | 测试与 CI 缺口 | `[ ]` | T-010 |
| P1 | P1-11 | 前端主包过大 | `[ ]` | T-011（待确认） |
| P2 | P2-1 | `.dockerignore` 构建上下文偏大 | `[ ]` | T-007 |
| P2 | P2-2 | 字符集/排序规则不一致 | `[ ]` | T-007 |
| P2 | P2-3 | 仓库残留空 lockfile | `[ ]` | T-008 |
| P2 | P2-4 | 方案 B 环境变量不完整 | `[ ]` | T-007 |
| P2 | P2-5 | CODEOWNERS/分支保护缺失 | `[ ]` | T-010 |
| P2 | P2-6 | 可观测性未闭环 | `[ ]` | 后续 |
| P2 | P2-7 | 虚拟线程 + JDBC 需压测验证 | `[ ]` | 后续 |
| P2 | P2-8 | Redis 序列化需单测确认 | `[ ]` | T-010 |

---

## P0：先处理，再继续业务功能

### P0-1 网关身份头可伪造

**证据**

- `atifah-gateway/src/main/java/com/atifah/gateway/filter/AuthGlobalFilter.java:32`：过滤器只在 `atifah.gateway.auth.enabled=true` 时装配。
- `atifah-gateway/src/main/resources/application.yml:122-133`：当前默认为 `false`。
- 网关没有清洗入站 `X-User-Id` / `X-Username`；`AuthGlobalFilter.java:49-60` 只在验签成功后追加这两个头。
- 白名单路径（`/api/v1/auth/`、`/actuator/`）即使开启鉴权也会直接透传。

**影响**

默认关闭鉴权时，客户端可自带身份头穿过网关；业务服务按 SEC-03 信任 `CommonConstants.HEADER_USER_ID` 后会形成越权。这是“默认配置 + 未来实现”叠加出的真实风险。

**动作**

- [ ] 新增 `IdentityHeaderSanitizingGlobalFilter`，Order 高于 `AuthGlobalFilter`，无条件删除入站 `X-User-Id` / `X-Username`。
- [ ] `AuthGlobalFilter` 验签通过后再写回；校验 `sub`、`username` 非空，避免下游收到 `"null"`。
- [ ] 增加 `WebTestClient` 测试：伪造头被剥离、合法 token 正确注入、非法 token 返回 401。
- [ ] 在 T-003 完成前保持 `auth.enabled=false`，不要提前打开。
- [ ] 在 `atifah-gateway/AGENTS.md` 补充“网关是身份头唯一写入方”的约束。

**验收**

- [ ] 伪造 `X-User-Id` 的请求到下游后该头不存在。
- [ ] 合法 token 请求下游能拿到正确身份。
- [ ] 网关测试纳入 CI。
- [ ] `mvn -pl atifah-gateway -am verify` 通过。

---

### P0-2 数据库/Nacos 凭据硬编码

**证据**

- `deploy/docker-compose/docker-compose.yml:9`：`MYSQL_ROOT_PASSWORD: root`
- 同文件 `:21`：healthcheck 使用 `-proot`
- 同文件 `:60-61`：`MYSQL_SERVICE_USER: root` / `MYSQL_SERVICE_PASSWORD: root`
- 同文件 `:70-71`：`NACOS_AUTH_IDENTITY_KEY/VALUE` 硬编码
- `README.md:76,141`、`deploy/dev-deploy.md:66,86`、`deploy/sql/02-nacos-config.sql:3`、`deploy/nacos/README.md:97` 存在 `root/root` 或 `-proot`
- `docs/review/reports/PR-11-3f724cc.md:13` 已判 `REQUEST_CHANGES`

**动作**

- [ ] `docker-compose.yml` 改为 `${MYSQL_ROOT_PASSWORD:?见 .env.example}` / `${MYSQL_SERVICE_PASSWORD:?...}`。
- [ ] healthcheck 改为 `mysqladmin ping -h 127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD`。
- [ ] `.env.example` 增加 `MYSQL_ROOT_PASSWORD`、`MYSQL_SERVICE_USER/PASSWORD`、`NACOS_AUTH_IDENTITY_KEY/VALUE`。
- [ ] `docker-compose.app.yml:28` 改用 `MYSQL_ROOT_PASSWORD`，避免与中间件口令漂移。
- [ ] `README.md` / `deploy/dev-deploy.md` / `deploy/sql/02-nacos-config.sql` / `deploy/nacos/README.md` 去掉明文口令，改用环境变量或 `-p` 交互。
- [ ] `.env` 保持被 `.gitignore` 忽略；文档注明 `.env` 不提交。
- [ ] 已存在本地数据卷按 README 手动迁移，不修改已执行脚本。

**验收**

- [ ] `grep -rn "root/root\|-proot" --include='*.md' --include='*.yml' --include='*.yaml' --include='*.sql' .` 无命中（评审报告除外）。
- [ ] 缺失 `MYSQL_ROOT_PASSWORD` 时 `docker compose config` 直接失败。
- [ ] 方案 A/B 均能正常启动。
- [ ] PR #11 评审报告中的 Blocker 已闭环。

---

### P0-3 方案 B 容器内 RocketMQ 不可用

**证据**

- `deploy/docker-compose/rocketmq/broker.conf:14`：`brokerIP1=127.0.0.1`
- `deploy/docker-compose/docker-compose.app.yml:31`：容器内 `ROCKETMQ_NAMESRV: rocketmq-namesrv:9876`
- `broker.conf` 注释已提示“服务在容器内请改为容器可达地址”，但方案 B 未处理。

**影响**

容器内服务从 NameServer 拿到 `127.0.0.1:10911`，会连接容器自身，MQ 发送/消费失败；方案 B 的“全部后端起容器”因此在 MQ 链路上不可用。

**动作（二选一）**

- [ ] 推荐：新增容器模式 broker 配置（如 `broker-container.conf`，`brokerIP1=rocketmq-broker`），通过 compose profile / `--env-file` 切换。
- [ ] 或使用宿主 LAN IP / `host.docker.internal`，并补 `extra_hosts`；评估宿主机与容器双向解析。
- [ ] `deploy/dev-deploy.md` 明确方案 A/B 切换时需重启 broker，并给出验证命令。
- [ ] 增加一条容器内消息发送冒烟测试（auth-service 或临时测试端点）。

**验收**

- [ ] 容器内启动 auth-service，发送一条测试消息，RocketMQ Dashboard 可见。
- [ ] 切回方案 A（宿主机 JVM）后 MQ 仍正常。
- [ ] 冒烟步骤写入 `deploy/dev-deploy.md`。

> 本轮未起 Docker，该结论来自静态分析；实施前先用容器实测确认。

---

### P0-4 工作区未收口 + 角色/流程缺口

**证据**

- `git status`：23 个已跟踪文件修改/删除 + 3 个未跟踪文件，`+240/-523`。
- 混合主题：Redis 空密码修复、删除 `NacosNamespaceGuard`、方案 B Docker、删除 k8s/prod 文档、README/architecture 重写。
- 直接发生在 `main` 工作区；没有对应 ADR/任务包；触碰 `deploy/nacos/**`、`deploy/docker-compose/**` 等受保护路径。
- `docs/review/reports/PR-10-00db983.md` 与 `PR-11-3f724cc.md` 均为 `REQUEST_CHANGES`；PR #10 的 Sentinel QPS 验收未闭环。
- `AGENTS.md` §0 未明确分配 `atifah-gateway/**` 与 `deploy/**` 的 AI 角色。

**动作**

- [ ] 先把工作区拆成 3 个分支/PR：
  - [ ] `arch/gateway-trust-boundary`（T-006）
  - [ ] `arch/deploy-runtime-credentials`（T-007）
  - [ ] `docs/architecture-sync`（T-008）
- [ ] 新增 ADR：网关身份头信任边界与鉴权启用顺序。
- [ ] 新增 ADR：当前仓库部署范围（仅开发环境？）与凭据管理；明确是否保留 `NacosNamespaceGuard`。
- [ ] 决定 `NacosNamespaceGuard`：恢复生产 namespace 防呆，或在 ADR 中明确“生产部署不在本仓库范围”并从文档移除生产承诺。
- [~] 更新 `AGENTS.md` §0，明确 `atifah-gateway/**`、`deploy/**` 的可写角色与受保护子路径（ADR-0007 已起草，待合并）。
- [ ] 创建任务包 T-006 / T-007 / T-008（Claude 只设 `draft`；`ready` 需人工确认）。
- [ ] 补做 PR #10 的 Sentinel QPS=1 实测，回填 ADR 0003。
- [ ] 修复 PR #11 的凭据 Blocker（与 P0-2 合并处理）。
- [ ] 合并后打新的 `arch-baseline-vX.Y` 标签。

**验收**

- [ ] 每个 PR 有任务包/ADR、CI 通过、Codex 报告归档。
- [ ] `main` 上不再有未收口改动。
- [ ] 单个 PR 语义 diff ≤400 行（docs-only 报告归档提交不计入），只做一件事。

---

## P1：下一批处理

### P1-1 注册幂等写入事务边界

**证据**

- `atifah-services/auth-service/src/main/java/com/atifah/auth/service/impl/AuthServiceImpl.java:66`：`register` 带 `@Transactional`。
- 同文件 `:100`：Redis 幂等结果在事务提交前写入。
- 同文件 `:144`：冲突重读在事务内 `Thread.sleep`，最多占用连接 100ms。

**动作**

- [ ] 幂等结果改为 `TransactionSynchronization.afterCommit` 写入，或用 `TransactionTemplate` 把写 Redis 放到提交后。
- [ ] 冲突重读移出事务（`REQUIRES_NEW` 或独立方法），避免事务内阻塞。
- [ ] 补测试：数据库提交失败时不留下“幽灵 userId”；Redis 写失败时的降级行为明确。
- [ ] 评估幂等键是否按手机号/请求指纹隔离，避免跨用户复用返回他人 userId。

**验收**

- [ ] 提交失败后同 key 重试不会返回不存在的账号。
- [ ] 单测覆盖成功、重复键、提交失败、Redis 异常路径。
- [ ] `mvn -pl atifah-services/auth-service -am verify` 通过。

---

### P1-2 登录时序枚举 + 状态判断

**证据**

- `AuthServiceImpl.java:107-119`：账号不存在时直接抛 401，不执行 BCrypt；密码错误时执行 BCrypt，存在明显时序差。
- 同文件 `:120-122`：只判断 `DISABLED`，未来新增状态（如锁定、待审核）会被放行。

**动作**

- [ ] 账号不存在时对固定 dummy hash 执行一次 `passwordEncoder.matches`，拉平时序。
- [ ] 状态判断改为 `status != NORMAL` 拒绝（或明确状态白名单）。
- [ ] 补时序/状态测试（至少验证调用路径一致）。

**验收**

- [ ] 不存在账号与密码错误的响应时间差在可接受范围（压测/单测断言路径）。
- [ ] 非 NORMAL 状态一律拒绝。
- [ ] 日志仍不含手机号。

---

### P1-3 登录失败计数 TTL 竞态

**证据**

- `atifah-services/auth-service/src/main/java/com/atifah/auth/service/impl/LoginAttemptServiceImpl.java:46-48`：只在 `count==1` 时 `EXPIRE`；EXPIRE 失败或 key 无 TTL 会永久锁死。
- 同文件 `:38`：`Integer.parseInt` 可能溢出。

**动作**

- [ ] 用 Lua 脚本原子执行 `INCR + PEXPIRE`（固定窗口）。
- [ ] 改用 `Long.parseLong` 并容错非法值（按 0 处理或删除 key）。
- [ ] 补测试：TTL 设置、计数达到阈值、TTL 缺失自愈。

**验收**

- [ ] 失败计数窗口结束后自动恢复。
- [ ] 并发失败请求不会突破阈值太多。
- [ ] Redis 异常时登录行为有明确降级。

---

### P1-4 网关 401 响应不符合 Result 契约

**证据**

- `atifah-gateway/src/main/java/com/atifah/gateway/filter/AuthGlobalFilter.java:72-79`：只有 `code/message`，缺 `data/timestamp`，Content-Type 无 charset。
- `atifah-gateway/AGENTS.md` 要求直接写响应体时保持 `{code,message,data,timestamp}`。
- `docs/contracts/api-conventions.md` §3 统一 `Result<T>`。

**动作**

- [ ] 输出 `{code,message,data:null,timestamp}`。
- [ ] Content-Type 使用 `application/json;charset=UTF-8`。
- [ ] 抽一个网关侧响应写入工具，避免多处重复。
- [ ] 增加 401 响应结构测试。

**验收**

- [ ] 401 响应可被前端 `request<T>()` 统一解析。
- [ ] 与 `docs/contracts/api-conventions.md` 一致。

---

### P1-5 ErrorCode → HTTP 状态映射硬编码

**证据**

- `atifah-common/common-web/src/main/java/com/atifah/common/web/handler/GlobalExceptionHandler.java:98-106`：手写 `40100/40300/40400/40900/42900`。
- `40500/50000` 通过 `BusinessException` 抛出时会落到 400。

**动作**

- [ ] 改为基于 `ErrorCode` 枚举映射（可给枚举加 `httpStatus` 或集中映射表）。
- [ ] 补 `40500`、`50000` 映射测试。
- [ ] 保持 `ErrorCode` 仍是唯一错误码来源。

**验收**

- [ ] 新增错误码无需改 `switch` 数字。
- [ ] HTTP 状态与契约表一致。

---

### P1-6 分页缺少统一上限

**证据**

- `atifah-common/common-mybatis/src/main/java/com/atifah/common/mybatis/config/MybatisPlusAutoConfiguration.java:32`：`new PaginationInnerInterceptor(DbType.MYSQL)` 未设置 maxLimit。
- `docs/rules/database.md` DB-02 / `api-conventions.md` §8 要求 `size` 上限 100。

**动作**

- [ ] `PaginationInnerInterceptor` 设置 `setMaxLimit(100L)`（必要时 `setOverflow(false)`）。
- [ ] 补一个超过 100 的分页单测（框架层）。

**验收**

- [ ] 任何服务传 `size=1000` 都被限制到 100 或拒绝。
- [ ] 现有查询不受影响。

---

### P1-7 TraceId 可注入 + 未跨服务透传

**证据**

- `atifah-common/common-web/src/main/java/com/atifah/common/web/filter/TraceIdFilter.java:32-37`：直接信任客户端 `X-Trace-Id`。
- `atifah-gateway/src/main/java/com/atifah/gateway/filter/TraceIdGlobalFilter.java:26-28`：同上。
- Feign / MQ 未见 TraceId 透传。

**动作**

- [ ] 校验 TraceId 格式（如 `[A-Za-z0-9-]{8,64}`），非法则重新生成。
- [ ] 增加 Feign `RequestInterceptor` 透传 `X-Trace-Id`。
- [ ] MQ 消息头透传 TraceId（与事件契约同步）。
- [ ] 补测试。

**验收**

- [ ] 恶意超长/控制字符 TraceId 不会进入日志。
- [ ] 网关 → 服务 → Feign → MQ 链路可串联。
- [ ] 日志格式仍为 `%X{traceId}`。

---

### P1-8 文档漂移

**证据**

- `AGENTS.md:42`、`atifah-services/AGENTS.md:47`：仍称“9 个服务只有启动类”，与 auth-service 已实现不符。
- `docs/architecture.md:227`：写 OAuth2.1 + `jti` 黑名单，与 ADR 0002 的自建 HS256 JWT（无 `jti`）不符。
- `docs/db.md:15`：`t_user` 含 `phone/password_hash`，与 ADR 0002 的 `t_auth_account` 归属冲突。
- `docs/adr/README.md:27-28`：索引缺 ADR 0003。
- `README.md` 的“下一步”仍把 auth 注册/登录列为待补。

**动作**

- [ ] 更新根 `AGENTS.md` §1 与 `atifah-services/AGENTS.md` 的当前状态。
- [ ] `docs/architecture.md` 区分“已启用 / 规划中”，OAuth2.1/`jti` 标为演进方向，当前以 ADR 0002 为准。
- [ ] `docs/db.md` 改为 `t_auth_account`（认证）+ `t_user_profile`（资料），明确归属。
- [ ] `docs/adr/README.md` 补 0003/0004/0005 索引。
- [ ] `README.md` “下一步”按实际进度重排。
- [ ] 检查所有相对链接与 ADR/契约引用可用。

**验收**

- [ ] 文档中不再出现“只有启动类”等过期状态。
- [ ] ADR 索引完整。
- [ ] 文档与契约、代码无矛盾。

---

### P1-9 网关鉴权启用边界未闭环

**证据**

- `atifah-gateway/src/main/resources/application.yml:126-128`：白名单只有 `/api/v1/auth/`、`/actuator/`。
- 开启后 `/admin/api/**`、`/ws/**` 会被 C 端 JWT 拦截；管理端独立鉴权与 WebSocket token 方案尚未实现。
- `/actuator/` 白名单会公开指标端点。

**动作（T-003 前置）**

- [ ] 改为精确路径白名单（register/login/refresh），logout 与业务接口需鉴权。
- [ ] 管理端独立鉴权链（独立密钥 + MFA + IP 白名单）。
- [ ] WebSocket 鉴权方案（query token / subprotocol / 短期连接票据）。
- [ ] `/actuator/**` 收敛到管理端口或 IP 白名单，不放在公网白名单。
- [ ] CORS 预检与鉴权过滤器顺序验证。
- [ ] 更新 ADR（T-003）与 `docs/rules/security.md` SEC-03。

**验收**

- [ ] 开启 `auth.enabled=true` 后 C 端、管理端、WebSocket 三条链路均可按预期鉴权。
- [ ] 未认证请求返回统一 401 Result。
- [ ] 管理端 Token 不能访问 C 端接口，反之亦然。

---

### P1-10 测试与 CI 缺口

**证据**

- 仅 `auth-service` 有测试；`atifah-gateway`、`atifah-common/*` 无测试。
- `.github/workflows/ci.yml` 只有 `mvn verify`、`npm run build`、gitleaks。
- 缺少契约 lint、YAML/compose 校验、`git diff --check`、覆盖率门槛、依赖漏洞扫描。

**动作**

- [ ] 增加网关过滤器测试（身份头、401、TraceId、CORS）。
- [ ] 增加 common 模块单测（Result/ErrorCode/分页/TraceId）。
- [ ] CI 增加 `git diff --check`、YAML 解析、`docker compose config`。
- [ ] CI 增加 OpenAPI/DDL 契约校验（工具选型需确认）。
- [ ] 增加 JaCoCo 覆盖率门槛（阈值先定低值，逐步提高）。
- [ ] 增加依赖漏洞扫描（OWASP Dependency-Check 或 Trivy，需确认）。
- [ ] 评估 Testcontainers 集成测试（MySQL/Redis/MQ，需确认依赖）。

**验收**

- [ ] CI 能在 PR 上拦住硬编码凭据、YAML 语法、契约不一致。
- [ ] 网关与 common 的关键路径有测试。
- [ ] 覆盖率报告可见。

---

### P1-11 前端主包过大

**证据**

- `atifah-admin-web/frontend/src/main.ts:4-16`：全量 `import ElementPlus` 并 `app.use(ElementPlus)`。
- 构建产物 `index-*.js` 约 `1,021.73 kB`（gzip `330.34 kB`），Vite 提示 chunk > 500 kB。

**动作**

- [ ] 评估按需导入（`unplugin-vue-components` + `unplugin-auto-import`，新增 devDependency 需确认）。
- [ ] 或改为手动注册实际使用的 Element Plus 组件。
- [ ] 构建后对比主包体积与首屏加载。

**验收**

- [ ] 主包体积明显下降（目标 < 500 kB，gzip 同步下降）。
- [ ] 现有页面功能与样式无回归。
- [ ] `npm run build` 通过。

---

## P2：排期优化

### P2-1 `.dockerignore` 构建上下文偏大

- [ ] 排除 `.github`、`.claude`、`atifah-admin-web/frontend`、`**/target/classes`、`**/target/test-classes`、`**/target/surefire-reports`、`deploy/nginx` 等。
- [ ] 保留 `**/target/*.jar`，确保 Dockerfile 能 COPY 到可执行 jar。
- [ ] 对比优化前后 `docker build` 上下文大小。

### P2-2 字符集/排序规则不一致

- [ ] `deploy/sql/01-init-databases.sql` 的 `utf8mb4_general_ci` 与 `V1__init.sql` 的 `utf8mb4_0900_ai_ci` 统一。
- [ ] 在 `docs/rules/database.md` 明确排序规则基线。
- [ ] 已存在库的迁移方案单独评估（不直接改已执行脚本）。

### P2-3 仓库残留空 lockfile

- [ ] 删除根 `package-lock.json` 与 `atifah-admin-web/package-lock.json`（当前均为空 `packages: {}`）。
- [ ] 只保留 `atifah-admin-web/frontend/package-lock.json`。
- [ ] 确认 `.gitignore` 不会误伤前端 lockfile。

### P2-4 方案 B 环境变量不完整

- [ ] `docker-compose.app.yml` 透传 `JWT_SECRET`，确保网关与 auth-service 一致。
- [ ] 需要 Nacos 鉴权时透传 `NACOS_USERNAME` / `NACOS_PASSWORD`。
- [ ] 增加 `REDIS_PASSWORD`、`SENTINEL_DASHBOARD` 等可选变量说明。
- [ ] 在 `.env.example` 中补齐并注明默认值仅限本地。

### P2-5 CODEOWNERS/分支保护缺失

- [ ] 增加 `.github/CODEOWNERS`，按 `docs/contracts/**`、`deploy/**`、`atifah-common/**`、`atifah-services/**` 划分。
- [ ] 配置 `main` 分支保护：必需 CI、必需评审、禁止直接推送。
- [ ] 与三角色工作流对齐。

### P2-6 可观测性未闭环

- [ ] TraceId 跨 Feign/MQ 透传（与 P1-7 合并）。
- [ ] Sentinel 规则持久化到 Nacos。
- [ ] Prometheus 指标 + Grafana 面板（JVM、HTTP、MQ 堆积、Redis 命中率）。
- [ ] 告警规则与值班说明。

### P2-7 虚拟线程 + JDBC 需压测验证

- [ ] 在压测环境验证 MySQL Connector/J 的 synchronized 路径是否 pin carrier thread。
- [ ] 对比虚拟线程开关前后的登录/打卡 P99。
- [ ] 如有 pinning，评估升级驱动或局部关闭虚拟线程。

### P2-8 Redis 序列化需单测确认

- [ ] 为 `GenericJackson2JsonRedisSerializer` 增加 `LocalDateTime`、集合、嵌套对象序列化/反序列化单测。
- [ ] 确认是否需要自定义 `ObjectMapper`（JavaTimeModule、默认类型信息）。
- [ ] 如不需要通用对象缓存，明确推荐使用 `StringRedisTemplate`。

---

## 建议任务包拆分

| 任务包 | 主题 | 允许路径（建议） | 状态 |
| --- | --- | --- | --- |
| T-006 | 网关身份头 + 401 Result | `atifah-gateway/**` | 待创建（draft） |
| T-007 | 部署凭据 + 方案 B 容器运行时 | `deploy/**`、`.env.example`、`.dockerignore` | 待创建（draft） |
| T-008 | 文档/ADR 同步 + 仓库清理 | `docs/**`、`AGENTS.md`、`README.md`、模块 `AGENTS.md`、根 lockfile | 待创建（draft） |
| T-009 | auth-service 安全加固 | `atifah-services/auth-service/**` | 待创建（draft） |
| T-010 | CI/测试/公共能力加固 | `.github/**`、`atifah-common/**`、根 `pom.xml`（如需） | 待创建（draft） |
| T-002 | refresh + logout | 预留 | 见 ADR 0002 |
| T-003 | 网关鉴权启用 + 白名单 + 管理端/WS | 预留 | 见 ADR 0002 |
| T-004 | 用户资料创建 / user-registered | 预留 | 见 ADR 0002 |

> T-002/T-003/T-004 编号已被 ADR 0002 预留，不要复用。新任务从 T-006 起编号（T-005 已用于注册幂等加固）。

---

## 通用验收命令

```bash
# 后端
mvn -B -ntp verify

# 前端
cd atifah-admin-web/frontend && npm run build

# 变更卫生
git diff --check
git status --short

# 凭据扫描（P0-2 完成后应无命中）
grep -rn "root/root\|-proot" --include='*.md' --include='*.yml' --include='*.yaml' --include='*.sql' .
```

---

## 变更记录

| 日期 | 变更 | 操作人 |
| --- | --- | --- |
| 2026-10-30 | 创建巡检 TODO，记录 P0/P1/P2 改进项 | Claude |
| 2026-11-07 | P0-4 工作流部分由 ADR-0007 处理（变更分类 / 四状态 / 报告归档 / 角色归属） | Claude |
