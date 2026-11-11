package com.atifah.auth.mapper;

import com.atifah.auth.entity.AuthAccount;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 认证账号 Mapper。
 * 不继承 BaseMapper，SQL 全部写在 resources/mapper/AuthAccountMapper.xml。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface AuthAccountMapper {

    /**
     * 按手机号查询未删除的账号。
     *
     * @param phone 手机号
     * @return 账号；不存在时返回 null
     */
    AuthAccount selectByPhone(@Param("phone") String phone);

    /**
     * 按全局用户 ID 查询未删除的账号，刷新令牌时使用。
     *
     * @param userId 全局用户 ID
     * @return 账号；不存在时返回 null
     */
    AuthAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 新增认证账号，id 由调用方生成。
     *
     * @param account 待插入账号
     * @return 影响行数
     */
    int insert(AuthAccount account);

    /**
     * 更新最后登录成功时间。
     *
     * @param id 认证账号主键
     * @param lastLoginAt 最后登录时间
     * @return 影响行数
     */
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);
}
