package com.campus.delivery.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.admin.dto.RolePermItem;
import com.campus.delivery.modules.admin.entity.SysRole;
import com.campus.delivery.modules.admin.entity.SysRolePermission;
import com.campus.delivery.modules.admin.mapper.SysRolePermissionMapper;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.modules.user.mapper.SysUserMapper;
import com.campus.delivery.security.UserContext;
import com.campus.delivery.common.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 权限管理（四个主体 + 角色授权 + 成员管理）。主责：成员4。
 *
 * <p>数据全部落在两张已有表上，不新增结构：
 * <ul>
 *   <li>{@code sys_role_permission}：角色 → 权限码 → 数据范围；</li>
 *   <li>{@code sys_user}：成员本身及其主体。</li>
 * </ul>
 *
 * <p>角色权限集合缓存在 Redis 的 {@code auth:perm:{role}}，TTL 30 分钟；
 * 管理端一保存就删缓存，下一次请求自动回源，保证「改完即生效」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 数据范围枚举值 */
    private static final List<String> SCOPES = List.of("ALL", "SHOP", "SELF");

    /**
     * 权限码全集：矩阵的列固定为这份清单。
     *
     * <p>不能只从 sys_role_permission 里取现有值——某个码一旦被所有角色撤销，
     * 它就会从矩阵里消失，管理员再也勾不回来。库里出现的新码仍然会并入（见 matrix()）。
     */
    private static final List<String> ALL_PERMS = List.of(
            "admin:all",
            "ai:business", "ai:use",
            "delivery:transit",
            "dish:read", "dish:write",
            "order:read", "order:transit", "order:write",
            "review:reply", "review:write",
            "rider:grab", "rider:hall",
            "shop:read");

    /** 管理员通配码，拥有它等于拥有一切权限（与前端 PERM_WILDCARD 一致） */
    public static final String WILDCARD = "admin:all";

    /** 内置管理员角色编码（用于「最后一个管理员」保护） */
    private static final String CODES_ADMIN = "ADMIN";

    /** 新增成员未指定密码时的默认密码 */
    public static final String DEFAULT_PASSWORD = "Campus@2026";

    private static final Duration PERM_CACHE_TTL = Duration.ofMinutes(30);

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    /** 角色字典：角色来自 sys_role 表，不再写死 */
    private final RoleService roleService;

    // ============================ 权限矩阵 ============================

    /**
     * 权限矩阵：角色 × 权限码，附带每人数据范围。
     *
     * <p>返回结构：
     * {@code { roles:[...], scopes:[...], perms:[权限码...], granted:{角色:{权限码:数据范围}} }}
     */
    public Map<String, Object> matrix() {
        // 角色来自 sys_role（数据驱动）：新增角色不需要改这里
        List<SysRole> roleRows = roleService.listAll();
        List<String> roleCodes = new ArrayList<>();
        List<Map<String, Object>> roleMeta = new ArrayList<>();
        for (SysRole role : roleRows) {
            roleCodes.add(role.getCode());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("code", role.getCode());
            meta.put("name", role.getName());
            meta.put("builtin", Integer.valueOf(1).equals(role.getBuiltin()));
            meta.put("status", role.getStatus());
            roleMeta.add(meta);
        }

        List<SysRolePermission> rows = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>()
                        .orderByAsc(SysRolePermission::getRole)
                        .orderByAsc(SysRolePermission::getPermission));

        // 权限码全集：固定清单打底，库里出现的新码并入（新码不必改代码也能显示）
        TreeSet<String> perms = new TreeSet<>(ALL_PERMS);
        // 角色 → (权限码 → 数据范围)
        Map<String, Map<String, String>> granted = new LinkedHashMap<>();
        for (String role : roleCodes) {
            granted.put(role, new LinkedHashMap<>());
        }
        for (SysRolePermission row : rows) {
            perms.add(row.getPermission());
            granted.computeIfAbsent(row.getRole(), k -> new LinkedHashMap<>())
                    .put(row.getPermission(), row.getDataScope());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", roleCodes);
        result.put("roleMeta", roleMeta);
        result.put("scopes", SCOPES);
        result.put("perms", new ArrayList<>(perms));
        result.put("granted", granted);
        return result;
    }

    /**
     * 保存某角色的授权：整体覆盖（先删该角色全部行，再按提交的清单插入）。
     *
     * <p>保存后立即清掉该角色的权限缓存，保证拦截器下一次请求就读到新授权。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(String role, List<RolePermItem> items) {
        String normalized = normalizeRole(role);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRole, normalized));
        if (items != null) {
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
        evictCache(normalized);
    }

    // ============================ 权限校验（供拦截器使用） ============================

    /**
     * 某角色拥有的权限码集合（带 Redis 缓存）。
     *
     * <p>缓存的是「角色 → 权限码」，不含数据范围：数据范围是行级过滤参数，
     * 由各业务 Service 按需查表，不进入拦截器的热路径。
     */
    public Set<String> permsOf(String role) {
        if (!StringUtils.hasText(role)) {
            return Set.of();
        }
        String key = RedisKeys.ROLE_PERMISSION + role;

        // 缓存只是加速手段：Redis 故障时降级查库，不能把登录/鉴权一起拖垮
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof Collection<?> collection && !collection.isEmpty()) {
                Set<String> hit = new LinkedHashSet<>();
                for (Object item : collection) {
                    if (item != null) {
                        hit.add(String.valueOf(item));
                    }
                }
                return hit;
            }
        } catch (Exception e) {
            log.warn("读取角色权限缓存失败，降级查库: role={}, err={}", role, e.getMessage());
        }

        List<SysRolePermission> rows = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRole, role));
        Set<String> perms = new LinkedHashSet<>();
        for (SysRolePermission row : rows) {
            perms.add(row.getPermission());
        }
        try {
            redisTemplate.opsForValue().set(key, new ArrayList<>(perms), PERM_CACHE_TTL);
        } catch (Exception e) {
            log.debug("回填角色权限缓存失败（忽略）: role={}, err={}", role, e.getMessage());
        }
        return perms;
    }

    /** 是否拥有所需权限之一（{@code admin:all} 通配） */
    public boolean hasPerm(String role, String[] required) {
        if (required == null || required.length == 0) {
            return true;
        }
        Set<String> owned = permsOf(role);
        if (owned.contains(WILDCARD)) {
            return true;
        }
        for (String code : required) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** 清掉某角色的权限缓存（管理员改了授权后调用） */
    public void evictCache(String role) {
        Boolean deleted = redisTemplate.delete(RedisKeys.ROLE_PERMISSION + role);
        log.debug("清理角色权限缓存: role={}, deleted={}", role, deleted);
    }

    // ============================ 成员管理 ============================

    /** 成员列表：只取页面需要的字段，不带密码 */
    public List<SysUser> users() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname, SysUser::getRole,
                        SysUser::getPhone, SysUser::getStatus, SysUser::getLastLoginTime)
                .orderByAsc(SysUser::getRole)
                .orderByAsc(SysUser::getUsername));
    }

    /** 给某个成员分配主体（改 sys_user.role，下一次请求即按新主体校验） */
    public void updateUserRole(String userId, String role) {
        String normalized = normalizeRole(role);
        SysUser user = requireUser(userId);
        user.setRole(normalized);
        sysUserMapper.updateById(user);
    }

    /** 新增成员：账号唯一，密码缺省用 {@link #DEFAULT_PASSWORD} */
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(String username, String nickname, String phone, String role, String rawPassword) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "账号不能为空");
        }
        String normalized = normalizeRole(role);
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "账号已存在：" + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim()
                : role_label(normalized) + username.trim());
        user.setPhone(StringUtils.hasText(phone) ? phone.trim() : username.trim());
        user.setRole(normalized);
        user.setStatus(1);
        user.setPassword(PasswordGenerator.encode(
                StringUtils.hasText(rawPassword) ? rawPassword : DEFAULT_PASSWORD));
        sysUserMapper.insert(user);
        log.info("新增成员: username={}, role={}", user.getUsername(), normalized);
        return user;
    }

    /** 启用 / 禁用成员（0 停用 1 启用） */
    public void updateUserStatus(String userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "status 只能是 0 或 1");
        }
        SysUser user = requireUser(userId);
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    /**
     * 删除成员（逻辑删除，deleted=1）。
     *
     * <p>两条保护：不能删除自己；不能删除最后一个启用的管理员。
     * 删除后该账号不能再登录，其角色下的用户数随之减少，角色才能被删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(String userId) {
        SysUser user = requireUser(userId);
        if (user.getId().equals(UserContext.getUserIdOrNull())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能删除当前登录的账号");
        }
        if (CODES_ADMIN.equalsIgnoreCase(user.getRole())) {
            Long enabledAdmins = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, CODES_ADMIN)
                    .eq(SysUser::getStatus, 1));
            if (enabledAdmins == null || enabledAdmins <= 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "系统至少要保留一个启用的管理员");
            }
        }
        // SysUser.deleted 上有 @TableLogic，deleteById 实际是 UPDATE deleted = 1
        sysUserMapper.deleteById(userId);
        log.warn("删除成员（逻辑删除）: userId={}, username={}, role={}",
                user.getId(), user.getUsername(), user.getRole());
    }

    /** 重置成员密码 */
    public void resetPassword(String userId, String rawPassword) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 6) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "密码至少 6 位");
        }
        SysUser user = requireUser(userId);
        user.setPassword(PasswordGenerator.encode(rawPassword));
        sysUserMapper.updateById(user);
    }

    private SysUser requireUser(String userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return user;
    }

    /** 规范化角色编码：必须是 sys_role 中存在的角色 */
    private String normalizeRole(String role) {
        return roleService.require(role).getCode();
    }

    /** 角色中文名，用于默认昵称 */
    private String role_label(String role) {
        return roleService.displayName(role);
    }
}
