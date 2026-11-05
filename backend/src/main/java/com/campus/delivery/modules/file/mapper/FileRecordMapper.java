package com.campus.delivery.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.delivery.modules.file.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件主记录数据访问。
 */
@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {
}
