package com.campus.delivery.modules.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.admin.entity.SysGroup;
import com.campus.delivery.modules.admin.entity.SysGroupPriv;
import com.campus.delivery.modules.admin.entity.SysGroupUser;
import com.campus.delivery.modules.admin.mapper.SysGroupMapper;
import com.campus.delivery.modules.admin.mapper.SysGroupPrivMapper;
import com.campus.delivery.modules.admin.mapper.SysGroupUserMapper;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ZenTao-style privilege model.
 *
 * <ul>
 *   <li>user -> groups (many to many), privileges = union of the groups;</li>
 *   <li>privilege item = module.method (controller + handler method);</li>
 *   <li>default deny: no row means forbidden;</li>
 *   <li>ADMIN bypasses everything (equivalent of zt_user.admin in ZenTao);</li>
 *   <li>{@code data_scope} is stored per item (ALL/SHOP/SELF) and only used by the
 *       admin UI for now - business services keep their own row level filters.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPermissionService {

    private static final String CACHE_KEY = "auth:gpriv:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String ADMIN_ROLE = "ADMIN";

    private final SysGroupMapper groupMapper;
    private final SysGroupUserMapper groupUserMapper;
    private final SysGroupPrivMapper groupPrivMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;

    // ------------------------------------------------------------ effective privileges

    public boolean isAdmin(String role) {
        return role != null && ADMIN_ROLE.equalsIgnoreCase(role);
    }

    public List<String> groupIdsOf(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<SysGroupUser> rows = groupUserMapper.selectList(Wrappers.<SysGroupUser>lambdaQuery()
                .eq(SysGroupUser::getUserId, userId));
        List<String> ids = new ArrayList<>();
        rows.forEach(r -> ids.add(r.getGroupId()));
        return ids;
    }

    /** module.method set of one user (union of all his groups), cached in redis */
    public Set<String> privKeysOf(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Set.of();
        }
        String key = CACHE_KEY + userId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (cached.isEmpty()) {
                    return Set.of();
                }
                return new LinkedHashSet<>(List.of(cached.split(",")));
            }
        } catch (Exception e) {
            log.warn("read group privilege cache failed: userId={}, err={}", userId, e.getMessage());
        }
        List<String> groupIds = groupIdsOf(userId);
        Set<String> keys = new LinkedHashSet<>();
        if (!groupIds.isEmpty()) {
            groupPrivMapper.selectList(Wrappers.<SysGroupPriv>lambdaQuery()
                            .in(SysGroupPriv::getGroupId, groupIds))
                    .forEach(p -> keys.add(p.getModule() + "." + p.getMethod()));
        }
        try {
            redisTemplate.opsForValue().set(key, String.join(",", keys), CACHE_TTL);
        } catch (Exception e) {
            log.warn("write group privilege cache failed: userId={}, err={}", userId, e.getMessage());
        }
        return keys;
    }

    public boolean hasPriv(String userId, String role, String module, String method) {
        if (isAdmin(role)) {
            return true;
        }
        return privKeysOf(userId).contains(module + "." + method);
    }

    public void evictUser(String userId) {
        if (StringUtils.hasText(userId)) {
            try {
                redisTemplate.delete(CACHE_KEY + userId);
            } catch (Exception e) {
                log.warn("evict group privilege cache failed: userId={}, err={}", userId, e.getMessage());
            }
        }
    }

    private void evictGroup(String groupId) {
        List<SysGroupUser> rows = groupUserMapper.selectList(Wrappers.<SysGroupUser>lambdaQuery()
                .eq(SysGroupUser::getGroupId, groupId));
        rows.forEach(r -> evictUser(r.getUserId()));
    }

    // ------------------------------------------------------------ admin side

    public List<SysGroup> groups() {
        return groupMapper.selectList(Wrappers.<SysGroup>lambdaQuery()
                .orderByDesc(SysGroup::getBuiltin)
                .orderByAsc(SysGroup::getCode));
    }

    public Map<String, Object> matrix() {
        List<SysGroup> groups = groups();
        List<SysGroupPriv> privs = groupPrivMapper.selectList(Wrappers.<SysGroupPriv>lambdaQuery());
        Map<String, List<String>> granted = new LinkedHashMap<>();
        Map<String, Map<String, String>> scopes = new LinkedHashMap<>();
        for (SysGroup g : groups) {
            granted.put(g.getId(), new ArrayList<>());
            scopes.put(g.getId(), new LinkedHashMap<>());
        }
        for (SysGroupPriv p : privs) {
            if (!granted.containsKey(p.getGroupId())) {
                continue;
            }
            granted.get(p.getGroupId()).add(p.getModule() + "." + p.getMethod());
            scopes.get(p.getGroupId()).put(p.getModule() + "." + p.getMethod(),
                    p.getDataScope() == null ? "ALL" : p.getDataScope());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> groupViews = new ArrayList<>();
        for (SysGroup g : groups) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", g.getId());
            v.put("code", g.getCode());
            v.put("name", g.getName());
            v.put("builtin", g.getBuiltin());
            v.put("remark", g.getRemark());
            groupViews.add(v);
        }
        out.put("groups", groupViews);
        out.put("granted", granted);
        out.put("scopes", scopes);
        out.put("scopesAllowed", List.of("ALL", "SHOP", "SELF"));
        return out;
    }

    /** replace the whole privilege set of one group (same semantics as ZenTao save) */
    @Transactional
    public void saveGroupPriv(String groupId, List<Map<String, String>> items) {
        SysGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        groupPrivMapper.delete(Wrappers.<SysGroupPriv>lambdaQuery().eq(SysGroupPriv::getGroupId, groupId));
        if (items != null) {
            for (Map<String, String> item : items) {
                String module = item.get("module");
                String method = item.get("method");
                if (!StringUtils.hasText(module) || !StringUtils.hasText(method)) {
                    continue;
                }
                SysGroupPriv p = new SysGroupPriv();
                p.setGroupId(groupId);
                p.setModule(module.trim());
                p.setMethod(method.trim());
                String scope = item.get("dataScope");
                p.setDataScope(StringUtils.hasText(scope) ? scope.trim().toUpperCase() : "ALL");
                groupPrivMapper.insert(p);
            }
        }
        evictGroup(groupId);
        log.info("group privilege saved: group={}, items={}", groupId, items == null ? 0 : items.size());
    }

    public SysGroup createGroup(String name, String code, String remark) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        String finalCode = StringUtils.hasText(code) ? code.trim().toUpperCase() : "G" + System.currentTimeMillis();
        Long exists = groupMapper.selectCount(Wrappers.<SysGroup>lambdaQuery().eq(SysGroup::getCode, finalCode));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "组编码已存在");
        }
        SysGroup g = new SysGroup();
        g.setId("G" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        g.setCode(finalCode);
        g.setName(name.trim());
        g.setBuiltin(0);
        g.setRemark(remark);
        groupMapper.insert(g);
        return g;
    }

    @Transactional
    public void deleteGroup(String groupId) {
        SysGroup g = groupMapper.selectById(groupId);
        if (g == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (g.getBuiltin() != null && g.getBuiltin() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "内置用户组不允许删除");
        }
        groupPrivMapper.delete(Wrappers.<SysGroupPriv>lambdaQuery().eq(SysGroupPriv::getGroupId, groupId));
        groupUserMapper.delete(Wrappers.<SysGroupUser>lambdaQuery().eq(SysGroupUser::getGroupId, groupId));
        groupMapper.deleteById(groupId);
    }

    /** users with their groups, for the member tab */
    public List<Map<String, Object>> users() {
        List<SysUser> users = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .orderByAsc(SysUser::getRole));
        List<SysGroupUser> links = groupUserMapper.selectList(Wrappers.<SysGroupUser>lambdaQuery());
        Map<String, List<String>> byUser = new LinkedHashMap<>();
        for (SysGroupUser link : links) {
            byUser.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(link.getGroupId());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysUser u : users) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", u.getId());
            v.put("username", u.getUsername());
            v.put("nickname", u.getNickname());
            v.put("role", u.getRole());
            v.put("status", u.getStatus());
            v.put("groupIds", byUser.getOrDefault(u.getId(), List.of()));
            out.add(v);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("role"))));
        return out;
    }

    @Transactional
    public void setUserGroups(String userId, List<String> groupIds) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        groupUserMapper.delete(Wrappers.<SysGroupUser>lambdaQuery().eq(SysGroupUser::getUserId, userId));
        if (groupIds != null) {
            for (String groupId : groupIds) {
                if (!StringUtils.hasText(groupId) || groupMapper.selectById(groupId) == null) {
                    continue;
                }
                SysGroupUser link = new SysGroupUser();
                link.setGroupId(groupId);
                link.setUserId(userId);
                groupUserMapper.insert(link);
            }
        }
        evictUser(userId);
    }
}
