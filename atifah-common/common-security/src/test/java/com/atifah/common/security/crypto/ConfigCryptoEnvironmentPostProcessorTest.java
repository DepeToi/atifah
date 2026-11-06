package com.atifah.common.security.crypto;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 配置密文装载器单元测试：覆盖直写密文、环境变量注入密文、缺密钥、错误密钥、篡改与字面量占位符。
 */
class ConfigCryptoEnvironmentPostProcessorTest {

    private static final String MASTER_KEY = ConfigCipher.generateMasterKey();
    private static final String WRONG_KEY = ConfigCipher.generateMasterKey();

    private StandardEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new StandardEnvironment();
    }

    private ConfigCryptoEnvironmentPostProcessor processor(String masterKey) {
        return new ConfigCryptoEnvironmentPostProcessor(() -> masterKey);
    }

    @Test
    void decryptsLiteralCipherTextInConfig() {
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", cipherText)));

        processor(MASTER_KEY).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("db-password");
        assertThat(Binder.get(environment).bind("spring.datasource.password", String.class).get())
                .isEqualTo("db-password");
    }

    @Test
    void decryptsCipherTextInjectedThroughPlaceholder() {
        String cipherText = ConfigCipher.encrypt("redis-password", MASTER_KEY);
        environment.getPropertySources().addLast(new MapPropertySource("test", Map.of(
                "spring.data.redis.password", "${MISSING_ENV:}",
                "MISSING_ENV", cipherText)));

        processor(MASTER_KEY).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("redis-password");
    }

    @Test
    void keepsPlainValuesUntouched() {
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "${MISSING_ENV:root}")));

        processor(MASTER_KEY).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("root");
    }

    @Test
    void failsFastWhenCipherTextPresentButKeyMissing() {
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", cipherText)));

        assertThatThrownBy(() -> processor(null).postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigCipher.KEY_ENV);
    }

    @Test
    void failsFastWhenCipherTextIsPlaceholderDefaultAndKeyMissing() {
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "${MISSING_ENV:" + cipherText + "}")));

        assertThatThrownBy(() -> processor(null).postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigCipher.KEY_ENV);
    }

    @Test
    void startsWithoutKeyWhenNoCipherTextExists() {
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "root")));

        processor(null).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("root");
    }

    @Test
    void failsFastWhenMasterKeyIsMalformed() {
        environment.getPropertySources().addLast(new MapPropertySource("test",
                Map.of("spring.datasource.password", "root")));

        assertThatThrownBy(() -> processor("password").postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigCipher.KEY_ENV);
    }

    @Test
    void failsFastWhenCipherTextUsesWrongKey() {
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", cipherText)));

        Throwable thrown = catchThrowable(() -> processor(WRONG_KEY).postProcessEnvironment(environment, null));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
                .contains("spring.datasource.password", ConfigCipher.KEY_ENV)
                .doesNotContain(cipherText)
                .doesNotContain("db-password");
    }

    @Test
    void failsFastWhenCipherTextIsTampered() {
        String cipherText = ConfigCipher.encrypt("db-password", MASTER_KEY);
        String body = cipherText.substring(ConfigCipher.PREFIX.length(), cipherText.length() - 1);
        byte[] payload = Base64.getDecoder().decode(body);
        payload[payload.length - 1] ^= 0xFF;
        String tampered = ConfigCipher.PREFIX + Base64.getEncoder().encodeToString(payload) + ")";
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", tampered)));

        assertThatThrownBy(() -> processor(MASTER_KEY).postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void keepsDollarBraceLiteralInDecryptedValue() {
        String plainText = "p@ss${SECRET_FRAGMENT}word";
        String cipherText = ConfigCipher.encrypt(plainText, MASTER_KEY);
        environment.getPropertySources().addLast(
                new MapPropertySource("test", Map.of("spring.datasource.password", cipherText)));

        processor(MASTER_KEY).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(plainText);
        assertThat(Binder.get(environment).bind("spring.datasource.password", String.class).get())
                .isEqualTo(plainText);
    }
}
