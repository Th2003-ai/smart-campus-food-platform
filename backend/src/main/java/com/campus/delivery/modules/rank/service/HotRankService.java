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
 * 鐑棬搴楅摵 / 鐑棬鑿滃搧姒滃崟鏈嶅姟銆? *
 * <p>瀵瑰簲 {@code RedisKeys.HOT_SHOP} 涓?{@code RedisKeys.HOT_DISH} 涓や釜 Key 鐨勮惤鍦板疄鐜般€? *
 * <p><b>涓轰粈涔堢敤鏈夊簭闆嗗悎锛圸Set锛?/b>锛? * ZSet 鐨?{@code score} 鐩存帴鎵胯浇銆屾湀閿€閲忋€嶏紝鍙?TopN 灏辨槸 {@code ZREVRANGE}锛? * 鎺掑簭鍦?Redis 鍐呴儴瀹屾垚锛屼笉闇€瑕佹妸鍏ㄨ〃鎹炲埌搴旂敤灞傚啀鎺掑簭銆? * 杩欐槸銆屾暟鎹粨鏋勮创鍚堜笟鍔°€嶇殑鍏稿瀷鐢ㄦ硶锛氭鍗曞ぉ鐒舵槸鏈夊簭闆嗗悎銆? *
 * <p><b>涓€鑷存€х瓥鐣?/b>锛氭鍗曞睘浜庛€屽厑璁哥煭鏆傝繃鏈熴€嶇殑鏁版嵁锛岄噰鐢? * 銆孼Set 鎺掕 + List 鏄庣粏缂撳瓨 + 鐭?TTL銆嶇殑缁勫悎锛? * <ul>
 *   <li>ZSet锛氫繚瀛?id 鈫?鏈堥攢閲忥紝璐熻矗鎺掑簭锛?/li>
 *   <li>List 缂撳瓨锛氫繚瀛?TopN 鐨勫疄浣撳揩鐓э紝閬垮厤姣忔鍙栨鍗曢兘鍥炶〃鏌ヨ鎯咃紱</li>
 *   <li>TTL 5 鍒嗛挓 + 鍐欐搷浣滀富鍔ㄥけ鏁堬細閿€閲忓彉鍖栧悗鏈€澶?5 鍒嗛挓鍐呰嚜鍔ㄦ敹鏁涳紱</li>
 *   <li>Redis 涓嶅彲鐢ㄦ椂闄嶇骇涓虹洿鎺ユ煡搴擄紙{@code rebuild}锛夛紝淇濊瘉鍔熻兘鍙敤銆?/li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankService {

    /** 姒滃崟榛樿鍙栧墠 N 鍚?*/
    private static final int DEFAULT_TOP_N = 10;
    /** 姒滃崟鏄庣粏缂撳瓨鏈夋晥鏈燂紙鍒嗛挓锛?*/
    private static final long LIST_CACHE_MINUTES = 5L;
    /** 閲嶅缓鏃舵渶澶氳杞界殑鏉＄洰鏁帮紝閬垮厤涓€娆℃妸鍏ㄨ〃濉炶繘 Redis */
    private static final int REBUILD_LIMIT = 50;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DishMapper dishMapper;
    private final ShopMapper shopMapper;

    // ==================================================================
    // 鐑棬鑿滃搧
    // ==================================================================

    /**
     * 鐑棬鑿滃搧姒滃崟銆?     *
     * <p>鍏堣鏄庣粏缂撳瓨锛涙湭鍛戒腑鍒欓噸寤猴紙鏌ュ簱 鈫?鍚屾椂鍒锋柊 ZSet 涓?List 缂撳瓨锛夈€?     */
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
     * 閲嶅缓鐑棬鑿滃搧姒滃崟锛氭煡搴?鈫?鍐?ZSet锛坕d 鈫?鏈堥攢閲忥級鈫?鍐欐槑缁嗙紦瀛樸€?     *
     * <p>ZSet 涓庢槑缁嗙紦瀛樺悓鏃跺埛鏂帮紝淇濊瘉銆屾帓琛屻€嶄笌銆屽睍绀哄唴瀹广€嶆潵鑷悓涓€浠芥暟鎹€?     */
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
            // TTL 鍔犻殢鏈烘姈鍔細閬垮厤澶ч噺姒滃崟鍚屼竴鏃跺埢杩囨湡閫犳垚缂撳瓨闆穿
            redisTemplate.opsForValue().set(RedisKeys.HOT_DISH_LIST, dishes,
                    jitterMinutes(LIST_CACHE_MINUTES), TimeUnit.MINUTES);
        } catch (Exception e) {
            // Redis 鏁呴殰涓嶅奖鍝嶄富娴佺▼锛氱洿鎺ヨ繑鍥炴暟鎹簱缁撴灉
            log.warn("鍐欏叆鐑棬鑿滃搧姒滃崟缂撳瓨澶辫触锛岄檷绾т负鐩存帴鏌ュ簱: {}", e.getMessage());
        }
        return dishes.size() <= topN ? dishes : dishes.subList(0, topN);
    }

    /**
     * 鑿滃搧閿€閲忓彉鍖栧悗鍚屾姒滃崟锛堝閲忔洿鏂帮級銆?     *
     * <p>鍙洿鏂板彈褰卞搷鐨勯偅涓€鏉?ZSet 鍒嗘暟锛屽苟浠ユ渶鏂板垎鏁伴噸鏂板啓鍥炶鑿滃搧锛?     * 閬垮厤姣忔閮藉叏閲忛噸寤恒€傛槑缁嗙紦瀛樻暣浣撳け鏁堬紝涓嬫璇诲彇鏃堕噸寤恒€?     */
    public void touchDishRank(Dish dish) {
        if (dish == null || dish.getId() == null) {
            return;
        }
        try {
            double score = dish.getMonthlySales() == null ? 0D : dish.getMonthlySales();
            redisTemplate.opsForZSet().add(RedisKeys.HOT_DISH, dish.getId(), score);
            redisTemplate.delete(RedisKeys.HOT_DISH_LIST);
        } catch (Exception e) {
            log.warn("鏇存柊鐑棬鑿滃搧姒滃崟澶辫触: {}", e.getMessage());
        }
    }

    /** 鍙栫儹闂ㄨ彍鍝佹帓琛岋紙鍙繑鍥?id 涓庡垎鏁帮紝鐢ㄤ簬鏍￠獙 ZSet 鎺掑悕鏄惁姝ｇ‘锛?*/
    public List<String> hotDishIds(int topN) {
        return redisTemplate.opsForZSet()
                .reverseRange(RedisKeys.HOT_DISH, 0, normalizeLimit(topN) - 1)
                .stream().map(String::valueOf).toList();
    }

    // ==================================================================
    // 鐑棬搴楅摵
    // ==================================================================

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
            log.warn("鍐欏叆鐑棬搴楅摵姒滃崟缂撳瓨澶辫触锛岄檷绾т负鐩存帴鏌ュ簱: {}", e.getMessage());
        }
        return shops.size() <= topN ? shops : shops.subList(0, topN);
    }

    /** 搴楅摵鏁版嵁鍙樺寲鍚庤姒滃崟澶辨晥锛堜笅娆¤鍙栭噸寤猴級 */
    /**
     * 菜品删除后让菜品榜单失效。
     *
     * <p>只清详情缓存不够：ZSet 成员独立存储，菜品逻辑删除后仍会留在
     * {@code cache:dish:hot} 里，榜单就会返回一个已查不到的菜品（score 停在删除前的值）。
     */
    public void invalidateDishRank() {
        try {
            redisTemplate.delete(List.of(RedisKeys.HOT_DISH_LIST, RedisKeys.HOT_DISH));
        } catch (Exception e) {
            log.warn("clear dish rank failed: {}", e.getMessage());
        }
    }

    public void invalidateShopRank() {
        try {
            redisTemplate.delete(List.of(RedisKeys.HOT_SHOP_LIST, RedisKeys.HOT_SHOP));
        } catch (Exception e) {
            log.warn("娓呯悊鐑棬搴楅摵姒滃崟澶辫触: {}", e.getMessage());
        }
    }

    // ==================================================================

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(limit, REBUILD_LIMIT);
    }

    /**
     * 缁?TTL 鍔犻殢鏈烘姈鍔紙卤20%锛夈€?     *
     * <p>鍥哄畾 TTL 浼氳鍚屼竴鎵瑰啓鍏ョ殑 key 鍦ㄥ悓涓€绉掗泦涓繃鏈燂紝
     * 褰㈡垚銆岀紦瀛橀洩宕┿€嶏紱鎶栧姩鍙妸杩囨湡鏃堕棿鎵撴暎銆?     */
    private long jitterMinutes(long baseMinutes) {
        long jitter = Math.max(1L, Math.round(baseMinutes * 0.2));
        return baseMinutes + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
    }
}
