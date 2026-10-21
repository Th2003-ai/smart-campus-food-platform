package com.campus.delivery.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统菜单权限表。主责：成员4（平台基础 / RBAC）。
 *
 * <p>这是一张典型的「树形结构」表：通过 {@code parent_id} 自引用外键表达层级关系。
 * <ul>
 *   <li>{@code parent_id = '0'} 表示根节点（四端：学生端 / 商户端 / 骑手端 / 管理端）；</li>
 *   <li>菜单树共三层：目录（menuType=0）→ 菜单（menuType=1）→ 按钮（menuType=2）。</li>
 * </ul>
 * 树形结构的组装见 {@code MenuService#buildTree}，可视化见前端管理端「菜单权限树」页面。
 */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 父菜单ID，根节点为 '0' */
    private String parentId;

    /** 菜单名称 */
    private String menuName;

    /** 节点类型：0目录 1菜单 2按钮 */
    private Integer menuType;

    /** 前端路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 权限标识，如 admin:audit:pass */
    private String perms;

    /** 图标（Element Plus 图标名） */
    private String icon;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 是否显示：0隐藏 1显示 */
    private Integer visible;

    /** 状态：0停用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
