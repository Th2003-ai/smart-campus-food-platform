package com.campus.delivery.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色表（sys_role）。
 *
 * <p>角色从「Java 枚举」迁移到数据库后，新增/停用角色只需改这张表：
 * <ul>
 *   <li>{@code code} 是角色编码，被 {@code sys_user.role} 与 {@code sys_role_permission.role} 引用；</li>
 *   <li>{@code builtin=1} 的四个角色供四端入口使用，不允许删除；</li>
 *   <li>{@code allow_register=1} 的角色才允许自助注册；</li>
 *   <li>{@code status=0} 的角色不能登录（已登录的旧 token 到期后失效）。</li>
 * </ul>
 */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    /** 角色编码，主键（手动输入，不用自增） */
    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    private String name;

    private String description;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 1=内置角色（四端入口用，不可删除） */
    private Integer builtin;

    /** 1启用 0停用 */
    private Integer status;

    /** 1=允许自助注册 */
    private Integer allowRegister;

    /**
     * 所属端：STUDENT / MERCHANT / RIDER / ADMIN。
     *
     * <p>决定这个角色能用哪一套页面与接口（`@RequiresRole` 校验的就是「端」）。
     * 新角色挂靠到某个端后，即可像内置角色一样进入该端。
     */
    private String portal;

    /** 登录后首页路由，如 /admin/dashboard；为空时前端落到「暂无工作台」提示页 */
    private String homePath;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
