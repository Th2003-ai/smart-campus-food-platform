package com.campus.delivery.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.admin.dto.RolePermItem;
import com.campus.delivery.modules.admin.entity.SysRolePermission;
import com.campus.delivery.modules.admin.mapper.SysRolePermissionMapper;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.modules.user.mapper.SysUserMapper;
import com.campus.delivery.security.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 权限管理（四个主体 + 角色授权 + 成员角色分配）。主责：成员4。
 *
 * <p>数据全部落在两张已有表上，不新增结构：
 * <ul>
 *   <li>{@code sys_role_permission}：角色 → 权限码 → 数据范围；</li>
 *   <li>{@code sys_user.role}：每个成员归属于哪个主体。</li>
 * </ul>
 *
 * <p>接口级鉴权仍由 {@code @RequiresRole} 在拦截器里完成；本服务负责把
 * 「谁能做什么、能看到多大数据范围」变成可配置、可展示的数据。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 四个主体（顺序即前端展示顺序） */
    private static final List<String> ROLES = List.of("STUDENT", "MERCHANT", "RIDER", "ADMIN");

    /** 数据范围枚举值 */
    private static final List<String> SCOPES = List.of("ALL", "SHOP", "SELF");

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 权限矩阵：角色 × 权限码，附带每人数据范围。
     *
     * <p>返回结构：
     * {@code { roles:[...], scopes:[...], perms:[权限码...], granted:{角色:{权限码:数据范围}} }}
     */
    public Map<String, Object> matrix() {
        List<SysRolePermission> rows = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>()
                        .orderByAsc(SysRolePermission::getRole)
                        .orderByAsc(SysRolePermission::getPermission));

        // 权限码全集：以现有授权为准（这几个码就是业务动作的枚举）
        TreeSet<String> perms = new TreeSet<>();
        // 角色 → (权限码 → 数据范围)
        Map<String, Map<String, String>> granted = new LinkedHashMap<>();
        for (String role : ROLES) {
            granted.put(role, new LinkedHashMap<>());
        }
        for (SysRolePermission row : rows) {
            perms.add(row.getPermission());
            granted.computeIfAbsent(row.getRole(), k -> new LinkedHashMap<>())
                    .put(row.getPermission(), row.getDataScope());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", ROLES);
        result.put("scopes", SCOPES);
        result.put("perms", new ArrayList<>(perms));
        result.put("granted", granted);
        return result;
    }

    /**
     * 保存某角色的授权：整体覆盖（先删该角色全部行，再按提交的清单插入）。
     *
     * <p>用「整体覆盖」而不是逐条 diff：一次保存只有一次 delete，
     * 前端提交的就是最终态，语义最简单，也不会残留取消勾选的行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(String role, List<RolePermItem> items) {
        String normalized = normalizeRole(role);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRole, normalized));
        if (items == null) {
            return;
        }
        for (RolePermItem item : items) {
            if (item == null || !StringUtils.hasText(item.permission())) {
                continue;
            }
            SysRolePermission row = new SysRolePermission();
            row.setRole(normalized);
            row.setPermission(item.permission().trim());
            row.setDataScope(StringUtils.hasText(item.dataScope()) ? item.dataScope().trim() : "SELF");
            rolePermissionMapper.insert(row);
        }
    }

    /** 成员列表：只取页面需要的字段，不带密码 */
    public List<SysUser> users() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname, SysUser::getRole,
                        SysUser::getPhone, SysUser::getStatus, SysUser::getLastLoginTime)
                .orderByAsc(SysUser::getRole)
                .orderByAsc(SysUser::getUsername));
    }

    /** 给某个成员分配主体（改 sys_user.role） */
    public void updateUserRole(String userId, String role) {
        String normalized = normalizeRole(role);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        user.setRole(normalized);
        sysUserMapper.updateById(user);
    }

    private String normalizeRole(String role) {
        RoleEnum matched = RoleEnum.of(role);
        if (matched == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "未知角色：" + role);
        }
        return matched.getCode();
    }
}
