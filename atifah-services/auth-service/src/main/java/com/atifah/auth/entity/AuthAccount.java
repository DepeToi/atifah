package com.atifah.auth.entity;

import com.atifah.common.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 认证账号：登录标识、密码哈希与账号状态。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@TableName("t_auth_account")
public class AuthAccount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 全局用户 ID（雪花 ID，逻辑外键）。 */
    private Long userId;

    /** 登录手机号；展示脱敏，日志禁止打印。 */
    private String phone;

    /** BCrypt 密码哈希（禁止存明文/可逆加密）。 */
    private String passwordHash;

    /** 账号状态：0 正常，1 禁用。 */
    private Integer status;

    /** 最后登录时间（登录成功后更新）。 */
    private LocalDateTime lastLoginAt;
}
