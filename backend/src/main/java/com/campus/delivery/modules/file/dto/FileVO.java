package com.campus.delivery.modules.file.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件对外展示对象（不含磁盘路径）。
 */
@Data
public class FileVO implements Serializable {

    private String id;

    private String fileName;

    private Long fileSize;

    private String md5;

    private Integer chunkTotal;

    /** 0 上传中 / 1 已完成 */
    private Integer status;

    private String downloadUrl;

    private LocalDateTime createTime;
}
