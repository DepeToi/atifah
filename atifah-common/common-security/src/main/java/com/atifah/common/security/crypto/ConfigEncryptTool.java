package com.atifah.common.security.crypto;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * 配置密文离线工具（只用 JDK，不需要 Spring 环境）。
 *
 * <p>用法（在仓库根目录执行，先 {@code mvn -q -DskipTests compile}）：
 * <pre>{@code
 * CLASSES=atifah-common/common-security/target/classes
 *
 * # 1) 生成主密钥（只做一次，写入环境变量 CONFIG_ENC_KEY，不要提交仓库）
 * java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool keygen
 * # 2) 加密配置值：从终端读取明文（不回显），输出整段 ENC(...)
 * CONFIG_ENC_KEY=xxx java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool encrypt
 * # 或从受限权限的文件经 stdin 读取，避免明文进入 shell 历史/CI 日志
 * CONFIG_ENC_KEY=xxx java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool encrypt < plaintext.txt
 * # 3) 校验密文与密钥是否匹配（会输出明文，禁止在共享终端/CI 日志使用）
 * CONFIG_ENC_KEY=xxx java -cp $CLASSES com.atifah.common.security.crypto.ConfigEncryptTool decrypt 'ENC(...)'
 * }</pre>
 *
 * <p>主密钥只从环境变量读取；明文不得作为命令行参数传入，避免进入 shell 历史与进程列表。
 */
public final class ConfigEncryptTool {

    private static final String CMD_KEYGEN = "keygen";
    private static final String CMD_ENCRYPT = "encrypt";
    private static final String CMD_DECRYPT = "decrypt";

    private ConfigEncryptTool() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.in, System.out, System.console(), ConfigCipher::resolveMasterKey);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, InputStream in, PrintStream out, Console console,
                   Supplier<String> masterKeySupplier) {
        if (args.length == 0) {
            printUsage(out);
            return 0;
        }
        String command = args[0];
        switch (command) {
            case CMD_KEYGEN -> {
                out.println(ConfigCipher.generateMasterKey());
                return 0;
            }
            case CMD_ENCRYPT -> {
                if (args.length > 1) {
                    out.println("明文不得通过命令行参数传入；请从终端或 stdin 输入。");
                    return 2;
                }
                String masterKey = masterKeySupplier.get();
                if (masterKey == null) {
                    out.println("未设置环境变量 " + ConfigCipher.KEY_ENV);
                    return 2;
                }
                String plainText = readPlainText(in, console);
                if (plainText == null || plainText.isEmpty()) {
                    out.println("未读取到明文；请从终端或 stdin 输入。");
                    return 2;
                }
                out.println(ConfigCipher.encrypt(plainText, masterKey));
                return 0;
            }
            case CMD_DECRYPT -> {
                if (args.length < 2) {
                    printUsage(out);
                    return 0;
                }
                String masterKey = masterKeySupplier.get();
                if (masterKey == null) {
                    out.println("未设置环境变量 " + ConfigCipher.KEY_ENV);
                    return 2;
                }
                out.println(ConfigCipher.decrypt(args[1], masterKey));
                return 0;
            }
            default -> {
                printUsage(out);
                return 0;
            }
        }
    }

    private static String readPlainText(InputStream in, Console console) {
        if (console != null) {
            char[] chars = console.readPassword("请输入明文（不回显）: ");
            return chars == null ? null : new String(chars);
        }
        try {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\r\n")) {
                return text.substring(0, text.length() - 2);
            }
            if (text.endsWith("\n")) {
                return text.substring(0, text.length() - 1);
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("读取明文失败", e);
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("""
                配置密文工具（主密钥从环境变量 %s 读取）
                  keygen              生成随机 32 字节主密钥（base64url）
                  encrypt             从终端（不回显）或 stdin 读取明文，输出 ENC(...)
                  decrypt <ENC(...)>  校验并输出明文

                明文与主密钥不得通过命令行参数传入，避免进入 shell 历史/进程列表。
                """.formatted(ConfigCipher.KEY_ENV));
    }
}
