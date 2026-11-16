package com.atifah.common.core.trace;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId 生成与规范化工具：只接受 32 位十六进制，避免超长请求头与日志注入。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public final class TraceIdUtils {

    /** 32 位十六进制 TraceId 格式。 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    private TraceIdUtils() {
    }

    /**
     * 生成新的 TraceId。
     *
     * @return 32 位小写十六进制字符串
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验并规范化 TraceId；缺失或非法时生成新值。
     *
     * @param candidate 候选 TraceId
     * @return 合法的 32 位小写十六进制 TraceId
     */
    public static String normalizeOrGenerate(String candidate) {
        if (candidate != null && TRACE_ID_PATTERN.matcher(candidate).matches()) {
            return candidate.toLowerCase(Locale.ROOT);
        }
        return generate();
    }
}
