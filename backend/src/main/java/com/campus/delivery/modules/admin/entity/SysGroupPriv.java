package com.campus.delivery.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Group privilege (sys_group_priv), ZenTao granularity: module (controller) + method.
 * Default deny: an endpoint without a row is not reachable by that group.
 */
@Data
@TableName("sys_group_priv")
public class SysGroupPriv implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupId;

    /** controller name without the Controller suffix, lower case, e.g. "dish" */
    private String module;

    /** java handler method name, e.g. "create" */
    private String method;

    /** ALL / SHOP / SELF - only returned to the UI for now */
    private String dataScope;
}
