package com.campus.delivery.modules.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 分片上传初始化请求。
 *
 * <p>前端在切片前先算好整个文件的 MD5 和大小，一次性提交，
 * 后端据此判断：秒传 / 续传（返回已上传分片）/ 新建上传。
 */
@Data
public class ChunkInitRequest implements Serializable {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 文件总字节数 */
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    /** 整个文件的 MD5（32 位小写十六进制） */
    @NotBlank(message = "文件 MD5 不能为空")
    private String md5;

    /** 分片大小（字节），不传则用后端默认 5MB */
    private Integer chunkSize;
}
