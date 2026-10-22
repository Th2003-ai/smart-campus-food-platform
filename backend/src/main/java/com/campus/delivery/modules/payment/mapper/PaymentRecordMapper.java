package com.campus.delivery.modules.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.delivery.modules.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录数据访问。
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
