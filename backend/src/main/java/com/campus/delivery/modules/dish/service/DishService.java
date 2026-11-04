package com.campus.delivery.modules.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.cache.service.CacheSwitch;
import com.campus.delivery.modules.cache.service.HotKeyTracker;
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
    /** 热 key 探测：记录每个缓存 key 的访问次数（PPT 第 15 页） */
    private final HotKeyTracker hotKeyTracker;
    /** 缓存策略运行期开关（答辩演示对比用） */
    private final CacheSwitch cacheSwitch;

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
    // 场景 1（PPT 第 13 页）：菜品详情缓存 —— 缓存三兄弟的完整对策
    //   结构：String(JSON) —— cache:dish:detail:{dishId}
    //   过期：10 分钟 ±20% 抖动（防雪崩）
    //   防击穿：SETNX lock:dish:cache:{dishId}（5 秒）+ 双重检查
    //   防穿透：不存在的菜品缓存空值标记 cache:dish:null:{dishId}（60 秒）
    //   降级：Redis 不可用时直查数据库，只牺牲性能、不牺牲可用性
    //   一致性：读时回填 + 写后删缓存（先写库、再 delete）
    // ------------------------------------------------------------------
    /**
     * 菜品详情（热点缓存）。
     *
     * <p>读路径按「空值标记 → 详情缓存 → 互斥重建 → 查库兜底」四步走：
     * <ol>
     *   <li><b>穿透</b>：库里也不存在的 ID 会缓存 60 秒空值标记，短时间重复请求不再打库；</li>
     *   <li><b>击穿</b>：热点 key 过期瞬间用 {@code SET NX EX} 只放一个线程回源，其余短暂等待后读新缓存；</li>
     *   <li><b>雪崩</b>：TTL 加 ±20% 抖动，把过期时间打散；</li>
     *   <li><b>降级</b>：Redis 抛异常时记 warn 并直查数据库，不把缓存故障放大成接口 500。</li>
     * </ol>
     */
    public Dish getDetail(String dishId) {
        String cacheKey = RedisKeys.DISH_DETAIL + dishId;
        String nullKey = RedisKeys.DISH_NULL + dishId;
        hotKeyTracker.record(cacheKey);

        // Redis 熔断期内：直接查库，不再尝试缓存（避免每个请求都等一次超时）
        if (!cacheSwitch.isRedisAvailable()) {
            return loadAndCache(dishId, cacheKey, nullKey);
        }

        long redisCallStart = System.currentTimeMillis();
        boolean redisAvailable = true;

        try {
            // ① 防穿透：空值标记命中，直接返回，不打库（可被演示开关关闭）
            if (isNullCached(nullKey)) {
                throw dishNotFound();
            }
            // ② 详情缓存命中
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            cacheSwitch.markRedisUp();      // 读成功说明 Redis 已恢复，解除熔断
            if (cached instanceof Dish dish) {
                return dish;
            }
        } catch (BusinessException e) {
            throw e;    // 空值标记属于正常业务拒绝，不是缓存故障
        } catch (Exception e) {
            // ③ 降级：Redis 不可用，直接查库，并熔断 30 秒
            redisAvailable = false;
            cacheSwitch.markRedisDown(System.currentTimeMillis() - redisCallStart);
            log.warn("Redis 不可用，降级直查数据库: key={}, err={}", cacheKey, e.getMessage());
        }

        if (redisAvailable) {
            String lockKey = RedisKeys.LOCK_DISH_CACHE + dishId;
            boolean locked = false;
            try {
                locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS));
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("抢缓存重建锁失败，降级直查数据库: {}", e.getMessage());
            }

            if (redisAvailable && locked) {
                try {
                    // 双重检查：等锁期间可能已有其他线程完成回填
                    Object again = redisTemplate.opsForValue().get(cacheKey);
                    if (again instanceof Dish dish) {
                        return dish;
                    }
                    return loadAndCache(dishId, cacheKey, nullKey);
                } finally {
                    safeDelete(lockKey);
                }
            }

            if (redisAvailable) {
                // 未抢到锁：短暂等待让持锁线程写完缓存，再读一次；仍未命中则查库兜底
                sleepQuietly(50L);
                // 等锁期间别人可能已经确认「这个 ID 不存在」并写了空值标记，
                // 这里必须再查一次，否则等待的线程会一起回源（实测能从 22 次降到 1 次）
                if (isNullCached(nullKey)) {
                    throw dishNotFound();
                }
                try {
                    Object retry = redisTemplate.opsForValue().get(cacheKey);
                    if (retry instanceof Dish dish) {
                        return dish;
                    }
                } catch (Exception ignore) {
                    // 读失败就走下面的查库兜底
                }
            }
        }

        // ④ 最终兜底：直接查库（也是 Redis 故障时唯一的路径）
        return loadAndCache(dishId, cacheKey, nullKey);
    }

    /** 查库 → 回填缓存；库里也没有就写空值标记（防穿透） */
    private Dish loadAndCache(String dishId, String cacheKey, String nullKey) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            cacheNullValue(nullKey);
            throw dishNotFound();
        }
        safeSet(cacheKey, dish);
        return dish;
    }

    private BusinessException dishNotFound() {
        return new BusinessException(CODE_DISH_NOT_FOUND, "菜品不存在");
    }

    /** 是否命中「空值标记」（Redis 异常或熔断时按未命中处理，走查库兜底） */
    private boolean isNullCached(String nullKey) {
        if (!cacheSwitch.isNullValueCacheEnabled() || !cacheSwitch.isRedisAvailable()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(nullKey));
        } catch (Exception e) {
            return false;
        }
    }

    /** 空值缓存 60 秒：同一个假 ID 短时间内反复请求不会再打库 */
    private void cacheNullValue(String nullKey) {
        if (!cacheSwitch.isNullValueCacheEnabled() || !cacheSwitch.isRedisAvailable()) {
            return;   // 演示「关闭防穿透」或 Redis 熔断时故意不写
        }
        try {
            redisTemplate.opsForValue().set(nullKey, "1", 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("写空值缓存失败（忽略）: {}", e.getMessage());
        }
    }

    private void safeSet(String cacheKey, Dish dish) {
        if (!cacheSwitch.isRedisAvailable()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, dish,
                    jitterMinutes(CACHE_MINUTES), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("回填缓存失败（忽略，不影响本次返回）: {}", e.getMessage());
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("删除缓存 key 失败（忽略）: {}", e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
