package com.campus.delivery.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件主记录（file_record）。
 *
 * <p>一个文件一条：大文件走分片，小文件 chunkTotal 固定为 1。
 * md5 唯一索引同时承担两件事：秒传判重、整体完整性校验。
 */
@Data
@TableName("file_record")
public class FileRecord implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String fileName;

    private Long fileSize;

    private String md5;

    private Integer chunkSize;

    private Integer chunkTotal;

    /** 0 上传中 / 1 已完成 */
    private Integer status;

    private String storagePath;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
