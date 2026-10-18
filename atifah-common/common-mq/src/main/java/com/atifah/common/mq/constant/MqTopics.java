package com.atifah.common.mq.constant;

/**
 * RocketMQ Topic 统一规划。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public final class MqTopics {

    /** 情绪打卡记录事件。 */
    public static final String MOOD_RECORDED = "mood-recorded";

    /** 帖子发布事件。 */
    public static final String POST_PUBLISHED = "post-published";

    /** 内容审核结果事件。 */
    public static final String CONTENT_AUDIT_RESULT = "content-audit-result";

    /** 通知推送事件。 */
    public static final String NOTIFICATION = "notification";

    /** 统计事件。 */
    public static final String STATS_EVENT = "stats-event";

    /** 积分变更事件。 */
    public static final String POINTS_CHANGED = "points-changed";

    /** 用户注销事件。 */
    public static final String USER_LOGOFF = "user-logoff";

    private MqTopics() {
    }
}
