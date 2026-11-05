package com.campus.delivery.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.delivery.common.api.PageResult;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.file.dto.BatchUploadItem;
import com.campus.delivery.modules.file.dto.ChunkInitRequest;
import com.campus.delivery.modules.file.dto.ChunkInitResponse;
import com.campus.delivery.modules.file.dto.FileVO;
import com.campus.delivery.modules.file.entity.FileChunk;
import com.campus.delivery.modules.file.entity.FileRecord;
import com.campus.delivery.modules.file.mapper.FileChunkMapper;
import com.campus.delivery.modules.file.mapper.FileRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 文件服务：分片上传、断点续传、合并校验、批量小文件、秒传。
 *
 * <p>磁盘布局：
 * <pre>
 * uploads/
 *   tmp/{fileId}/000000.part ...   ← 分片临时文件（合并成功后删除）
 *   files/{md5前2位}/{md5}          ← 合并结果，内容寻址，天然去重
 * </pre>
 *
 * <p>关键设计：
 * <ul>
 *   <li><b>分片校验</b>：每片带自己的 MD5，落盘后立即比对，不一致就删掉并要求重传该片；</li>
 *   <li><b>断点续传</b>：{@code init} 返回已上传的分片序号，前端只传缺失的；</li>
 *   <li><b>整体校验</b>：合并时边写边算 MD5，与前端提交的整体 MD5 比对，防止分片缺失/错位；</li>
 *   <li><b>秒传</b>：MD5 已有完成记录时直接返回下载地址，不再传输。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    private static final int MAX_CHUNK_SIZE = 20 * 1024 * 1024;
    private static final int MIN_CHUNK_SIZE = 256 * 1024;

    /** 分片 MD5 不一致：前端据此只重传该片 */
    private static final int CODE_CHUNK_MD5_MISMATCH = 4001;
    private static final int CODE_FILE_NOT_FOUND = 4002;
    private static final int CODE_UPLOAD_FINISHED = 4006;
    private static final int CODE_CHUNK_INCOMPLETE = 4007;
    private static final int CODE_CHUNK_INDEX_INVALID = 4008;
    private static final int CODE_CHUNK_MISSING = 4009;
    private static final int CODE_FILE_MD5_MISMATCH = 4010;

    private final FileRecordMapper fileRecordMapper;
    private final FileChunkMapper fileChunkMapper;

    @Value("${campus.file.local-path:./uploads}")
    private String uploadRoot;

    // ==================================================================
    // 1) 初始化：秒传 / 续传 / 新建
    // ==================================================================

    public ChunkInitResponse init(ChunkInitRequest request) {
        String md5 = request.getMd5().trim().toLowerCase();
        FileRecord existing = findByMd5(md5);

        // 秒传：同 MD5 的文件已经上传完成，直接给下载地址
        if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
            ChunkInitResponse instant = buildResponse(existing);
            instant.setInstant(true);
            instant.setUploaded(List.of());
            log.info("秒传命中: md5={}, fileId={}", md5, existing.getId());
            return instant;
        }

        FileRecord record = existing;
        if (record == null) {
            int chunkSize = normalizeChunkSize(request.getChunkSize());
            long total = (request.getFileSize() + chunkSize - 1) / chunkSize;
            record = new FileRecord();
            record.setFileName(request.getFileName());
            record.setFileSize(request.getFileSize());
            record.setMd5(md5);
            record.setChunkSize(chunkSize);
            record.setChunkTotal((int) Math.max(total, 1));
            record.setStatus(0);
            fileRecordMapper.insert(record);
            log.info("创建上传任务: fileId={}, name={}, size={}, chunks={}",
                    record.getId(), record.getFileName(), record.getFileSize(), record.getChunkTotal());
        }

        ChunkInitResponse response = buildResponse(record);
        // 断点续传：把已上传的分片序号返回给前端
        response.setUploaded(fileChunkMapper.selectUploadedIndexes(record.getId()));
        response.setInstant(false);
        return response;
    }

    // ==================================================================
    // 2) 上传单个分片（含分片 MD5 校验）
    // ==================================================================

    public void uploadChunk(String fileId, Integer chunkIndex, String chunkMd5, MultipartFile file) {
        FileRecord record = require(fileId);
        if (Integer.valueOf(1).equals(record.getStatus())) {
            throw new BusinessException(CODE_UPLOAD_FINISHED, "文件已上传完成，无需再传分片");
        }
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= record.getChunkTotal()) {
            throw new BusinessException(CODE_CHUNK_INDEX_INVALID,
                    "分片序号非法：应在 0 ~ " + (record.getChunkTotal() - 1) + " 之间");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CODE_CHUNK_MISSING, "分片内容为空");
        }

        try {
            Path dir = tmpDir(fileId);
            Files.createDirectories(dir);
            Path part = dir.resolve(partName(chunkIndex));
            // transferTo 要求绝对路径
            file.transferTo(part.toAbsolutePath().toFile());

            // 分片校验：损坏就删掉，让前端重传这一片
            if (StringUtils.hasText(chunkMd5)) {
                String actual = DigestUtils.md5DigestAsHex(Files.newInputStream(part));
                if (!actual.equalsIgnoreCase(chunkMd5.trim())) {
                    Files.deleteIfExists(part);
                    log.warn("分片校验失败: fileId={}, index={}, expect={}, actual={}",
                            fileId, chunkIndex, chunkMd5, actual);
                    throw new BusinessException(CODE_CHUNK_MD5_MISMATCH,
                            "分片 " + chunkIndex + " 校验失败，请重传该分片");
                }
            }

            saveChunkRecord(fileId, chunkIndex, chunkMd5, (int) Files.size(part));
        } catch (IOException e) {
            throw new BusinessException(500, "分片写入失败: " + e.getMessage());
        }
    }

    // ==================================================================
    // 3) 合并：按序号顺序拼接 + 整体 MD5 校验
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public FileVO complete(String fileId) {
        FileRecord record = require(fileId);
        if (Integer.valueOf(1).equals(record.getStatus())) {
            return toVO(record);
        }

        int uploaded = fileChunkMapper.countByFileId(fileId);
        if (uploaded < record.getChunkTotal()) {
            throw new BusinessException(CODE_CHUNK_INCOMPLETE,
                    "分片不完整：已上传 " + uploaded + " / " + record.getChunkTotal());
        }

        Path dir = tmpDir(fileId);
        Path target = finalPath(record);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                // 必须按 chunkIndex 顺序合并（不能按文件名排序，否则 10 会排在 2 前面）
                for (int i = 0; i < record.getChunkTotal(); i++) {
                    Path part = dir.resolve(partName(i));
                    if (!Files.exists(part)) {
                        throw new BusinessException(CODE_CHUNK_MISSING,
                                "分片 " + i + " 缺失，请重新上传该分片");
                    }
                    try (InputStream in = Files.newInputStream(part)) {
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                            digest.update(buffer, 0, len);
                        }
                    }
                }
            }

            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(record.getMd5())) {
                Files.deleteIfExists(target);
                throw new BusinessException(CODE_FILE_MD5_MISMATCH,
                        "文件完整性校验失败（期望 " + record.getMd5() + "，实际 " + actual + "），请重新上传");
            }

            record.setStatus(1);
            record.setStoragePath(target.toString());
            fileRecordMapper.updateById(record);
            deleteQuietly(dir);
            log.info("文件合并完成: fileId={}, size={}, path={}", fileId, record.getFileSize(), target);
            return toVO(record);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new BusinessException(500, "文件合并失败: " + e.getMessage());
        }
    }

    // ==================================================================
    // 4) 小文件批量上传（不走分片，直接落盘）
    // ==================================================================

    public List<BatchUploadItem> batchUpload(MultipartFile[] files) {
        List<BatchUploadItem> results = new ArrayList<>();
        if (files == null) {
            return results;
        }
        for (MultipartFile file : files) {
            BatchUploadItem item = new BatchUploadItem();
            item.setFileName(file.getOriginalFilename());
            item.setFileSize(file.getSize());
            try {
                if (file.isEmpty()) {
                    item.setSuccess(false);
                    item.setMessage("空文件已跳过");
                    results.add(item);
                    continue;
                }
                FileRecord saved = saveSmallFile(file);
                item.setSuccess(true);
                item.setFileId(saved.getId());
                item.setDownloadUrl(downloadUrl(saved.getId()));
                item.setMessage(Integer.valueOf(1).equals(saved.getStatus()) ? "上传成功" : "上传成功");
                results.add(item);
            } catch (Exception e) {
                item.setSuccess(false);
                item.setMessage(e.getMessage());
                results.add(item);
            }
        }
        return results;
    }

    /** 小文件直存：一次写盘 + 一条记录（chunkTotal=1） */
    private FileRecord saveSmallFile(MultipartFile file) throws IOException {
        String md5 = DigestUtils.md5DigestAsHex(file.getInputStream());
        FileRecord existing = findByMd5(md5);
        if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
            log.info("小文件秒传: name={}, md5={}", file.getOriginalFilename(), md5);
            return existing;
        }
        FileRecord record = existing;
        if (record == null) {
            record = new FileRecord();
            record.setFileName(file.getOriginalFilename());
            record.setFileSize(file.getSize());
            record.setMd5(md5);
            record.setChunkSize((int) Math.max(file.getSize(), 1));
            record.setChunkTotal(1);
            record.setStatus(0);
            fileRecordMapper.insert(record);
        }
        Path target = finalPath(record);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toAbsolutePath().toFile());
        record.setStatus(1);
        record.setStoragePath(target.toString());
        fileRecordMapper.updateById(record);
        return record;
    }

    // ==================================================================
    // 5) 查询 / 删除 / 下载用到的读取
    // ==================================================================

    public FileRecord require(String fileId) {
        FileRecord record = fileRecordMapper.selectById(fileId);
        if (record == null) {
            throw new BusinessException(CODE_FILE_NOT_FOUND, "文件不存在");
        }
        return record;
    }

    public FileRecord requireReady(String fileId) {
        FileRecord record = require(fileId);
        if (!Integer.valueOf(1).equals(record.getStatus())) {
            throw new BusinessException(4003, "文件尚未上传完成，暂不能下载");
        }
        Path path = Paths.get(record.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException(4003, "文件已丢失，请重新上传");
        }
        return record;
    }

    public PageResult<FileVO> page(long pageNum, long pageSize) {
        Page<FileRecord> page = fileRecordMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<FileRecord>().orderByDesc(FileRecord::getCreateTime));
        List<FileVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    public FileVO detail(String fileId) {
        return toVO(require(fileId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String fileId) {
        FileRecord record = require(fileId);
        fileChunkMapper.delete(new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getFileId, fileId));
        fileRecordMapper.deleteById(fileId);
        if (StringUtils.hasText(record.getStoragePath())) {
            try {
                Files.deleteIfExists(Paths.get(record.getStoragePath()));
            } catch (IOException e) {
                log.warn("删除物理文件失败: {}", e.getMessage());
            }
        }
        deleteQuietly(tmpDir(fileId));
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private FileRecord findByMd5(String md5) {
        return fileRecordMapper.selectOne(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getMd5, md5).last("LIMIT 1"));
    }

    /** 分片记录幂等落库：并发重复上传同一片时，靠唯一索引挡住第二次插入 */
    private void saveChunkRecord(String fileId, int chunkIndex, String chunkMd5, int size) {
        FileChunk exists = fileChunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getFileId, fileId)
                .eq(FileChunk::getChunkIndex, chunkIndex)
                .last("LIMIT 1"));
        if (exists != null) {
            exists.setChunkMd5(chunkMd5);
            exists.setSize(size);
            fileChunkMapper.updateById(exists);
            return;
        }
        FileChunk chunk = new FileChunk();
        chunk.setFileId(fileId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkMd5(chunkMd5);
        chunk.setSize(size);
        try {
            fileChunkMapper.insert(chunk);
        } catch (DuplicateKeyException ignore) {
            // 并发下另一线程已经插入，视为成功
            log.debug("分片记录已存在（并发）: fileId={}, index={}", fileId, chunkIndex);
        }
    }

    private int normalizeChunkSize(Integer chunkSize) {
        if (chunkSize == null || chunkSize <= 0) {
            return DEFAULT_CHUNK_SIZE;
        }
        return Math.min(Math.max(chunkSize, MIN_CHUNK_SIZE), MAX_CHUNK_SIZE);
    }

    private Path root() {
        return Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    private Path tmpDir(String fileId) {
        return root().resolve("tmp").resolve(fileId);
    }

    private Path finalPath(FileRecord record) {
        String md5 = record.getMd5();
        return root().resolve("files").resolve(md5.substring(0, 2)).resolve(md5);
    }

    private String partName(int index) {
        // 6 位补零：保证按文件名排序时顺序正确（虽然合并是按 index 取，但便于人工排查）
        return String.format("%06d.part", index);
    }

    private String downloadUrl(String fileId) {
        return "/api/files/" + fileId + "/download";
    }

    private ChunkInitResponse buildResponse(FileRecord record) {
        ChunkInitResponse response = new ChunkInitResponse();
        response.setFileId(record.getId());
        response.setFileName(record.getFileName());
        response.setFileSize(record.getFileSize());
        response.setChunkSize(record.getChunkSize());
        response.setChunkTotal(record.getChunkTotal());
        response.setDownloadUrl(downloadUrl(record.getId()));
        return response;
    }

    private FileVO toVO(FileRecord record) {
        FileVO vo = new FileVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setFileSize(record.getFileSize());
        vo.setMd5(record.getMd5());
        vo.setChunkTotal(record.getChunkTotal());
        vo.setStatus(record.getStatus());
        vo.setCreateTime(record.getCreateTime());
        vo.setDownloadUrl(downloadUrl(record.getId()));
        return vo;
    }

    private void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignore) {
                            // 忽略
                        }
                    });
        } catch (IOException e) {
            log.warn("清理临时目录失败: {}", e.getMessage());
        }
    }

    /** 供 Controller 计算 Content-Length 等使用 */
    public Path storagePath(FileRecord record) {
        return Paths.get(Objects.requireNonNull(record.getStoragePath()));
    }
}
