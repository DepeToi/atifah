package com.atifah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求体。
 * 手机号为个人敏感字段，禁止打印到日志。
 *
 * @param phone 中国大陆手机号
 * @param password 密码：8–64 位可打印 ASCII 字符，至少包含字母与数字
 * @author DepeToi
 * @since 1.0.0
 */
public record RegisterRequest(

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$",
                message = "密码需为 8-64 位可打印 ASCII 字符，且至少包含字母与数字")
        String password) {
}
