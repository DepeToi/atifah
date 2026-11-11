package com.atifah.auth.mapper;

import com.atifah.auth.entity.AuthRefreshToken;

/**
 * 刷新令牌 Mapper。
 * 不继承 BaseMapper，SQL 全部写在 resources/mapper/AuthRefreshTokenMapper.xml。
 * 本任务只声明签发所需的插入，撤销与轮换留给后续任务。
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
}
