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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限管理接口：四个主体（学生/商户/骑手/管理员）的授权与成员角色分配。
 * 主责：成员4。完整路径前缀 {@code /api/admin/perm}。
 */
@Tag(name = "18-管理端·权限管理", description = "角色权限矩阵的读写、成员角色分配")
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
}
