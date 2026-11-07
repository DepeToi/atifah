package com.atifah.common.core.constant;

/**
 * 跨服务通用常量。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public final class CommonConstants {

    /** 链路追踪 ID 请求头。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 下游用户 ID 请求头，由网关写入。 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 下游用户名请求头，由网关写入。 */
    public static final String HEADER_USERNAME = "X-Username";

    /** 幂等键请求头，写接口必填。 */
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private CommonConstants() {
    }
}
