package com.campus.delivery.modules.cache.service;

import com.campus.delivery.modules.rank.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * 热点数据启动预热（内存数据库 Redis 预热）。
 *
 * <p>字典预热由 {@link DictCacheService} 负责，本类负责「会变但访问极频繁」的热点业务数据：
 * 应用启动后主动把热门菜品、热门店铺榜单重建到 Redis，让用户第一次访问就命中缓存，
 * 而不是等到第一个请求才临时查库（避免冷启动瞬间的回源与缓存击穿）。
 *
 * <p>预热失败只告警、不影响应用启动；榜单本身支持降级，运行期仍可懒加载重建。
 */
@Slf4j
@Order(20)
@Service
@RequiredArgsConstructor
public class CacheWarmupService implements ApplicationRunner {

    /** 预热榜单取前 N 名 */
    private static final int WARMUP_TOP_N = 10;

    private final HotRankService hotRankService;

    /** 预热开关：默认开启，设 false 可对比「不预热」的冷启动表现 */
    @Value("${campus.cache.hot-warmup:true}")
    private boolean warmupEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            log.warn("热点数据预热已关闭（campus.cache.hot-warmup=false）");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            int dishes = hotRankService.rebuildHotDishes(WARMUP_TOP_N).size();
            int shops = hotRankService.rebuildHotShops(WARMUP_TOP_N).size();
            log.info("热点数据启动预热完成：热门菜品 {} 条、热门店铺 {} 条，耗时 {} ms",
                    dishes, shops, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("热点数据启动预热失败（不影响启动，运行期可懒加载）: {}", e.getMessage());
        }
    }
}
