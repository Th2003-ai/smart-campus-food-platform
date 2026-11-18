package com.campus.delivery.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.admin.entity.SysRole;
import com.campus.delivery.modules.admin.entity.SysRolePermission;
import com.campus.delivery.modules.admin.mapper.SysRoleMapper;
import com.campus.delivery.modules.admin.mapper.SysRolePermissionMapper;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 角色服务（RBAC 角色字典，数据驱动）。
 *
 * <p>角色不再由 Java 枚举定义，而是 {@code sys_role} 表里的行：
 * <ul>
 *   <li>新增角色：管理端提交编码/名称/排序/是否可注册，即可得到一个新角色；</li>
 *   <li>授权：新角色在「角色权限」页勾权限码（写 {@code sys_role_permission}）；</li>
 *   <li>分配：管理员在用户管理里把用户改成该角色；</li>
 *   <li>停用：{@code status=0} 后不能再登录，已签发的 token 到期即失效。</li>
 * </ul>
 *
 * <p>列表缓存在 Redis {@code cache:role:all}（10 分钟），任何写操作都会主动失效；
 * Redis 故障时自动回源数据库，不影响登录与鉴权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    /** 角色不存在 */
    private static final int CODE_ROLE_NOT_FOUND = 10030;
    /** 角色已停用 */
    private static final int CODE_ROLE_DISABLED = 10031;
    /** 内置角色不可删除 */
    private static final int CODE_ROLE_BUILTIN = 10032;
    /** 角色仍被用户使用 */
    private static final int CODE_ROLE_IN_USE = 10033;
    /** 角色编码重复 */
    private static final int CODE_ROLE_DUPLICATED = 10034;
    /** 角色不支持注册 */
    private static final int CODE_ROLE_NOT_REGISTRABLE = 10035;
    /** 角色编码格式错误 */
    private static final int CODE_ROLE_CODE_INVALID = 10036;

    /** 角色编码格式：大写字母开头，允许大写字母/数字/下划线，2~32 位 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    /** 允许挂靠的四个端（与 @RequiresRole 的取值一致） */
    private static final java.util.Set<String> PORTALS =
            java.util.Set.of("STUDENT", "MERCHANT", "RIDER", "ADMIN");

    /** 门户配置非法 */
    private static final int CODE_PORTAL_INVALID = 10037;

    private static final String CACHE_KEY = RedisKeys.ROLE_LIST;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // ============================ 查询 ============================

    /** 全部角色（按 sort、code 排序） */
    public List<SysRole> listAll() {
        List<SysRole> cached = readCache();
        if (cached != null) {
            return cached;
        }
        List<SysRole> rows = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getCode));
        writeCache(rows);
        return rows;
    }

    /** 启用中的角色 */
    public List<SysRole> listEnabled() {
        return listAll().stream().filter(role -> Integer.valueOf(1).equals(role.getStatus())).toList();
    }

    /** 允许自助注册的角色（注册页下拉用） */
    public List<SysRole> listRegisterable() {
        return listEnabled().stream()
                .filter(role -> Integer.valueOf(1).equals(role.getAllowRegister()))
                .toList();
    }

    /** 按编码取角色，不存在抛错 */
    public SysRole require(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(CODE_ROLE_NOT_FOUND, "角色编码不能为空");
        }
        SysRole role = findByCode(code.trim());
        if (role == null) {
            throw new BusinessException(CODE_ROLE_NOT_FOUND, "角色不存在：" + code);
        }
        return role;
    }

    /** 按编码取启用中的角色（登录、分配角色时用） */
    public SysRole requireEnabled(String code) {
        SysRole role = require(code);
        if (!Integer.valueOf(1).equals(role.getStatus())) {
            throw new BusinessException(CODE_ROLE_DISABLED, "角色「" + role.getName() + "」已停用");
        }
        return role;
    }

    /** 按编码取可注册角色（注册接口用） */
    public SysRole requireRegisterable(String code) {
        SysRole role = requireEnabled(code);
        if (!Integer.valueOf(1).equals(role.getAllowRegister())) {
            throw new BusinessException(CODE_ROLE_NOT_REGISTRABLE,
                    "角色「" + role.getName() + "」不开放注册");
        }
        return role;
    }

    /** 角色是否存在且启用（不抛错的版本，供登录校验用） */
    public boolean isEnabled(String code) {
        return StringUtils.hasText(code) && findByCode(code.trim()) != null
                && Integer.valueOf(1).equals(findByCode(code.trim()).getStatus());
    }

    /** 角色名称，取不到时回退编码（日志/展示用） */
    public String displayName(String code) {
        SysRole role = findByCode(code);
        return role == null ? code : role.getName();
    }

    // ============================ 写入 ============================

    /** 新增角色 */
    @Transactional(rollbackFor = Exception.class)
    public SysRole create(SysRole form) {
        String code = normalizeCode(form.getCode());
        if (findByCode(code) != null) {
            throw new BusinessException(CODE_ROLE_DUPLICATED, "角色编码已存在：" + code);
        }
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(requireName(form.getName()));
        role.setDescription(trimToNull(form.getDescription()));
        role.setSort(form.getSort() == null ? 0 : form.getSort());
        role.setBuiltin(0);
        role.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        role.setAllowRegister(form.getAllowRegister() == null ? 0 : form.getAllowRegister());
        role.setPortal(normalizePortal(form.getPortal()));
        role.setHomePath(normalizeHomePath(form.getHomePath()));
        roleMapper.insert(role);
        evictCache();
        log.info("新增角色: code={}, name={}", role.getCode(), role.getName());
        return role;
    }

    /**
     * 修改角色。
     *
     * <p>内置角色允许改名/改排序/停用，但不允许删除（见 {@link #delete(String)}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysRole update(String code, SysRole form) {
        SysRole role = require(code);
        if (StringUtils.hasText(form.getName())) {
            role.setName(requireName(form.getName()));
        }
        role.setDescription(trimToNull(form.getDescription()));
        if (form.getSort() != null) {
            role.setSort(form.getSort());
        }
        if (form.getStatus() != null) {
            role.setStatus(form.getStatus());
        }
        if (form.getAllowRegister() != null) {
            role.setAllowRegister(form.getAllowRegister());
        }
        // portal / homePath 允许显式清空（传空串即清空）
        if (form.getPortal() != null) {
            role.setPortal(normalizePortal(form.getPortal()));
        }
        if (form.getHomePath() != null) {
            role.setHomePath(normalizeHomePath(form.getHomePath()));
        }
        roleMapper.updateById(role);
        evictCache();
        evictPermissionCache(role.getCode());
        log.info("修改角色: code={}, name={}, status={}", role.getCode(), role.getName(), role.getStatus());
        return role;
    }

    /**
     * 删除角色。
     *
     * <p>两条保护：内置角色不可删；仍有用户使用该角色时不可删。
     * 删除时同时清理该角色的授权行与权限缓存。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {
        SysRole role = require(code);
        if (Integer.valueOf(1).equals(role.getBuiltin())) {
            throw new BusinessException(CODE_ROLE_BUILTIN,
                    "「" + role.getName() + "」是内置角色（四端入口使用），不能删除");
        }
        Long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, role.getCode()));
        if (userCount != null && userCount > 0) {
            throw new BusinessException(CODE_ROLE_IN_USE,
                    "还有 " + userCount + " 个用户（含已停用）属于该角色，"
                            + "请先在「角色权限」页的用户管理里删除或改角色，再删除角色");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRole, role.getCode()));
        roleMapper.deleteById(role.getCode());
        evictCache();
        evictPermissionCache(role.getCode());
        log.info("删除角色: code={}", role.getCode());
    }

    /** 某角色下的用户数（管理端列表展示用） */
    public long userCount(String roleCode) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, roleCode));
        return count == null ? 0 : count;
    }

    /** 角色编码列表（兼容旧逻辑：需要 List<String> 的地方） */
    public List<String> codes() {
        List<String> codes = new ArrayList<>();
        listAll().forEach(role -> codes.add(role.getCode()));
        return codes;
    }

    // ============================ 内部工具 ============================

    private SysRole findByCode(String code) {
        return roleMapper.selectById(code.toUpperCase());
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(CODE_ROLE_CODE_INVALID, "角色编码不能为空");
        }
        String normalized = code.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(CODE_ROLE_CODE_INVALID,
                    "角色编码需以大写字母开头，只能包含大写字母/数字/下划线，长度 2~32");
        }
        return normalized;
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(CODE_ROLE_CODE_INVALID, "角色名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 50) {
            throw new BusinessException(CODE_ROLE_CODE_INVALID, "角色名称不能超过 50 个字符");
        }
        return trimmed;
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    /**
     * 规范化「所属端」。
     *
     * <p>为空表示这个角色不挂靠任何端（登录后只能看到「暂无工作台」提示页）；
     * 否则必须是 STUDENT / MERCHANT / RIDER / ADMIN 之一。
     */
    private String normalizePortal(String portal) {
        if (!StringUtils.hasText(portal)) {
            return null;
        }
        String normalized = portal.trim().toUpperCase();
        if (!PORTALS.contains(normalized)) {
            throw new BusinessException(CODE_PORTAL_INVALID,
                    "所属端只能是 STUDENT / MERCHANT / RIDER / ADMIN");
        }
        return normalized;
    }

    /** 首页路径：必须以 / 开头，长度不超过 200；为空表示不配置 */
    private String normalizeHomePath(String homePath) {
        if (!StringUtils.hasText(homePath)) {
            return null;
        }
        String path = homePath.trim();
        if (!path.startsWith("/")) {
            throw new BusinessException(CODE_PORTAL_INVALID, "首页路径必须以 / 开头，如 /admin/dashboard");
        }
        if (path.length() > 200) {
            throw new BusinessException(CODE_PORTAL_INVALID, "首页路径不能超过 200 个字符");
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private List<SysRole> readCache() {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof SysRole) {
                return (List<SysRole>) list;
            }
        } catch (Exception e) {
            log.debug("读取角色缓存失败，回源数据库: {}", e.getMessage());
        }
        return null;
    }

    private void writeCache(List<SysRole> roles) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, new ArrayList<>(roles), CACHE_TTL);
        } catch (Exception e) {
            log.debug("写角色缓存失败（忽略）: {}", e.getMessage());
        }
    }

    /** 清角色列表缓存 */
    public void evictCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception e) {
            log.debug("清角色缓存失败（忽略）: {}", e.getMessage());
        }
    }

    /** 清某角色的权限码缓存（角色改名/停用/删除后，避免拦截器读到旧授权） */
    private void evictPermissionCache(String roleCode) {
        try {
            redisTemplate.delete(RedisKeys.ROLE_PERMISSION + roleCode);
        } catch (Exception e) {
            log.debug("清角色权限缓存失败（忽略）: {}", e.getMessage());
        }
    }
}
