package com.campus.delivery.websocket;

import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.chat.entity.ChatMessage;
import com.campus.delivery.modules.chat.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单与抢单消息推送处理器（需求文档 5.6.6 难点六）。
 *
 * <p>连接地址：{@code ws://localhost:8080/api/ws/order?token=<jwt>&role=<ROLE>&userId=<id>}
 *
 * <p>推送场景：
 * <ul>
 *   <li>学生：自己订单的状态变更</li>
 *   <li>商户：本店新订单提醒</li>
 *   <li>骑手：订单大厅新单广播</li>
 * </ul>
 *
 * <p>可靠性：关键状态变更采用「WebSocket 推送 + 前端轮询」双通道，
 * 断线后前端调用订单列表接口即可恢复状态。
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class OrderNotifyHandler extends TextWebSocketHandler {

    /** userId -> 该用户的全部连接（同一账号可能多端登录） */
    private static final Map<String, Set<WebSocketSession>> USER_SESSIONS = new ConcurrentHashMap<>();

    /** role -> 该角色的全部连接（用于骑手大厅广播） */
    private static final Map<String, Set<WebSocketSession>> ROLE_SESSIONS = new ConcurrentHashMap<>();

    /** LocalDateTime 需要 JavaTime 模块，否则聊天消息序列化会抛 InvalidDefinitionException */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final com.campus.delivery.modules.delivery.service.RiderOnlineService riderOnlineService;

    private final ChatService chatService;
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = attr(session, "userId");
        String role = attr(session, "role");
        if (userId == null || role == null) {
            closeQuietly(session);
            return;
        }
        USER_SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        ROLE_SESSIONS.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet()).add(session);
        // 骑手连接建立即在 Redis 打上在线标记（TTL 即离线判定窗口）
        if ("RIDER".equalsIgnoreCase(role)) {
            riderOnlineService.markOnlineByUserId(userId);
        }
        log.info("WebSocket 连接建立: userId={}, role={}, 在线连接数={}", userId, role, USER_SESSIONS.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        updateRiderOnlineOnClose(session);
        removeSession(session);
        log.info("WebSocket 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        // 客户端心跳：回复 pong，并用心跳续期骑手在线状态
        if ("ping".equals(payload)) {
            String userId = attr(session, "userId");
            String role = attr(session, "role");
            if ("RIDER".equalsIgnoreCase(role) && userId != null) {
                riderOnlineService.markOnlineByUserId(userId);
            }
            sendText(session, "{\"type\":\"PONG\"}");
            return;
        }
        if (payload.startsWith("{")) {
            handleChatMessage(session, payload);
        }
    }

    /**
     * 订单聊天帧：{@code {"type":"CHAT","orderId":"...","content":"..."}}。
     *
     * <p>落库后广播给订单三方（学生、商户、骑手），发送人也会收到自己的消息，
     * 前端据此渲染回执，不需要本地先插一条再等服务器。
     */
    private void handleChatMessage(WebSocketSession session, String payload) {
        String userId = attr(session, "userId");
        String role = attr(session, "role");
        if (userId == null) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(payload);
            if (!"CHAT".equals(node.path("type").asText())) {
                return;
            }
            String orderId = node.path("orderId").asText("");
            String content = node.path("content").asText("");
            ChatMessage saved = chatService.send(orderId, userId, role, content);
            chatService.participantList(orderId).forEach(pid -> sendToUser(pid, "CHAT", saved));
            if (ChatService.isServiceThread(orderId)) {
                // 客服线程里管理员不是「订单参与人」，单独广播给在线管理员，客服台才能实时看到
                broadcastToRole(ChatService.ROLE_ADMIN, "CHAT", saved);
            }
        } catch (BusinessException e) {
            sendText(session, buildPayload("CHAT_ERROR", Map.of("message", e.getMessage())));
        } catch (Exception e) {
            log.warn("聊天消息处理失败: userId={}, err={}", userId, e.getMessage());
            sendText(session, buildPayload("CHAT_ERROR", Map.of("message", "消息发送失败")));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常: sessionId={}, msg={}", session.getId(), exception.getMessage());
        updateRiderOnlineOnClose(session);
        removeSession(session);
    }

    /** 断线时清除骑手在线标记，避免「已离线仍显示在线」 */
    private void updateRiderOnlineOnClose(WebSocketSession session) {
        String userId = attr(session, "userId");
        String role = attr(session, "role");
        if ("RIDER".equalsIgnoreCase(role) && userId != null) {
            riderOnlineService.markOfflineByUserId(userId);
        }
    }

    // ------------------------------------------------------------------
    // 对外推送方法：供 order / delivery 模块调用
    // ------------------------------------------------------------------

    /** 推送给指定用户（学生、商户） */
    public void sendToUser(String userId, String type, Object data) {
        Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = buildPayload(type, data);
        sessions.forEach(session -> sendText(session, payload));
    }

    /** 广播给某角色的全部在线连接（骑手订单大厅） */
    public void broadcastToRole(String role, String type, Object data) {
        Set<WebSocketSession> sessions = ROLE_SESSIONS.get(role);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = buildPayload(type, data);
        sessions.forEach(session -> sendText(session, payload));
    }

    /** 当前在线连接数（用于数据看板） */
    public int onlineCount(String role) {
        Set<WebSocketSession> sessions = ROLE_SESSIONS.get(role);
        return sessions == null ? 0 : sessions.size();
    }

    // ------------------------------------------------------------------

    private String buildPayload(String type, Object data) {
        try {
            return MAPPER.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("WebSocket 消息序列化失败", e);
            return "{\"type\":\"ERROR\",\"data\":null}";
        }
    }

    private void sendText(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (IOException e) {
            log.warn("WebSocket 推送失败: sessionId={}, msg={}", session.getId(), e.getMessage());
            removeSession(session);
        }
    }

    private void removeSession(WebSocketSession session) {
        String userId = attr(session, "userId");
        String role = attr(session, "role");
        if (userId != null) {
            Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    USER_SESSIONS.remove(userId);
                }
            }
        }
        if (role != null) {
            Set<WebSocketSession> sessions = ROLE_SESSIONS.get(role);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    ROLE_SESSIONS.remove(role);
                }
            }
        }
    }

    private String attr(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
    }
}
