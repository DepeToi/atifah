package com.atifah.common.security.nacos;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 生产环境 Nacos namespace 防呆校验：防止生产服务误连开发/测试配置中心。
 *
 * <p>激活 {@code prod} profile 时，Nacos 配置和服务发现必须使用 {@code atifah_prod}；
 * 缺失、拼写错误或两者不一致时直接拒绝启动。本地与其他环境不受影响。
 *
 * <p>通过 {@code META-INF/spring.factories} 注册（EnvironmentPostProcessor 在自动装配之前执行，
 * 不走 {@code AutoConfiguration.imports}）。
 */
public class NacosNamespaceGuard implements EnvironmentPostProcessor {

    private static final String PRODUCTION_NAMESPACE = "atifah_prod";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        String configNamespace = environment.getProperty("spring.cloud.nacos.config.namespace", "");
        String discoveryNamespace = environment.getProperty("spring.cloud.nacos.discovery.namespace", "");
        for (String namespace : List.of(configNamespace, discoveryNamespace)) {
            if (!PRODUCTION_NAMESPACE.equals(namespace)) {
                throw new IllegalStateException("生产环境 Nacos namespace 校验失败：[" + namespace
                        + "] 必须为生产命名空间 " + PRODUCTION_NAMESPACE
                        + "，请通过环境变量 NACOS_NAMESPACE 注入，防止误连其他环境配置中心。");
            }
        }
    }
}
