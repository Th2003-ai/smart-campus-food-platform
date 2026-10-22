package com.campus.delivery.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.delivery.modules.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 订单数据访问。主责：成员4。
 *
 * <p>单表操作继承 {@link BaseMapper}；数据看板所需的统计查询写在本接口
 * （复杂 SQL 建议移到 {@code resources/mapper/OrderMapper.xml}）。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 数据看板：按日统计订单量与销售额（供成员3调用）。
     *
     * <p>返回字段：stat_date / order_count / sales_amount
     *
     * <p>注意：{@code orders} 表没有 {@code deleted} 字段（见 database/schema.sql
     * 与 database/README.md 第五节的软删除表清单），订单的「取消」语义由
     * {@code order_status = 5} 表达，因此这里不做逻辑删除过滤。
     */
    @Select("SELECT DATE(create_time) AS stat_date, COUNT(*) AS order_count, "
            + "IFNULL(SUM(total_amount), 0) AS sales_amount "
            + "FROM orders WHERE order_status = 4 "
            + "AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(create_time) ORDER BY stat_date")
    List<Map<String, Object>> statDailyTrend(@Param("days") int days);

    /**
     * 商户端「经营概览」：本店订单聚合指标。
     *
     * <p>返回 key 全部是 SQL 别名（驼峰），因为 Map 结果不会走
     * {@code map-underscore-to-camel-case}（该配置只作用于实体映射）。
     *
     * @param shopId 店铺ID（由 Service 从登录态解析，不接受前端传入）
     */
    @Select("SELECT COUNT(*) AS totalOrders, "
            + "IFNULL(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN 1 ELSE 0 END), 0) AS todayOrders, "
            + "IFNULL(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN total_amount ELSE 0 END), 0) AS todaySales, "
            + "IFNULL(SUM(CASE WHEN order_status = 0 THEN 1 ELSE 0 END), 0) AS pendingCount, "
            + "IFNULL(SUM(CASE WHEN order_status = 1 THEN 1 ELSE 0 END), 0) AS preparingCount, "
            + "IFNULL(SUM(CASE WHEN order_status = 2 THEN 1 ELSE 0 END), 0) AS pickingCount, "
            + "IFNULL(SUM(CASE WHEN order_status = 3 THEN 1 ELSE 0 END), 0) AS deliveringCount, "
            + "IFNULL(SUM(CASE WHEN order_status = 4 THEN 1 ELSE 0 END), 0) AS finishedCount, "
            + "IFNULL(SUM(CASE WHEN order_status = 4 THEN total_amount ELSE 0 END), 0) AS totalSales "
            + "FROM orders WHERE shop_id = #{shopId}")
    Map<String, Object> statShopOverview(@Param("shopId") String shopId);

    /**
     * 把订单标记为已退款（退款闸门）。
     *
     * <p>用条件更新而不是「先查后改」：只有 {@code pay_status} 还是 1（已支付）时才会
     * 成功改成 2（已退款）；影响行数为 0 说明已经退过款。
     * 这样即使退款被重复调用（学生取消与超时任务同时触发），也不会重复给学生加钱。
     *
     * @return 影响行数，1 表示本次抢到了退款权
     */
    @Update("UPDATE orders SET pay_status = 2, update_time = NOW() "
            + "WHERE id = #{orderId} AND pay_status = 1")
    int markRefunded(@Param("orderId") String orderId);
}
