package com.atifah.common.security.crypto;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/**
 * 配置密文解密覆盖层。
 *
 * <p>作为属性源置顶插入，取值时按原有优先级顺序回查真实属性源并对 {@code ENC(...)} 值解密。
 * 与「包装每个属性源」相比不持有快照，因此 Nacos 配置刷新替换属性源后依旧生效。
 *
 * <p>兼容两种写法：
 * <ul>
 *   <li>配置值直接写成密文：{@code password: "ENC(...)"}；</li>
 *   <li>环境变量注入密文：{@code password: ${MYSQL_PASSWORD:}} 且 {@code MYSQL_PASSWORD=ENC(...)}。</li>
 * </ul>
 * 后者需要先解析占位符才能判断是否密文，故解析复用原始属性源（不含本层）避免回环；
 * 解析结果不是密文时返回原值，交回 Spring 正常解析，不改变非密文配置的既有行为。
 */
final class ConfigCryptoPropertySource extends PropertySource<Object> {

    /**
     * 属性源名称，排在所有属性源之前。
     */
    static final String NAME = "atifahConfigCrypto";

    private final ConfigurableEnvironment environment;

    private final String masterKey;

    private final ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> Boolean.FALSE);

    ConfigCryptoPropertySource(ConfigurableEnvironment environment, String masterKey) {
        super(NAME);
        this.environment = environment;
        this.masterKey = masterKey;
    }

    @Override
    public Object getProperty(String name) {
        if (Boolean.TRUE.equals(resolving.get())) {
            // 占位符解析期间的回环调用直接放弃，交给下一个属性源
            return null;
        }
        resolving.set(Boolean.TRUE);
        try {
            for (PropertySource<?> source : environment.getPropertySources()) {
                if (source == this) {
                    continue;
                }
                Object value = source.getProperty(name);
                if (value != null) {
                    return decryptIfNecessary(value);
                }
            }
            return null;
        } finally {
            resolving.set(Boolean.FALSE);
        }
    }

    private Object decryptIfNecessary(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) {
            return value;
        }
        if (ConfigCipher.isEncrypted(text)) {
            return ConfigCipher.decrypt(text, masterKey);
        }
        if (text.contains("${")) {
            String resolved = placeholderResolver().resolvePlaceholders(text);
            if (ConfigCipher.isEncrypted(resolved)) {
                return ConfigCipher.decrypt(resolved, masterKey);
            }
        }
        return value;
    }

    /**
     * 仅供密文判定使用：只看原始属性源，避免再次进入本层。
     */
    private PropertySourcesPropertyResolver placeholderResolver() {
        List<PropertySource<?>> snapshot = new ArrayList<>();
        environment.getPropertySources().forEach(snapshot::add);
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> source : snapshot) {
            if (source != this) {
                sources.addLast(source);
            }
        }
        return new PropertySourcesPropertyResolver(sources);
    }
}
