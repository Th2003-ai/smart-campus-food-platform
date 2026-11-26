package com.campus.delivery.modules.chat.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.admin.entity.Ticket;
import com.campus.delivery.modules.chat.entity.ChatMessage;
import com.campus.delivery.modules.chat.service.ChatService;
import com.campus.delivery.security.UserContext;
import com.campus.delivery.websocket.OrderNotifyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单聊天接口。
 *
 * <p>实时收发走 WebSocket（{@code /ws/order} 的 CHAT 帧），这里提供历史消息、REST 兜底发送
 * 与「申请平台客服介入」。
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final OrderNotifyHandler orderNotifyHandler;

    /** 拉取会话记录（群聊=订单ID；单聊=dm:订单ID:A-B；客服=svc:订单ID） */
    @GetMapping("/{threadId}/messages")
    public Result<List<ChatMessage>> history(@PathVariable String threadId) {
        return Result.ok(chatService.history(threadId, UserContext.getUserId(), UserContext.getRole()));
    }

    /** REST 兜底发送（正常走 WebSocket，这里给断线场景与联调用） */
    @PostMapping("/{threadId}/messages")
    public Result<ChatMessage> send(@PathVariable String threadId,
                                    @RequestBody Map<String, String> body) {
        ChatMessage message = chatService.send(
                threadId, UserContext.getUserId(), UserContext.getRole(), body.get("content"));
        return Result.ok(message);
    }

    /**
     * 申请平台客服介入：建工单并通知在线管理员，前端拿到 threadId 后进入客服会话。
     * 同一订单重复申请会复用未关闭的工单。
     */
    @PostMapping("/{orderId}/service")
    public Result<Map<String, Object>> requestService(@PathVariable String orderId) {
        Ticket ticket = chatService.requestService(orderId, UserContext.getUserId());
        String threadId = ChatService.SERVICE_PREFIX + orderId;
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("orderId", orderId);
        notice.put("threadId", threadId);
        notice.put("ticketNo", ticket.getTicketNo());
        notice.put("fromRole", UserContext.getRole());
        orderNotifyHandler.broadcastToRole(ChatService.ROLE_ADMIN, "SERVICE_REQUEST", notice);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketId", ticket.getId());
        data.put("ticketNo", ticket.getTicketNo());
        data.put("threadId", threadId);
        return Result.ok(data);
    }
}