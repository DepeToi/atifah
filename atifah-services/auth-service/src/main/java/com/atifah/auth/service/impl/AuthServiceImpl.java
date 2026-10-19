package com.atifah.auth.service.impl;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.enums.AuthAccountStatus;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.service.AuthService;
import com.atifah.auth.service.LoginAttemptService;
import com.atifah.auth.service.RefreshTokenService;
import com.atifah.auth.vo.AuthTokenVO;
import com.atifah.auth.vo.RegisterResponse;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import com.atifah.common.security.config.JwtProperties;
import com.atifah.common.security.jwt.JwtTokenProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 认证服务实现：注册与登录。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 注册幂等键作用域。 */
    private static final String REGISTER_IDEMPOTENT_SCOPE = "auth:register";

    /** Idempotency-Key 规范 UUID（8-4-4-4-12，十六进制大小写均可）。 */
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** 唯一键冲突后重读幂等结果的最大次数（含首次立即读取）。 */
    private static final int IDEMPOTENT_REREAD_ATTEMPTS = 3;

    /** 相邻重读间隔（毫秒）。 */
    private static final long IDEMPOTENT_REREAD_INTERVAL_MILLIS = 50L;

    private final AuthAccountMapper authAccountMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuthProperties authProperties;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "缺少 Idempotency-Key");
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "Idempotency-Key 必须为 UUID");
        }
        String idemKey = CacheKeys.IDEMPOTENT.formatted(REGISTER_IDEMPOTENT_SCOPE, idempotencyKey);
        String cached = stringRedisTemplate.opsForValue().get(idemKey);
        if (cached != null) {
            return new RegisterResponse(cached);
        }
        Long count = authAccountMapper.selectCount(
                new LambdaQueryWrapper<AuthAccount>().eq(AuthAccount::getPhone, request.getPhone()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "手机号已注册");
        }
        Long userId = IdWorker.getId();
        AuthAccount account = new AuthAccount();
        account.setUserId(userId);
        account.setPhone(request.getPhone());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setStatus(AuthAccountStatus.NORMAL.getCode());
        try {
            authAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            String first = readIdempotentResultWithRetry(idemKey);
            if (first != null) {
                return new RegisterResponse(first);
            }
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "手机号已注册");
        }
        String userIdStr = String.valueOf(userId);
        stringRedisTemplate.opsForValue().set(idemKey, userIdStr, authProperties.getIdempotentTtl());
        log.info("auth register succeeded userId={}", userIdStr);
        return new RegisterResponse(userIdStr);
    }

    @Override
    public AuthTokenVO login(LoginRequest request) {
        loginAttemptService.assertAllowed(request.getPhone());
        AuthAccount account = authAccountMapper.selectOne(
                new LambdaQueryWrapper<AuthAccount>().eq(AuthAccount::getPhone, request.getPhone()));
        if (account == null) {
            loginAttemptService.recordFailure(request.getPhone());
            log.warn("auth login failed reason=bad-credentials");
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "手机号或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            loginAttemptService.recordFailure(request.getPhone());
            log.warn("auth login failed reason=bad-credentials");
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "手机号或密码错误");
        }
        if (account.getStatus() != null && account.getStatus() == AuthAccountStatus.DISABLED.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "账号已禁用");
        }
        loginAttemptService.clear(request.getPhone());
        updateLastLoginAt(account);
        String accessToken = jwtTokenProvider.generateToken(account.getUserId(), String.valueOf(account.getUserId()));
        String refreshToken = refreshTokenService.issue(account.getUserId());
        long expiresIn = jwtProperties.getAccessTokenTtl().toSeconds();
        log.info("auth login succeeded userId={}", account.getUserId());
        return new AuthTokenVO(accessToken, refreshToken, "Bearer", expiresIn, String.valueOf(account.getUserId()));
    }

    /**
     * 唯一键冲突后有界短暂重读幂等结果，取到首次 userId 即返回；重读耗尽返回 null。
     * 最多 3 次读取（含首次立即读取），相邻间隔 50ms，总等待 ≤100ms。
     */
    private String readIdempotentResultWithRetry(String idemKey) {
        for (int attempt = 1; attempt <= IDEMPOTENT_REREAD_ATTEMPTS; attempt++) {
            String value = stringRedisTemplate.opsForValue().get(idemKey);
            if (value != null) {
                return value;
            }
            if (attempt < IDEMPOTENT_REREAD_ATTEMPTS) {
                try {
                    Thread.sleep(IDEMPOTENT_REREAD_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("auth register idempotent reread interrupted");
                    return null;
                }
            }
        }
        return null;
    }

    private void updateLastLoginAt(AuthAccount account) {
        try {
            AuthAccount update = new AuthAccount();
            update.setId(account.getId());
            update.setLastLoginAt(LocalDateTime.now());
            authAccountMapper.updateById(update);
        } catch (Exception e) {
            log.warn("auth login update last_login_at failed userId={}", account.getUserId(), e);
        }
    }
}
