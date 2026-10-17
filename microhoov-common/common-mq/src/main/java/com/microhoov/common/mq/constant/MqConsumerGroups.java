package com.microhoov.common.mq.constant;

/**
 * RocketMQ 消费组统一命名，避免各环境冲突。
 */
public final class MqConsumerGroups {

    public static final String STATS_MOOD_CONSUMER = "microhoov-stats-mood-consumer";
    public static final String ANALYSIS_MOOD_CONSUMER = "microhoov-analysis-mood-consumer";
    public static final String GROWTH_MOOD_CONSUMER = "microhoov-growth-mood-consumer";
    public static final String MESSAGE_NOTIFICATION_CONSUMER = "microhoov-message-notification-consumer";
    public static final String COMMUNITY_FANOUT_CONSUMER = "microhoov-community-fanout-consumer";
    public static final String AUDIT_RESULT_CONSUMER = "microhoov-audit-result-consumer";

    private MqConsumerGroups() {
    }
}
