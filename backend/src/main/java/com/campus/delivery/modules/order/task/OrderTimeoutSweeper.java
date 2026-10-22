package com.campus.delivery.modules.order.task;

import com.campus.delivery.modules.order.service.OrderService;
import com.campus.delivery.modules.order.service.OrderTimeoutQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单超时扫描任务。
 *
 * <p>每分钟扫一次 Redis 延时队列，把「已到期且仍是待接单」的订单自动取消，
 * 回补库存并通知相关方。对应 {@code sys_config.order.auto.cancel.minutes} 这个配置项
 * —— 之前这项配置只存在于数据库里，没有任何代码读它，属于未实现的规划功能。
 *
 * <p>为什么不用为每个订单注册一个 {@code ScheduledExecutorService} 定时器：
 * 下单量大时定时器会堆积，且应用重启后全部丢失；Redis ZSet 天然持久化，
 * 重启后未处理的订单仍在队列里。
 *
 * <p>扫描间隔可用 {@code CAMPUS_ORDER_TIMEOUT_SCAN_MS} 调整，默认 60 秒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutSweeper {

    private final OrderTimeoutQueue queue;
    private final OrderService orderService;

    /** 定时扫描（fixedDelay：上一次执行完再等一个间隔，避免任务叠加） */
    @Scheduled(fixedDelayString = "${campus.order.timeout-scan-ms:60000}",
            initialDelayString = "${campus.order.timeout-scan-initial-delay-ms:30000}")
    public void scheduledSweep() {
        Map<String, Object> result = sweep();
        int cancelled = (int) result.get("cancelled");
        if (cancelled > 0) {
            log.warn("超时未接单自动取消 {} 单（扫描 {} 单，队列剩余 {}）",
                    cancelled, result.get("scanned"), result.get("queueSize"));
        }
    }

    /**
     * 执行一次扫描，供定时任务与管理端手动触发共用。
     *
     * @return scanned 取出的到期单数 / cancelled 实际取消数 / queueSize 剩余积压
     */
    public Map<String, Object> sweep() {
        int minutes = queue.autoCancelMinutes();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("autoCancelMinutes", minutes);

        if (minutes <= 0) {
            result.put("scanned", 0);
            result.put("cancelled", 0);
            result.put("queueSize", queue.size());
            result.put("enabled", false);
            return result;
        }

        List<String> due = queue.claimDue();
        int cancelled = 0;
        for (String orderId : due) {
            boolean done = orderService.systemCancelIfPending(orderId,
                    "超时 " + minutes + " 分钟未接单，系统自动取消");
            if (done) {
                cancelled++;
                log.info("订单超时自动取消: orderId={}", orderId);
            }
        }

        result.put("scanned", due.size());
        result.put("cancelled", cancelled);
        result.put("queueSize", queue.size());
        result.put("enabled", true);
        return result;
    }
}
