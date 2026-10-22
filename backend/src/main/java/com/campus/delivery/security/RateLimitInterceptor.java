package com.campus.delivery.security;

import com.campus.delivery.common.annotation.LimitBy;
import com.campus.delivery.common.annotation.RateLimit;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * 接口限流拦截器（Redis 固定窗口计数）。
 *
 * <p>为什么放在拦截器而不是每个 Service 里手写：
 * 限流是横切关注点，用 {@link RateLimit} 注解声明即可，业务代码零侵入。
 *
 * <p>计数模型：固定窗口。
 * <pre>
 *   key = rate:{name}:{subject}:{窗口序号}
 *   窗口序号 = 当前秒 / 窗口长度
 * </pre>
 * 由于窗口序号进了 key，每个窗口都是独立的 key，
 * 不需要「第一次请求时补设过期时间」的额外逻辑，也就不会出现
 * 「INCR 成功但 EXPIRE 失败 → key 永不过期 → 用户被永久限流」的经典问题。
 *
 * <p>为什么仍要用 Lua：{@code INCR + EXPIRE} 必须原子。虽然分窗口后 EXPIRE 只在
 * 计数为 1 时设置，但两条命令之间仍有窗口期；用脚本包起来最稳妥。
 *
 * <p>Redis 异常时放行（fail-open）：限流是保护措施，不能因为缓存故障把业务全拒掉，
 * 与 {@code AuthService#isTokenBlacklisted} 的降级策略保持一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    /**
     * KEYS[1] 计数键；ARGV[1] 窗口秒数。
     * 首次计数时设置过期，返回当前窗口累计次数。
     */
    private static final String LUA_INCR_AND_EXPIRE = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(LUA_INCR_AND_EXPIRE, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 预检请求不计入限流
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = resolveAnnotation(handlerMethod);
        if (rateLimit == null || !rateLimit.enabled()) {
            return true;
        }

        String subject = resolveSubject(request, rateLimit.by());
        int seconds = Math.max(1, rateLimit.seconds());
        long windowIndex = System.currentTimeMillis() / 1000L / seconds;
        String key = RedisKeys.RATE_LIMIT + rateLimit.name() + ":" + subject + ":" + windowIndex;

        Long current;
        try {
            current = stringRedisTemplate.execute(
                    SCRIPT, Collections.singletonList(key), String.valueOf(seconds + 1));
        } catch (Exception e) {
            // 缓存故障时放行，避免限流组件把整个站点拖垮
            log.warn("限流计数失败，本次放行: key={}, err={}", key, e.getMessage());
            return true;
        }

        if (current != null && current > rateLimit.limit()) {
            log.warn("触发限流: name={}, subject={}, count={}, limit={}/{}s",
                    rateLimit.name(), subject, current, rateLimit.limit(), seconds);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(),
                    rateLimit.message() + "（" + seconds + " 秒内最多 " + rateLimit.limit() + " 次）");
        }
        return true;
    }

    /** 方法上的注解优先，其次取类上的 */
    private RateLimit resolveAnnotation(HandlerMethod handlerMethod) {
        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation != null) {
            return annotation;
        }
        return handlerMethod.getBeanType().getAnnotation(RateLimit.class);
    }

    /**
     * 解析计数主体：
     * 按用户计时取登录用户ID（拦截器顺序保证 {@link UserContext} 已就绪），
     * 未登录或按 IP 计时取真实客户端 IP。
     */
    private String resolveSubject(HttpServletRequest request, LimitBy by) {
        if (by == LimitBy.USER) {
            String userId = UserContext.getUserIdOrNull();
            if (StringUtils.hasText(userId)) {
                return "u:" + userId;
            }
        }
        return "ip:" + resolveClientIp(request);
    }

    /** 取真实客户端 IP（经过 Nginx 时优先 X-Forwarded-For 的第一段） */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
