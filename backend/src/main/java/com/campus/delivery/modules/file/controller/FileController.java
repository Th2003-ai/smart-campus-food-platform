package com.campus.delivery.modules.file.controller;

import com.campus.delivery.common.api.PageResult;
import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.file.dto.BatchUploadItem;
import com.campus.delivery.modules.file.dto.ChunkInitRequest;
import com.campus.delivery.modules.file.dto.ChunkInitResponse;
import com.campus.delivery.modules.file.dto.FileVO;
import com.campus.delivery.modules.file.entity.FileRecord;
import com.campus.delivery.modules.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件上传下载接口。
 *
 * <p>对应作业的 5 个问题：
 * <ol>
 *   <li>大文件分片：{@code /init} + {@code /upload/chunk} + {@code /complete}；</li>
 *   <li>小文件批量：{@code /upload/batch}；</li>
 *   <li>断点续传：{@code /init} 返回已传分片；下载用 {@code Range} 支持续传；</li>
 *   <li>进度条：前端按分片数/字节数计算，下载依赖 {@code Content-Length}；</li>
 *   <li>高并发下载：{@link #download} 返回 {@link ResourceRegion}，零拷贝分块发送，
 *       前端可切多段 {@code Range} 并行拉取。</li>
 * </ol>
 */
@Tag(name = "20-文件上传下载", description = "分片上传、断点续传、批量小文件、Range 下载")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "分片上传初始化", description = "返回秒传标记或已上传分片序号（断点续传）")
    @PostMapping("/init")
    public Result<ChunkInitResponse> init(@Valid @RequestBody ChunkInitRequest request) {
        return Result.ok(fileService.init(request));
    }

    @Operation(summary = "上传单个分片", description = "分片 MD5 不一致返回 code=4001，前端只需重传该片")
    @PostMapping("/upload/chunk")
    public Result<Void> uploadChunk(@RequestParam String fileId,
                                    @RequestParam Integer chunkIndex,
                                    @RequestParam(required = false) String chunkMd5,
                                    @RequestParam("file") MultipartFile file) {
        fileService.uploadChunk(fileId, chunkIndex, chunkMd5, file);
        return Result.ok();
    }

    @Operation(summary = "合并分片", description = "按序号顺序合并并校验整体 MD5")
    @PostMapping("/complete")
    public Result<FileVO> complete(@RequestParam String fileId) {
        return Result.ok(fileService.complete(fileId));
    }

    @Operation(summary = "小文件批量上传", description = "一次请求多个文件，逐个返回结果")
    @PostMapping("/upload/batch")
    public Result<List<BatchUploadItem>> batch(@RequestParam("files") MultipartFile[] files) {
        return Result.ok(fileService.batchUpload(files));
    }

    @Operation(summary = "文件列表")
    @GetMapping
    public Result<PageResult<FileVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(fileService.page(pageNum, pageSize));
    }

    @Operation(summary = "文件信息")
    @GetMapping("/{id}")
    public Result<FileVO> detail(@PathVariable String id) {
        return Result.ok(fileService.detail(id));
    }

    /**
     * 下载文件：支持 HTTP Range，因此天然支持「断点续传」与「多段并行下载」。
     *
     * <p>用 {@link StreamingResponseBody} 手动流式写出（按 8KB 缓冲循环写），
     * 不把整个文件读进内存，大文件也不会 OOM；同时可以精确控制
     * {@code Content-Length} / {@code Content-Range}，前端才能算出下载进度。
     *
     * <p>没有用 {@code ResourceRegion}：它的消息转换器会往已只读的响应头里 add，
     * 在 Spring 6 下会抛 UnsupportedOperationException，这里直接自己写更可控。
     */
    @Operation(summary = "下载文件", description = "支持 Range，可用于断点续传与并行下载")
    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        FileRecord record = fileService.requireReady(id);
        Path path = fileService.storagePath(record);
        long contentLength = path.toFile().length();

        long start = 0;
        long end = contentLength - 1;
        HttpStatus status = HttpStatus.OK;

        if (StringUtils.hasText(rangeHeader)) {
            HttpRange range = HttpRange.parseRanges(rangeHeader).get(0);
            start = range.getRangeStart(contentLength);
            end = range.getRangeEnd(contentLength);
            status = HttpStatus.PARTIAL_CONTENT;
        }
        long count = end - start + 1;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentLength(count);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(record.getFileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        if (status == HttpStatus.PARTIAL_CONTENT) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength);
        }

        final long from = start;
        final long bytes = count;
        StreamingResponseBody body = outputStream -> {
            try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                file.seek(from);
                byte[] buffer = new byte[8192];
                long remaining = bytes;
                while (remaining > 0) {
                    int read = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    outputStream.write(buffer, 0, read);
                    remaining -= read;
                }
                outputStream.flush();
            }
        };

        return ResponseEntity.status(status).headers(headers).body(body);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        fileService.delete(id);
        return Result.ok();
    }
}
