package com.campus.delivery.modules.user.controller;

import com.campus.delivery.common.annotation.LimitBy;
import com.campus.delivery.common.annotation.RateLimit;
import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.admin.entity.SysRole;
import com.campus.delivery.modules.admin.service.RoleService;
import com.campus.delivery.modules.user.dto.LoginRequest;
import com.campus.delivery.modules.user.dto.LoginResponse;
import com.campus.delivery.modules.user.dto.RegisterRequest;
import com.campus.delivery.modules.user.service.AuthService;
import com.campus.delivery.security.RateLimitInterceptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证接口（公开）。主责：成员4。
 */
@Tag(name = "01-认证", description = "登录、注册、退出")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final RoleService roleService;

    @Operation(summary = "角色列表（公开）",
            description = "scope=register（默认）只返回可注册角色；scope=login 返回全部启用角色，供登录页渲染入口")
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles(
            @RequestParam(defaultValue = "register") String scope) {
        List<SysRole> source = "login".equalsIgnoreCase(scope)
                ? roleService.listEnabled()
                : roleService.listRegisterable();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysRole role : source) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", role.getCode());
            item.put("name", role.getName());
            rows.add(item);
        }
        return Result.ok(rows);
    }

    @Operation(summary = "登录", description = "学生/商户/骑手/管理员统一入口")
    @RateLimit(name = "auth:login", limit = 10, seconds = 60, by = LimitBy.IP,
            message = "登录尝试过于频繁")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request);
        // 登录成功即清零本窗口计数：正常用户不该被之前的失败尝试拖累
        rateLimitInterceptor.reset(httpRequest, "auth:login", LimitBy.IP, 60);
        return Result.ok(response);
    }

    @Operation(summary = "学生注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return Result.ok();
    }
}
