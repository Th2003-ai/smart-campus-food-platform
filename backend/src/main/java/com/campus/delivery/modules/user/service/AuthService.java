package com.campus.delivery.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.common.util.PasswordGenerator;
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
import com.campus.delivery.security.RoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final DietProfileMapper dietProfileMapper;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 登录 */
    public LoginResponse login(LoginRequest request) {
        SysUser user = findByUsername(request.getUsername());
        if (user == null || !PasswordGenerator.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(CODE_LOGIN_FAILED, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(CODE_ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        if (StringUtils.hasText(request.getRole())
                && !request.getRole().equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(CODE_ROLE_MISMATCH, "账号角色与当前登录入口不匹配");
        }

        // 记录最近登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole(), user.getNickname());
        String token = jwtUtils.generateToken(loginUser);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setAvatar(user.getAvatar());
        response.setExpiresIn(jwtUtils.getExpireMinutes() * 60);
        log.info("用户登录成功: userId={}, role={}", user.getId(), user.getRole());
        return response;
    }

    /** 学生注册：同时创建账号、学生档案与空白饮食档案 */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        if (findByUsername(request.getStudentNo()) != null) {
            throw new BusinessException(CODE_USERNAME_EXISTS, "该学号已注册");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getStudentNo());
        user.setPassword(PasswordGenerator.encode(request.getPassword()));
        user.setRole(RoleEnum.STUDENT.getCode());
        user.setNickname(StringUtils.hasText(request.getRealName()) ? request.getRealName() : request.getStudentNo());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        sysUserMapper.insert(user);

        Student student = new Student();
        student.setUserId(user.getId());
        student.setStudentNo(request.getStudentNo());
        student.setRealName(request.getRealName());
        student.setCollege(request.getCollege());
        student.setGrade(request.getGrade());
        studentMapper.insert(student);

        // 初始化空白饮食档案，保证后续推荐与饮食分析有画像载体
        DietProfile profile = new DietProfile();
        profile.setUserId(user.getId());
        profile.setDietGoal(0);
        dietProfileMapper.insert(profile);

        log.info("学生注册成功: userId={}, studentNo={}", user.getId(), request.getStudentNo());
    }

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
