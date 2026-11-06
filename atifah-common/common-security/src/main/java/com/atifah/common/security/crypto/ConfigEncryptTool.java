package com.atifah.common.security.crypto;

/**
 * 配置密文离线工具（只用 JDK，不需要 Spring 环境）。
 *
 * <p>用法（在仓库根目录执行，先 {@code mvn -q -DskipTests compile}）：
 * <pre>{@code
 * CLASSES=atifah-common/common-security/target/classes
 *
 * # 1) 生成主密钥（只做一次，写入环境变量 CONFIG_ENC_KEY，不要提交仓库）
 * java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool keygen
 *
 * # 2) 加密配置值，把输出整段贴进 yml / Nacos
 * CONFIG_ENC_KEY=xxx java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool encrypt 'MySql@Prod#2026'
 *
 * # 3) 校验密文与密钥是否匹配
 * CONFIG_ENC_KEY=xxx java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool decrypt 'ENC(...)'
 * }</pre>
 *
 * <p>密钥只从环境变量（或同名 JVM system property）读取，不接受命令行参数，避免泄漏到 shell 历史。
 */
public final class ConfigEncryptTool {

    private static final String CMD_KEYGEN = "keygen";
    private static final String CMD_ENCRYPT = "encrypt";
    private static final String CMD_DECRYPT = "decrypt";

    private ConfigEncryptTool() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        String command = args[0];
        switch (command) {
            case CMD_KEYGEN -> System.out.println(ConfigCipher.generateMasterKey());
            case CMD_ENCRYPT -> {
                if (args.length < 2) {
                    printUsage();
                    return;
                }
                System.out.println(ConfigCipher.encrypt(args[1], requireMasterKey()));
            }
            case CMD_DECRYPT -> {
                if (args.length < 2) {
                    printUsage();
                    return;
                }
                System.out.println(ConfigCipher.decrypt(args[1], requireMasterKey()));
            }
            default -> printUsage();
        }
    }

    private static String requireMasterKey() {
        String masterKey = ConfigCipher.resolveMasterKey();
        if (masterKey == null) {
            throw new IllegalStateException("未设置环境变量 " + ConfigCipher.KEY_ENV);
        }
        return masterKey;
    }

    private static void printUsage() {
        System.out.println("""
                配置密文工具（主密钥从环境变量 %s 读取）
                  keygen             生成随机 32 字节主密钥
                  encrypt <明文>      输出 ENC(...) 密文
                  decrypt <ENC(...)>  校验并输出明文
                """.formatted(ConfigCipher.KEY_ENV));
    }
}
