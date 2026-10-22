package com.campus.delivery.config;

import com.campus.delivery.common.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 开发环境演示账号密码初始化。
 *
 * <p>database/data.sql 中的演示账号不写入伪造的 BCrypt 哈希（密码列为空字符串），
 * 由本类在 dev profile 启动时统一初始化为预设口令的密文，
 * 保证「拉下代码 → 建库 → 启动 → 直接登录演示」可用。
 *
 * <p>四个角色各用一套口令（见 {@link #DEMO_PASSWORDS}），避免一个账号泄露牵连其余角色。
 * 口令属于演示用凭据，明文只写在本类与 database/README.md 中；生产环境必须移除本类。
 *
 * <p>关闭方式：{@code campus.dev.init-demo-password=false}
 * 或改用 {@code dev,local} 之外的 profile。
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    /** 演示账号（用户名 → 初始口令），与 database/README.md「三、演示账号」保持一致 */
    private static final Map<String, String> DEMO_PASSWORDS = Map.of(
            "20210001", "Stu@Campus2026",
            "13900000001", "Mch@Campus2026",
            "13700000001", "Rid@Campus2026",
            "admin", "Adm@Campus#2026");

    private final JdbcTemplate jdbcTemplate;

    @Value("${campus.dev.init-demo-password:false}")
    private boolean initDemoPassword;

    /** 当前生效的数据库口令；用于在仍是占位符时提前给出明确提示 */
    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Override
    public void run(ApplicationArguments args) {
        warnIfPlaceholderPassword();
        if (!initDemoPassword) {
            return;
        }
        try {
            int updated = 0;
            for (Map.Entry<String, String> entry : DEMO_PASSWORDS.entrySet()) {
                // 只补空口令，绝不覆盖已经设置过的密码（避免开发中改过密码又被重置）
                updated += jdbcTemplate.update(
                        "UPDATE sys_user SET password = ? "
                                + "WHERE username = ? AND (password IS NULL OR password = '') AND deleted = 0",
                        PasswordGenerator.encode(entry.getValue()), entry.getKey());
            }
            if (updated > 0) {
                // 日志只打条数与用户名，不打印口令明文
                log.warn("已初始化 {} 个演示账号的密码（用户名：{}），口令见 database/README.md，请勿在生产环境开启此功能",
                        updated, DEMO_PASSWORDS.keySet());
            } else {
                log.info("演示账号密码已初始化，跳过");
            }
        } catch (Exception e) {
            log.warn("演示账号密码初始化跳过：{}", e.getMessage());
            log.warn("排查顺序：① 启动 profile 是否为 dev,local（缺 local 会读不到 application-local.yml）"
                    + "；② 是否已执行 database/schema.sql 与 data.sql"
                    + "；③ MySQL 口令是否与 DB_PASSWORD 或 application-local.yml 一致");
        }
    }

    /**
     * 口令仍是占位符时提前告警。
     *
     * <p>这个提示是为了避免「启动看起来成功、实际连不上库」的迷惑场景：
     * Hikari 连接池初始化失败不会阻断 Tomcat 启动，端口照样监听，
     * 只有业务接口才会报错，排查成本较高。
     */
    private void warnIfPlaceholderPassword() {
        if ("CHANGE_ME".equals(datasourcePassword)) {
            log.error("======================================================================");
            log.error("数据库口令仍是占位符 CHANGE_ME，必然无法连接数据库。");
            log.error("请任选其一：");
            log.error("  1) 以 dev,local 启动，并在 application-local.yml 中填写真实口令");
            log.error("  2) 设置环境变量 DB_PASSWORD=<你的 MySQL 口令>");
            log.error("  3) 使用加密配置：password: ENC(...)，并设置 JASYPT_ENCRYPTOR_PASSWORD");
            log.error("======================================================================");
        }
    }
}
