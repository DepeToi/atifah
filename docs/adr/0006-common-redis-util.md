# 0006. 通用 Redis 工具类 RedisUtil

- 状态：已采纳
- 日期：2026-11-16
- 决策人：项目维护者

## 背景

`common-redis` 目前只提供 RedisTemplate 序列化配置与 `CacheKeys`，业务服务需要自行注入 `RedisTemplate` / `StringRedisTemplate`，并重复处理以下问题：

- 对象缓存与字符串 / 计数器使用不同 Template，容易出现 JSON 序列化与 `INCR` 混用；
- 计数的首次过期需要 `INCR` 与 `EXPIRE` 原子执行，各服务重复编写 Lua；
- 空值、TTL、集合空结果等边界处理不一致。

auth-service 的幂等与登录失败计数已经出现两处独立的 Redis 操作实现，后续业务服务还会继续增加缓存、计数与 Hash / Set / ZSet 操作。若不收敛，DB-05 只能靠评审口头约束。

## 决策

- 在 `common-redis` 增加 `com.atifah.common.redis.util.RedisUtil`，由 `RedisAutoConfiguration` 以 `@ConditionalOnMissingBean` 自动装配，业务服务构造注入使用。
- 职责边界：
  - 对象缓存与 Hash / Set / ZSet 使用 `RedisTemplate<String, Object>`（String Key + JSON Value）；
  - 字符串值与普通计数器使用 `StringRedisTemplate`；
  - `increment(key, delta, ttl)` 使用 Lua 原子完成 `INCRBY` 与首次 `PEXPIRE`，已有 TTL 不续期；
  - 统一校验 null 值与正 TTL，集合读取返回空集合而非 null；
  - 不封装分布式锁，继续按 DB-05 直接使用 Redisson + `CacheKeys.LOCK`。
- Key 仍必须先登记 `CacheKeys`；`RedisUtil` 不拼接业务 Key，也不自动加前缀。
- `RedisUtil` 不做敏感数据处理：禁止直接缓存日记正文、量表结果、手机号等明文；字段级加密与脱敏仍由业务层按 SEC-01 执行。
- 本次为兼容新增，不修改接口 / DDL / 事件契约，不改变现有服务行为；auth-service 现有 Redis 实现暂不强制迁移，后续 Feature 任务再按需收敛。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 保持现状，各服务直接注入 Template | 无新增抽象 | 重复序列化 / INCR / TTL 逻辑，边界处理不一致 | 否决 |
| 只封装 `RedisTemplate<String, Object>` | API 简单 | 无法覆盖字符串令牌与 `INCR`，计数仍要绕过工具类 | 否决 |
| 同时封装两个 Template + 原子计数 Lua | 覆盖 DB-05 主要场景，减少重复实现 | API 面变大，需要长期维护与测试 | 采纳 |
| 在工具类中再封装 Redisson 锁 | 业务代码更短 | 锁所有权 / 重入 / 看门狗语义易被包装掩盖，且 DB-05 已明确直用 Redisson | 否决 |

## 影响

- 正面：统一常用 Redis 操作的序列化、TTL、计数原子性与空值行为；DB-05 从口头规则落到可复用组件；新增测试覆盖 TTL 与计数脚本。
- 负面 / 代价：`common-redis` 新增公共 API 与维护成本；业务需理解两个 Template 的边界；`RedisUtil` 不覆盖所有 Redis 命令，复杂命令仍需直接注入 Template。
- 需要同步修改的文档与代码：`docs/rules/database.md` DB-05、`atifah-common/AGENTS.md`、`atifah-common/common-redis/pom.xml`；新增 `RedisUtil` 与单元测试。
- 回滚方式：删除 `RedisUtil`、`RedisAutoConfiguration` 中的装配 Bean 与测试，恢复 DB-05 与模块说明；业务服务未强制迁移，回滚不影响现有调用。

## 关联

- 相关契约：无（不涉及接口 / DDL / 事件）
- 相关任务包：无（Arch 变更，不占 `T-xxx` 编号）
- 替代 / 被替代的 ADR：无；参考 ADR 0005（兼容新增的契约 ADR 分级）
