package com.atifah.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 认证账号状态。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum AuthAccountStatus {

    /** 正常。 */
    NORMAL(0, "正常"),

    /** 禁用。 */
    DISABLED(1, "禁用");

    /** 状态码。 */
    private final int code;

    /** 描述。 */
    private final String description;
}
