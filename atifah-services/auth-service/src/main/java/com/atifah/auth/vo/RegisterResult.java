package com.atifah.auth.vo;

/**
 * 注册结果。
 * userId 以字符串返回，避免雪花 ID 在 JavaScript 中丢失精度。
 *
 * @param userId 全局用户 ID
 * @author DepeToi
 * @since 1.0.0
 */
public record RegisterResult(String userId) {
}
