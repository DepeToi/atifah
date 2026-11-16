package com.atifah.common.core.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TraceId 生成与规范化测试。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class TraceIdUtilsTest {

    @Test
    void generateReturns32LowerHex() {
        String traceId = TraceIdUtils.generate();

        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }

    @Test
    void normalizeKeepsValidAndLowercases() {
        String traceId = TraceIdUtils.normalizeOrGenerate("A".repeat(32));

        assertEquals("a".repeat(32), traceId);
    }

    @Test
    void normalizeGeneratesForInvalidValue() {
        String traceId = TraceIdUtils.normalizeOrGenerate("not-a-trace-id");

        assertTrue(traceId.matches("[0-9a-f]{32}"));
        assertNotEquals("not-a-trace-id", traceId);
    }

    @Test
    void normalizeGeneratesForNull() {
        String traceId = TraceIdUtils.normalizeOrGenerate(null);

        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }
}
