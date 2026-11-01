package com.campus.delivery.modules.file.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分片上传初始化响应。
 */
@Data
public class ChunkInitResponse implements Serializable {

    private String fileId;

    private String fileName;

    private Long fileSize;

    /** 分片大小（后端可能回填默认值） */
    private Integer chunkSize;

    /** 分片总数 */
    private Integer chunkTotal;

    /**
     * 已上传的分片序号。
     *
     * <p>前端据此跳过这些分片 —— 这就是「断点续传」：
     * 刷新页面后重新 init，已传的部分不用重传。
     */
    private List<Integer> uploaded;

    /** true 表示秒传：同 MD5 的文件已经存在，无需再传 */
    private Boolean instant;

    /** 下载地址（相对 context-path，形如 /api/files/{id}/download） */
    private String downloadUrl;
}
