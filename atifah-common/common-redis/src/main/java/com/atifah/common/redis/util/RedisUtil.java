package com.atifah.common.redis.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Redis 通用操作工具。
 * <p>
 * 对象缓存与 Hash / Set / ZSet 使用 {@link RedisTemplate}（String Key + JSON Value）；
 * 字符串值与普通计数器使用 {@link StringRedisTemplate}，避免 JSON 序列化与 {@code INCR} 不兼容。
 * Key 必须先登记 {@code CacheKeys}，且不得包含手机号等敏感明文；
 * 分布式锁仍按 DB-05 直接使用 Redisson。
 * <p>
 * 禁止直接缓存情绪日记正文、量表结果等敏感明文，字段级加密由业务层负责（SEC-01）。
 * Redis 不支持 null 值，缓存空值防穿透时请使用业务约定的哨兵值。
 * 设计取舍见 ADR 0006。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class RedisUtil {

    /**
     * 原子递增；Key 没有过期时间时设置 TTL，已有 TTL 时保持不变。
     */
    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('INCRBY', KEYS[1], ARGV[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return value
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 写入对象缓存，不设置过期时间。
     *
     * @param key   Redis Key，须先登记 CacheKeys
     * @param value 缓存对象，不能为 null
     */
    public void set(String key, Object value) {
        requireValue(value);
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入对象缓存并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 缓存对象，不能为 null
     * @param ttl   过期时间，必须大于 0
     */
    public void set(String key, Object value, Duration ttl) {
        requireValue(value);
        redisTemplate.opsForValue().set(key, value, requirePositiveTtl(ttl));
    }

    /**
     * 读取对象缓存。
     *
     * @param key Redis Key
     * @return 缓存对象；不存在时返回 null
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 仅当 Key 不存在时写入对象缓存，并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 缓存对象，不能为 null
     * @param ttl   过期时间，必须大于 0
     * @return true 表示本次写入成功，false 表示 Key 已存在
     */
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        requireValue(value);
        Boolean written = redisTemplate.opsForValue()
                .setIfAbsent(key, value, requirePositiveTtl(ttl));
        return Boolean.TRUE.equals(written);
    }

    /**
     * 删除单个 Key。
     *
     * @param key Redis Key
     * @return true 表示 Key 存在并被删除
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 批量删除 Key。
     *
     * @param keys Redis Key 集合
     * @return 实际删除的 Key 数量
     */
    public long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Long deleted = redisTemplate.delete(keys);
        return deleted == null ? 0L : deleted;
    }

    /**
     * 判断 Key 是否存在。
     *
     * @param key Redis Key
     * @return true 表示 Key 存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 设置 Key 的过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间，必须大于 0
     * @return true 表示设置成功
     */
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, requirePositiveTtl(ttl)));
    }

    /**
     * 写入字符串值，不设置过期时间。
     * 适用于令牌、验证码、状态标记等纯字符串场景。
     *
     * @param key   Redis Key
     * @param value 字符串值，不能为 null
     */
    public void setString(String key, String value) {
        requireValue(value);
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入字符串值并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 字符串值，不能为 null
     * @param ttl   过期时间，必须大于 0
     */
    public void setString(String key, String value, Duration ttl) {
        requireValue(value);
        stringRedisTemplate.opsForValue().set(key, value, requirePositiveTtl(ttl));
    }

    /**
     * 读取字符串值。
     *
     * @param key Redis Key
     * @return 字符串值；不存在时返回 null
     */
    public String getString(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 仅当 Key 不存在时写入字符串值，并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 字符串值，不能为 null
     * @param ttl   过期时间，必须大于 0
     * @return true 表示本次写入成功，false 表示 Key 已存在
     */
    public boolean setStringIfAbsent(String key, String value, Duration ttl) {
        requireValue(value);
        Boolean written = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, requirePositiveTtl(ttl));
        return Boolean.TRUE.equals(written);
    }

    /**
     * 原子递增计数。
     * 计数 Key 只能通过本方法或 StringRedisTemplate 写入，不能与 JSON 对象缓存混用。
     *
     * @param key   Redis Key
     * @param delta 增量，可为负数
     * @return 递增后的值
     */
    public long increment(String key, long delta) {
        Long value = stringRedisTemplate.opsForValue().increment(key, delta);
        return Objects.requireNonNull(value, "Redis INCR 返回值为空");
    }

    /**
     * 原子递增计数，并在 Key 没有过期时间时设置 TTL。
     * 已有 TTL 保持不变，避免每次递增都把窗口向后延长。
     *
     * @param key   Redis Key
     * @param delta 增量，可为负数
     * @param ttl   过期时间，必须大于 0
     * @return 递增后的值
     */
    public long increment(String key, long delta, Duration ttl) {
        long ttlMillis = requirePositiveTtl(ttl).toMillis();
        Long value = stringRedisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                String.valueOf(delta),
                String.valueOf(ttlMillis));
        return Objects.requireNonNull(value, "Redis INCR 返回值为空");
    }

    /**
     * 原子递减计数。
     *
     * @param key   Redis Key
     * @param delta 递减量
     * @return 递减后的值
     */
    public long decrement(String key, long delta) {
        Long value = stringRedisTemplate.opsForValue().decrement(key, delta);
        return Objects.requireNonNull(value, "Redis DECRBY 返回值为空");
    }

    /**
     * 写入 Hash 字段。
     *
     * @param key   Redis Key
     * @param field 字段名
     * @param value 字段值，不能为 null
     */
    public void hSet(String key, String field, Object value) {
        requireValue(value);
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * 读取 Hash 字段。
     *
     * @param key   Redis Key
     * @param field 字段名
     * @return 字段值；字段不存在时返回 null
     */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 读取 Hash 的全部字段。
     *
     * @param key Redis Key
     * @return 字段 Map；Key 不存在时返回空 Map
     */
    public Map<Object, Object> hGetAll(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries == null ? Map.of() : entries;
    }

    /**
     * 判断 Hash 字段是否存在。
     *
     * @param key   Redis Key
     * @param field 字段名
     * @return true 表示字段存在
     */
    public boolean hHasKey(String key, String field) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, field));
    }

    /**
     * 原子递增 Hash 字段，用于互动计数等场景。
     *
     * @param key   Redis Key
     * @param field 字段名
     * @param delta 增量，可为负数
     * @return 递增后的值
     */
    public long hIncrement(String key, String field, long delta) {
        Long value = redisTemplate.opsForHash().increment(key, field, delta);
        return Objects.requireNonNull(value, "Redis HINCRBY 返回值为空");
    }

    /**
     * 删除 Hash 字段。
     *
     * @param key    Redis Key
     * @param fields 字段名，可传多个
     * @return 实际删除的字段数量
     */
    public long hDelete(String key, Object... fields) {
        Long deleted = redisTemplate.opsForHash().delete(key, fields);
        return deleted == null ? 0L : deleted;
    }

    /**
     * 向 Set 添加成员。
     *
     * @param key    Redis Key
     * @param values 成员，可传多个
     * @return 实际新增的成员数量
     */
    public long sAdd(String key, Object... values) {
        Long added = redisTemplate.opsForSet().add(key, values);
        return added == null ? 0L : added;
    }

    /**
     * 读取 Set 的全部成员。
     *
     * @param key Redis Key
     * @return 成员集合；Key 不存在时返回空集合
     */
    public Set<Object> sMembers(String key) {
        Set<Object> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    /**
     * 判断成员是否在 Set 中。
     *
     * @param key   Redis Key
     * @param value 成员
     * @return true 表示成员存在
     */
    public boolean sIsMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /**
     * 从 Set 移除成员。
     *
     * @param key    Redis Key
     * @param values 成员，可传多个
     * @return 实际移除的成员数量
     */
    public long sRemove(String key, Object... values) {
        Long removed = redisTemplate.opsForSet().remove(key, values);
        return removed == null ? 0L : removed;
    }

    /**
     * 获取 Set 成员数量。
     *
     * @param key Redis Key
     * @return 成员数量
     */
    public long sSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 向 ZSet 添加成员。
     *
     * @param key   Redis Key
     * @param value 成员
     * @param score 分值
     * @return true 表示新增成员，false 表示成员已存在并更新分值
     */
    public boolean zAdd(String key, Object value, double score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    /**
     * 按分值升序读取 ZSet 区间。
     *
     * @param key   Redis Key
     * @param start 起始下标，从 0 开始
     * @param end   结束下标，-1 表示最后一位
     * @return 成员集合；Key 不存在时返回空集合
     */
    public Set<Object> zRange(String key, long start, long end) {
        Set<Object> range = redisTemplate.opsForZSet().range(key, start, end);
        return range == null ? Set.of() : range;
    }

    /**
     * 按分值降序读取 ZSet 区间。
     *
     * @param key   Redis Key
     * @param start 起始下标，从 0 开始
     * @param end   结束下标，-1 表示最后一位
     * @return 成员集合；Key 不存在时返回空集合
     */
    public Set<Object> zReverseRange(String key, long start, long end) {
        Set<Object> range = redisTemplate.opsForZSet().reverseRange(key, start, end);
        return range == null ? Set.of() : range;
    }

    /**
     * 按分值升序读取 ZSet 区间，同时返回分值。
     *
     * @param key   Redis Key
     * @param start 起始下标，从 0 开始
     * @param end   结束下标，-1 表示最后一位
     * @return 成员与分值；Key 不存在时返回空集合
     */
    public Set<TypedTuple<Object>> zRangeWithScores(String key, long start, long end) {
        Set<TypedTuple<Object>> range = redisTemplate.opsForZSet().rangeWithScores(key, start, end);
        return range == null ? Set.of() : range;
    }

    /**
     * 按分值降序读取 ZSet 区间，同时返回分值。
     *
     * @param key   Redis Key
     * @param start 起始下标，从 0 开始
     * @param end   结束下标，-1 表示最后一位
     * @return 成员与分值；Key 不存在时返回空集合
     */
    public Set<TypedTuple<Object>> zReverseRangeWithScores(String key, long start, long end) {
        Set<TypedTuple<Object>> range = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        return range == null ? Set.of() : range;
    }

    /**
     * 原子递增 ZSet 成员分值，用于榜单等场景。
     *
     * @param key   Redis Key
     * @param value 成员
     * @param delta 增量，可为负数
     * @return 递增后的分值
     */
    public double zIncrementScore(String key, Object value, double delta) {
        Double score = redisTemplate.opsForZSet().incrementScore(key, value, delta);
        return Objects.requireNonNull(score, "Redis ZINCRBY 返回值为空");
    }

    /**
     * 从 ZSet 移除成员。
     *
     * @param key    Redis Key
     * @param values 成员，可传多个
     * @return 实际移除的成员数量
     */
    public long zRemove(String key, Object... values) {
        Long removed = redisTemplate.opsForZSet().remove(key, values);
        return removed == null ? 0L : removed;
    }

    /**
     * 获取 ZSet 成员数量。
     *
     * @param key Redis Key
     * @return 成员数量
     */
    public long zSize(String key) {
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size == null ? 0L : size;
    }

    private static void requireValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Redis 值不能为 null；缓存空值请使用业务哨兵值");
        }
    }

    private static Duration requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException("Redis TTL 必须大于 0");
        }
        return ttl;
    }
}
