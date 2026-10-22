package com.campus.delivery.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.modules.admin.dto.MenuTreeVO;
import com.campus.delivery.modules.admin.entity.SysMenu;
import com.campus.delivery.modules.admin.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单服务：树形数据结构的构建与查询。主责：成员4。
 *
 * <p>数据库里的菜单是「平铺」的一行行记录，靠 {@code parent_id} 记录父子关系；
 * 本类负责把平铺列表组装成前端可用的「多叉树」。
 *
 * <p>组装采用一次遍历 + Map 索引的方式，时间复杂度 O(n)：
 * <ol>
 *   <li>把所有节点放入 Map（id → 节点），建立索引；</li>
 *   <li>再遍历一遍，每个节点按 parentId 找到父节点并加入其 children；
 *       parentId 为 '0' 或找不到父节点的，作为根节点收集。</li>
 * </ol>
 * 相比「每个节点都全表找儿子」的 O(n²) 递归写法，本实现不会随节点数量平方级增长。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    /** 根节点约定的父ID */
    public static final String ROOT_PARENT_ID = "0";

    private final SysMenuMapper sysMenuMapper;

    /** 只查启用的菜单，按 sort、id 排序，返回完整树形结构 */
    public List<MenuTreeVO> tree() {
        List<SysMenu> all = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return buildTree(all);
    }

    /** 平铺列表（按 sort 排序），用于与树形结果对照 */
    public List<SysMenu> list() {
        return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
    }

    /**
     * 核心算法：把平铺的菜单列表组装成多叉树。
     *
     * @param menus 平铺菜单（已按期望顺序排序）
     * @return 根节点列表（每个根节点内部已挂好各层 children）
     */
    public List<MenuTreeVO> buildTree(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 全部转成树节点，并用 Map 建立 id → 节点 索引（保持插入顺序）
        Map<String, MenuTreeVO> index = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            index.put(m.getId(), MenuTreeVO.of(m));
        }

        // 2. 逐个挂到父节点；根节点收集到 roots
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO node : index.values()) {
            String pid = node.getParentId();
            MenuTreeVO parent = (pid == null) ? null : index.get(pid);
            if (parent == null || ROOT_PARENT_ID.equals(pid)) {
                // 无父节点或父节点不在当前集合中 → 作为根
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }
}
