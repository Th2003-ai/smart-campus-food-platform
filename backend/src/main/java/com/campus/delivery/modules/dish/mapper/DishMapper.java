package com.campus.delivery.modules.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.delivery.modules.dish.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 菜品数据访问。主责：成员2。
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 条件扣减库存，保证库存不为负（需求文档 5.6.2）。
     *
     * <p>SQL 中带 {@code stock >= #{quantity}} 条件，配合数据库行锁实现并发安全；
     * 返回 0 表示库存不足，调用方应回滚事务并提示用户。
     *
     * @return 影响行数：1 成功，0 库存不足或菜品不存在
     */
    @Update("UPDATE dish SET stock = stock - #{quantity}, update_time = NOW() "
            + "WHERE id = #{dishId} AND deleted = 0 AND stock >= #{quantity}")
    int deductStock(@Param("dishId") String dishId, @Param("quantity") int quantity);

    /** 取消订单时回补库存 */
    @Update("UPDATE dish SET stock = stock + #{quantity}, update_time = NOW() "
            + "WHERE id = #{dishId} AND deleted = 0")
    int restoreStock(@Param("dishId") String dishId, @Param("quantity") int quantity);

    /**
     * 关键词检索（AI 语义搜索的降级兜底方案）。
     *
     * <p>大模型返回解析失败时，退化为按菜品名、描述、标签、食材的关键词匹配。
     *
     * <p>注意：本方法必须显式声明 SQL（注解或 XML）。MyBatis 对「无注解且无 XML」的
     * 接口方法不会注册 statement，MyBatis-Plus 的 MybatisMapperAnnotationBuilder 在
     * 解析完 Mapper 后会校验每条自定义方法，缺失即抛 BindingException 导致应用启动失败。
     *
     * @param shopId 为空表示全平台检索（AI 搜索不限定店铺的场景）
     */
    @Select("<script>"
            + "SELECT * FROM dish WHERE deleted = 0 AND status = 1 "
            + "AND (dish_name LIKE CONCAT('%', #{keyword}, '%') "
            + "  OR tags LIKE CONCAT('%', #{keyword}, '%') "
            + "  OR description LIKE CONCAT('%', #{keyword}, '%') "
            + "  OR ingredients LIKE CONCAT('%', #{keyword}, '%')) "
            + "<if test='shopId != null and shopId != \"\"'>AND shop_id = #{shopId} </if>"
            + "ORDER BY monthly_sales DESC LIMIT #{limit}"
            + "</script>")
    List<Dish> searchByKeyword(@Param("keyword") String keyword, @Param("shopId") String shopId, @Param("limit") int limit);
}
