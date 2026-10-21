package com.campus.delivery.modules.user.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人中心展示对象：账号信息 + 学生档案 + 饮食档案（手机号已脱敏）。
 */
@Data
public class UserProfileVO implements Serializable {

    private String userId;

    private String username;

    private String nickname;

    /** 已脱敏 */
    private String phone;

    private String avatar;

    private String role;

    private String studentNo;

    private String college;

    private String grade;

    /** 0普通 1减脂 2增肌 */
    private Integer dietGoal;

    private String tastePreference;

    private String allergyFoods;

    /**
     * 账户余额（仅学生有；余额支付用）。
     *
     * <p>金额只以数据库为准，Redis 不缓存余额 —— 见 PPT「金额/余额只落磁盘库 + 事务」。
     */
    private java.math.BigDecimal balance;
}
