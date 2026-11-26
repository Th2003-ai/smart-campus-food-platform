package com.campus.delivery.modules.admin.controller;

import com.campus.delivery.common.api.Result;
import com.campus.delivery.modules.admin.entity.SysGroup;
import com.campus.delivery.modules.admin.service.GroupPermissionService;
import com.campus.delivery.security.EndpointRegistry;
import com.campus.delivery.security.RequiresPerm;
import com.campus.delivery.security.RequiresRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ZenTao-style user group privilege administration.
 *
 * <p>Privileges are granted per group at module.method granularity; a user in
 * several groups gets the union. Every change invalidates the redis cache of the
 * affected users, so it takes effect on the next request without a restart.
 */
@RestController
@RequestMapping("/admin/groups")
@RequiresRole("ADMIN")
@RequiresPerm("admin:all")
@RequiredArgsConstructor
public class AdminGroupController {

    private final GroupPermissionService groupPermissionService;
    private final EndpointRegistry endpointRegistry;

    /** groups + granted privilege keys + data scopes */
    @GetMapping("/matrix")
    public Result<Map<String, Object>> matrix() {
        return Result.ok(groupPermissionService.matrix());
    }

    /** every endpoint that exists in code: module -> methods */
    @GetMapping("/endpoints")
    public Result<Map<String, Object>> endpoints() {
        return Result.ok(endpointRegistry.tree());
    }

    @PutMapping("/{groupId}/privs")
    public Result<Void> savePrivs(@PathVariable String groupId,
                                  @RequestBody List<Map<String, String>> items) {
        groupPermissionService.saveGroupPriv(groupId, items);
        return Result.ok(null);
    }

    @PostMapping
    public Result<SysGroup> create(@RequestBody Map<String, String> body) {
        return Result.ok(groupPermissionService.createGroup(
                body.get("name"), body.get("code"), body.get("remark")));
    }

    @DeleteMapping("/{groupId}")
    public Result<Void> delete(@PathVariable String groupId) {
        groupPermissionService.deleteGroup(groupId);
        return Result.ok(null);
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users() {
        return Result.ok(groupPermissionService.users());
    }

    @PutMapping("/users/{userId}")
    public Result<Void> setUserGroups(@PathVariable String userId,
                                      @RequestBody List<String> groupIds) {
        groupPermissionService.setUserGroups(userId, groupIds);
        return Result.ok(null);
    }
}
