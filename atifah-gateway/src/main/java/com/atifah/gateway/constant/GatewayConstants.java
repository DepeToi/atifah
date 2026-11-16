package com.atifah.gateway.constant;

/**
 * 网关内部 exchange 属性键。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public final class GatewayConstants {

    /** exchange 属性：TraceId。 */
    public static final String ATTR_TRACE_ID = "atifah.traceId";

    /** exchange 属性：鉴权通过后的用户 ID。 */
    public static final String ATTR_USER_ID = "atifah.userId";

    private GatewayConstants() {
    }
}
