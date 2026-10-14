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

    @Override
    public void run(ApplicationArguments args) {
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
            log.warn("演示账号密码初始化跳过（可能尚未执行 database/schema.sql）：{}", e.getMessage());
        }
    }
}
