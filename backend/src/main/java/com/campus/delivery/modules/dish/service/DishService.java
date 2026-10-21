package com.campus.delivery.modules.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.dish.entity.Dish;
import com.campus.delivery.modules.dish.entity.DishCategory;
import com.campus.delivery.modules.dish.mapper.DishCategoryMapper;
import com.campus.delivery.modules.dish.mapper.DishMapper;
import com.campus.delivery.modules.rank.service.HotRankService;
import com.campus.delivery.modules.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 菜品服务。主责：成员2。
 *
 * <p>对外提供两类能力：
 * <ol>
 *   <li>浏览与检索：供学生端（成员1）与 AI 模块（成员1/成员3）调用；</li>
 *   <li>库存扣减：供订单模块（成员4）在下单事务中调用。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DishService {

    private static final int CODE_DISH_NOT_FOUND = 20010;
    private static final int CODE_DISH_OFF_SHELF = 20011;
    private static final int CODE_STOCK_NOT_ENOUGH = 20012;

    /** 菜品详情缓存有效期（分钟） */
    private static final long CACHE_MINUTES = 10L;

    private final DishMapper dishMapper;
    private final DishCategoryMapper dishCategoryMapper;
    private final ShopService shopService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HotRankService hotRankService;

    // ------------------------------------------------------------------
    // 浏览与检索
    // ------------------------------------------------------------------

    /** 店铺内菜品（按分类分组展示） */
    public List<Dish> listByShop(String shopId, String categoryId) {
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getShopId, shopId)
                .eq(Dish::getStatus, 1)
                .eq(StringUtils.hasText(categoryId), Dish::getCategoryId, categoryId)
                .orderByDesc(Dish::getMonthlySales));
    }

    // ------------------------------------------------------------------
    // 场景 1（PPT 第 13 页）：菜品详情缓存
    //   结构：String(JSON) —— cache:dish:detail:{dishId}
    //   过期：10 分钟，加 ±20% 抖动（实测 540~660 秒），防缓存雪崩
    //   防击穿：SETNX 抢 lock:dish:cache:{dishId}（5 秒），只允许一个线程回源重建
    //   一致性：读时回填 + 写后删缓存（先写库、再 delete 缓存，不是 update）
    // ------------------------------------------------------------------
    /**
     * 菜品详情（热点缓存）。
     *
     * <p>Cache-Aside：读时回填、写时删除（见 {@link #evictCache}）。
     *
     * <p>防缓存击穿：热点 key 过期瞬间若大量请求同时未命中，会一起压到数据库。
     * 这里用 Redis 互斥锁（{@code SET NX EX}）保证同一菜品只有一个线程回源重建，
     * 其余线程短暂等待后直接读新缓存；拿不到锁时也会兜底查库，不会死等。
     */
    public Dish getDetail(String dishId) {
        String cacheKey = RedisKeys.DISH_DETAIL + dishId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Dish dish) {
            return dish;
        }

        String lockKey = RedisKeys.LOCK_DISH_CACHE + dishId;
        boolean locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS));
        if (locked) {
            try {
                // 双重检查：等锁期间可能已有其他线程完成回填
                Object again = redisTemplate.opsForValue().get(cacheKey);
                if (again instanceof Dish dish) {
                    return dish;
                }
                Dish dish = dishMapper.selectById(dishId);
                if (dish == null) {
                    throw new BusinessException(CODE_DISH_NOT_FOUND, "菜品不存在");
                }
                redisTemplate.opsForValue().set(cacheKey, dish,
                        jitterMinutes(CACHE_MINUTES), TimeUnit.MINUTES);
                return dish;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        // 未抢到锁：稍等让持锁线程写完缓存，再读一次；仍未命中则直接查库兜底
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Object retry = redisTemplate.opsForValue().get(cacheKey);
        if (retry instanceof Dish dish) {
            return dish;
        }
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(CODE_DISH_NOT_FOUND, "菜品不存在");
        }
        return dish;
    }

    /** 关键词检索：AI 语义搜索的降级兜底 */
    public List<Dish> searchByKeyword(String keyword, String shopId, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        return dishMapper.searchByKeyword(keyword.trim(), shopId, limit);
    }

    /**
     * 上架菜品候选集：AI 点餐、语义搜索、个性化推荐的候选来源。
     *
     * @param shopId 为空表示全平台
     * @param limit  候选集上限（控制注入提示词的 Token 数量）
     */
    public List<Dish> listOnShelfDishes(String shopId, int limit) {
        Page<Dish> page = dishMapper.selectPage(Page.of(1, limit),
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getStatus, 1)
                        .eq(StringUtils.hasText(shopId), Dish::getShopId, shopId)
                        .orderByDesc(Dish::getMonthlySales));
        return page.getRecords();
    }

    /** 按 ID 批量查询菜品（AI 返回菜品ID后回查，避免使用模型生成的菜品信息） */
    public List<Dish> listByIds(List<String> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return List.of();
        }
        return dishMapper.selectBatchIds(dishIds);
    }

    // ------------------------------------------------------------------
    // 商户端维护
    // ------------------------------------------------------------------

    /** 本店菜品列表（可按分类与上下架状态筛选） */
    public List<Dish> listMyDishes(String categoryId, Integer status) {
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getShopId, shopService.currentShopId())
                .eq(StringUtils.hasText(categoryId), Dish::getCategoryId, categoryId)
                .eq(status != null, Dish::getStatus, status)
                .orderByDesc(Dish::getCreateTime));
    }

    /** 新增或修改菜品 */
    @Transactional(rollbackFor = Exception.class)
    public Dish saveDish(Dish form) {
        String shopId = shopService.currentShopId();
        if (form.getPrice() == null || form.getPrice().signum() <= 0) {
            throw new BusinessException(20013, "菜品价格必须大于 0");
        }
        if (form.getStock() != null && form.getStock() < 0) {
            throw new BusinessException(20014, "库存不能为负数");
        }
        if (!StringUtils.hasText(form.getId())) {
            form.setShopId(shopId);
            form.setStatus(form.getStatus() == null ? 1 : form.getStatus());
            form.setStock(form.getStock() == null ? 0 : form.getStock());
            dishMapper.insert(form);
            return form;
        }
        Dish exists = requireOwnDish(form.getId(), shopId);
        form.setShopId(shopId);
        form.setMonthlySales(exists.getMonthlySales());
        dishMapper.updateById(form);
        evictCache(form.getId());
        return form;
    }

    /** 上下架：0下架 1上架 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String dishId, Integer status) {
        Dish dish = requireOwnDish(dishId, shopService.currentShopId());
        dish.setStatus(status);
        dishMapper.updateById(dish);
        evictCache(dishId);
        // 下架会改变榜单结果，同步失效热门榜单
        hotRankService.touchDishRank(dish);
    }

    /** 调整库存；库存为 0 时自动下架（需求文档 5.2.2） */
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(String dishId, Integer stock) {
        if (stock == null || stock < 0) {
            throw new BusinessException(20014, "库存不能为负数");
        }
        Dish dish = requireOwnDish(dishId, shopService.currentShopId());
        dish.setStock(stock);
        if (stock == 0) {
            dish.setStatus(0);
        }
        dishMapper.updateById(dish);
        evictCache(dishId);
        hotRankService.touchDishRank(dish);
    }

    /**
     * 删除菜品（逻辑删除）。
     *
     * <p>此前缺少这个方法，商户端「删除菜品」按钮点了只会拿到 404。
     *
     * <p>dish 表有 {@code @TableLogic deleted} 字段，{@code deleteById} 实际是把
     * deleted 置 1，不会物理删除；历史订单里的 order_item 是成交快照，
     * 因此删菜不会影响已下单的订单展示。
     *
     * <p>权限：只能删本店菜品（店铺由登录态解析）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeDish(String dishId) {
        Dish dish = requireOwnDish(dishId, shopService.currentShopId());
        dishMapper.deleteById(dish.getId());
        evictCache(dishId);
        // 榜单 ZSet 里的成员不会随菜品删除自动消失，必须主动失效，否则榜单会出现已删除菜品
        hotRankService.invalidateDishRank();
        log.info("菜品已删除(逻辑删除): dishId={}, shopId={}", dishId, dish.getShopId());
    }

    /** 菜品分类列表 */    public List<DishCategory> listCategories(String shopId) {
        return dishCategoryMapper.selectList(new LambdaQueryWrapper<DishCategory>()
                .eq(DishCategory::getShopId, shopId)
                .orderByAsc(DishCategory::getSort));
    }

    // ------------------------------------------------------------------
    // 供订单模块（成员4）调用
    // ------------------------------------------------------------------

    /**
     * 扣减库存（在下单事务内调用）。
     *
     * @throws BusinessException 库存不足时抛出，触发事务回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(String dishId, int quantity) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(CODE_DISH_NOT_FOUND, "菜品不存在或已删除");
        }
        if (dish.getStatus() == null || dish.getStatus() != 1) {
            throw new BusinessException(CODE_DISH_OFF_SHELF, "菜品「" + dish.getDishName() + "」已下架");
        }
        int rows = dishMapper.deductStock(dishId, quantity);
        if (rows == 0) {
            throw new BusinessException(CODE_STOCK_NOT_ENOUGH, "菜品「" + dish.getDishName() + "」库存不足");
        }
        evictCache(dishId);
    }

    /** 回补库存（取消订单时调用） */
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(String dishId, int quantity) {
        dishMapper.restoreStock(dishId, quantity);
        evictCache(dishId);
    }

    // ------------------------------------------------------------------

    private Dish requireOwnDish(String dishId, String shopId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(CODE_DISH_NOT_FOUND, "菜品不存在");
        }
        // 数据行级隔离：只能操作本店菜品
        if (!shopId.equals(dish.getShopId())) {
            throw new BusinessException(20015, "无权操作其他店铺的菜品");
        }
        return dish;
    }

    private void evictCache(String dishId) {
        redisTemplate.delete(RedisKeys.DISH_DETAIL + dishId);
    }

    /**
     * 给缓存 TTL 加随机抖动（±20%）。
     *
     * <p>固定 TTL 会让同一批写入的 key 集中过期，形成缓存雪崩；
     * 抖动把过期时间打散，代价只是命中率略有下降。
     */
    private long jitterMinutes(long baseMinutes) {
        long jitter = Math.max(1L, Math.round(baseMinutes * 0.2));
        return baseMinutes + java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(-jitter, jitter + 1);
    }
}
