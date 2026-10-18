# 已知坑与排查清单

> 读这份文档的时机：服务起不来、接口 404/401、配置不生效、MQ 不消费、前端类型检查失败。
> 先按本文件排查，再去翻代码；确认是文档缺失就回来补一条。

## 1. Maven 与模块

- `microhoov-services/pom.xml` 才是业务服务的**实际父 POM**，common×5、Nacos、OpenFeign、LoadBalancer 已声明；子服务 pom 里再写一遍属于重复依赖。
- `microhoov-admin-web/backend` 通过 `<relativePath>../../microhoov-services/pom.xml</relativePath>` 复用父 POM，但聚合在**根** pom 下；artifactId 是 `admin-service`，不是 `admin-web-backend`。
- `microhoov-gateway` 只依赖 `common-core` + `common-security`。加入 `common-web` / `common-mybatis` / `common-redis` / `spring-boot-starter-web` 会拉起 servlet 栈，网关启动失败或行为异常。
- 网关是响应式栈，**不要开启** `spring.threads.virtual.enabled`；业务服务（servlet）才开启虚拟线程。
- 公共模块靠 `AutoConfiguration.imports` 自动装配，业务服务**不需要** `@ComponentScan("com.microhoov")`；新增自动配置类必须登记到对应 imports 文件。
- `common-web` 标注了 `@ConditionalOnWebApplication(SERVLET)`，所以网关拿不到 `GlobalExceptionHandler`，网关异常要在过滤器内自行转 `Result`。
- `@MapperScan` 必须指向 `com.microhoov.<service>.mapper`；Mapper 放错包会启动即报 `Invalid bound statement`。

## 2. 配置

- 共享配置只改 `deploy/nacos/config/common-application.yaml`，改完必须**重新导入 Nacos**；只改仓库不会影响运行中的服务。
- 本地 `application.yml` 只留差异化项：端口、应用名、Nacos 连接、数据源 URL、生产者组；复制共享配置到本地会造成两处漂移。
- 各服务通过 `spring.config.import` 引入 Nacos，前缀是 `optional:`：Nacos 挂了仍能启动，但 MyBatis-Plus 逻辑删除、RocketMQ 等共享配置不会生效。
- `common-application.yaml` 刻意不含 `spring.cloud.nacos.*`；连接 Nacos 的配置只能留在本地。
- Nacos 3.x 控制台映射在宿主机 `8849`，服务端地址是 `8848`，不要混用。

## 3. 网关与鉴权

- `microhoov.gateway.auth.enabled` 默认 `false`；开启前必须补白名单。当前白名单只有 `/api/v1/auth/` 与 `/actuator/`，`/ws/**`、`/admin/api/**` 会被直接 401。
- 网关过滤器链禁止阻塞调用（JDBC、同步 HTTP、`Thread.sleep`），必须返回 `Mono` / `Flux`。
- 网关没有 `@RestControllerAdvice`（无 servlet 上下文），不要照搬业务服务的异常处理。
- 启动时出现 `spring-cloud-starter-gateway` deprecation WARN 属预期：SCA 的 Sentinel 网关适配器仍依赖经典 starter，不要“顺手升级”。

## 4. 数据库与数据

- `common-mybatis` 引入了 Flyway，但仓库尚无 `db/migration` 脚本；服务启动**不会自动建表**。补 DDL 时按服务放 `src/main/resources/db/migration/V1__init.sql`。
- 雪花 ID 超出 JS `Number` 精度；接口必须把 ID 序列化为字符串，前端类型用 `string`。
- 逻辑删除依赖 `deleted` 字段与 Nacos 中的 MyBatis-Plus 配置；配置没导入时删除会变成物理删除或查询不过滤。
- 写接口幂等靠 `Idempotency-Key` + Redis + 数据库唯一索引；只做 Redis 去重会在并发/过期时失效。
- 禁止跨库 JOIN、`SELECT *`、循环内查库、拼接 SQL；列表接口必须分页。

## 5. 缓存与消息

- 新增缓存 Key 先登记 `CacheKeys`；新增 Topic / 消费组先登记 `MqTopics` / `MqConsumerGroups`。散落硬编码会冲突且难排查。
- 发消息统一用 `MessagePublisher`，不要裸用 `RocketMQTemplate`；key 用业务键（通常 userId）保证同一用户事件顺序。
- 消费端必须幂等：`msgId` 去重 + 业务唯一键 + 状态机；失败重试 + 死信 + 告警，禁止吞异常。
- 分布式锁用 Redisson + `CacheKeys.LOCK`；锁必须有过期时间或 watchdog，释放用 Lua 校验持有者。

## 6. 管理后台与前端

- 前端只经网关 `/admin/api/**` 访问 `admin-service`；开发期 Vite 代理到 `http://127.0.0.1:8080`。
- `verbatimModuleSyntax` 已开启：类型导入必须 `import type`，否则构建报错。
- `strict`、`noUnusedLocals`、`noUnusedParameters` 全开；未使用变量/参数会直接编译失败。
- 只通过 `src/api/http.ts` 的 `request<T>()` 发请求，不裸调 axios；响应类型以 `src/api/types.ts` 为准。
- 新增路由必须有 `meta.title`（顶栏与 `document.title` 都依赖它），组件一律懒加载。
- 未引入 ESLint / Prettier，风格为手工约定：无分号、单引号、2 空格缩进、尾逗号。
- 管理端独立鉴权链（独立密钥 + MFA）尚未接入；不要把 C 端 Token 或登录逻辑写死在页面里。

## 7. 构建与环境

- JDK 21、Maven 3.9+；前端 Node.js `^20.19.0 || >=22.12.0`（Vite 8 要求）。
- 当前机器可能未安装 Docker；仅编译 Maven 工程不需要 Docker，启动服务前需先准备 MySQL / Redis / Nacos / RocketMQ。
- `.gitattributes` 控制换行符，不要修改；Windows 下提交前用 `git diff --check` 看行尾空白。
- 改完代码先跑 `mvn -o -q -DskipTests compile`；前端跑 `npm run type-check`。不通过不得声称完成。

## 8. 快速排查表

| 现象 | 优先检查 |
| --- | --- |
| 服务启动失败 | 网关是否误引 servlet 依赖；虚拟线程配置；端口占用；Nacos 连接 |
| 接口 404 | 网关路由是否缺失；服务是否注册到 Nacos；Path 前缀是否匹配 |
| 接口 401 | `microhoov.gateway.auth.enabled`；白名单是否覆盖路径；Token 是否过期 |
| 配置不生效 | Nacos 是否发布并导入；本地是否重复配置；配置项是否放错层级 |
| Mapper 注入失败 | `@MapperScan` 路径；Mapper 是否继承 `BaseMapper`；是否在正确包 |
| 表不存在 | 是否补了 Flyway `db/migration` 脚本；服务是否有建表权限 |
| MQ 不消费 | Topic / 消费组是否登记；NameServer 地址；消费组订阅关系 |
| 前端类型检查失败 | `import type`；未使用变量；`ImportMetaEnv` 是否补声明 |
