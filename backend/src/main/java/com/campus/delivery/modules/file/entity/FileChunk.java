package com.campus.delivery.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件分片记录（file_chunk）。
 *
 * <p>{@code (file_id, chunk_index)} 唯一：并发重复上传同一分片时，
 * 第二次插入会被数据库挡住，不会产生重复记录。
 */
@Data
@TableName("file_chunk")
public class FileChunk implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String fileId;

    private Integer chunkIndex;

    private String chunkMd5;

    private Integer size;

    private LocalDateTime createTime;
}
