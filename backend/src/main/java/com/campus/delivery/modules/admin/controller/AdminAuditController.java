package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.admin.service.AdminAuditService;
import com.campus.delivery.security.RequiresRole;
import com.campus.delivery.security.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商户与骑手审核接口。主责：成员3（数据写入由对应业务域负责）。
 *
 * <p>操作人取自登录态（{@code UserContext}），不在参数中出现，审核记录不可伪造。
 */
@Tag(name = "16-管理端·审核与处置", description = "审核列表、商户审核、骑手审核、违规处置")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@RequiresRole(RoleEnum.ADMIN)
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    // ------------------------------------------------------------------
    // 审核列表：管理端必须先能看到待审对象，才能执行审核
    // ------------------------------------------------------------------

    @Operation(summary = "商户审核列表", description = "auditStatus：0待审核 1已通过 2已驳回，不传为全部")
    @GetMapping("/merchants")
    public Result<List<Map<String, Object>>> listMerchants(
            @RequestParam(required = false) Integer auditStatus) {
        return Result.ok(adminAuditService.listMerchants(auditStatus));
    }

    @Operation(summary = "骑手审核列表", description = "auditStatus：0待审核 1已通过 2已驳回，不传为全部")
    @GetMapping("/riders")
    public Result<List<Map<String, Object>>> listRiders(
            @RequestParam(required = false) Integer auditStatus) {
        return Result.ok(adminAuditService.listRiders(auditStatus));
    }

    @Operation(summary = "店铺列表（含已封禁）", description = "banStatus：0正常 1已封禁，不传为全部")
    @GetMapping("/shops")
    public Result<List<Map<String, Object>>> listShops(
            @RequestParam(required = false) Integer banStatus) {
        return Result.ok(adminAuditService.listShops(banStatus));
    }

    // ------------------------------------------------------------------
    // 审核与处置
    // ------------------------------------------------------------------

    @Operation(summary = "审核商户", description = "pass=true 通过，false 驳回（驳回必须填写 remark）")
    @PostMapping("/merchant/{id}")
    public Result<Void> auditMerchant(@PathVariable String id,
                                      @RequestParam boolean pass,
                                      @RequestParam(required = false) String remark) {
        adminAuditService.auditMerchant(id, pass, remark);
        return Result.ok();
    }

    @Operation(summary = "审核骑手", description = "pass=true 通过，false 驳回（驳回必须填写 remark）")
    @PostMapping("/rider/{id}")
    public Result<Void> auditRider(@PathVariable String id,
                                   @RequestParam boolean pass,
                                   @RequestParam(required = false) String remark) {
        adminAuditService.auditRider(id, pass, remark);
        return Result.ok();
    }

    @Operation(summary = "封禁/解封店铺", description = "ban=true 封禁（同时置为休息中），false 解封")
    @PostMapping("/shop/{id}/ban")
    public Result<Void> banShop(@PathVariable String id, @RequestParam boolean ban) {
        adminAuditService.banShop(id, ban);
        return Result.ok();
    }

    @Operation(summary = "封禁/解封骑手", description = "ban=true 封禁（禁用其登录账号），false 解封")
    @PostMapping("/rider/{id}/ban")
    public Result<Void> banRider(@PathVariable String id, @RequestParam boolean ban) {
        adminAuditService.banRider(id, ban);
        return Result.ok();
    }
}
