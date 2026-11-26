package com.campus.delivery.modules.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.enums.CommonEnums;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.admin.entity.Ticket;
import com.campus.delivery.modules.admin.mapper.TicketMapper;
import com.campus.delivery.modules.admin.service.TicketService;
import com.campus.delivery.modules.chat.entity.ChatMessage;
import com.campus.delivery.modules.chat.mapper.ChatMessageMapper;
import com.campus.delivery.modules.delivery.entity.Rider;
import com.campus.delivery.modules.delivery.mapper.RiderMapper;
import com.campus.delivery.modules.order.entity.Order;
import com.campus.delivery.modules.order.mapper.OrderMapper;
import com.campus.delivery.modules.shop.entity.Merchant;
import com.campus.delivery.modules.shop.entity.Shop;
import com.campus.delivery.modules.shop.mapper.MerchantMapper;
import com.campus.delivery.modules.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单实时聊天服务。
 *
 * <p>一个订单下有三条会话线，共表 {@code chat_message}（按 threadId 区分）：
 * <ul>
 *   <li>订单群聊：threadId = 订单ID，参与者是下单学生 / 店铺商户 / 接单骑手；</li>
 *   <li>单聊：threadId = {@code dm:订单ID:A-B}（A、B 是角色码，字母序），例如
 *       {@code dm:xxx:RIDER-STUDENT}，只有这两个角色能看；</li>
 *   <li>客服介入：threadId = {@code svc:订单ID}，订单三方 + 管理员可见。</li>
 * </ul>
 * 消息落库，实时投递走 WebSocket，历史消息用于断线补齐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 客服介入线程前缀 */
    public static final String SERVICE_PREFIX = "svc:";
    /** 单聊线程前缀 */
    public static final String DM_PREFIX = "dm:";

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_MERCHANT = "MERCHANT";
    public static final String ROLE_RIDER = "RIDER";
    public static final String ROLE_ADMIN = "ADMIN";

    /** 单条消息最大长度 */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 一次拉取的历史条数上限 */
    private static final int HISTORY_LIMIT = 200;

    private final ChatMessageMapper chatMessageMapper;
    private final OrderMapper orderMapper;
    private final ShopMapper shopMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final TicketMapper ticketMapper;
    private final TicketService ticketService;

    /** 是否客服介入线程 */
    public static boolean isServiceThread(String threadId) {
        return threadId != null && threadId.startsWith(SERVICE_PREFIX);
    }

    /** threadId 中真正的订单ID */
    public static String orderIdOf(String threadId) {
        if (!StringUtils.hasText(threadId)) {
            return threadId;
        }
        if (threadId.startsWith(SERVICE_PREFIX)) {
            return threadId.substring(SERVICE_PREFIX.length());
        }
        if (threadId.startsWith(DM_PREFIX)) {
            int end = threadId.indexOf(':', DM_PREFIX.length());
            if (end > 0) {
                return threadId.substring(DM_PREFIX.length(), end);
            }
        }
        return threadId;
    }

    /** 单聊线程里的两个角色；订单群聊与客服线程返回 null（表示订单三方全部） */
    private Set<String> dmRoles(String threadId) {
        if (threadId == null || !threadId.startsWith(DM_PREFIX)) {
            return null;
        }
        int sep = threadId.lastIndexOf(':');
        if (sep <= DM_PREFIX.length()) {
            return null;
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : threadId.substring(sep + 1).split("-")) {
            if (StringUtils.hasText(role)) {
                roles.add(role.trim().toUpperCase());
            }
        }
        return roles.isEmpty() ? null : roles;
    }

    /** 订单三方：角色码 → sys_user.id */
    private Map<String, String> roleUserIds(Order order) {
        Map<String, String> map = new LinkedHashMap<>();
        if (StringUtils.hasText(order.getUserId())) {
            map.put(ROLE_STUDENT, order.getUserId());
        }
        if (StringUtils.hasText(order.getShopId())) {
            Shop shop = shopMapper.selectById(order.getShopId());
            // shop.merchant_id 是 merchant.id，不是 sys_user.id，需要再转一层拿登录账号
            if (shop != null && StringUtils.hasText(shop.getMerchantId())) {
                Merchant merchant = merchantMapper.selectById(shop.getMerchantId());
                if (merchant != null && StringUtils.hasText(merchant.getUserId())) {
                    map.put(ROLE_MERCHANT, merchant.getUserId());
                }
            }
        }
        if (StringUtils.hasText(order.getRiderId())) {
            // orders.rider_id 同样存的是 rider.id，需要转成 sys_user.id
            Rider rider = riderMapper.selectById(order.getRiderId());
            if (rider != null && StringUtils.hasText(rider.getUserId())) {
                map.put(ROLE_RIDER, rider.getUserId());
            }
        }
        return map;
    }

    /** 会话参与者：群聊=三方，单聊=双方，客服线程=三方（管理员另行按角色放行） */
    public Set<String> participants(String threadId) {
        Order order = orderMapper.selectById(orderIdOf(threadId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        Map<String, String> byRole = roleUserIds(order);
        Set<String> roles = dmRoles(threadId);
        if (roles == null) {
            return new LinkedHashSet<>(byRole.values());
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String role : roles) {
            String userId = byRole.get(role);
            if (StringUtils.hasText(userId)) {
                ids.add(userId);
            }
        }
        return ids;
    }

    /** 拉取聊天记录，按时间正序 */
    public List<ChatMessage> history(String threadId, String userId, String role) {
        if (!StringUtils.hasText(threadId) || !StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        checkVisible(threadId, userId, role);
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getOrderId, threadId)
                .orderByAsc(ChatMessage::getCreateTime)
                .last("LIMIT " + HISTORY_LIMIT));
    }

    /** 发送一条消息：校验可见性、长度，落库后返回（投递由调用方负责） */
    public ChatMessage send(String threadId, String fromUserId, String fromRole, String content) {
        if (!StringUtils.hasText(threadId) || !StringUtils.hasText(fromUserId)
                || !StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        String text = content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "消息不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        checkVisible(threadId, fromUserId, fromRole);
        ChatMessage message = new ChatMessage();
        message.setOrderId(threadId);
        message.setFromUserId(fromUserId);
        message.setFromRole(fromRole == null ? "UNKNOWN" : fromRole);
        message.setContent(text);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        log.info("聊天消息: thread={}, from={}({}), len={}", threadId, fromUserId, fromRole, text.length());
        return message;
    }

    /**
     * 申请平台客服介入：建一张工单（同一用户同一订单只保留一张未关闭的），
     * 返回的 threadId 用于进入客服会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public Ticket requestService(String orderId, String userId) {
        if (!StringUtils.hasText(orderId) || !StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        if (!participants(orderId).contains(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Ticket existing = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, orderId)
                .eq(Ticket::getUserId, userId)
                .in(Ticket::getStatus, CommonEnums.TicketStatus.WAITING.getCode(),
                        CommonEnums.TicketStatus.PROCESSING.getCode())
                .orderByDesc(Ticket::getCreateTime)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        return ticketService.createFromChat(userId, null, orderId, "申请平台客服介入");
    }

    /** 未读条数（本端暂未使用，留给演示页面角标） */
    public long count(String threadId) {
        if (!StringUtils.hasText(threadId)) {
            return 0L;
        }
        try {
            return chatMessageMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getOrderId, threadId));
        } catch (Exception e) {
            log.warn("统计聊天条数失败: thread={}, err={}", threadId, e.getMessage());
            return 0L;
        }
    }

    /** 参与者列表的只读视图，供推送循环使用 */
    public List<String> participantList(String threadId) {
        try {
            return List.copyOf(participants(threadId));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 管理员可在任意会话里以客服身份进出；其余人必须是该会话的参与者 */
    private void checkVisible(String threadId, String userId, String role) {
        if (ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (!participants(threadId).contains(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 供前端拼单聊 threadId：角色码按字母序，双方拼出的 key 才能对上 */
    public static String dmThreadId(String orderId, String roleA, String roleB) {
        String[] pair = {roleA.toUpperCase(), roleB.toUpperCase()};
        Arrays.sort(pair);
        return DM_PREFIX + orderId + ":" + pair[0] + "-" + pair[1];
    }
}