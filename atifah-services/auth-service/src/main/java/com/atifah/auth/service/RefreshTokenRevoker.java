package com.atifah.auth.service;

import com.atifah.auth.mapper.AuthRefreshTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 刷新令牌泄露处置。
 * 全量撤销使用独立事务（REQUIRES_NEW），确保撤销结果在调用方抛出 40100 并回滚外层事务前已提交。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    /**
     * 撤销指定用户的全部有效刷新令牌。
     *
     * @param userId 全局用户 ID
     * @return 被撤销的会话数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int revokeAllActive(Long userId) {
        int revokedCount = authRefreshTokenMapper.revokeAllActiveByUserId(userId, LocalDateTime.now());
        log.warn("refresh token reuse detected, all active tokens revoked, userId={}, count={}", userId, revokedCount);
        return revokedCount;
    }
}
