package com.campus.delivery.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限校验：要求当前登录人的角色拥有 {@link #value()} 中任意一个权限码。
 *
 * <p>与 {@link RequiresRole} 的关系：
 * <ul>
 *   <li>{@code @RequiresRole} 按「四个主体」粗粒度拦截（学生进不了管理端）；</li>
 *   <li>{@code @RequiresPerm} 按「角色授权矩阵」细粒度拦截（商户没被授予 dish:write 就不能改菜品）。</li>
 * </ul>
 * 两者可以叠加使用，先角色后权限。
 *
 * <p>权限码来自 {@code sys_role_permission}，由管理端「角色权限」页面维护；
 * 角色拥有 {@code admin:all} 时视为通配，放行一切。
 *
 * <p>用法：{@code @RequiresPerm("dish:write")} 或 {@code @RequiresPerm({"order:transit", "admin:all"})}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPerm {

    /** 需要的权限码，满足任意一个即通过 */
    String[] value();
}
