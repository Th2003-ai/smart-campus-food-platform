package com.campus.delivery.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解：标注在 Controller 类或方法上，由 {@link AuthInterceptor} 校验。
 *
 * <p>使用示例：
 * <pre>
 *   &#64;RequiresRole("STUDENT")                    // 仅学生可访问
 *   &#64;RequiresRole({"MERCHANT", "ADMIN"})          // 商户或管理员可访问
 * </pre>
 *
 * <p><b>为什么不直接用角色枚举：</b>角色已经是数据库表 {@code sys_role} 里的数据
 * （编码 + 名称 + 启停 + 可注册），新增角色不需要改代码。
 * 这里的字符串只是「内置四端入口」的编码，用于接口级的粗粒度隔离；
 * 细粒度的动作授权请用 {@link RequiresPerm}（权限码同样来自数据库）。
 *
 * <p>不标注该注解时，仅要求已登录（公开接口请加入
 * {@code WebMvcConfig} 的放行路径白名单）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /** 允许访问的角色编码，满足任一即可（大小写不敏感） */
    String[] value();
}
