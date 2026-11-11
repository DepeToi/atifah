package com.atifah.auth.mapper;

import com.atifah.auth.entity.AuthRefreshToken;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 刷新令牌 Mapper。
 * 不继承 BaseMapper，SQL 全部写在 resources/mapper/AuthRefreshTokenMapper.xml。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface AuthRefreshTokenMapper {

    /**
     * 新增刷新令牌会话，id 由调用方生成。
     *
     * @param refreshToken 待插入的刷新令牌会话
     * @return 影响行数
     */
    int insert(AuthRefreshToken refreshToken);

    /**
     * 按令牌哈希查询会话。
     *
     * @param tokenHash 令牌的 SHA-256 哈希
     * @return 会话；不存在时返回 null
     */
    AuthRefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 条件撤销单个会话：仅当尚未撤销时生效，用于并发轮换的抢先判断。
     *
     * @param id 会话主键
     * @param revokedAt 撤销时间
     * @return 影响行数；0 表示已被其他请求撤销
     */
    int revoke(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * 撤销指定用户的全部有效会话，用于检测到令牌重用后的泄露处置。
     *
     * @param userId 全局用户 ID
     * @param revokedAt 撤销时间
     * @return 影响行数
     */
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
