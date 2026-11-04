package com.campus.delivery.modules.cache.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.cache.service.CacheSwitch;
import com.campus.delivery.modules.cache.service.DictCacheService;
import com.campus.delivery.modules.cache.service.HotKeyTracker;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存运维接口（管理端）。对应 PPT 第 15 页（热 key）与第 21 页（静态字典）。
 *
 * <p>答辩演示顺序建议：
 * <ol>
 *   <li>打开菜品详情几次 → {@code GET /admin/cache/hotkeys} 看 TopN 里出现该 key；</li>
 *   <li>{@code POST /admin/cache/hotkeys/reset} 清零，再刷新页面看重新计数；</li>
 *   <li>{@code POST /admin/cache/dicts/refresh} 刷新字典，看日志与耗时；</li>
 *   <li>{@code GET /admin/cache/dicts/compare?table=dish_category} 看缓存 vs 直查库耗时。</li>
 * </ol>
 */
@Tag(name = "22-缓存运维", description = "热 key 探测、字典缓存刷新与对比")
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class CacheOpsController {

    private final DictCacheService dictCacheService;
    private final HotKeyTracker hotKeyTracker;
    private final CacheSwitch cacheSwitch;

    @Operation(summary = "切换防穿透开关", description = "关闭后不缓存空值，用来对比「打库次数」")
    @PostMapping("/switch/null-cache")
    public Result<Map<String, Object>> switchNullCache(@RequestParam boolean enabled) {
        cacheSwitch.setNullValueCache(enabled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nullValueCache", enabled);
        return Result.ok(result);
    }

    @Operation(summary = "热 key TopN", description = "客户端埋点统计，5 分钟滚动窗口")
    @GetMapping("/hotkeys")
    public Result<Map<String, Object>> hotKeys(@RequestParam(defaultValue = "10") int topN) {
        List<Map<String, Object>> list = hotKeyTracker.topN(topN);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topN", list);
        result.put("totalAccess", hotKeyTracker.total());
        result.put("keyCount", hotKeyTracker.keyCount());
        result.put("windowStart", hotKeyTracker.windowStart());
        return Result.ok(result);
    }

    @Operation(summary = "重置热 key 统计", description = "演示时想把计数从 0 开始")
    @PostMapping("/hotkeys/reset")
    public Result<Void> resetHotKeys() {
        hotKeyTracker.clear();
        return Result.ok();
    }

    @Operation(summary = "刷新字典缓存", description = "写时刷新：配置/分类变更后立刻重载")
    @PostMapping("/dicts/refresh")
    public Result<Map<String, Object>> refreshDicts() {
        long start = System.currentTimeMillis();
        dictCacheService.refresh("管理端手动刷新");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("snapshot", dictCacheService.snapshot());
        return Result.ok(result);
    }

    @Operation(summary = "字典缓存 vs 直查库", description = "同一张表连查 3 次，比较耗时")
    @GetMapping("/dicts/compare")
    public Result<Map<String, Object>> compare(@RequestParam(defaultValue = "dish_category") String table) {
        return Result.ok(dictCacheService.compareWithDb(table));
    }

    @Operation(summary = "缓存总览", description = "字典表行数 + 热 key 概况，演示页首屏用")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dicts", dictCacheService.snapshot());
        result.put("dictTables", dictCacheService.tables());
        result.put("hotKeys", hotKeyTracker.topN(10));
        result.put("hotKeyTotal", hotKeyTracker.total());
        result.put("nullValueCacheEnabled", cacheSwitch.isNullValueCacheEnabled());
        return Result.ok(result);
    }
}
