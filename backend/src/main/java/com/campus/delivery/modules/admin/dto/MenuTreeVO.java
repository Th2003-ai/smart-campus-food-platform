package com.campus.delivery.modules.admin.dto;

import com.campus.delivery.modules.admin.entity.SysMenu;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点视图对象。
 *
 * <p>在平铺实体 {@link SysMenu} 基础上增加 {@link #children} 集合，
 * 形成「多叉树」的递归节点结构，前端 el-tree 可直接绑定 {@code label} 与 {@code children} 渲染。
 */
@Data
public class MenuTreeVO implements Serializable {

    private String id;

    private String parentId;

    /** 节点显示名（el-tree 默认读取 label 字段） */
    private String label;

    /** 节点类型：0目录 1菜单 2按钮 */
    private Integer menuType;

    /** 节点类型中文描述（便于页面展示） */
    private String typeText;

    private String path;

    private String component;

    private String perms;

    private String icon;

    private Integer sort;

    private Integer visible;

    private Integer status;

    /** 子节点集合 —— 树形数据结构的核心：每个节点持有自己的下一层节点 */
    private List<MenuTreeVO> children = new ArrayList<>();

    /** 实体 → 树节点 */
    public static MenuTreeVO of(SysMenu m) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(m.getId());
        vo.setParentId(m.getParentId());
        vo.setLabel(m.getMenuName());
        vo.setMenuType(m.getMenuType());
        vo.setTypeText(typeText(m.getMenuType()));
        vo.setPath(m.getPath());
        vo.setComponent(m.getComponent());
        vo.setPerms(m.getPerms());
        vo.setIcon(m.getIcon());
        vo.setSort(m.getSort());
        vo.setVisible(m.getVisible());
        vo.setStatus(m.getStatus());
        return vo;
    }

    public static String typeText(Integer menuType) {
        if (menuType == null) {
            return "未知";
        }
        switch (menuType) {
            case 0:
                return "目录";
            case 1:
                return "菜单";
            case 2:
                return "按钮";
            default:
                return "未知";
        }
    }
}
