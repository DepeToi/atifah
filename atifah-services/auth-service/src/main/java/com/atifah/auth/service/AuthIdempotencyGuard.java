package com.atifah.auth.service;

import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 注册接口的幂等组件。
 * Redis 只保存 {@code sha256(phone):userId}，用于识别重复请求与幂等键复用。
 * Redis 不可用时直接失败，不降级为“只靠唯一索引”，避免破坏幂等语义。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class AuthIdempotencyGuard {

    /** 规范 UUID（8-4-4-4-12）。 */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** 注册业务标识，用于拼接 CacheKeys.IDEMPOTENT。 */
    private static final String REGISTER_BIZ = "auth:register";

    /** 幂等结果保留 24 小时。 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /** 幂等值中手机号哈希与 userId 的分隔符。 */
    private static final String VALUE_SEPARATOR = ":";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 校验幂等键格式，缺失或非法时返回 40000。
     *
     * @param idempotencyKey 客户端传入的幂等键
     */
    public void validate(String idempotencyKey) {
        if (idempotencyKey == null || !UUID_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "Idempotency-Key 缺失或格式非法");
        }
    }

    /**
     * 构造幂等结果在 Redis 中的 Key。
     *
     * @param idempotencyKey 客户端传入的幂等键
     * @return Redis Key
     */
    public String buildKey(String idempotencyKey) {
        return String.format(CacheKeys.IDEMPOTENT, REGISTER_BIZ, idempotencyKey);
    }

    /**
     * 查询幂等结果。
     * 同一幂等键对应不同手机号时返回 40900，避免幂等键被其他请求复用。
     *
     * @param idempotencyKey 客户端传入的幂等键
     * @param phone 本次请求的手机号
     * @return 首次注册的 userId；没有幂等记录时为空
     */
    public Optional<Long> findUserId(String idempotencyKey, String phone) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(idempotencyKey));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(VALUE_SEPARATOR, 2);
        if (parts.length != 2) {
            throw new IllegalStateException("注册幂等记录格式非法");
        }
        if (!fingerprint(phone).equals(parts[0])) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "Idempotency-Key 已被其他请求使用");
        }
        return Optional.of(Long.parseLong(parts[1]));
    }

    /**
     * 保存幂等结果，TTL 24 小时。
     *
     * @param idempotencyKey 客户端传入的幂等键
     * @param phone 本次注册的手机号
     * @param userId 首次注册生成的全局用户 ID
     */
    public void save(String idempotencyKey, String phone, Long userId) {
        String value = fingerprint(phone) + VALUE_SEPARATOR + userId;
        stringRedisTemplate.opsForValue().set(buildKey(idempotencyKey), value, IDEMPOTENCY_TTL);
    }

    /**
     * 计算手机号的 SHA-256 十六进制摘要，避免幂等值中出现手机号明文。
     *
     * @param phone 手机号
     * @return 小写十六进制摘要
     */
    private String fingerprint(String phone) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(phone.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }
}
