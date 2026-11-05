package com.campus.delivery.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色权限表（RBAC 的「角色 → 权限码」映射）。主责：成员4。
 *
 * <p>四个主体各一组：
 * <ul>
 *   <li>STUDENT  学生：shop:read / order:write / order:read / review:write / ai:use</li>
 *   <li>MERCHANT 商户：dish:write / dish:read / order:read / order:transit / review:reply / ai:business</li>
 *   <li>RIDER    骑手：rider:hall / rider:grab / delivery:transit</li>
 *   <li>ADMIN    管理员：admin:all</li>
 * </ul>
 *
 * <p>{@code dataScope} 是该权限的数据范围：ALL 全部、SHOP 本店、SELF 本人。
 * 行级隔离仍由各业务 Service 拼接查询条件完成，本表负责配置与展示。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 角色码：STUDENT / MERCHANT / RIDER / ADMIN */
    private String role;

    /** 权限码，如 dish:write */
    private String permission;

    /** 数据范围：ALL / SHOP / SELF */
    private String dataScope;

    private LocalDateTime createTime;
}
