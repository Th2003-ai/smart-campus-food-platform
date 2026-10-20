package com.campus.delivery.modules.shop.controller;

import com.campus.delivery.common.api.PageResult;
import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.dish.entity.Dish;
import com.campus.delivery.modules.dish.service.DishService;
import com.campus.delivery.modules.shop.entity.Shop;
import com.campus.delivery.modules.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 店铺浏览接口（公开）。主责：成员2，使用方：成员1（学生端首页）。
 */
@Tag(name = "03-店铺（公开）", description = "店铺列表、详情、店内菜品")
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final DishService dishService;

    @Operation(summary = "店铺列表", description = "支持分类筛选、名称搜索，按销量或评分排序")
    @GetMapping
    public Result<PageResult<Shop>> list(@RequestParam(required = false) String categoryId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false, defaultValue = "sales") String sortBy,
                                         @RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(shopService.pageShops(categoryId, keyword, sortBy, pageNum, pageSize));
    }

    @Operation(summary = "店铺详情")
    @GetMapping("/{id}")
    public Result<Shop> detail(@PathVariable String id) {
        return Result.ok(shopService.getDetail(id));
    }

    /**
     * 店内菜品列表（公开）。
     *
     * <p>对应 docs/03-接口规范与协作约定.md 中约定的 {@code GET /shops/{id}/dishes}。
     * 学生端店铺详情页依赖它：原清单打算用 {@code /dishes/search?keyword=''} 兜底，
     * 但后端对空关键词直接返回空列表，实际拿不到数据。
     *
     * <p>{@code DishService.listByShop} 内部已过滤 {@code status = 1}，只返回上架菜品。
     */
    @Operation(summary = "店内菜品列表", description = "学生端店铺详情页用；只返回上架菜品")
    @GetMapping("/{id}/dishes")
    public Result<List<Dish>> dishes(@PathVariable String id,
                                     @RequestParam(required = false) String categoryId) {
        return Result.ok(dishService.listByShop(id, categoryId));
    }
}
