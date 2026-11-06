package com.atifah.common.security.crypto;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置密文装载器单元测试：覆盖直写密文、环境变量注入密文、缺密钥三种场景。
 */
class ConfigCryptoEnvironmentPostProcessorTest {

    private static final String MASTER_KEY = "unit-test-master-key";

    private final ConfigCryptoEnvironmentPostProcessor postProcessor = new ConfigCryptoEnvironmentPostProcessor();

    private StandardEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new StandardEnvironment();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ConfigCipher.KEY_ENV);
    }

    @Test
    void decryptsLiteralCipherTextInConfig() {
        System.setProperty(ConfigCipher.KEY_ENV, MASTER_KEY);
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", cipherText)));

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("db-password");
        assertThat(Binder.get(environment).bind("spring.datasource.password", String.class).get())
                .isEqualTo("db-password");
    }

    @Test
    void decryptsCipherTextInjectedThroughPlaceholder() {
        System.setProperty(ConfigCipher.KEY_ENV, MASTER_KEY);
        String cipherText = ConfigCipher.encrypt("redis-password", MASTER_KEY);
        environment.getPropertySources().addLast(new MapPropertySource("test", Map.of(
                "spring.data.redis.password", "${MISSING_ENV:}",
                "MISSING_ENV", cipherText)));

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("redis-password");
    }

    @Test
    void keepsPlainValuesUntouched() {
        System.setProperty(ConfigCipher.KEY_ENV, MASTER_KEY);
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "${MISSING_ENV:root}")));

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("root");
    }

    @Test
    void failsFastWhenCipherTextPresentButKeyMissing() {
        Assumptions.assumeTrue(ConfigCipher.resolveMasterKey() == null, "本机已配置 CONFIG_ENC_KEY，跳过缺密钥场景");
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", cipherText)));

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigCipher.KEY_ENV);
    }

    @Test
    void failsFastWhenCipherTextIsPlaceholderDefaultAndKeyMissing() {
        Assumptions.assumeTrue(ConfigCipher.resolveMasterKey() == null, "本机已配置 CONFIG_ENC_KEY，跳过缺密钥场景");
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "${MISSING_ENV:" + cipherText + "}")));

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigCipher.KEY_ENV);
    }

    @Test
    void startsWithoutKeyWhenNoCipherTextExists() {
        Assumptions.assumeTrue(ConfigCipher.resolveMasterKey() == null, "本机已配置 CONFIG_ENC_KEY，跳过缺密钥场景");
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "root")));

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("root");
    }
}
