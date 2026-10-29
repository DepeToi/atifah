package com.atifah.common.redis.config;

import org.redisson.config.BaseConfig;
import org.redisson.config.SentinelServersConfig;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate 序列化配置：Key 用字符串，Value 用 JSON。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@AutoConfiguration
public class RedisAutoConfiguration {

    /**
     * 装配 RedisTemplate，Key 使用字符串序列化，Value 使用 JSON 序列化。
     *
     * @param connectionFactory Redis 连接工厂
     * @return RedisTemplate
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 本地配置使用 {@code ${REDIS_PASSWORD:}}，未设置密码时得到空字符串。
     * Redisson 只判断密码是否为 null，空串也会发送 AUTH，导致无密码 Redis 拒绝连接
     * （ERR AUTH called without any password configured）；这里把空白密码归一化为 null。
     *
     * @return Redisson 配置定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "blankRedisPasswordCustomizer")
    public RedissonAutoConfigurationCustomizer blankRedisPasswordCustomizer() {
        return config -> {
            if (config.isSingleConfig()) {
                clearBlankPassword(config.useSingleServer());
            } else if (config.isSentinelConfig()) {
                SentinelServersConfig sentinel = config.useSentinelServers();
                clearBlankPassword(sentinel);
                if (isBlank(sentinel.getSentinelPassword())) {
                    sentinel.setSentinelPassword(null);
                }
            } else if (config.isClusterConfig()) {
                clearBlankPassword(config.useClusterServers());
            }
        };
    }

    private static void clearBlankPassword(BaseConfig<?> config) {
        if (isBlank(config.getPassword())) {
            config.setPassword(null);
        }
    }

    private static boolean isBlank(String value) {
        return value != null && value.isBlank();
    }
}
