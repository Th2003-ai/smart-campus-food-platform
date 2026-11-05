package com.campus.delivery.modules.cache.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.cache.service.DictCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 字典数据接口（公开）。对应 PPT 第 21 页「静态字典数据」。
 *
 * <p>读的是启动时预热好的内存镜像，不查库、不走 Redis —— 这是它和「菜品详情缓存」最大的区别。
 */
@Tag(name = "21-字典数据", description = "分类/配置/权限等静态字典（启动预热、常驻内存）")
@RestController
@RequestMapping("/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictCacheService dictCacheService;

    @Operation(summary = "字典概览", description = "每张表的行数、总行数、加载时间")
    @GetMapping
    public Result<Map<String, Object>> snapshot() {
        return Result.ok(dictCacheService.snapshot());
    }

    @Operation(summary = "字典明细", description = "table 取值见概览接口返回的 tables")
    @GetMapping("/{table}")
    public Result<List<Map<String, Object>>> detail(@PathVariable String table) {
        return Result.ok(dictCacheService.detail(table));
    }
}
