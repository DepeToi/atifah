package com.atifah.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 公共配置：分页插件。
 * 所有 SQL 走 Mapper XML（ADR 0002），不使用 BaseMapper CRUD 与自动填充。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@AutoConfiguration
public class MybatisPlusAutoConfiguration {

    /**
     * 装配 MyBatis-Plus 分页插件。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
