package com.atifah.auth.service.impl;

import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.service.AuthIdempotencyGuard;
import com.atifah.auth.service.AuthService;
import com.atifah.auth.vo.RegisterResult;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 认证业务实现。
 * 注册是单条 INSERT，不加事务，避免在事务内做 Redis 远程调用（DB-04）。
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

    private final AuthAccountMapper authAccountMapper;

    private final PasswordEncoder passwordEncoder;

    private final AuthIdempotencyGuard idempotencyGuard;

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
