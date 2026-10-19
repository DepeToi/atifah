package com.atifah.common.redis.constant;

/**
 * Redis Key 统一规划，避免各服务 Key 冲突。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public final class CacheKeys {

    /** 登录态 / Token 黑名单。 */
    public static final String AUTH_TOKEN = "auth:token:%s";

    /** 当日打卡状态。 */
    public static final String MOOD_TODAY = "mood:today:%s:%s";

    /** 连续打卡天数。 */
    public static final String MOOD_STREAK = "mood:streak:%s";

    /** 社区收件箱（ZSET）。 */
    public static final String FEED_INBOX = "feed:inbox:%s";

    /** 点赞用户集合（SET）。 */
    public static final String POST_LIKE_USERS = "post:like:users:%s";

    /** 互动计数（HASH）。 */
    public static final String POST_COUNTER = "post:counter:%s";

    /** 通用详情缓存。 */
    public static final String DETAIL = "cache:%s:%s";

    /** 接口幂等键。 */
    public static final String IDEMPOTENT = "idem:%s:%s";

    /** 刷新令牌（key 为令牌哈希，value 为 userId）。 */
    public static final String AUTH_REFRESH = "auth:refresh:%s";

    /** 登录失败计数（key 为登录标识的 SHA-256 哈希，value 为失败次数）。 */
    public static final String AUTH_LOGIN_FAIL = "auth:login:fail:%s";

    /** 分布式锁。 */
    public static final String LOCK = "lock:%s:%s";

    /** 在线路由。 */
    public static final String ONLINE_ROUTE = "online:ws:%s";

    private CacheKeys() {
    }
}
