package com.campus.delivery.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.common.util.PasswordGenerator;
import com.campus.delivery.modules.admin.service.PermissionService;
import com.campus.delivery.modules.delivery.service.DeliveryService;
import com.campus.delivery.modules.shop.service.ShopService;
import com.campus.delivery.modules.user.dto.LoginRequest;
import com.campus.delivery.modules.user.dto.LoginResponse;
import com.campus.delivery.modules.user.dto.RegisterRequest;
import com.campus.delivery.modules.user.entity.DietProfile;
import com.campus.delivery.modules.user.entity.Student;
import com.campus.delivery.modules.user.entity.SysUser;
import com.campus.delivery.modules.user.mapper.DietProfileMapper;
import com.campus.delivery.modules.user.mapper.StudentMapper;
import com.campus.delivery.modules.user.mapper.SysUserMapper;
import com.campus.delivery.security.JwtUtils;
import com.campus.delivery.security.LoginUser;
import com.campus.delivery.modules.admin.entity.SysRole;
import com.campus.delivery.modules.admin.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录、注册、退出。主责：成员4。
 *
 * <p>安全要点（需求文档 5.6.1）：
 * <ul>
 *   <li>密码 BCrypt 校验，账号或密码错误统一返回同一提示，避免账号枚举；</li>
 *   <li>账号被禁用不允许登录；</li>
 *   <li>登录入口与账号角色不匹配时拒绝登录。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 业务码段：1xxxx 用户与权限 */
    private static final int CODE_LOGIN_FAILED = 10001;
    private static final int CODE_ACCOUNT_DISABLED = 10002;
    private static final int CODE_ROLE_MISMATCH = 10003;
    private static final int CODE_USERNAME_EXISTS = 10004;
    /** 角色不允许注册（如 ADMIN） */
    private static final int CODE_ROLE_NOT_REGISTRABLE = 10007;
    /** 该角色必填字段缺失 */
    private static final int CODE_FIELD_REQUIRED = 10008;

    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final DietProfileMapper dietProfileMapper;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    /** 商户档案（含默认店铺）由店铺域写入 */
    private final ShopService shopService;
    /** 骑手档案由配送域写入 */
    private final DeliveryService deliveryService;
    /** 角色授权矩阵（登录时下发权限码） */
    private final PermissionService permissionService;
    /** 角色字典（角色来自 sys_role，不再是枚举） */
    private final RoleService roleService;

    /** 登录 */
    public LoginResponse login(LoginRequest request) {
        SysUser user = findByUsername(request.getUsername());
        // 账号不存在：统一提示，不暴露「这个账号存在但密码错」之外的信息
        if (user == null) {
            throw new BusinessException(CODE_LOGIN_FAILED, "账号或密码错误");
        }
        // 账号停用：在密码校验之前判断，保证停用账号无论密码对错都能看到明确提示
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("停用账号尝试登录: username={}, ip 已记录于访问日志", request.getUsername());
            throw new BusinessException(CODE_ACCOUNT_DISABLED, "该账号已被停用，请联系管理员");
        }
        if (!PasswordGenerator.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(CODE_LOGIN_FAILED, "账号或密码错误");
        }
        // 角色必须在 sys_role 中存在且启用：角色停用后不允许再登录（管理端可随时停用角色）
        SysRole role = roleService.requireEnabled(user.getRole());
        if (StringUtils.hasText(request.getRole())
                && !request.getRole().equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(CODE_ROLE_MISMATCH, "账号角色与当前登录入口不匹配");
        }

        // 记录最近登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole(),
                role.getPortal(), user.getNickname());
        String token = jwtUtils.generateToken(loginUser);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setPortal(role.getPortal());
        response.setHomePath(role.getHomePath());
        response.setAvatar(user.getAvatar());
        response.setExpiresIn(jwtUtils.getExpireMinutes() * 60);
        // 下发该角色的权限码，前端据此渲染菜单/按钮（与后端 @RequiresPerm 同一份数据）
        response.setPerms(new ArrayList<>(permissionService.permsOf(user.getRole())));
        log.info("用户登录成功: userId={}, role={}", user.getId(), user.getRole());
        return response;
    }

    /**
     * 注册：学生 / 商户 / 骑手三种角色共用入口，管理员不开放注册。
     *
     * <p>登录账号来源：
     * <ul>
     *   <li>学生 → 学号</li>
     *   <li>商户 / 骑手 → 手机号</li>
     * </ul>
     *
     * <p>商户与骑手注册后审核状态为「待审核」，可以登录但业务操作会被拦住，
     * 需管理员在后台审核通过（见 AdminAuditController）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        String roleCode = resolveRegisterRole(request.getRole());
        String username = resolveUsername(roleCode, request);

        if (findByUsername(username) != null) {
            throw new BusinessException(CODE_USERNAME_EXISTS,
                    "STUDENT".equalsIgnoreCase(roleCode) ? "该学号已注册" : "该手机号已注册");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(PasswordGenerator.encode(request.getPassword()));
        user.setRole(roleCode);
        user.setNickname(resolveNickname(roleCode, request));
        user.setPhone(trimToNull(request.getPhone()));
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 角色决定要建哪种档案；新增角色若没有专属档案，只建账号不建档案
        if ("STUDENT".equalsIgnoreCase(roleCode)) {
            createStudentProfile(user.getId(), request);
        } else if ("MERCHANT".equalsIgnoreCase(roleCode)) {
            createMerchantProfile(user.getId(), request);
        } else if ("RIDER".equalsIgnoreCase(roleCode)) {
            createRiderProfile(user.getId(), request);
        }

        log.info("注册成功: role={}, username={}, userId={}", roleCode, username, user.getId());
    }

    /** 学生档案 + 空白饮食档案 */
    private void createStudentProfile(String userId, RegisterRequest request) {
        Student student = new Student();
        student.setUserId(userId);
        student.setStudentNo(request.getStudentNo().trim());
        student.setRealName(trimToNull(request.getRealName()));
        student.setCollege(trimToNull(request.getCollege()));
        student.setGrade(trimToNull(request.getGrade()));
        studentMapper.insert(student);

        // 初始化空白饮食档案，保证后续推荐与饮食分析有画像载体
        DietProfile profile = new DietProfile();
        profile.setUserId(userId);
        profile.setDietGoal(0);
        dietProfileMapper.insert(profile);
    }

    /** 商户档案（含默认店铺），具体写入由店铺域负责 */
    private void createMerchantProfile(String userId, RegisterRequest request) {
        String merchantName = trimToNull(request.getMerchantName());
        if (!StringUtils.hasText(merchantName)) {
            throw new BusinessException(CODE_FIELD_REQUIRED, "商户注册必须填写商户名称");
        }
        String contactName = StringUtils.hasText(request.getContactName())
                ? request.getContactName().trim()
                : trimToNull(request.getRealName());
        shopService.registerMerchantProfile(userId, merchantName, contactName,
                trimToNull(request.getPhone()), trimToNull(request.getLicenseNo()));
    }

    /** 骑手档案，具体写入由配送域负责 */
    private void createRiderProfile(String userId, RegisterRequest request) {
        String realName = trimToNull(request.getRealName());
        if (!StringUtils.hasText(realName)) {
            throw new BusinessException(CODE_FIELD_REQUIRED, "骑手注册必须填写真实姓名");
        }
        deliveryService.registerRiderProfile(userId, realName,
                trimToNull(request.getStudentCardNo()), trimToNull(request.getVehicleType()));
    }

    /**
     * 校验注册角色：角色必须存在于 {@code sys_role}、处于启用状态、且 {@code allow_register=1}。
     *
     * <p>「哪些角色能自助注册」是数据（角色管理页可改），不再是代码里的枚举判断。
     */
    private String resolveRegisterRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException(CODE_FIELD_REQUIRED, "请选择注册角色");
        }
        return roleService.requireRegisterable(roleCode.trim()).getCode();
    }

    /** 学生用学号、其他角色用手机号作为登录账号 */
    private String resolveUsername(String roleCode, RegisterRequest request) {
        if ("STUDENT".equalsIgnoreCase(roleCode)) {
            String studentNo = trimToNull(request.getStudentNo());
            if (!StringUtils.hasText(studentNo)) {
                throw new BusinessException(CODE_FIELD_REQUIRED, "学生注册必须填写学号");
            }
            if (studentNo.length() > 20) {
                throw new BusinessException(CODE_FIELD_REQUIRED, "学号长度不能超过20位");
            }
            return studentNo;
        }
        return request.getPhone().trim();
    }

    /** 昵称：学生用姓名（缺省用学号）、商户用商户名、骑手用真实姓名 */
    private String resolveNickname(String roleCode, RegisterRequest request) {
        String candidate;
        if ("STUDENT".equalsIgnoreCase(roleCode)) {
            candidate = StringUtils.hasText(request.getRealName())
                    ? request.getRealName() : request.getStudentNo();
        } else if ("MERCHANT".equalsIgnoreCase(roleCode)) {
            candidate = request.getMerchantName();
        } else if ("RIDER".equalsIgnoreCase(roleCode)) {
            candidate = request.getRealName();
        } else {
            candidate = null;
        }
        return trimToNull(candidate);
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    // ------------------------------------------------------------------
    // 场景 7（PPT 第 13、14 页）：退出登录 Token 黑名单
    //   结构：String —— auth:token:blacklist:{token}
    //   过期：等于该 Token 的剩余有效期（实测约 43199 秒 ≈ 12 小时）
    //   为什么 TTL 要这么设：Token 自然过期后黑名单就没意义了，
    //           用剩余有效期可以让条目随 Token 一起消失，不需要定时清理任务
    //   拦截：AuthInterceptor 每次请求先查黑名单，命中直接 401
    // ------------------------------------------------------------------
    /**
     * 退出登录：把 Token 写入 Redis 黑名单，使其立即失效。
     *
     * <p>JWT 本身无状态、签发后无法「撤回」，所以采用「黑名单」方式：
     * 退出时把该 Token 以 {@code auth:token:blacklist:<token>} 存入 Redis，
     * TTL 设为该 Token 的剩余有效期 —— 到期后 Redis 自动清理，不会无限堆积。
     * {@code AuthInterceptor} 在每次请求时先查黑名单，命中则拒绝。
     */
    public void logout(String authorizationHeader) {
        String token = resolveToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            return;
        }
        long remaining = jwtUtils.getRemainingSeconds(token);
        if (remaining <= 0) {
            // Token 已过期或非法，无需再拉黑
            return;
        }
        redisTemplate.opsForValue().set(
                RedisKeys.TOKEN_BLACKLIST + token, System.currentTimeMillis(), remaining, TimeUnit.SECONDS);
        log.info("用户退出登录，Token 已加入黑名单，剩余有效期 {} 秒", remaining);
    }

    /**
     * 判断 Token 是否已在黑名单中（供拦截器调用）。
     *
     * <p>Redis 异常时返回 false（放行）：认证链路的可用性优先，
     * 避免因缓存故障导致全站无法登录；此时仅失去「主动退出即时生效」能力。
     */
    public boolean isTokenBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(RedisKeys.TOKEN_BLACKLIST + token);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("查询 Token 黑名单失败，降级放行: {}", e.getMessage());
            return false;
        }
    }

    /** 从 Authorization 头中剥离 Bearer 前缀 */
    private String resolveToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String token = authorizationHeader.trim();
        String prefix = "Bearer ";
        if (token.regionMatches(true, 0, prefix, 0, prefix.length())) {
            token = token.substring(prefix.length()).trim();
        }
        return token;
    }

    private SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }
}
