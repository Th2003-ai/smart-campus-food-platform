package com.campus.delivery.common.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * 配置项加解密工具（Jasypt）。
 *
 * <p>用途：把数据库口令等敏感值以 {@code ENC(密文)} 形式写进配置文件，
 * 使仓库里不再出现明文口令；解密口令由环境变量
 * {@code JASYPT_ENCRYPTOR_PASSWORD} 注入，不随代码分发。
 *
 * <p>与 {@link PasswordGenerator} 的区别：
 * <ul>
 *   <li>{@link PasswordGenerator}：BCrypt 单向哈希，用于<b>入库</b>的登录口令，不可逆；</li>
 *   <li>本类：可逆加密，用于<b>配置文件</b>里必须还原成原文的凭据（如数据库口令）。</li>
 * </ul>
 *
 * <p>命令行使用（密钥务必用环境变量传入，不要写进命令历史之外的仓库文件）：
 * <pre>
 *   # 1) 先设置密钥（自行替换成足够长的随机串，并妥善保管）
 *   set JASYPT_ENCRYPTOR_PASSWORD=你的密钥
 *
 *   # 2) 加密
 *   mvn -q compile exec:java -Dexec.mainClass=com.campus.delivery.common.util.ConfigEncryptor -Dexec.args="enc 数据库口令"
 *
 *   # 3) 解密核对（确认密文可用）
 *   mvn -q compile exec:java -Dexec.mainClass=com.campus.delivery.common.util.ConfigEncryptor -Dexec.args="dec ENC(...)"
 * </pre>
 *
 * <p>算法与 {@code application.yml} 中 {@code jasypt.encryptor.*} 保持一致，
 * 否则启动时解密会失败。
 */
public final class ConfigEncryptor {

    /** 与 application.yml 中 jasypt.encryptor.algorithm 一致 */
    private static final String ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
    /** 与 application.yml 中 jasypt.encryptor.iv-generator-classname 一致 */
    private static final String IV_GENERATOR = "org.jasypt.iv.RandomIvGenerator";
    private static final String KEY_OBTENTION_ITERATIONS = "1000";
    private static final String SALT_GENERATOR = "org.jasypt.salt.RandomSaltGenerator";

    private ConfigEncryptor() {
    }

    /** 用指定密钥构造加密器 */
    public static PooledPBEStringEncryptor create(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException(
                    "缺少密钥：请先设置环境变量 JASYPT_ENCRYPTOR_PASSWORD，例如 set JASYPT_ENCRYPTOR_PASSWORD=你的密钥");
        }
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm(ALGORITHM);
        config.setKeyObtentionIterations(KEY_OBTENTION_ITERATIONS);
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName(SALT_GENERATOR);
        config.setIvGeneratorClassName(IV_GENERATOR);
        config.setStringOutputType("base64");

        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(config);
        return encryptor;
    }

    /** 从环境变量读密钥并加密 */
    public static String encrypt(String plain) {
        return create(System.getenv("JASYPT_ENCRYPTOR_PASSWORD")).encrypt(plain);
    }

    /** 从环境变量读密钥并解密（入参可带 ENC(...) 包装） */
    public static String decrypt(String encrypted) {
        String cipher = unwrap(encrypted);
        return create(System.getenv("JASYPT_ENCRYPTOR_PASSWORD")).decrypt(cipher);
    }

    /** 去掉 ENC(...) 包装 */
    public static String unwrap(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.startsWith("ENC(") && t.endsWith(")")) {
            return t.substring(4, t.length() - 1);
        }
        return t;
    }

    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            usage();
            return;
        }
        String action = args[0];
        String value = args[1];
        if ("enc".equalsIgnoreCase(action)) {
            String cipher = encrypt(value);
            System.out.println("明文      : " + value);
            System.out.println("写入配置  : ENC(" + cipher + ")");
            System.out.println();
            System.out.println("对应环境变量（不要提交到仓库）：");
            System.out.println("  JASYPT_ENCRYPTOR_PASSWORD=<你设置的那个密钥>");
        } else if ("dec".equalsIgnoreCase(action)) {
            try {
                System.out.println("密文      : " + value);
                System.out.println("明文      : " + decrypt(value));
            } catch (Exception e) {
                System.out.println("解密失败：" + e.getMessage());
                System.out.println("请确认 JASYPT_ENCRYPTOR_PASSWORD 与加密时一致。");
            }
        } else {
            usage();
        }
    }

    private static void usage() {
        System.out.println("用法：");
        System.out.println("  set JASYPT_ENCRYPTOR_PASSWORD=你的密钥");
        System.out.println("  ConfigEncryptor enc <明文>        生成 ENC(...)");
        System.out.println("  ConfigEncryptor dec <ENC(密文)>   解密核对");
        System.out.println();
        System.out.println("提示：密钥请用足够长的随机串，且只放在环境变量/密钥管理系统中，");
        System.out.println("      绝对不要提交到 Git 仓库，否则加密等同于没加密。");
    }
}
