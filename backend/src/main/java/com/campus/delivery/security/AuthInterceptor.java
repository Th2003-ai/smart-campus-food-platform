package com.campus.delivery.security;

import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.modules.admin.service.GroupPermissionService;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

/**
 * 统一认证与授权拦截器（对应需求文档 5.6.1 难点一的解决思路）。
 *
 * <p>职责：
 * <ol>
 *   <li>解析请求头中的 JWT，把登录人写入 {@link UserContext}；</li>
 *   <li>默认要求登录（公开接口在 {@code WebMvcConfig} 的放行白名单中排除）；</li>
 *   <li>校验 {@link RequiresRole} 注解声明的角色，实现接口级权限控制。</li>
 * </ol>
 *
 * <p>数据行级隔离（商户只能看本店数据、骑手只能看自己的订单）由各业务 Service
 * 基于 {@code UserContext} 拼接查询条件实现，见 docs/02-四人分工与模块归属.md。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final AuthService authService;
    private final GroupPermissionService groupPermissionService;

    @Value("${campus.jwt.header:Authorization}")
    private String tokenHeader;

    @Value("${campus.jwt.prefix:Bearer }")
    private String tokenPrefix;

    /**
     * 「仅 GET 公开」的路径。
     *
     * <p>为什么需要这个：{@code WebMvcConfig#PUBLIC_PATHS} 用的是
     * {@code excludePathPatterns}，只能按路径排除、无法区分 HTTP 方法。
     * 像 {@code /reviews} 这种「列表公开、提交要登录」的接口，
     * 如果整体放进白名单，POST 也会跳过鉴权，控制器里的
     * {@code UserContext.getUserId()} 就会抛 401（学生提交评价一直失败的原因）。
     *
     * <p>所以这类路径不进白名单，改为在这里按方法放行。
     */
    private static final Set<String> PUBLIC_GET_PATHS = Set.of("/reviews");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 非 Controller 方法（静态资源等）不校验
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 只读接口（GET）对这批路径免登录
        if (HttpMethod.GET.matches(request.getMethod())
                && PUBLIC_GET_PATHS.contains(request.getRequestURI().replaceFirst(
                        request.getContextPath(), ""))) {
            return true;
        }

        LoginUser loginUser = resolveLoginUser(request);
        if (loginUser != null) {
            UserContext.set(loginUser);
        }

        // 先按「端」校验角色（@RequiresRole），再按「模块-方法」校验用户组权限
        RequiresRole requiresRole = resolveRequiresRole(handlerMethod);
        if (requiresRole != null) {
            if (loginUser == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED);
            }
            // 以「端」为准：新角色挂靠到某个端后即可通过该端的角色校验
            String portal = loginUser.getPortal();
            String currentPortal = (portal != null && !portal.isBlank()) ? portal : loginUser.getRole();
            boolean allowed = Arrays.stream(requiresRole.value())
                    .anyMatch(code -> code.equalsIgnoreCase(currentPortal));
            if (!allowed) {
                log.warn("越权访问被拒绝: uri={}, role={}, need={}",
                        request.getRequestURI(), loginUser.getRole(), Arrays.toString(requiresRole.value()));
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        } else if (loginUser == null) {
            // 未标注注解：只要求登录
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 用户组权限（禅道模式）：模块.方法，默认拒绝，ADMIN 绕过
        checkEndpointPriv(handlerMethod, loginUser, request);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        // 必须清理，避免线程复用导致的身份串号
        UserContext.clear();
    }

    private LoginUser resolveLoginUser(HttpServletRequest request) {
        String token = request.getHeader(tokenHeader);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (StringUtils.hasText(tokenPrefix) && token.startsWith(tokenPrefix.trim())) {
            token = token.substring(tokenPrefix.trim().length()).trim();
        }
        // 已退出登录的 Token 立即失效（黑名单存于 Redis，TTL 与 Token 剩余有效期一致）
        if (authService.isTokenBlacklisted(token)) {
            log.debug("Token 已在黑名单中，拒绝访问: uri={}", request.getRequestURI());
            return null;
        }
        return jwtUtils.parseToken(token);
    }

    private RequiresRole resolveRequiresRole(HandlerMethod handlerMethod) {
        RequiresRole annotation = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (annotation != null) {
            return annotation;
        }
        return handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
    }

    /**
     * 用户组权限校验（禅道模式）。
     *
     * <p>权限项 = {@code 模块.方法}：模块取控制器类名去掉 Controller 后缀（小写），
     * 方法取 handler 的 Java 方法名。用户可属多个组，权限取并集；
     * <b>默认拒绝</b>——组里没勾这个接口就 403，ADMIN 直接绕过。
     *
     * <p>权限集合缓存在 Redis（{@code auth:gpriv:{userId}}，30 分钟），
     * 管理端保存时主动失效，所以关掉一个接口下一个请求就生效，不用重启。
     *
     * <p>原来的 {@code @RequiresPerm} 权限码不再参与判定，只作为文档标注保留。
     */
    private void checkEndpointPriv(HandlerMethod handlerMethod, LoginUser loginUser,
                                   HttpServletRequest request) {
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (groupPermissionService.isAdmin(loginUser.getRole())) {
            return;
        }
        String module = EndpointRegistry.moduleOf(handlerMethod);
        String method = handlerMethod.getMethod().getName();
        if (!groupPermissionService.hasPriv(loginUser.getUserId(), loginUser.getRole(), module, method)) {
            log.warn("用户组权限不足: uri={}, role={}, need={}.{}",
                    request.getRequestURI(), loginUser.getRole(), module, method);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
