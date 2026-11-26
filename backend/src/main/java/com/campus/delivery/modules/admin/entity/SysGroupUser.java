package com.campus.delivery.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** User <-> group relation (sys_group_user). One user may hold several groups. */
@Data
@TableName("sys_group_user")
public class SysGroupUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupId;

    private String userId;
}
