package com.campus.delivery.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * User group (sys_group). ZenTao-style: a user may belong to several groups and
 * the effective privileges are the union of them.
 */
@Data
@TableName("sys_group")
public class SysGroup implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    /** 1 = builtin group, cannot be deleted */
    private Integer builtin;

    private String remark;

    private LocalDateTime createTime;
}