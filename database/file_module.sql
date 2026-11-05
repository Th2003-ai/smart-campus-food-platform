-- =============================================================================
-- 文件模块（分片上传 / 断点续传 / 批量小文件）
--
-- 与现有业务表完全隔离，只新增两张表，不改任何已有表。
-- 执行：mysql -u root -p campus_ai_delivery < database/file_module.sql
-- =============================================================================

USE campus_ai_delivery;

-- 文件主记录
CREATE TABLE IF NOT EXISTS file_record (
    id           VARCHAR(32)  NOT NULL COMMENT '文件ID',
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size    BIGINT       NOT NULL COMMENT '文件字节数',
    md5          CHAR(32)     NOT NULL COMMENT '整个文件的 MD5（用于秒传与完整性校验）',
    chunk_size   INT          NOT NULL COMMENT '分片大小（字节）',
    chunk_total  INT          NOT NULL COMMENT '分片总数',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0上传中 1已完成',
    storage_path VARCHAR(500)          DEFAULT NULL COMMENT '合并后的磁盘绝对路径',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md5 (md5),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='文件主记录（分片上传）';

-- 分片记录：唯一索引保证并发重复上传同一片不会产生脏数据
CREATE TABLE IF NOT EXISTS file_chunk (
    id          VARCHAR(32) NOT NULL COMMENT '分片ID',
    file_id     VARCHAR(32) NOT NULL COMMENT '所属文件ID',
    chunk_index INT         NOT NULL COMMENT '分片序号（从 0 开始）',
    chunk_md5   CHAR(32)             DEFAULT NULL COMMENT '分片 MD5（接收后校验，不一致要求重传）',
    size        INT                  DEFAULT NULL COMMENT '分片字节数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_chunk (file_id, chunk_index),
    KEY idx_file (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='文件分片记录（断点续传依据）';
