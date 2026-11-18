package com.campus.delivery.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录态载体：由 JWT 解析得到，保存在 {@link UserContext} 的 ThreadLocal 中。
 *
 * <p>{@code role} 是角色编码字符串，取值来自数据库 {@code sys_role.code}，
 * 不再由枚举约束 —— 新增角色不需要改这里。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    /** 用户ID（sys_user.id） */
    private String userId;
    /** 登录账号 */
    private String username;
    /** 角色编码（来自 sys_role.code，如 STUDENT / MERCHANT / RIDER / ADMIN） */
    private String role;
    /**
     * 所属端（来自 sys_role.portal）：决定能访问哪套页面与接口。
     *
     * <p>新角色只要在管理端挂靠到某个端，就能像内置角色一样进入该端；
     * 为空表示未配置，只能停留在「暂无工作台」页。
     */
    private String portal;
    /** 昵称 */
    private String nickname;

    public boolean isStudent() {
        return inPortal("STUDENT");
    }

    public boolean isMerchant() {
        return inPortal("MERCHANT");
    }

    public boolean isRider() {
        return inPortal("RIDER");
    }

    public boolean isAdmin() {
        return inPortal("ADMIN");
    }

    /** 是否属于某个端：优先看 portal，未配置时退回角色编码 */
    private boolean inPortal(String code) {
        String current = portal != null && !portal.isBlank() ? portal : role;
        return code.equalsIgnoreCase(current);
    }
}
