package com.campus.delivery.modules.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价图片表（review_image）。
 *
 * <p>一条评价可挂多张图，{@code sort} 决定展示顺序。
 * 图片文件本身走文件模块（{@code /api/files/{id}/download}）上传与下载，
 * 这里只存访问 URL，不存二进制。
 */
@Data
@TableName("review_image")
public class ReviewImage implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String reviewId;

    /** 图片地址：文件模块返回的 /api/files/{id}/download，或完整 http(s) 链接 */
    private String url;

    /** 展示顺序，从 0 开始 */
    private Integer sort;

    private LocalDateTime createTime;
}
