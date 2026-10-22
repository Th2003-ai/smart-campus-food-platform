package com.campus.delivery.common.annotation;

/**
 * 限流计数的维度。
 */
public enum LimitBy {

    /** 按登录用户计数（未登录时自动回退为 IP） */
    USER,

    /** 按客户端 IP 计数（适用于登录、注册这类还没有用户身份的接口） */
    IP
}
