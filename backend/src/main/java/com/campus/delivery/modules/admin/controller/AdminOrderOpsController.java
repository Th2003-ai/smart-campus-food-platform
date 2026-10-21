package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.order.task.OrderTimeoutSweeper;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端·订单运维接口。
 *
 * <p>目前只暴露「手动触发超时订单扫描」：定时任务每分钟自动跑一次，
 * 但演示时不可能等 15 分钟，所以提供一个手动入口，
 * 配合把 {@code sys_config.order.auto.cancel.minutes} 调小即可现场演示。
 */
@Tag(name = "18-管理端·订单运维", description = "超时未接单订单的手动扫描")
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class AdminOrderOpsController {

    private final OrderTimeoutSweeper timeoutSweeper;

    @Operation(summary = "手动触发超时订单扫描",
            description = "返回扫描数/取消数/队列积压；定时任务每分钟也会自动执行")
    @PostMapping("/sweep-timeout")
    public Result<Map<String, Object>> sweepTimeout() {
        return Result.ok(timeoutSweeper.sweep());
    }
}
