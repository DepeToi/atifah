package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.dto.RefreshTokenRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.entity.AuthRefreshToken;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.mapper.AuthRefreshTokenMapper;
import com.atifah.auth.vo.AuthToken;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 刷新与登出业务实现。
 * 刷新在单事务内完成“条件撤销旧会话 + 插入新会话”，并发刷新同一令牌只有一个请求成功；
 * 已撤销令牌超过宽限期再次使用视为泄露，撤销该用户全部有效令牌后仍返回 40100（ADR 0003）。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** 账号正常状态。 */
    private static final int ACCOUNT_STATUS_NORMAL = 0;

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "刷新令牌无效或已过期";

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    private final AuthAccountMapper authAccountMapper;

    private final TokenFactory tokenFactory;

    private final RefreshTokenRevoker refreshTokenRevoker;

    private final AuthProperties authProperties;

    /**
     * 刷新令牌并轮换：撤销旧会话，签发新的 Access Token 与 Refresh Token。
     *
     * @param request 刷新请求
     * @return 轮换后的令牌
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthToken refresh(RefreshTokenRequest request) {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken session = authRefreshTokenMapper.selectByTokenHash(tokenFactory.hash(request.refreshToken()));
        if (session == null || !session.getExpiresAt().isAfter(now)) {
            log.info("refresh rejected, reason=token invalid or expired");
            throw invalidToken();
        }

        if (session.getRevokedAt() != null) {
            // 宽限期内的重复使用按并发轮换处理；超过宽限期视为令牌泄露。
            if (!session.getRevokedAt().isBefore(now.minus(authProperties.getRefreshReuseGrace()))) {
                log.info("refresh rejected, reason=token revoked within grace, userId={}", session.getUserId());
                throw invalidToken();
            }
            refreshTokenRevoker.revokeAllActive(session.getUserId());
            throw invalidToken();
        }

        AuthAccount account = authAccountMapper.selectByUserId(session.getUserId());
        if (account == null || !Objects.equals(account.getStatus(), ACCOUNT_STATUS_NORMAL)) {
            log.info("refresh rejected, reason=account unavailable, userId={}", session.getUserId());
            throw invalidToken();
        }

        if (authRefreshTokenMapper.revoke(session.getId(), now) == 0) {
            log.info("refresh rejected, reason=token already rotated, userId={}", session.getUserId());
            throw invalidToken();
        }

        String refreshToken = tokenFactory.newRefreshToken();
        AuthRefreshToken rotated = new AuthRefreshToken();
        rotated.setId(IdWorker.getId());
        rotated.setUserId(account.getUserId());
        rotated.setTokenHash(tokenFactory.hash(refreshToken));
        rotated.setExpiresAt(now.plus(authProperties.getRefreshTokenTtl()));
        authRefreshTokenMapper.insert(rotated);

        log.info("refresh succeeded, userId={}", account.getUserId());
        return tokenFactory.buildAuthToken(account, refreshToken);
    }

    /**
     * 撤销请求体中的刷新令牌；令牌不存在、已过期或已撤销时同样返回成功，保证登出幂等。
     *
     * @param request 登出请求
     */
    public void logout(RefreshTokenRequest request) {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken session = authRefreshTokenMapper.selectByTokenHash(tokenFactory.hash(request.refreshToken()));
        if (session == null) {
            log.info("logout succeeded, reason=token not found");
            return;
        }
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            log.info("logout succeeded, reason=token inactive, userId={}", session.getUserId());
            return;
        }

        authRefreshTokenMapper.revoke(session.getId(), now);
        log.info("logout succeeded, userId={}", session.getUserId());
    }

    /**
     * 构造刷新令牌无效的异常，对外不区分具体原因，避免泄露令牌状态。
     *
     * @return 业务异常
     */
    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), INVALID_REFRESH_TOKEN_MESSAGE);
    }
}
