package com.atifah.auth.entity;

import com.atifah.common.mybatis.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 认证账号实体，对应表 t_auth_account。
 * 手机号按 ADR 0003 明文存储，接口与日志必须脱敏。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
public class AuthAccount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 全局用户 ID（雪花 ID，逻辑外键）。 */
    private Long userId;

    /** 登录手机号，明文存储，禁止打印到日志。 */
    private String phone;

    /** BCrypt 密码哈希，禁止存明文。 */
    private String passwordHash;

    /** 账号状态：0 正常，1 禁用。 */
    private Integer status;

    /** 最后登录成功时间。 */
    private LocalDateTime lastLoginAt;
}
