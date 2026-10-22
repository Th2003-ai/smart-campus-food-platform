package com.campus.delivery.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解。
 *
 * <p>标注在 Controller 方法（或类）上，由 {@code RateLimitInterceptor} 用 Redis 计数实现。
 *
 * <p>典型用法：
 * <pre>
 *   // 同一 IP 每分钟最多 5 次（登录防爆破）
 *   &#64;RateLimit(name = "login", limit = 5, seconds = 60, by = LimitBy.IP)
 *
 *   // 同一用户每分钟最多 10 次（下单防刷）
 *   &#64;RateLimit(name = "order:create", limit = 10, seconds = 60)
 * </pre>
 *
 * <p>实现要点见 {@code RateLimitInterceptor}：固定窗口计数，key 里带窗口序号，
 * 用 Lua 保证 INCR 与 EXPIRE 的原子性。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度名，会拼进 Redis key，建议用「业务:动作」格式 */
    String name();

    /** 一个窗口内允许的最大请求数 */
    int limit() default 60;

    /** 窗口长度（秒） */
    int seconds() default 60;

    /** 按什么维度计数 */
    LimitBy by() default LimitBy.USER;

    /** 超限时返回给前端的提示 */
    String message() default "操作过于频繁，请稍后再试";

    /** 临时关闭某个接口的限流（如压测、演示） */
    boolean enabled() default true;
}
