package com.atifah.common.redis.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Redis 通用操作工具的 TTL 校验、计数脚本与空值兜底测试。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(redisTemplate, stringRedisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
    }

    @Test
    void setWithTtlWritesObjectValue() {
        redisUtil.set("cache:user:1", "value", Duration.ofMinutes(30));

        verify(valueOperations).set("cache:user:1", "value", Duration.ofMinutes(30));
    }

    @Test
    void setRejectsNullValue() {
        assertThrows(IllegalArgumentException.class, () -> redisUtil.set("cache:user:1", null));

        verifyNoInteractions(valueOperations);
    }

    @Test
    void setRejectsNonPositiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> redisUtil.set("cache:user:1", "value", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> redisUtil.set("cache:user:1", "value", Duration.ofSeconds(-1)));

        verifyNoInteractions(valueOperations);
    }

    @Test
    void setIfAbsentReturnsFalseWhenKeyExists() {
        when(valueOperations.setIfAbsent("lock", "owner", Duration.ofSeconds(10))).thenReturn(false);

        assertFalse(redisUtil.setIfAbsent("lock", "owner", Duration.ofSeconds(10)));
    }

    @Test
    void incrementWithTtlExecutesSingleAtomicScript() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(2L);

        long value = redisUtil.increment("counter", 2, Duration.ofMinutes(1));

        assertEquals(2L, value);

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> deltaCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ttlCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of("counter")),
                deltaCaptor.capture(),
                ttlCaptor.capture());

        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("redis.call('INCRBY', KEYS[1], ARGV[1])"), "计数必须走原子 INCRBY");
        assertTrue(script.contains("redis.call('PTTL', KEYS[1]) < 0"), "只允许在 Key 没有 TTL 时设置过期");
        assertTrue(script.contains("redis.call('PEXPIRE', KEYS[1], ARGV[2])"),
                "首次计数必须在同一脚本内设置 TTL");
        assertEquals(Long.class, scriptCaptor.getValue().getResultType());
        assertEquals("2", deltaCaptor.getValue());
        assertEquals("60000", ttlCaptor.getValue());
        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void incrementRejectsNonPositiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> redisUtil.increment("counter", 1, Duration.ZERO));
    }

    @Test
    void decrementDelegatesToRedis() {
        when(stringValueOperations.decrement("counter", 1L)).thenReturn(4L);

        assertEquals(4L, redisUtil.decrement("counter", 1));
    }

    @Test
    void deleteCollectionReturnsZeroWhenRedisReturnsNull() {
        when(redisTemplate.delete(anyCollection())).thenReturn(null);

        assertEquals(0L, redisUtil.delete(List.of("cache:user:1", "cache:user:2")));
    }

    @Test
    void collectionReadsReturnEmptyValuesWhenRedisReturnsNull() {
        when(hashOperations.entries("missing")).thenReturn(null);
        when(setOperations.members("missing")).thenReturn(null);
        when(zSetOperations.rangeWithScores("missing", 0, -1)).thenReturn(null);

        assertTrue(redisUtil.hGetAll("missing").isEmpty());
        assertTrue(redisUtil.sMembers("missing").isEmpty());
        assertTrue(redisUtil.zRangeWithScores("missing", 0, -1).isEmpty());
    }

    @Test
    void hashAndSetOperationsDelegateToRedis() {
        when(hashOperations.increment("post:counter:1", "like", 1L)).thenReturn(3L);
        when(setOperations.add("post:like:users:1", "1001")).thenReturn(1L);

        assertEquals(3L, redisUtil.hIncrement("post:counter:1", "like", 1L));
        assertEquals(1L, redisUtil.sAdd("post:like:users:1", "1001"));
    }
}
