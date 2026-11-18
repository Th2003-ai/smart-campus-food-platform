package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.admin.entity.SysRole;
import com.campus.delivery.modules.admin.service.RoleService;
import com.campus.delivery.security.RequiresPerm;
import com.campus.delivery.security.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理接口（管理端）。
 *
 * <p>角色是数据（{@code sys_role}），不再是 Java 枚举：
 * 这里可以新增角色、改名、排序、停用，删除有两条保护（内置角色、仍被用户使用）。
 */
@Tag(name = "23-角色管理", description = "RBAC 角色字典的增删改查（角色数据驱动）")
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@RequiresRole("ADMIN")
@RequiresPerm("admin:all")
public class AdminRoleController {

    private final RoleService roleService;

    @Operation(summary = "角色列表", description = "附带每个角色下的用户数")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysRole role : roleService.listAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", role.getCode());
            item.put("name", role.getName());
            item.put("description", role.getDescription());
            item.put("sort", role.getSort());
            item.put("builtin", Integer.valueOf(1).equals(role.getBuiltin()));
            item.put("status", role.getStatus());
            item.put("allowRegister", role.getAllowRegister());
            item.put("portal", role.getPortal());
            item.put("homePath", role.getHomePath());
            item.put("userCount", roleService.userCount(role.getCode()));
            rows.add(item);
        }
        return Result.ok(rows);
    }

    @Operation(summary = "新增角色", description = "编码大写字母开头，2~32 位，创建后可去「角色权限」页授权")
    @PostMapping
    public Result<SysRole> create(@RequestBody SysRole form) {
        return Result.ok(roleService.create(form));
    }

    @Operation(summary = "修改角色", description = "内置角色允许改名/排序/停用，但不允许删除")
    @PutMapping("/{code}")
    public Result<SysRole> update(@PathVariable String code, @RequestBody SysRole form) {
        return Result.ok(roleService.update(code, form));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        roleService.delete(code);
        return Result.ok();
    }
}
