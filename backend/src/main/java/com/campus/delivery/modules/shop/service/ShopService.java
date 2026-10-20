package com.campus.delivery.modules.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.delivery.common.api.PageResult;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.enums.CommonEnums;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.shop.entity.Merchant;
import com.campus.delivery.modules.shop.entity.Shop;
import com.campus.delivery.modules.shop.mapper.MerchantMapper;
import com.campus.delivery.modules.shop.mapper.ShopMapper;
import com.campus.delivery.modules.user.service.UserService;
import com.campus.delivery.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 店铺服务。主责：成员2。
 *
 * <p>要点：
 * <ul>
 *   <li>店铺列表按分类/销量/评分排序，热点数据缓存到 Redis；</li>
 *   <li>商户端所有操作通过 {@link #currentMerchantId()} 取本店，禁止前端传 merchantId。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopMapper shopMapper;
    private final MerchantMapper merchantMapper;
    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.campus.delivery.modules.rank.service.HotRankService hotRankService;

    /** 店铺列表（学生端首页） */
    public PageResult<Shop> pageShops(String categoryId, String keyword, String sortBy,
                                      long pageNum, long pageSize) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getBanStatus, 0)
                .eq(StringUtils.hasText(categoryId), Shop::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Shop::getShopName, keyword);
        // 营业中的店铺优先展示
        wrapper.orderByDesc(Shop::getBusinessStatus);
        if ("score".equalsIgnoreCase(sortBy)) {
            wrapper.orderByDesc(Shop::getScore);
        } else {
            wrapper.orderByDesc(Shop::getMonthlySales);
        }

        Page<Shop> page = shopMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /** 店铺详情（热点缓存，减少数据库压力） */
    public Shop getDetail(String shopId) {
        String cacheKey = RedisKeys.SHOP_DETAIL + shopId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Shop shop) {
            return shop;
        }
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(20001, "店铺不存在");
        }
        redisTemplate.opsForValue().set(cacheKey, shop, 10, TimeUnit.MINUTES);
        return shop;
    }

    /** 当前登录商户的店铺（商户端入口） */
    public Shop getMyShop() {
        return shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getMerchantId, currentMerchantId())
                .last("LIMIT 1"));
    }

    /**
     * 当前登录商户的店铺ID。
     *
     * <p>供菜品、订单、评价等商户端模块复用，避免各处重复解析商户归属。
     */
    public String currentShopId() {
        Shop shop = getMyShop();
        if (shop == null) {
            throw new BusinessException(20002, "尚未创建店铺，请联系管理员");
        }
        return shop.getId();
    }

    /** 维护店铺信息 */
    @Transactional(rollbackFor = Exception.class)
    public Shop updateMyShop(Shop form) {
        Shop shop = getMyShop();
        if (shop == null) {
            throw new BusinessException(20002, "尚未创建店铺，请联系管理员");
        }
        // 只允许修改本店的可编辑字段，主键与归属字段不可改
        shop.setShopName(form.getShopName());
        shop.setDescription(form.getDescription());
        shop.setAddress(form.getAddress());
        shop.setPhone(form.getPhone());
        shop.setNotice(form.getNotice());
        shop.setOpenTime(form.getOpenTime());
        shop.setMinPrice(form.getMinPrice());
        shop.setDeliveryFee(form.getDeliveryFee());
        shop.setDeliveryTime(form.getDeliveryTime());
        shop.setLogo(form.getLogo());
        shopMapper.updateById(shop);
        evictCache(shop.getId());
        hotRankService.invalidateShopRank();
        return shop;
    }

    /** 设置营业状态：0休息中 1营业中 */
    @Transactional(rollbackFor = Exception.class)
    public void updateBusinessStatus(Integer businessStatus) {
        Shop shop = getMyShop();
        if (shop == null) {
            throw new BusinessException(20002, "尚未创建店铺，请联系管理员");
        }
        shop.setBusinessStatus(businessStatus);
        shopMapper.updateById(shop);
        evictCache(shop.getId());
        hotRankService.invalidateShopRank();
    }

    /**
     * 当前登录商户对应的商户ID。
     *
     * <p>数据行级隔离的关键：所有商户端接口都通过本方法取商户，不接受前端传入。
     */
    public String currentMerchantId() {
        String userId = UserContext.getUserId();
        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUserId, userId).last("LIMIT 1"));
        if (merchant == null) {
            throw new BusinessException(20003, "当前账号未绑定商户信息");
        }
        if (merchant.getAuditStatus() == null || merchant.getAuditStatus() != 1) {
            throw new BusinessException(20004, "商户尚未通过审核");
        }
        return merchant.getId();
    }

    /**
     * 店铺所属商户的登录账号ID。
     *
     * <p>供订单模块做「新订单推送给商户」时使用，避免跨模块访问商户 Mapper。
     */
    public String getMerchantUserId(String shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(20001, "店铺不存在");
        }
        Merchant merchant = merchantMapper.selectById(shop.getMerchantId());
        return merchant == null ? null : merchant.getUserId();
    }

    private void evictCache(String shopId) {
        redisTemplate.delete(RedisKeys.SHOP_DETAIL + shopId);
    }

    // ==================================================================
    // 管理端：商户审核与店铺封禁（供 AdminAuditService 调用）
    // ==================================================================

    /**
     * 审核商户资质。
     *
     * <p>约束：只有「待审核」状态可审核，防止重复审核覆盖 audit_time / audit_by
     * 使审计链失效；驳回必须填写原因，避免静默驳回。
     *
     * <p>审核通过/驳回只改 merchant 表；是否需要同时禁用账号由调用方决定
     * （见 AdminAuditService），本方法保持单一职责。
     *
     * @param merchantId  商户ID（merchant.id，不是 sys_user.id）
     * @param pass        true 通过，false 驳回
     * @param remark      审核意见，驳回时必填
     * @param adminUserId 审核人（管理员 sys_user.id）
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditMerchant(String merchantId, boolean pass, String remark, String adminUserId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(20005, "商户不存在");
        }
        if (!CommonEnums.AuditStatus.WAITING.getCode().equals(merchant.getAuditStatus())) {
            throw new BusinessException(20006, "该商户已审核（当前状态："
                    + auditStatusText(merchant.getAuditStatus()) + "），不可重复审核");
        }
        if (!pass && !StringUtils.hasText(remark)) {
            throw new BusinessException(20007, "驳回商户必须填写驳回原因");
        }
        merchant.setAuditStatus(pass
                ? CommonEnums.AuditStatus.PASSED.getCode()
                : CommonEnums.AuditStatus.REJECTED.getCode());
        merchant.setAuditRemark(remark);
        merchant.setAuditBy(adminUserId);
        merchant.setAuditTime(LocalDateTime.now());
        merchantMapper.updateById(merchant);
    }

    /**
     * 封禁 / 解封店铺。
     *
     * <p>封禁时同时把营业状态置为「休息中」：否则会出现「已封禁但前台显示营业中」
     * 的矛盾状态。封禁后 {@link #pageShops} 会把该店铺从学生端列表滤掉。
     *
     * @param shopId 店铺ID
     * @param ban    true 封禁，false 解封
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBanStatus(String shopId, boolean ban) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(20001, "店铺不存在");
        }
        shop.setBanStatus(ban ? 1 : 0);
        if (ban) {
            shop.setBusinessStatus(0);
        }
        shopMapper.updateById(shop);
        evictCache(shopId);
    }

    /**
     * 管理端：商户审核列表。
     *
     * <p>不做分页（校园场景待审商户量级很小），按「待审核优先 + 提交时间倒序」排列，
     * 便于管理端第一屏就看到要处理的记录。
     *
     * @param auditStatus 审核状态过滤，null 表示全部
     */
    public List<Merchant> listMerchantsForAudit(Integer auditStatus) {
        return merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                .eq(auditStatus != null, Merchant::getAuditStatus, auditStatus)
                .orderByAsc(Merchant::getAuditStatus)
                .orderByDesc(Merchant::getCreateTime));
    }

    /**
     * 管理端：店铺列表（含已封禁）。
     *
     * <p>必须与 {@link #pageShops} 区分：后者会过滤 ban_status = 0，
     * 管理端若也用它，店铺一旦封禁就再也查不到，无法解封。
     *
     * @param banStatus 封禁状态过滤，null 表示全部
     */
    public List<Shop> listShopsForAudit(Integer banStatus) {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(banStatus != null, Shop::getBanStatus, banStatus)
                .orderByDesc(Shop::getCreateTime));
    }

    /** 商户的登录账号ID；商户不存在返回 null（供管理端审核后处理账号状态） */
    public String merchantUserId(String merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        return merchant == null ? null : merchant.getUserId();
    }

    private String auditStatusText(Integer status) {
        if (CommonEnums.AuditStatus.PASSED.getCode().equals(status)) {
            return "已通过";
        }
        if (CommonEnums.AuditStatus.REJECTED.getCode().equals(status)) {
            return "已驳回";
        }
        return "待审核";
    }
}
