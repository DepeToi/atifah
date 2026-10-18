# 数据模型与存储设计

> 读这份文档的时机：设计表、索引、分片、加密字段，或排查数据一致性问题。
> 写代码时的硬约束见 [`.claude/rules/database.md`](../.claude/rules/database.md)；业务边界见 [`domain.md`](domain.md)。

## 1. 数据库边界

- 一服务一逻辑库：`microhoov_<service>`；初期可共用一个 MySQL 实例，但 Schema 独立。
- **禁止跨库 JOIN**；跨服务数据通过 OpenFeign 或 RocketMQ 获取。
- 管理端 `admin-service` 不直连业务库，只通过 API / MQ 聚合。
- 表名 `t_<业务名>`，字段 snake_case，主键雪花 ID，禁止自增。

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

## 3. 分库分表

- 逻辑库按服务拆分；`t_mood_record`、`t_mood_diary`、`t_post`、`t_comment`、`t_message` 按 `user_id` 哈希分片（示例：4 库 × 8 表）。
- 分片键必须命中查询条件；评论按 `post_id` 分片，避免跨分片聚合。
- 使用雪花 ID，禁止分片表自增主键。
- 跨分片统计一律走 `stats-service` 聚合表，不在线做广播查询。

## 4. 敏感字段与加密

- 日记正文、量表结果使用 AES-256-GCM 字段级加密，列名以 `_cipher` 结尾；密钥由 KMS/Vault 管理，禁止写在配置或代码里。
- 手机号、密码、设备标识按最小化原则存储；密码只存 BCrypt/Argon2id 哈希。
- 日志、备份、MQ 消息中不得出现敏感明文；备份必须加密。
- 管理端查看明文需专项授权并记录审计；匿名帖反查同样留痕。
- 用户注销走 Saga 清理各服务数据，不得只删主表。

## 5. DDL 与索引规范

- DDL 用 Flyway 管理：`src/main/resources/db/migration/V1__init.sql`；已执行脚本禁止修改，新增变更用新版本号。
- 表、列、索引必须写注释；字符集统一 `utf8mb4`，时间字段统一 `datetime`。
- 唯一业务键必须有唯一索引：`(user_id, record_date)`、`(user_id, target_type, target_id)` 等。
- 高频查询建组合索引（最左前缀）；避免在索引列上做函数/隐式转换；大偏移分页用游标（seek）。
- 禁止 `SELECT *`、循环内查库、拼接 SQL；列表接口必须分页。

## 6. 一致性设计

- 主链路只做核心落库；统计、积分、通知、审核、报告生成异步化。
- 「落库成功 → 事件必达」用事务消息 + 本地消息表（`t_local_message`）+ 定时对账。
- 消费端幂等：`msgId` 去重 + 业务唯一键 + 状态机；失败重试 + 死信 + 告警。
- 跨服务强一致优先最终一致 + 对账；确需 Seata 时严格控制范围（`growth-service` 默认关闭）。

## 7. 缓存与热点数据

| 场景 | 方案 |
| --- | --- |
| 用户/内容详情 | Caffeine(3–10s) + Redis(30–60min 随机 TTL) + MySQL |
| 今日打卡状态 | `CacheKeys.MOOD_TODAY`，过期到次日凌晨 |
| 连续打卡 | Redis Bitmap/Set，异步落库 |
| 点赞去重 | Redis Set + Lua 原子操作 |
| Feed 收件箱 | Redis ZSET，score=时间戳 |
| 热门/榜单 | Redis ZSET |
| 分布式锁 | Redisson（watchdog 续期，Lua 安全释放） |
| 接口幂等 | `CacheKeys.IDEMPOTENT`，24h |

**三大缓存问题**：穿透 → 空值缓存 + 布隆过滤器；击穿 → 逻辑过期/互斥重建；雪崩 → 随机 TTL + 多级缓存 + Sentinel 兜底。缓存一致性采用 Cache Aside，关键场景可加延迟双删或订阅 Binlog（Canal）刷新。
