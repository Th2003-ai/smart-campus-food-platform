package com.campus.delivery.modules.admin.dto;

/**
 * 前端提交的一条「角色-权限」授权项。
 *
 * @param permission 权限码，如 dish:write
 * @param dataScope  数据范围：ALL / SHOP / SELF
 */
public record RolePermItem(String permission, String dataScope) {
}
