package com.atifah.common.security.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 配置密文装载：把 {@link ConfigCryptoPropertySource} 置顶，使 {@code ENC(...)} 在业务读取前完成解密。
 *
 * <p>时序：实现 {@link Ordered} 且取最低优先级，确保在 config data（本地 yml / Nacos 导入）装载完成之后执行。
 *
 * <p>密钥缺失策略：
 * <ul>
 *   <li>配置中不存在 {@code ENC(...)} 且未配置主密钥 —— 不做任何改动（本地开发与测试零成本）；</li>
 *   <li>配置中存在 {@code ENC(...)} 但未配置主密钥 —— 直接启动失败，避免带着密文静默运行。</li>
 * </ul>
 *
 * <p>限制：Nacos 客户端凭据（{@code spring.cloud.nacos.*.password}）在 config data 装载期即被使用，
 * 早于本处理器，无法用本机制加密，只能走环境变量。
 */
public class ConfigCryptoEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String masterKey = ConfigCipher.resolveMasterKey();
        if (masterKey == null) {
            if (containsEncryptedValue(environment)) {
                throw new IllegalStateException("配置中存在 ENC(...) 密文，但未提供主密钥 "
                        + ConfigCipher.KEY_ENV + "；请通过环境变量注入后再启动。");
            }
            return;
        }
        environment.getPropertySources().addFirst(new ConfigCryptoPropertySource(environment, masterKey));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean containsEncryptedValue(ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (source.getProperty(name) instanceof String value && ConfigCipher.isEncrypted(value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
