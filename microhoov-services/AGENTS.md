# AGENTS.md — microhoov-services

> 模块级规则，与根 [`AGENTS.md`](../AGENTS.md) 及 [`.claude/rules/*`](../.claude/rules/) 叠加生效；冲突时以本文件为准（就近覆盖）。
> 父 POM 的继承关系见 [`docs/module-layout.md`](../docs/module-layout.md)；编码与数据细则见 [`code-style.md`](../.claude/rules/code-style.md)、[`database.md`](../.claude/rules/database.md)。

## 依赖

- 本模块父 POM 已声明 common×5、Nacos discovery/config、OpenFeign、LoadBalancer 和 `spring-boot-maven-plugin`，**子服务 pom 里不要再写这些依赖**，只加本服务独有的（如 `growth-service` 的 Seata）。
- 新增依赖先问人；不加第二套 JSON / 日志 / HTTP 客户端。

## 单服务骨架

```text
<service>/src/main/java/com/microhoov/<service>/
├── <Service>Application.java   # 三注解：@SpringBootApplication @EnableDiscoveryClient @MapperScan("com.microhoov.<service>.mapper")
├── controller/                 # @Valid 校验 + 调 service + 返回 Result
├── service/                    # 业务编排，impl 子包
├── mapper/                     # 继承 BaseMapper<T>
├── entity/                     # 继承 BaseEntity
├── dto/ · vo/                  # 入参 / 出参
└── config/                     # 本服务配置类
```

- 包根固定 `com.microhoov.<service>`；Mapper 必须在 `...<service>.mapper`，否则 `@MapperScan` 扫不到。
- 本地 `application.yml` 只留差异化项：端口、`spring.application.name`、Nacos 连接（`spring.config.import` + `spring.cloud.nacos.*`）、数据源 URL、生产者组。共享配置不要复制到本地。
- 服务名 `microhoov-<service>`，生产者组 `microhoov-<service>-producer`，库名 `microhoov_<service>`。

## 业务落地顺序

1. 实体（继承 `BaseEntity`，表字段 `created_at` / `updated_at` / `deleted`，禁止自增主键）。
2. Mapper（`BaseMapper<T>`，复杂 SQL 才写 XML 到 `resources/mapper/`）。
3. Service（`@RequiredArgsConstructor` 构造注入；写操作加事务；业务失败抛 `BusinessException`）。
4. Controller（只做参数校验与组装；不要 try-catch 包装）。
5. 需要缓存 / 消息时，先在 `CacheKeys` / `MqTopics` / `MqConsumerGroups` 登记，再引用。

## 禁止事项

- 禁止跨库 JOIN、`SELECT *`、循环内查库、拼接 SQL。
- 禁止在 Controller 写业务逻辑；禁止在 Service 里读 `HttpServletRequest`。
- 禁止服务间用共享数据库"绕过"接口；同步查询走 OpenFeign，异步走 `MessagePublisher`。
- 当前 9 个服务都只有启动类，新增代码不要顺手改动其他服务的同名类。
