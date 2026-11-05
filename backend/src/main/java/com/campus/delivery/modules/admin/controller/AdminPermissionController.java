package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.admin.dto.RolePermItem;
import com.campus.delivery.modules.admin.service.PermissionService;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限管理接口：四个主体（学生/商户/骑手/管理员）的授权与成员管理。
 * 主责：成员4。完整路径前缀 {@code /api/admin/perm}。
 *
 * <p>成员管理只做「够用」的三件事：加人、改主体、停用/改密码。
 * 不做批量导入和部门树——课设原型里没有这两类需求。
 */
@Tag(name = "18-管理端·权限管理", description = "角色权限矩阵的读写、成员角色分配、成员新增/停用/重置密码")
@RestController
@RequestMapping("/admin/perm")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class AdminPermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "权限矩阵", description = "四个主体 × 权限码，含每个授权的数据范围")
    @GetMapping("/matrix")
    public Result<Map<String, Object>> matrix() {
        return Result.ok(permissionService.matrix());
    }

    @Operation(summary = "保存某角色的权限", description = "整体覆盖：提交的清单即该角色的最终授权")
    @PutMapping("/roles/{role}")
    public Result<Void> saveRole(@PathVariable String role, @RequestBody List<RolePermItem> items) {
        permissionService.saveRole(role, items);
        return Result.ok();
    }

    @Operation(summary = "成员列表", description = "sys_user 中的成员及其当前所属主体")
    @GetMapping("/users")
    public Result<List<SysUser>> users() {
        return Result.ok(permissionService.users());
    }

    @Operation(summary = "新增成员", description = "密码缺省为 Campus@2026，账号不可重复")
    @PostMapping("/users")
    public Result<Map<String, Object>> createUser(@RequestBody Map<String, Object> payload) {
        String username = text(payload, "username");
        String nickname = text(payload, "nickname");
        String phone = text(payload, "phone");
        String role = text(payload, "role");
        String password = text(payload, "password");
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "role 不能为空");
        }
        SysUser created = permissionService.createUser(username, nickname, phone, role, password);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", created.getId());
        result.put("username", created.getUsername());
        result.put("role", created.getRole());
        result.put("defaultPassword", StringUtils.hasText(password) ? null : PermissionService.DEFAULT_PASSWORD);
        return Result.ok(result);
    }

    @Operation(summary = "给成员分配主体", description = "改 sys_user.role，立即影响该成员可访问的接口")
    @PutMapping("/users/{id}/role")
    public Result<Void> updateUserRole(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String role = payload == null ? null : payload.get("role");
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "role 不能为空");
        }
        permissionService.updateUserRole(id, role);
        return Result.ok();
    }

    @Operation(summary = "启用/停用成员", description = "status: 0 停用 1 启用；停用后该成员无法登录")
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("status");
        Integer status = raw instanceof Number number ? number.intValue()
                : raw == null ? null : Integer.valueOf(String.valueOf(raw));
        permissionService.updateUserStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "重置成员密码", description = "至少 6 位")
    @PutMapping("/users/{id}/password")
    public Result<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String password = payload == null ? null : payload.get("password");
        permissionService.resetPassword(id, password);
        return Result.ok();
    }

    private String text(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
