package com.campus.delivery.modules.file.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量上传中单个文件的结果。
 */
@Data
public class BatchUploadItem implements Serializable {

    private String fileName;

    private Long fileSize;

    private boolean success;

    private String fileId;

    private String downloadUrl;

    /** 失败原因，或成功时的附加说明（如"秒传"） */
    private String message;
}
