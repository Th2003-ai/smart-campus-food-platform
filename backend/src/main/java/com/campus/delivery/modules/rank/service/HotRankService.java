package com.campus.delivery.modules.rank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.modules.dish.entity.Dish;
import com.campus.delivery.modules.dish.mapper.DishMapper;
import com.campus.delivery.modules.shop.entity.Shop;
import com.campus.delivery.modules.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 热门店铺 / 热门菜品榜单服务。
 *
 * <p>对应技术分享 PPT 第 13 页的第 8 个 Redis 场景，落在两个 Key 上：
 * <ul>
 *   <li>{@link RedisKeys#HOT_DISH}：ZSet，member = 菜品 ID，score = 月销量；</li>
 *   <li>{@link RedisKeys#HOT_SHOP}：ZSet，member = 店铺 ID，score = 月销量。</li>
 * </ul>
 *
 * <p><b>为什么用 ZSet 而不是 List / 数据库排序？</b>
 * ZSet 的 {@code score} 天然承载「销量」这个权重，取 TopN 就是一条
 * {@code ZREVRANGE key 0 N-1}，排序在 Redis 内部完成，应用层既不用把全表捞回来，
 * 也不用写 {@code ORDER BY}。这是「数据结构贴合业务」最典型的一处：榜单本身就是有序集合。
 *
 * <p><b>为什么要额外缓存一份明细快照？</b>
 * ZSet 里只存 ID 和分数，前端要展示菜名、图片、价格，如果每次都按 ID 回表查，
 * TopN 就是 N 次查询。所以再加一个 {@code cache:dish:hot:list} 保存 TopN 的实体快照，
 * 读的时候一次拿到完整对象。
 *
 * <p><b>一致性策略：允许短暂过期。</b>
 * 榜单是「晚几秒更新也无所谓」的数据，因此采用三件套：
 * <ol>
 *   <li>ZSet 只存分数，负责排序；</li>
 *   <li>明细快照负责展示，避免回表；</li>
 *   <li>写操作（上下架、改库存、改销量）主动失效，兜底靠 TTL 5 分钟 ±20% 抖动收敛。</li>
 * </ol>
 *
 * <p><b>Redis 故障时的降级</b>：所有方法都包了 try/catch，写缓存失败只记日志，
 * 直接返回数据库查询结果——榜单属于「可降级」的功能，不能因为它挂掉整个点餐流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankService {

    /** 榜单默认取前 N 名 */
    private static final int DEFAULT_TOP_N = 10;

    /** 榜单明细快照的有效期（分钟）；实际写入时会加 ±20% 抖动 */
    private static final long LIST_CACHE_MINUTES = 5L;

    /** 重建时最多装载的条目数，避免一次把全表塞进 Redis */
    private static final int REBUILD_LIMIT = 50;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DishMapper dishMapper;
    private final ShopMapper shopMapper;

    // ==================================================================
    // 热门菜品
    // ==================================================================

    /**
     * 热门菜品榜单。
     *
     * <p>读取顺序：先读明细快照 {@code cache:dish:hot:list}；
     * 未命中则调用 {@link #rebuildHotDishes(int)} 查库重建，
     * 重建时会同时刷新 ZSet（顺序）和明细快照（展示内容）。
     *
     * @param limit 取前几名，传 null 或非正数时取 {@value #DEFAULT_TOP_N}
     * @return 菜品列表，Redis 不可用时返回数据库直查结果
     */
    @SuppressWarnings("unchecked")
    public List<Dish> hotDishes(Integer limit) {
        int topN = normalizeLimit(limit);
        Object cached = redisTemplate.opsForValue().get(RedisKeys.HOT_DISH_LIST);
        if (cached instanceof List<?> list && !list.isEmpty()) {
            List<Dish> dishes = (List<Dish>) list;
            return dishes.size() <= topN ? dishes : dishes.subList(0, topN);
        }
        return rebuildHotDishes(topN);
    }

    /**
     * 重建热门菜品榜单：查库 → 写 ZSet（id → 月销量）→ 写明细快照。
     *
     * <p>ZSet 与明细快照同时刷新，保证「排名」与「展示内容」来自同一份数据，
     * 不会出现排名第 3 但明细里是另一个菜品的错位。
     *
     * @param topN 返回条数上限
     */
    public List<Dish> rebuildHotDishes(int topN) {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, 1)
                .orderByDesc(Dish::getMonthlySales)
                .last("LIMIT " + REBUILD_LIMIT));
        if (dishes.isEmpty()) {
            return List.of();
        }
        try {
            redisTemplate.delete(RedisKeys.HOT_DISH);
            for (Dish dish : dishes) {
                double score = dish.getMonthlySales() == null ? 0D : dish.getMonthlySales();
                redisTemplate.opsForZSet().add(RedisKeys.HOT_DISH, dish.getId(), score);
            }
            // TTL 加随机抖动：避免大量榜单在同一时刻过期，形成缓存雪崩
            redisTemplate.opsForValue().set(RedisKeys.HOT_DISH_LIST, dishes,
                    jitterMinutes(LIST_CACHE_MINUTES), TimeUnit.MINUTES);
        } catch (Exception e) {
            // Redis 故障不影响主流程：直接返回数据库结果
            log.warn("写入热门菜品榜单缓存失败，降级为直接查库: {}", e.getMessage());
        }
        return dishes.size() <= topN ? dishes : dishes.subList(0, topN);
    }

    /**
     * 菜品销量变化后同步榜单（增量更新）。
     *
     * <p>只更新受影响的那一条 ZSet 分数，并用最新分数重写该菜品，
     * 避免每次都全量重建。明细快照整体失效，下次读取时重建——
     * 这是「增量改分数、整体换快照」的折中：分数必须准，快照允许短暂空缺。
     */
    public void touchDishRank(Dish dish) {
        if (dish == null || dish.getId() == null) {
            return;
        }
        try {
            double score = dish.getMonthlySales() == null ? 0D : dish.getMonthlySales();
            redisTemplate.opsForZSet().add(RedisKeys.HOT_DISH, dish.getId(), score);
            redisTemplate.delete(RedisKeys.HOT_DISH_LIST);
        } catch (Exception e) {
            log.warn("更新热门菜品榜单失败: {}", e.getMessage());
        }
    }

    /**
     * 取热门菜品排行（只返回 ID 与顺序），用于校验 ZSet 排名是否正确。
     *
     * <p>这是给验证 / 演示用的旁路方法：直接读 ZSet，不碰明细快照。
     */
    public List<String> hotDishIds(int topN) {
        return redisTemplate.opsForZSet()
                .reverseRange(RedisKeys.HOT_DISH, 0, normalizeLimit(topN) - 1)
                .stream().map(String::valueOf).toList();
    }

    // ==================================================================
    // 热门店铺
    // ==================================================================

    /**
     * 热门店铺榜单，逻辑与 {@link #hotDishes(Integer)} 一致，只是过滤条件换成「未被封禁」。
     *
     * @param limit 取前几名，传 null 或非正数时取 {@value #DEFAULT_TOP_N}
     */
    @SuppressWarnings("unchecked")
    public List<Shop> hotShops(Integer limit) {
        int topN = normalizeLimit(limit);
        Object cached = redisTemplate.opsForValue().get(RedisKeys.HOT_SHOP_LIST);
        if (cached instanceof List<?> list && !list.isEmpty()) {
            List<Shop> shops = (List<Shop>) list;
            return shops.size() <= topN ? shops : shops.subList(0, topN);
        }
        return rebuildHotShops(topN);
    }

    /**
     * 重建热门店铺榜单：查库（排除封禁店铺）→ 写 ZSet → 写明细快照。
     *
     * @param topN 返回条数上限
     */
    public List<Shop> rebuildHotShops(int topN) {
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getBanStatus, 0)
                .orderByDesc(Shop::getMonthlySales)
                .last("LIMIT " + REBUILD_LIMIT));
        if (shops.isEmpty()) {
            return List.of();
        }
        try {
            redisTemplate.delete(RedisKeys.HOT_SHOP);
            for (Shop shop : shops) {
                double score = shop.getMonthlySales() == null ? 0D : shop.getMonthlySales();
                redisTemplate.opsForZSet().add(RedisKeys.HOT_SHOP, shop.getId(), score);
            }
            redisTemplate.opsForValue().set(RedisKeys.HOT_SHOP_LIST, shops,
                    jitterMinutes(LIST_CACHE_MINUTES), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入热门店铺榜单缓存失败，降级为直接查库: {}", e.getMessage());
        }
        return shops.size() <= topN ? shops : shops.subList(0, topN);
    }

    /**
     * 菜品删除后让菜品榜单失效。
     *
     * <p>只清详情缓存不够：ZSet 成员独立存储，菜品逻辑删除后仍会留在
     * {@code cache:dish:hot} 里，榜单就会返回一个已查不到的菜品（score 停在删除前的值）。
     * 所以这里连 ZSet 一起删，下次读取时由 {@link #rebuildHotDishes(int)} 重建。
     */
    public void invalidateDishRank() {
        try {
            redisTemplate.delete(List.of(RedisKeys.HOT_DISH_LIST, RedisKeys.HOT_DISH));
        } catch (Exception e) {
            log.warn("清理热门菜品榜单失败: {}", e.getMessage());
        }
    }

    /**
     * 店铺信息变化（改资料、开关营业、封禁 / 解封）后让店铺榜单失效。
     *
     * <p>与菜品榜同理：ZSet 里的 member 不会因为数据库更新而自动消失，
     * 封禁的店铺如果留在榜单里，前端点进去就是 404。
     */
    public void invalidateShopRank() {
        try {
            redisTemplate.delete(List.of(RedisKeys.HOT_SHOP_LIST, RedisKeys.HOT_SHOP));
        } catch (Exception e) {
            log.warn("清理热门店铺榜单失败: {}", e.getMessage());
        }
    }

    // ==================================================================

    /** 归一化 limit：空值 / 非正数取默认值，并且不超过单次重建上限 */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(limit, REBUILD_LIMIT);
    }

    /**
     * 给 TTL 加随机抖动（±20%）。
     *
     * <p>固定 TTL 会让同一批写入的 key 在同一秒集中过期，
     * 形成「缓存雪崩」；抖动可把过期时间打散。
     *
     * @param baseMinutes 基准分钟数
     * @return 加抖动后的分钟数，最少抖动 1 分钟，保证结果始终为正
     */
    private long jitterMinutes(long baseMinutes) {
        long jitter = Math.max(1L, Math.round(baseMinutes * 0.2));
        return baseMinutes + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
    }
}
