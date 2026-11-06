package com.atifah.common.security.crypto;

import java.util.function.Supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 配置密文装载：校验主密钥与全部密文，再把 {@link ConfigCryptoPropertySource} 置顶。
 *
 * <p>时序：实现 {@link Ordered} 且取最低优先级，确保在 config data（本地 yml / Nacos 导入）装载完成之后执行。
 *
 * <p>密钥缺失策略：
 * <ul>
 *   <li>配置中不存在 {@code ENC(...)} 且未配置主密钥 —— 不做任何改动（本地开发与测试零成本）；</li>
 *   <li>配置中存在 {@code ENC(...)} 但未配置主密钥 —— 直接启动失败，避免带着密文静默运行；</li>
 *   <li>主密钥格式非法，或密文用错误密钥/被篡改/被截断 —— 在启动阶段失败，不等到首次读取。</li>
 * </ul>
 *
 * <p>限制：Nacos 客户端凭据（{@code spring.cloud.nacos.*.password}）在 config data 装载期即被使用，
 * 早于本处理器，无法用本机制加密，只能走环境变量。
 */
public class ConfigCryptoEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final Supplier<String> masterKeySupplier;

    public ConfigCryptoEnvironmentPostProcessor() {
        this(ConfigCipher::resolveMasterKey);
    }

    ConfigCryptoEnvironmentPostProcessor(Supplier<String> masterKeySupplier) {
        this.masterKeySupplier = masterKeySupplier;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String masterKey = masterKeySupplier.get();
        if (masterKey == null) {
            if (containsEncryptedValue(environment)) {
                throw new IllegalStateException("配置中存在 ENC(...) 密文，但未提供主密钥 "
                        + ConfigCipher.KEY_ENV + "；请通过环境变量注入后再启动。");
            }
            return;
        }
        ConfigCipher.validateMasterKey(masterKey);
        verifyEncryptedValues(environment, masterKey);
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
                Object value = source.getProperty(name);
                if (value instanceof String text && findEncryptedValue(environment, name, text) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 启动期验证所有密文：错误密钥、篡改、截断都立即失败，而不是等到首次读取属性。
     */
    private void verifyEncryptedValues(ConfigurableEnvironment environment, String masterKey) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                Object value = source.getProperty(name);
                if (!(value instanceof String text)) {
                    continue;
                }
                String cipherText = findEncryptedValue(environment, name, text);
                if (cipherText == null) {
                    continue;
                }
                try {
                    ConfigCipher.decrypt(cipherText, masterKey);
                } catch (RuntimeException e) {
                    // 只暴露属性名与环境变量名，不拼接密文/明文/密钥
                    throw new IllegalStateException("配置项 " + name + " 解密失败：请检查 "
                            + ConfigCipher.KEY_ENV + " 与密文是否匹配", e);
                }
            }
        }
    }

    /**
     * 直接密文、占位符默认值或环境变量解析后为密文，都返回密文；否则返回 {@code null}。
     */
    private String findEncryptedValue(ConfigurableEnvironment environment, String name, String value) {
        if (value.isEmpty()) {
            return null;
        }
        if (ConfigCipher.isEncrypted(value)) {
            return value;
        }
        if (!value.contains("${")) {
            return null;
        }
        try {
            String resolved = environment.getProperty(name);
            return ConfigCipher.isEncrypted(resolved) ? resolved : null;
        } catch (RuntimeException e) {
            // 占位符无法解析时交给 Spring 后续处理，不在本阶段扩大失败面
            return null;
        }
    }
}
