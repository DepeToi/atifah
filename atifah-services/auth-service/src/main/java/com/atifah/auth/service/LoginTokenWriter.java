package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.entity.AuthRefreshToken;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.mapper.AuthRefreshTokenMapper;
import com.atifah.auth.vo.AuthToken;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录令牌签发。
 * 会话记录与最后登录时间更新在同一事务内，Redis 操作留在事务之外（DB-04）；
 * 随机令牌、哈希与 Access Token 组装统一由 {@link TokenFactory} 完成。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class LoginTokenWriter {

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    private final AuthAccountMapper authAccountMapper;

    private final TokenFactory tokenFactory;

    private final AuthProperties authProperties;

    /**
     * 签发 Access Token 与 Refresh Token，并记录本次会话。
     *
     * @param account 已通过密码校验的账号
     * @return 返回给客户端的令牌
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthToken issue(AuthAccount account) {
        String refreshToken = tokenFactory.newRefreshToken();
        LocalDateTime issuedAt = LocalDateTime.now();

        AuthRefreshToken session = new AuthRefreshToken();
        session.setId(IdWorker.getId());
        session.setUserId(account.getUserId());
        session.setTokenHash(tokenFactory.hash(refreshToken));
        session.setExpiresAt(issuedAt.plus(authProperties.getRefreshTokenTtl()));
        authRefreshTokenMapper.insert(session);

        authAccountMapper.updateLastLoginAt(account.getId(), issuedAt);

        return tokenFactory.buildAuthToken(account, refreshToken);
    }
}
