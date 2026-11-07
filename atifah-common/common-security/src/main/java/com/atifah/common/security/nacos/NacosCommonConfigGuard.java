package com.atifah.common.security.nacos;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 必选公共配置装载校验：防止 common-application.yaml 未发布/未拉取成功时静默走本地默认值。
 *
 * <p>SCA 的 NacosConfigDataLoader 对不存在的 dataId 只打 WARN 并按空配置处理，非 optional
 * 导入也不会失败，因此通过公共配置内的装载标记 {@link #MARKER_PROPERTY} 判断是否真的装载：
 * 服务声明了 {@code nacos:common-application.yaml} 导入却读不到标记时，直接拒绝启动。
 * 未声明该导入的模块（如网关）与 {@code test} profile 不受约束。
 */
public class NacosCommonConfigGuard implements EnvironmentPostProcessor {

    /**
     * 公共配置 common-application.yaml 中的装载标记 key，发布时必须携带且值为 true。
     */
    static final String MARKER_PROPERTY = "atifah.common.config-loaded";

    private static final String COMMON_DATA_ID = "common-application.yaml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (List.of(environment.getActiveProfiles()).contains("test")) {
            return;
        }
        List<String> imports = Binder.get(environment)
                .bind("spring.config.import", Bindable.listOf(String.class))
                .orElse(List.of());
        boolean importsCommon = imports.stream().anyMatch(item -> item.contains(COMMON_DATA_ID));
        if (!importsCommon) {
            return;
        }
        if (!"true".equalsIgnoreCase(environment.getProperty(MARKER_PROPERTY, ""))) {
            throw new IllegalStateException("必选公共配置 common-application.yaml 未装载（标记 " + MARKER_PROPERTY
                    + " 缺失）：请确认 Nacos 可达，并已按 deploy/nacos/README.md 将公共配置发布到当前命名空间（"
                    + environment.getProperty("spring.cloud.nacos.config.namespace", "") + "）后再启动。");
        }
    }
}
