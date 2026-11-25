package com.campus.delivery.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单聊天消息。聊天室以订单为维度：学生、商户、骑手三方围绕一笔订单沟通。
 *
 * <p>写入走 WebSocket（{@code /ws/order} 的 CHAT 帧），读取走
 * {@code GET /api/chat/{orderId}/messages}，历史消息落库便于断线重连后补齐。
 */
@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 订单ID，即聊天室ID */
    private String orderId;

    /** 发送人ID */
    private String fromUserId;

    /** 发送人角色：STUDENT / MERCHANT / RIDER / ADMIN */
    private String fromRole;

    /** 消息内容，最长 500 字 */
    private String content;

    private LocalDateTime createTime;
}