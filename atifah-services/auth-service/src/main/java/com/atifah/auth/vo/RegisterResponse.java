package com.atifah.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 注册响应。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public class RegisterResponse {

    /** 全局用户 ID（雪花 ID，字符串返回）。 */
    private final String userId;
}
