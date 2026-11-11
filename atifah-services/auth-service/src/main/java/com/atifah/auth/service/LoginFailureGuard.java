package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 登录尝试计数组件。
 * 每次登录在校验密码前原子占用一次尝试机会：Lua 脚本内完成 INCR 与首次 PEXPIRE，
 * 既保证并发突发请求不会读到旧计数多试密码，也保证首个计数一定带窗口过期。
 * Redis 只保存 {@code sha256(phone)} 对应的次数，不出现手机号明文；
 * Redis 不可用时直接向上抛出，不降级绕过锁定（ADR 0003）。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class LoginFailureGuard {

    /** 锁定提示与契约 42900 一致。 */
    private static final String TOO_MANY_FAILURES_MESSAGE = "登录失败次数过多，请稍后再试";

    /**
     * 原子占用一次尝试：累加计数，首次写入时设置窗口过期（毫秒），返回窗口内累计次数。
     */
    private static final RedisScript<Long> ACQUIRE_ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return attempts
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final AuthProperties authProperties;

    /**
     * 占用一次登录尝试，超过阈值时直接返回 42900。
     * 在密码校验之前调用，保证同一窗口内并发请求最多透传前 N 次尝试。
     *
     * @param phone 登录手机号
     */
    public void acquireAttempt(String phone) {
        String windowMillis = String.valueOf(authProperties.getLoginFailureWindow().toMillis());
        Long attempts = stringRedisTemplate.execute(ACQUIRE_ATTEMPT_SCRIPT, List.of(buildKey(phone)), windowMillis);
        if (attempts != null && attempts > authProperties.getMaxLoginFailures()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), TOO_MANY_FAILURES_MESSAGE);
        }
    }

    /**
     * 登录成功后清除尝试计数。
     *
     * @param phone 登录手机号
     */
    public void clear(String phone) {
        stringRedisTemplate.delete(buildKey(phone));
    }

    /**
     * 构造计数 Key：手机号先做 SHA-256 摘要。
     *
     * @param phone 登录手机号
     * @return Redis Key
     */
    public String buildKey(String phone) {
        return String.format(CacheKeys.AUTH_LOGIN_FAIL, fingerprint(phone));
    }

    /**
     * 计算手机号的 SHA-256 十六进制摘要，与注册幂等组件的指纹算法保持一致。
     *
     * @param phone 登录手机号
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
