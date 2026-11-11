# 数据库、缓存与消息规则（DB / MQ）

> 表模型与分片的架构背景见 `docs/architecture.md`；DDL 契约放 `docs/contracts/db/`，事件契约放 `docs/contracts/events/`。
> 本文件是写代码时必须遵守的硬约束，规则编号与 `docs/review/standards.md` 一致。

## DB-01 数据库边界

- 一服务一逻辑库：`atifah_<service>`；**禁止跨库 JOIN**。
- 跨服务同步查询走 OpenFeign，异步解耦走 RocketMQ；不允许共享库绕过接口。
- 管理端 `admin-service` 不直连业务库，只通过 API / MQ 聚合。
- 表名 `t_<业务名>`；字段 snake_case；主键雪花 ID，禁止自增。

## DB-02 SQL 与索引

- 禁止 `SELECT *`（`COUNT(*)` 除外），只查需要的列。
- 禁止循环内查库（N+1）；批量用 `IN` / 批量接口。
- **所有 SQL（SELECT / INSERT / UPDATE / DELETE）必须写在 `resources/mapper/*.xml`**：Mapper 接口只声明方法、不继承 `BaseMapper`，禁止调用 `BaseMapper` / `IService` 的任何 CRUD 方法。
- XML 的 `namespace` 为 Mapper 全限定名，statement id 与接口方法名一致；动态 SQL 用 `<if>` / `<where>` / `<foreach>`；禁止拼接 SQL，参数一律 `#{}` 绑定，动态排序字段用白名单。
- INSERT 的 `id` 由 Service 用 `IdWorker.getId()` 生成并由 XML 显式插入；`created_at` / `updated_at` 由数据库默认值 / `ON UPDATE` 维护，`MetaObjectHandler` 不会作用于 XML。
- 自定义 XML 不受 `@TableLogic` 影响：查询 / 更新 / 删除必须显式带 `deleted = 0`，逻辑删除写 `deleted = 1`。
- 分页查询第一个参数使用 MyBatis-Plus `Page<T>`，由分页插件追加 `LIMIT`，禁止在 XML 手写 `LIMIT`。
- 列表接口必须分页：`page` 从 1 起，`size` 默认 20、上限 100；禁止无界查询。
- 高频查询建组合索引，遵循最左前缀；避免在索引列上做函数或隐式转换；大偏移分页用游标（seek）方式。
- 唯一业务键必须有唯一索引（如 `(user_id, record_date)`、点赞 `(user_id, target_type, target_id)`）。

## DB-03 实体与 DDL

- 实体继承 `BaseEntity`，`created_at` / `updated_at` / `deleted` 全表统一；`created_at` / `updated_at` 由数据库默认值 / `ON UPDATE` 维护。
- `deleted` 逻辑删除（0/1）；SQL 全部走 XML，`@TableLogic` 不生效，查询 / 更新 / 删除必须显式带 `deleted = 0`，逻辑删除写 `deleted = 1`。
- DDL 用 Flyway：`src/main/resources/db/migration/V1__init.sql`；已执行脚本禁止修改，新增变更用新版本号。
- Flyway 脚本必须与 `docs/contracts/db/<service>.sql` 的逻辑结构一致；不一致时以契约为准，先改契约。
- 表、列、索引必须写注释；计数/金额用合适精度，禁止用浮点存钱。
- 需字段级加密的敏感字段（见 SEC-01 数据分级）用 `*_cipher`（如 `content_cipher`）存储密文，禁止明文列；手机号按 ADR 0003 处理。

## DB-04 事务

- `@Transactional` 只加在 Service 公共方法；默认回滚 `RuntimeException`，必要时 `rollbackFor = Exception.class`。
- 事务尽量短：不在事务内做 HTTP/RPC/MQ/文件 IO。
- 跨服务强一致优先异步 + 对账；确需 Seata 时严格控制范围（`growth-service` 默认关闭）。

## DB-05 缓存

- Key 先登记 `CacheKeys`，禁止散落硬编码；模板统一 `业务:对象:%s`。
- `RedisTemplate<String, Object>` 已装配（String key + JSON value），直接注入使用；令牌、计数器等纯字符串/数值场景用 `StringRedisTemplate`，避免 JSON 序列化与 `INCR` 不兼容。
- 分布式锁用 Redisson + `CacheKeys.LOCK`；必须设置过期或 watchdog，Lua 安全释放。
- 缓存一致性用 Cache Aside；热点 key 加随机 TTL、逻辑过期/互斥重建；空值缓存 + 布隆过滤器防穿透。
- 缓存不得作为唯一数据源；关键状态必须可回源数据库。

## MQ-01 Topic、消费组与发送入口

- Topic 登记 `MqTopics`，消费组登记 `MqConsumerGroups`，禁止硬编码字符串。
- 发消息统一用 `MessagePublisher`（`send` / `sendAsync`），禁止裸用 `RocketMQTemplate`。
- 新增 Topic / 消费组必须同步更新 `docs/contracts/events/` 与常量类。

## MQ-02 消费幂等与可靠性

- 消费端必须幂等：`msgId` 去重 + 业务唯一键 + 状态机；重复消费不得产生副作用。
- 失败走重试 + 死信队列 + 告警，禁止吞异常；除同一 key 分区外，不依赖消息到达顺序。
- 事务消息 + 本地消息表（`t_local_message`）+ 定时对账，保证“落库成功 → 事件必达”。

## MQ-03 消息内容与顺序

- key 用业务键（通常 userId），保证同一用户事件顺序。
- 消息体只放必要字段，禁止敏感明文；字段变更必须保持向后兼容或升级事件版本。
- 生产者与消费者必须引用同一份 `docs/contracts/events/<event>.md`。

## DB-06 分库分表

- 逻辑库按服务拆分；`t_mood_record`、`t_mood_diary`、`t_post`、`t_message` 按 `user_id` 哈希分片。
- `t_comment` 按 `post_id` 分片；分片键必须命中查询条件，避免跨分片聚合。
- 跨分片统计走 `stats-service` 聚合表，不在线广播查询。
