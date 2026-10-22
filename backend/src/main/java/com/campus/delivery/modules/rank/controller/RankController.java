package com.campus.delivery.modules.rank.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.dish.entity.Dish;
import com.campus.delivery.modules.rank.service.HotRankService;
import com.campus.delivery.modules.shop.entity.Shop;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 热门榜单接口（公开）。主责：成员2（数据来自菜品与店铺域）。
 *
 * <p>榜单由 Redis 有序集合承载：{@code cache:dish:hot}、{@code cache:shop:hot}。
 * 读取路径为「明细缓存 → 未命中重建」，不直接压数据库。
 */
@Tag(name = "11-热门榜单", description = "热门菜品 / 热门店铺排行（Redis ZSet）")
@RestController
@RequestMapping("/rank")
@RequiredArgsConstructor
public class RankController {

    private final HotRankService hotRankService;

    @Operation(summary = "热门菜品榜", description = "按月销量倒序，取自 Redis 有序集合")
    @GetMapping("/dishes")
    public Result<List<Dish>> hotDishes(@RequestParam(required = false) Integer limit) {
        return Result.ok(hotRankService.hotDishes(limit));
    }

    @Operation(summary = "热门店铺榜", description = "按月销量倒序，取自 Redis 有序集合")
    @GetMapping("/shops")
    public Result<List<Shop>> hotShops(@RequestParam(required = false) Integer limit) {
        return Result.ok(hotRankService.hotShops(limit));
    }

    @Operation(summary = "重建热门榜单", description = "运维/排查用：强制按数据库最新销量重算 ZSet 与明细缓存")
    @PostMapping("/rebuild")
    public Result<Void> rebuild(@RequestParam(defaultValue = "10") Integer limit) {
        hotRankService.rebuildHotDishes(limit);
        hotRankService.rebuildHotShops(limit);
        return Result.ok();
    }
}
