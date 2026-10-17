package com.microhoov.common.mq.constant;

/**
 * RocketMQ Topic 统一规划。
 */
public final class MqTopics {

    public static final String MOOD_RECORDED = "mood-recorded";
    public static final String POST_PUBLISHED = "post-published";
    public static final String CONTENT_AUDIT_RESULT = "content-audit-result";
    public static final String NOTIFICATION = "notification";
    public static final String STATS_EVENT = "stats-event";
    public static final String POINTS_CHANGED = "points-changed";
    public static final String USER_LOGOFF = "user-logoff";

    private MqTopics() {
    }
}
