package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.modules.admin.dto.MenuTreeVO;
import com.campus.delivery.modules.admin.entity.SysMenu;
import com.campus.delivery.modules.admin.service.MenuService;
import com.campus.delivery.common.api.Result;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端菜单权限树接口（树形数据结构可视化）。主责：成员4。
 *
 * <p>完整接口路径前缀为 {@code /api/admin/menu}。
 */
@Tag(name = "14-管理端·菜单权限树", description = "菜单树形结构查询，用于权限树可视化")
@RestController
@RequestMapping("/admin/menu")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "菜单树形结构", description = "返回四端目录→菜单→按钮的多级树，供 el-tree 渲染")
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> tree() {
        return Result.ok(menuService.tree());
    }

    @Operation(summary = "菜单平铺列表", description = "数据库中按 parent_id 排列的原始平铺数据，与树形结果对照")
    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.ok(menuService.list());
    }
}
