package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口。主责：成员3。
 *
 * <p>此前管理端「系统配置」页没有任何后端接口（清单里的缺口 G5），页面无从对接。
 * 这里直接读写 {@code sys_config} 表，键值对形式，适合放开关类配置
 * （例如是否开启演示账号初始化、平台公告等）。
 *
 * <p>用 {@link JdbcTemplate} 而非新建实体 + Mapper：表结构就是简单的 key/value，
 * 为它单独建一套 ORM 映射收益很低（同类做法见 {@code DashboardService}）。
 */
@Tag(name = "17-管理端·系统配置", description = "平台配置项的查询与修改")
@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class AdminConfigController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "配置列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, config_key AS configKey, config_value AS configValue, remark, "
                        + "create_time AS createTime, update_time AS updateTime "
                        + "FROM sys_config ORDER BY config_key");
        return Result.ok(rows);
    }

    /**
     * 新增或修改配置：按 config_key 做「有则更新、无则插入」。
     *
     * @param payload 至少包含 configKey / configValue
     */
    @Operation(summary = "保存配置", description = "按 configKey 有则更新、无则新增")
    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> save(@RequestBody Map<String, String> payload) {
        String key = payload.get("configKey");
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(70020, "配置键不能为空");
        }
        String value = payload.getOrDefault("configValue", "");
        String remark = payload.get("remark");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_config WHERE config_key = ?", Integer.class, key.trim());
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE sys_config SET config_value = ?, remark = ? WHERE config_key = ?",
                    value, remark, key.trim());
        } else {
            jdbcTemplate.update(
                    "INSERT INTO sys_config (id, config_key, config_value, remark) VALUES (?, ?, ?, ?)",
                    newId(), key.trim(), value, remark);
        }
        return Result.ok();
    }

    /** 32 位无横线 UUID，与库表 VARCHAR(32) 主键约定一致 */
    private String newId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
