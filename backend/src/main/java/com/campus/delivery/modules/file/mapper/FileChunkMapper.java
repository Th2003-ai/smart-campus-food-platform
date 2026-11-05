package com.campus.delivery.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.delivery.modules.file.entity.FileChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件分片数据访问。
 */
@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunk> {

    /**
     * 已上传的分片序号（升序）。
     *
     * <p>断点续传的核心查询：{@code /files/init} 把它返回给前端，
     * 前端只传缺失的分片。
     */
    @Select("SELECT chunk_index FROM file_chunk WHERE file_id = #{fileId} ORDER BY chunk_index")
    List<Integer> selectUploadedIndexes(@Param("fileId") String fileId);

    /** 已上传分片数（合并前校验是否齐全） */
    @Select("SELECT COUNT(*) FROM file_chunk WHERE file_id = #{fileId}")
    int countByFileId(@Param("fileId") String fileId);
}
