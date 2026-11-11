package com.atifah.auth.service.impl;

import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RefreshTokenRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.service.AuthIdempotencyGuard;
import com.atifah.auth.service.AuthService;
import com.atifah.auth.service.LoginFailureGuard;
import com.atifah.auth.service.LoginTokenWriter;
import com.atifah.auth.service.RefreshTokenService;
import com.atifah.auth.vo.AuthToken;
import com.atifah.auth.vo.RegisterResult;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 认证业务实现。
 * 注册是单条 INSERT，不加事务，避免在事务内做 Redis 远程调用（DB-04）；
 * 登录的写库动作集中在 LoginTokenWriter 的短事务内。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 唯一键冲突后最多重读幂等结果的次数。 */
    private static final int IDEMPOTENCY_RETRY_TIMES = 3;

    /** 幂等结果重读间隔，3 次重读共等待 100ms。 */
    private static final long IDEMPOTENCY_RETRY_INTERVAL_MILLIS = 50L;

    /** 账号正常状态。 */
    private static final int ACCOUNT_STATUS_NORMAL = 0;

    private static final String DUPLICATE_PHONE_MESSAGE = "手机号已注册";

    /** 凭据错误统一提示，不区分账号是否存在。 */
    private static final String INVALID_CREDENTIALS_MESSAGE = "手机号或密码错误";

    private static final String ACCOUNT_DISABLED_MESSAGE = "账号已禁用";

    /**
     * 账号不存在时用于等时比对的 BCrypt 哈希。
     * 对固定口令编码一次，保证与真实密码校验耗时相近，降低通过响应时间枚举账号的风险。
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$oRDE4Lm0UV6.TU7FFI1biu.lBHpCixbTfU38ZqB8A9lgIb8VSESOu";

    private final AuthAccountMapper authAccountMapper;

    private final PasswordEncoder passwordEncoder;

    private final AuthIdempotencyGuard idempotencyGuard;

    private final LoginFailureGuard loginFailureGuard;

    private final LoginTokenWriter loginTokenWriter;

    private final RefreshTokenService refreshTokenService;

    /**
     * 手机号注册。
     *
     * @param request 注册请求
     * @param idempotencyKey 幂等键，规范 UUID
     * @return 注册结果
     */
    @Override
    public RegisterResult register(RegisterRequest request, String idempotencyKey) {
        idempotencyGuard.validate(idempotencyKey);

        Optional<Long> replayUserId = idempotencyGuard.findUserId(idempotencyKey, request.phone());
        if (replayUserId.isPresent()) {
            log.info("register idempotent replay, userId={}", replayUserId.get());
            return new RegisterResult(String.valueOf(replayUserId.get()));
        }

        if (authAccountMapper.selectByPhone(request.phone()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), DUPLICATE_PHONE_MESSAGE);
        }

        AuthAccount account = buildAccount(request);
        try {
            authAccountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            // 并发注册由唯一索引兜底，冲突方在限定次数内重读幂等结果。
            log.warn("register unique key conflict, retry idempotent read");
            return recoverFromConcurrentRegister(idempotencyKey, request.phone());
        }

        idempotencyGuard.save(idempotencyKey, request.phone(), account.getUserId());
        log.info("register succeeded, userId={}", account.getUserId());
        return new RegisterResult(String.valueOf(account.getUserId()));
    }

    /**
     * 手机号密码登录。
     * 校验密码前先原子占用一次尝试，避免并发突发请求绕过锁定；
     * 账号不存在与密码错误返回同一错误码，并在账号不存在时执行等时比对；
     * 密码校验通过后才清除计数并判断账号状态，避免暴露账号是否存在。
     *
     * @param request 登录请求
     * @return 访问令牌与刷新令牌
     */
    @Override
    public AuthToken login(LoginRequest request) {
        loginFailureGuard.acquireAttempt(request.phone());

        AuthAccount account = authAccountMapper.selectByPhone(request.phone());
        if (account == null) {
            // 等时比对：账号不存在时也执行一次 BCrypt，避免响应时间泄露账号是否存在。
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            log.info("login failed, reason=account not found");
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), INVALID_CREDENTIALS_MESSAGE);
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            log.info("login failed, reason=password mismatch, userId={}", account.getUserId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), INVALID_CREDENTIALS_MESSAGE);
        }

        loginFailureGuard.clear(request.phone());
        if (!Objects.equals(account.getStatus(), ACCOUNT_STATUS_NORMAL)) {
            log.info("login rejected, reason=account disabled, userId={}", account.getUserId());
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), ACCOUNT_DISABLED_MESSAGE);
        }

        AuthToken token = loginTokenWriter.issue(account);
        log.info("login succeeded, userId={}", account.getUserId());
        return token;
    }

    /**
     * 刷新令牌并轮换，委托 {@link RefreshTokenService} 处理。
     *
     * @param request 刷新请求
     * @return 轮换后的令牌
     */
    @Override
    public AuthToken refresh(RefreshTokenRequest request) {
        return refreshTokenService.refresh(request);
    }

    /**
     * 登出并撤销刷新令牌，委托 {@link RefreshTokenService} 处理。
     *
     * @param request 登出请求
     */
    @Override
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.logout(request);
    }

    /**
     * 组装待插入账号，密码只保存 BCrypt 哈希。
     *
     * @param request 注册请求
     * @return 待插入账号
     */
    private AuthAccount buildAccount(RegisterRequest request) {
        AuthAccount account = new AuthAccount();
        account.setId(IdWorker.getId());
        account.setUserId(IdWorker.getId());
        account.setPhone(request.phone());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setStatus(ACCOUNT_STATUS_NORMAL);
        return account;
    }

    /**
     * 唯一键冲突后重读幂等结果：最多 3 次、间隔 50ms、总等待不超过 100ms。
     *
     * @param idempotencyKey 幂等键
     * @param phone 手机号
     * @return 冲突方与首次请求相同手机号时的注册结果
     */
    private RegisterResult recoverFromConcurrentRegister(String idempotencyKey, String phone) {
        for (int attempt = 1; attempt <= IDEMPOTENCY_RETRY_TIMES; attempt++) {
            Optional<Long> userId = idempotencyGuard.findUserId(idempotencyKey, phone);
            if (userId.isPresent()) {
                log.info("register concurrent conflict resolved, userId={}", userId.get());
                return new RegisterResult(String.valueOf(userId.get()));
            }
            if (attempt < IDEMPOTENCY_RETRY_TIMES) {
                sleepBeforeRetry();
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT.getCode(), DUPLICATE_PHONE_MESSAGE);
    }

    /**
     * 重读间隔等待；中断时恢复中断标记并向上抛出。
     */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(IDEMPOTENCY_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("注册幂等重读被中断", ex);
        }
    }
}
