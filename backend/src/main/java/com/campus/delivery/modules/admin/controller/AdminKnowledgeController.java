package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.ai.entity.KnowledgeBase;
import com.campus.delivery.modules.ai.service.KnowledgeBaseService;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理接口。主责：成员3（维护客服问答对）。
 *
 * <p>2026-10 修正：保存与删除此前是空实现（直接 return ok），
 * 出现「改完知识库、客服答案却不变」的假成功。现改为真正落库。
 */
@Tag(name = "15-管理端·知识库", description = "客服问答对的增删改查")
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class AdminKnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "知识库列表")
    @GetMapping
    public Result<List<KnowledgeBase>> list(@RequestParam(required = false) String category) {
        return Result.ok(knowledgeBaseService.list(category));
    }

    @Operation(summary = "新增或修改知识", description = "带 id 为修改，不带 id 为新增")
    @PostMapping
    public Result<KnowledgeBase> save(@RequestBody KnowledgeBase knowledge) {
        return Result.ok(knowledgeBaseService.save(knowledge));
    }

    @Operation(summary = "删除知识")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String id) {
        knowledgeBaseService.remove(id);
        return Result.ok();
    }
}
