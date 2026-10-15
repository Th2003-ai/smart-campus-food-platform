package com.campus.delivery.modules.admin.service;

import com.campus.delivery.modules.delivery.entity.Rider;
import com.campus.delivery.modules.delivery.service.DeliveryService;
import com.campus.delivery.modules.shop.entity.Merchant;
import com.campus.delivery.modules.shop.entity.Shop;
import com.campus.delivery.modules.shop.service.ShopService;
import com.campus.delivery.modules.user.service.UserService;
import com.campus.delivery.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端审核与处置的编排服务。主责：成员3。
 *
 * <p>分层用意：审核列表需要「业务表 + 账号状态」两份数据，
 * 其中业务表由各自的数据责任域（{@link ShopService} / {@link DeliveryService}）读取，
 * 账号状态由 {@link UserService} 提供；本类只负责编排与组装，不直接访问任何 Mapper，
 * 符合 docs/01「同层模块之间通过 Service 调用，不直接跨模块访问对方 Mapper」的约束。
 *
 * <p>操作人一律取 {@link UserContext#getUserId()}（当前登录管理员），不接受前端传入，
 * 保证审核记录不可伪造。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    /** 账号状态：0禁用（与 sys_user.status 一致） */
    private static final int ACCOUNT_DISABLED = 0;

    private final ShopService shopService;
    private final DeliveryService deliveryService;
    private final UserService userService;

    // ==================================================================
    // 审核 / 处置（写）
    // ==================================================================

    /**
     * 审核商户。
     *
     * <p>驳回时同时禁用该商户的登录账号：否则被驳回的商户仍能登录商户端。
     * 通过时不主动启用账号，避免把管理员手动封禁过的账号意外放开。
     */
    public void auditMerchant(String merchantId, boolean pass, String remark) {
        String adminUserId = UserContext.getUserId();
        shopService.auditMerchant(merchantId, pass, remark, adminUserId);
        if (!pass) {
            disableMerchantAccount(merchantId);
        }
        log.info("商户审核完成: merchantId={}, pass={}, admin={}", merchantId, pass, adminUserId);
    }

    /** 审核骑手；驳回时同时禁用登录账号 */
    public void auditRider(String riderId, boolean pass, String remark) {
        String adminUserId = UserContext.getUserId();
        deliveryService.auditRider(riderId, pass, remark);
        if (!pass) {
            deliveryService.banRider(riderId, true);
        }
        log.info("骑手审核完成: riderId={}, pass={}, admin={}", riderId, pass, adminUserId);
    }

    /** 封禁 / 解封店铺 */
    public void banShop(String shopId, boolean ban) {
        shopService.updateBanStatus(shopId, ban);
        log.info("店铺{}: shopId={}, admin={}", ban ? "封禁" : "解封", shopId, UserContext.getUserId());
    }

    /** 封禁 / 解封骑手（封禁即禁用登录账号） */
    public void banRider(String riderId, boolean ban) {
        deliveryService.banRider(riderId, ban);
        log.info("骑手{}: riderId={}, admin={}", ban ? "封禁" : "解封", riderId, UserContext.getUserId());
    }

    // ==================================================================
    // 审核列表（读）
    // ==================================================================

    /**
     * 商户审核列表。
     *
     * @param auditStatus 0待审核 1已通过 2已驳回，null 表示全部
     */
    public List<Map<String, Object>> listMerchants(Integer auditStatus) {
        List<Merchant> merchants = shopService.listMerchantsForAudit(auditStatus);
        List<String> userIds = merchants.stream().map(Merchant::getUserId).toList();
        Map<String, Integer> statusMap = userService.mapAccountStatus(userIds);

        List<Map<String, Object>> list = new ArrayList<>(merchants.size());
        for (Merchant merchant : merchants) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", merchant.getId());
            item.put("merchantName", merchant.getMerchantName());
            item.put("contactName", merchant.getContactName());
            item.put("contactPhone", merchant.getContactPhone());
            item.put("licenseNo", merchant.getLicenseNo());
            item.put("auditStatus", merchant.getAuditStatus());
            item.put("auditRemark", merchant.getAuditRemark());
            item.put("auditTime", merchant.getAuditTime());
            item.put("createTime", merchant.getCreateTime());
            item.put("userId", merchant.getUserId());
            item.put("accountStatus", statusMap.get(merchant.getUserId()));
            list.add(item);
        }
        return list;
    }

    /**
     * 骑手审核列表。
     *
     * @param auditStatus 0待审核 1已通过 2已驳回，null 表示全部
     */
    public List<Map<String, Object>> listRiders(Integer auditStatus) {
        List<Rider> riders = deliveryService.listRidersForAudit(auditStatus);
        List<String> userIds = riders.stream().map(Rider::getUserId).toList();
        Map<String, Integer> statusMap = userService.mapAccountStatus(userIds);

        List<Map<String, Object>> list = new ArrayList<>(riders.size());
        for (Rider rider : riders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rider.getId());
            item.put("realName", rider.getRealName());
            item.put("studentCardNo", rider.getStudentCardNo());
            item.put("vehicleType", rider.getVehicleType());
            item.put("workStatus", rider.getWorkStatus());
            item.put("deliveringCount", rider.getDeliveringCount());
            item.put("totalIncome", rider.getTotalIncome());
            item.put("auditStatus", rider.getAuditStatus());
            item.put("auditRemark", rider.getAuditRemark());
            item.put("createTime", rider.getCreateTime());
            item.put("userId", rider.getUserId());
            item.put("accountStatus", statusMap.get(rider.getUserId()));
            list.add(item);
        }
        return list;
    }

    /**
     * 店铺列表（含已封禁，供管理端处置）。
     *
     * @param banStatus 0正常 1已封禁，null 表示全部
     */
    public List<Map<String, Object>> listShops(Integer banStatus) {
        List<Shop> shops = shopService.listShopsForAudit(banStatus);

        List<Map<String, Object>> list = new ArrayList<>(shops.size());
        for (Shop shop : shops) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", shop.getId());
            item.put("shopName", shop.getShopName());
            item.put("merchantId", shop.getMerchantId());
            item.put("businessStatus", shop.getBusinessStatus());
            item.put("banStatus", shop.getBanStatus());
            item.put("monthlySales", shop.getMonthlySales());
            item.put("score", shop.getScore());
            item.put("createTime", shop.getCreateTime());
            list.add(item);
        }
        return list;
    }

    // ------------------------------------------------------------------

    /** 驳回商户时禁用其登录账号 */
    private void disableMerchantAccount(String merchantId) {
        String userId = shopService.merchantUserId(merchantId);
        if (userId != null) {
            userService.updateAccountStatus(userId, ACCOUNT_DISABLED);
        }
    }
}
