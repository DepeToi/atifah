# 数据模型与存储设计

> 读这份文档的时机：设计表、索引、分片、加密字段，或排查数据一致性问题。
> 写代码时的硬约束见 [`rules/database.md`](rules/database.md)；业务边界见 [`domain.md`](domain.md)；
> 逻辑 DDL 契约见 [`contracts/db/`](contracts/db/)。

## 1. 数据库边界

一服务一逻辑库 `atifah_<service>`，初期可共用一个 MySQL 实例但 Schema 独立；禁止跨库 JOIN，管理端不直连业务库。编码级硬约束（表名、主键、索引、分页）见 [`rules/database.md`](rules/database.md) DB-01/DB-02；本文件只保留表清单、分片与加密落点等数据设计。

## 2. 核心表

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `t_user` | id, phone, password_hash, nickname, avatar, status | 手机号脱敏展示 |
| `t_user_profile` | user_id, gender, birthday, privacy_level, emotion_goal | 隐私设置 |
| `t_mood_record` | id, user_id, record_date, mood_type, mood_score, tags, content_cipher, visibility, voice_url | 唯一索引 `(user_id, record_date)` |
| `t_mood_diary` | id, user_id, title, content_cipher, mood_score, created_at | 一天可多条 |
| `t_post` | id, author_id, is_anonymous, content, audit_status, like_count, comment_count | 匿名帖后台脱敏 |
| `t_comment` | id, post_id, author_id, content, audit_status | 按 `post_id` 分片 |
| `t_like` | user_id, target_type, target_id | 唯一索引防重复 |
| `t_message` | id, user_id, type, title, content, read_status | 按 `user_id` 分片 |
| `t_content` | id, type, title, media_url, duration, status | 冥想/文章/课程 |
| `t_mood_daily_stat` | user_id, stat_date, avg_score, record_count, emotion_dist | 预聚合 |
| `t_admin_user` / `t_admin_role` / `t_admin_oper_log` | — | RBAC 与审计 |
| `t_local_message` | biz_id, topic, payload, status, retry_count | 本地消息表补偿 |

> 上述是架构级表清单；具体字段、索引与约束以 `docs/contracts/db/<service>.sql` 为准，Flyway 脚本必须与之保持一致。

## 3. 分库分表

- 逻辑库按服务拆分；`t_mood_record`、`t_mood_diary`、`t_post`、`t_message` 按 `user_id` 哈希分片（示例：4 库 × 8 表）。
- `t_comment` 按 `post_id` 分片；分片键必须命中查询条件，避免跨分片聚合。
- 使用雪花 ID，禁止分片表自增主键。
- 跨分片统计一律走 `stats-service` 聚合表，不在线做广播查询。

## 4. 敏感字段与加密

数据分级、加密算法、密钥管理与脱敏规则见 [`rules/security.md`](rules/security.md) SEC-01/02；本文件只约定表级落点：

- 日记正文、量表结果落 `*_cipher` 列（如 `t_mood_record.content_cipher`、`t_mood_diary.content_cipher`），禁止明文列。
- 密码只存 BCrypt/Argon2id 哈希；手机号、设备标识按最小化原则存储，并在接口与日志中脱敏。
- 日志、MQ 与备份中不得出现敏感明文，备份必须加密。
- 用户注销需跨服务清理，不得只删主表（Saga 与事件见 `architecture.md` §5.3/§5.4）。

## 5. DDL 与索引规范

Flyway、雪花 ID、审计字段、`SELECT *`、N+1、唯一索引等硬约束见 [`rules/database.md`](rules/database.md) DB-02/DB-03；本文件只补充表级索引基线：

- 唯一业务键：`(user_id, record_date)`、`(user_id, target_type, target_id)` 等必须有唯一索引。
- 高频查询按最左前缀建组合索引；大偏移分页用游标（seek）。
- 字符集统一 `utf8mb4`，时间字段统一 `datetime`。
- DDL 必须与 `docs/contracts/db/<service>.sql` 一致，Flyway 变更使用新版本号。

## 6. 一致性设计

异步化、事务消息、本地消息表、消费幂等、重试/死信与对账的强制规则见 [`rules/database.md`](rules/database.md) MQ-02 与 [`architecture.md`](architecture.md) §5.3；本文件只保留数据侧约束：`t_local_message` 保存业务键、Topic、payload 状态与重试次数，关键状态必须可回源数据库。

## 7. 缓存与热点数据

缓存 Key、TTL、Cache Aside、穿透/击穿/雪崩与一致性方案见 [`architecture.md`](architecture.md) §5.2 与 [`rules/database.md`](rules/database.md) DB-05；本文件只强调：所有 Key 先登记 `CacheKeys`，缓存不得作为唯一数据源，关键状态必须可回源数据库。
