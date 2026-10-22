package com.campus.delivery.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 注册请求（学生 / 商户 / 骑手共用）。
 *
 * <p>三种角色的登录账号来源不同（与 database/schema.sql 的字段注释一致）：
 * <ul>
 *   <li>学生：用<b>学号</b>登录（{@code studentNo}）</li>
 *   <li>商户 / 骑手：用<b>手机号</b>登录（{@code phone}）</li>
 *   <li>管理员：<b>不开放注册</b>，只能由数据库预置（admin 账号）</li>
 * </ul>
 *
 * <p>不同角色的必填项不一样，因此这里只对「所有角色都必填」的字段加注解，
 * 角色专属的必填校验放在 {@code AuthService#register} 里按角色判断，
 * 以便返回更准确的中文提示（例如「商户注册必须填写商户名称」）。
 */
@Data
public class RegisterRequest implements Serializable {

    /** 注册角色：STUDENT / MERCHANT / RIDER（ADMIN 不允许） */
    @NotBlank(message = "请选择注册角色")
    private String role;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在6-20位之间")
    private String password;

    /** 手机号：商户/骑手的登录账号，也是联系方式（三种角色都必填） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    // ---------------- 学生专属 ----------------

    /** 学号（学生必填，作为登录账号） */
    private String studentNo;

    /** 真实姓名（学生、骑手必填；商户可选，用于联系人） */
    private String realName;

    private String college;

    private String grade;

    // ---------------- 商户专属 ----------------

    /** 商户名称（商户必填） */
    private String merchantName;

    /** 联系人（选填，默认取 realName） */
    private String contactName;

    /** 营业执照 / 校园经营许可编号（选填，供管理员审核参考） */
    private String licenseNo;

    // ---------------- 骑手专属 ----------------

    /** 学生证 / 工号（选填） */
    private String studentCardNo;

    /** 配送车辆：步行 / 自行车 / 电动车（选填） */
    private String vehicleType;
}
