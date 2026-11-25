package com.campus.delivery.modules.chat.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.chat.entity.ChatMessage;
import com.campus.delivery.modules.chat.service.ChatService;
import com.campus.delivery.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单聊天接口。
 *
 * <p>实时收发走 WebSocket（{@code /ws/order} 的 CHAT 帧），这里提供历史消息与 REST 兜底发送：
 * WebSocket 断线时前端仍能靠它补发，不依赖连接状态。
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 拉取订单聊天记录（仅订单参与者可见：学生 / 商户 / 骑手） */
    @GetMapping("/{orderId}/messages")
    public Result<List<ChatMessage>> history(@PathVariable String orderId) {
        return Result.ok(chatService.history(orderId, UserContext.getUserId()));
    }

    /** REST 兜底发送（正常走 WebSocket，这里给断线场景与联调用） */
    @PostMapping("/{orderId}/messages")
    public Result<ChatMessage> send(@PathVariable String orderId,
                                    @RequestBody Map<String, String> body) {
        ChatMessage message = chatService.send(
                orderId, UserContext.getUserId(), UserContext.getRole(), body.get("content"));
        return Result.ok(message);
    }
}