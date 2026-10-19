package com.atifah.auth.service;

/**
 * 登录失败计数服务。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface LoginAttemptService {

    /**
     * 校验是否允许继续登录，超过阈值抛出 42900。
     *
     * @param phone 登录手机号
     */
    void assertAllowed(String phone);

    /**
     * 记录一次登录失败。
     *
     * @param phone 登录手机号
     */
    void recordFailure(String phone);

    /**
     * 登录成功后清除失败计数。
     *
     * @param phone 登录手机号
     */
    void clear(String phone);
}
