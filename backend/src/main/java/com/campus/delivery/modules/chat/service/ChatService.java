package com.campus.delivery.modules.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.delivery.common.api.ResultCode;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.chat.entity.ChatMessage;
import com.campus.delivery.modules.chat.mapper.ChatMessageMapper;
import com.campus.delivery.modules.order.entity.Order;
import com.campus.delivery.modules.order.mapper.OrderMapper;
import com.campus.delivery.modules.delivery.entity.Rider;
import com.campus.delivery.modules.delivery.mapper.RiderMapper;
import com.campus.delivery.modules.shop.entity.Merchant;
import com.campus.delivery.modules.shop.mapper.MerchantMapper;
import com.campus.delivery.modules.shop.entity.Shop;
import com.campus.delivery.modules.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单实时聊天服务。
 *
 * <p>一条订单就是一个聊天室，参与者是三方：下单学生、店铺商户、接单骑手。
 * 消息落库（{@code chat_message}），实时投递走 WebSocket，历史消息用于断线补齐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 单条消息最大长度 */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 一次拉取的历史条数上限 */
    private static final int HISTORY_LIMIT = 100;

    private final ChatMessageMapper chatMessageMapper;
    private final OrderMapper orderMapper;
    private final ShopMapper shopMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;

    /**
     * 聊天室参与者：学生 + 商户 + 骑手（骑手未接单时为空）。
     *
     * <p>只认订单上的三方，管理员与陌生人不在其中——这样即使拿到订单ID也读不到别人聊天。
     */
    public Set<String> participants(String orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return participants(order);
    }

    private Set<String> participants(Order order) {
        Set<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(order.getUserId())) {
            ids.add(order.getUserId());
        }
        if (StringUtils.hasText(order.getShopId())) {
            Shop shop = shopMapper.selectById(order.getShopId());
            // shop.merchant_id 是 merchant.id，不是 sys_user.id，需要再转一层拿登录账号
            if (shop != null && StringUtils.hasText(shop.getMerchantId())) {
                Merchant merchant = merchantMapper.selectById(shop.getMerchantId());
                if (merchant != null && StringUtils.hasText(merchant.getUserId())) {
                    ids.add(merchant.getUserId());
                }
            }
        }
        if (StringUtils.hasText(order.getRiderId())) {
            // orders.rider_id 同样存的是 rider.id，需要转成 sys_user.id
            Rider rider = riderMapper.selectById(order.getRiderId());
            if (rider != null && StringUtils.hasText(rider.getUserId())) {
                ids.add(rider.getUserId());
            }
        }
        return ids;
    }

    /** 拉取聊天记录（仅参与者可见），按时间正序 */
    public List<ChatMessage> history(String orderId, String userId) {
        if (!StringUtils.hasText(orderId) || !StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        if (!participants(orderId).contains(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getOrderId, orderId)
                .orderByAsc(ChatMessage::getCreateTime)
                .last("LIMIT " + HISTORY_LIMIT));
    }

    /** 发送一条消息：校验参与者身份、长度，落库后返回（投递由调用方负责） */
    public ChatMessage send(String orderId, String fromUserId, String fromRole, String content) {
        if (!StringUtils.hasText(orderId) || !StringUtils.hasText(fromUserId)
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
        if (!participants(orderId).contains(fromUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        ChatMessage message = new ChatMessage();
        message.setOrderId(orderId);
        message.setFromUserId(fromUserId);
        message.setFromRole(fromRole == null ? "UNKNOWN" : fromRole);
        message.setContent(text);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        log.info("聊天消息: orderId={}, from={}({}), len={}", orderId, fromUserId, fromRole, text.length());
        return message;
    }

    /** 未读条数（本端暂未使用，留给演示页面角标） */
    public long count(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            return 0L;
        }
        try {
            return chatMessageMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getOrderId, orderId));
        } catch (Exception e) {
            log.warn("统计聊天条数失败: orderId={}, err={}", orderId, e.getMessage());
            return 0L;
        }
    }

    /** 参与者列表的只读视图，供推送循环使用 */
    public List<String> participantList(String orderId) {
        try {
            return List.copyOf(participants(orderId));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}