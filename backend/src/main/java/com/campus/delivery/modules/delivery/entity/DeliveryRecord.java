package com.campus.delivery.modules.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("delivery_record")
public class DeliveryRecord implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String orderId;
    private String riderId;

    /** 配送状态：0待取餐 1配送中 2已送达 3异常（与表注释保持一致） */
    private Integer status;

    private LocalDateTime grabTime;
    private LocalDateTime pickupTime;
    private LocalDateTime finishTime;
    private Integer durationMin;      //配送耗时（分钟）
    private BigDecimal deliveryFee;   //本单配送费（骑手收入）
    private String abnormalType;
    private String abnormalDesc;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
