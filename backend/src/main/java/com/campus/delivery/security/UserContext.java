package com.campus.delivery.security;

import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.exception.BusinessException;

/**
 * 当前登录人上下文。
 *
 * <p>由 {@link AuthInterceptor} 在请求进入时写入、请求结束时清除。
 * 业务代码通过 {@link #getUserId()} / {@link #getRole()} 获取身份，
 * 据此拼接数据行级过滤条件（如商户只能查自己店铺的订单）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 获取当前登录人，未登录直接抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static String getUserId() {
        return require().getUserId();
    }

    /**
     * 获取当前登录人ID，未登录返回 null。
     *
     * <p>给「登录不是必需」的场景用，例如限流按用户计数：
     * 拿不到用户就回退成按 IP 计数，而不是抛 401。
     */
    public static String getUserIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getRole() {
        return require().getRole();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }
}
