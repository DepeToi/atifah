package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 登录失败计数组件。
 * Redis 只保存 {@code sha256(phone)} 对应的失败次数，不出现手机号明文；
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

    private final StringRedisTemplate stringRedisTemplate;

    private final AuthProperties authProperties;

    /**
     * 校验登录标识是否已在窗口内被锁定。
     *
     * @param phone 登录手机号
     */
    public void assertNotLocked(String phone) {
        String failures = stringRedisTemplate.opsForValue().get(buildKey(phone));
        if (failures != null && Long.parseLong(failures) >= authProperties.getMaxLoginFailures()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), TOO_MANY_FAILURES_MESSAGE);
        }
    }

    /**
     * 记录一次登录失败；首次失败时开启计数窗口。
     *
     * @param phone 登录手机号
     */
    public void recordFailure(String phone) {
        String key = buildKey(phone);
        Long failures = stringRedisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            stringRedisTemplate.expire(key, authProperties.getLoginFailureWindow());
        }
    }

    /**
     * 登录成功后清除失败计数。
     *
     * @param phone 登录手机号
     */
    public void clear(String phone) {
        stringRedisTemplate.delete(buildKey(phone));
    }

    /**
     * 构造失败计数 Key：手机号先做 SHA-256 摘要。
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
