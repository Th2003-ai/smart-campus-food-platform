package com.campus.delivery.modules.dish.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.common.util.DishCoverGenerator;
import com.campus.delivery.modules.dish.entity.Dish;
import com.campus.delivery.modules.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 菜品浏览接口（公开）。主责：成员2，使用方：成员1（学生端）。
 */
@Tag(name = "05-菜品（公开）", description = "菜品列表、详情、关键词检索、自动封面")
@RestController
@RequestMapping("/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    @Operation(summary = "菜品详情")
    @GetMapping("/{id}")
    public Result<Dish> detail(@PathVariable String id) {
        return Result.ok(dishService.getDetail(id));
    }

    @Operation(summary = "菜品关键词检索", description = "AI 语义搜索的降级兜底方案")
    @GetMapping("/search")
    public Result<List<Dish>> search(@RequestParam String keyword,
                                     @RequestParam(required = false) String shopId,
                                     @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(dishService.searchByKeyword(keyword, shopId, limit));
    }

    /**
     * 菜品自动封面（SVG）。
     *
     * <p>背景：本项目没有对象存储、也没有真实菜品照片，{@code dish.image} 全为空。
     * 前端在 {@code image} 为空时回退到本接口，按「菜品名 + 标签」生成一张确定性封面
     * （食物图标 + 渐变底色 + 菜名），保证任何菜品都有图，不依赖外网图片源。
     *
     * <p>一旦 {@code dish.image} 有值，前端会优先用真实图片，本接口自动退居兜底。
     *
     * <p>返回 {@code image/svg+xml} 而不是 {@code Result}，因为这是图片资源，
     * 直接给 {@code <img src>} / {@code el-image} 使用。
     */
    @Operation(summary = "菜品自动封面", description = "image 为空时的兜底封面，返回 SVG")
    @GetMapping(value = "/{id}/cover.svg", produces = "image/svg+xml;charset=UTF-8")
    public ResponseEntity<String> cover(@PathVariable String id) {
        Dish dish;
        try {
            dish = dishService.getDetail(id);
        } catch (BusinessException e) {
            // 菜品不存在时给 404，避免返回 JSON 破坏了 SVG 的 Content-Type
            return ResponseEntity.notFound().build();
        }
        String svg = DishCoverGenerator.generate(dish.getDishName(), dish.getTags());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml;charset=UTF-8"))
                // 内容由菜名决定、短期不会变，允许浏览器缓存以减少重复请求
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(svg);
    }
}
