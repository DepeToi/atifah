package com.atifah.common.security.config;

import com.atifah.common.security.jwt.JwtTokenProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全公共能力自动装配。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonSecurityAutoConfiguration {

    /**
     * 装配 JWT 处理器。
     *
     * @param properties JWT 配置
     * @return JWT 处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(properties);
    }

    /**
     * 装配密码编码器（BCrypt）。
     *
     * @return 密码编码器
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
