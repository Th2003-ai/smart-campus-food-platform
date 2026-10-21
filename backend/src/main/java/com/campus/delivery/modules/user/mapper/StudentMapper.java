package com.campus.delivery.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.delivery.modules.user.entity.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 学生用户数据访问。主责：成员1。
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 扣减余额（余额支付）。
     *
     * <p>判断与扣减在同一条 SQL 内完成，依赖 InnoDB 行锁：
     * 并发下不会出现「都读到够、都扣」的超扣问题。
     *
     * @return 影响行数，0 表示余额不足或用户不存在
     */
    @Update("UPDATE student SET balance = balance - #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    /** 退还余额（订单取消 / 拒单 / 超时自动取消） */
    @Update("UPDATE student SET balance = balance + #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId}")
    int addBalance(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    /** 查询余额（个人中心展示用） */
    @Select("SELECT balance FROM student WHERE user_id = #{userId}")
    BigDecimal selectBalance(@Param("userId") String userId);
}
