package com.atifah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体。
 * 手机号为个人敏感字段，禁止打印到日志。
 *
 * @param phone 中国大陆手机号
 * @param password 登录密码，8–64 位
 * @author DepeToi
 * @since 1.0.0
 */
public record LoginRequest(

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        String password) {
}
