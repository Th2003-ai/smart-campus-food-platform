package com.campus.delivery.modules.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录响应。前端把 token 存入 Pinia 与 localStorage，后续请求放在 Authorization 头。
 */
@Data
public class LoginResponse implements Serializable {

    private String token;

    private String userId;

    private String username;

    private String nickname;

    private String role;

    private String avatar;

    /** Token 有效期（秒） */
    private Long expiresIn;

    /**
     * 当前角色被授予的权限码（来自 sys_role_permission）。
     *
     * <p>前端据此决定菜单/按钮是否展示；后端拦截器用同一份数据做 @RequiresPerm 校验，
     * 两边判断口径一致。ADMIN 会拿到 admin:all 通配码。
     */
    private List<String> perms;
}
