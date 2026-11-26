"""扫描后端 Controller，按禅道「模块-方法」粒度生成用户组权限初始化 SQL。

规则（与运行时 AuthInterceptor 的解析保持一致）：
  模块 module = 控制器类名去掉 Controller 后缀，全小写
  方法 method = Java 方法名
  权限来源   = 方法（或类上）的 @RequiresRole(RoleEnum.X)
  没有 @RequiresRole 也没有 @RequiresPerm 的接口 = 只要求登录 → 四个内置组都给
"""
import re
import pathlib

ROOT = pathlib.Path(r"D:\SE\campus-ai-delivery\backend\src\main\java\com\campus\delivery")
GROUPS = ["STUDENT", "MERCHANT", "RIDER", "ADMIN"]

ANN = re.compile(r"@(\w+)(\(([^)]*)\))?")
SIG = re.compile(r"^\s+(public|protected|private)\s+[\w<>\[\], .?]+\s+(\w+)\s*\(")
MAPPINGS = {"GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "RequestMapping", "PatchMapping"}


def roles_of(arg):
    """@RequiresRole 支持 RoleEnum.ADMIN 与 "ADMIN" 两种写法"""
    found = set(re.findall(r"RoleEnum\.(\w+)", arg or ""))
    found |= {s.upper() for s in re.findall(r'"([^"]+)"', arg or "")}
    return {r for r in found if r in {"STUDENT", "MERCHANT", "RIDER", "ADMIN"}}


def scan(path):
    cls_roles, cls_perms = set(), set()
    rows = []
    pending_roles, pending_perms = set(), set()
    has_mapping = False
    in_class = False
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if not in_class:
            for name, _, arg in ANN.findall(line):
                if name == "RequiresRole":
                    cls_roles |= roles_of(arg)
                elif name == "RequiresPerm":
                    cls_perms |= set(re.findall(r'"([^"]+)"', arg))
            if re.search(r"\bpublic\s+class\b", line):
                in_class = True
            continue

        for name, _, arg in ANN.findall(line):
            if name in MAPPINGS:
                has_mapping = True
            elif name == "RequiresRole":
                pending_roles |= roles_of(arg)
            elif name == "RequiresPerm":
                pending_perms |= set(re.findall(r'"([^"]+)"', arg))

        m = SIG.match(line)
        if not m:
            continue
        method = m.group(2)
        if not has_mapping or method in ("if", "for", "while", "switch", "catch", "return"):
            has_mapping = False
            pending_roles, pending_perms = set(), set()
            continue
        rows.append({
            "module": path.name[: -len("Controller.java")].lower(),
            "method": method,
            "roles": sorted(pending_roles or cls_roles),
            "perms": sorted(pending_perms or cls_perms),
        })
        has_mapping = False
        pending_roles, pending_perms = set(), set()
    return rows


def main():
    rows = []
    for f in sorted(ROOT.rglob("*Controller.java")):
        rows.extend(scan(f))

    # 去重
    seen, uniq = set(), []
    for r in rows:
        k = (r["module"], r["method"])
        if k in seen:
            continue
        seen.add(k)
        uniq.append(r)

    grants = []  # (group, module, method)
    for r in uniq:
        groups = r["roles"] if r["roles"] else GROUPS
        for g in groups:
            grants.append((g, r["module"], r["method"]))

    out = []
    out.append("-- 用户组权限（禅道式：模块-方法粒度）初始化数据")
    out.append("-- 由 docs/gen_group_priv.py 扫描 Controller 生成，勿手改")
    out.append("USE campus_ai_delivery;\n")
    out.append("CREATE TABLE IF NOT EXISTS sys_group (")
    out.append("  id          VARCHAR(32)  NOT NULL COMMENT '用户组ID',")
    out.append("  code        VARCHAR(32)  NOT NULL COMMENT '组编码',")
    out.append("  name        VARCHAR(64)  NOT NULL COMMENT '组名称',")
    out.append("  builtin     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=内置组',")
    out.append("  remark      VARCHAR(255)          DEFAULT NULL,")
    out.append("  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,")
    out.append("  PRIMARY KEY (id), UNIQUE KEY uk_group_code (code)")
    out.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户组（一人可属多组，权限取并集）';\n")
    out.append("CREATE TABLE IF NOT EXISTS sys_group_user (")
    out.append("  id       BIGINT      NOT NULL AUTO_INCREMENT,")
    out.append("  group_id VARCHAR(32) NOT NULL,")
    out.append("  user_id  VARCHAR(32) NOT NULL,")
    out.append("  PRIMARY KEY (id), UNIQUE KEY uk_group_user (group_id, user_id), KEY idx_gu_user (user_id)")
    out.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-用户组';\n")
    out.append("CREATE TABLE IF NOT EXISTS sys_group_priv (")
    out.append("  id         BIGINT      NOT NULL AUTO_INCREMENT,")
    out.append("  group_id   VARCHAR(32) NOT NULL COMMENT '用户组ID',")
    out.append("  module     VARCHAR(64) NOT NULL COMMENT '模块=控制器名，例如 dish',")
    out.append("  method     VARCHAR(64) NOT NULL COMMENT '方法=接口名，例如 create',")
    out.append("  data_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/SHOP/SELF',")
    out.append("  PRIMARY KEY (id), UNIQUE KEY uk_group_priv (group_id, module, method)")
    out.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户组权限（模块-方法粒度，默认拒绝）';\n")

    names = {"STUDENT": "学生", "MERCHANT": "商户", "RIDER": "骑手", "ADMIN": "管理员"}
    out.append("INSERT IGNORE INTO sys_group (id, code, name, builtin) VALUES")
    out.append(",\n".join(
        f"  ('G{code}', '{code}', '{names[code]}组', 1)" for code in GROUPS) + ";\n")

    out.append("-- 按现有 sys_user.role 建立成员关系（一人一组，后续可在页面上加多组）")
    for code in GROUPS:
        out.append(f"INSERT IGNORE INTO sys_group_user (group_id, user_id) "
                   f"SELECT 'G{code}', id FROM sys_user WHERE role = '{code}';")
    out.append("")

    out.append(f"-- 权限行：{len(grants)} 条（{len(uniq)} 个接口）")
    out.append("INSERT IGNORE INTO sys_group_priv (group_id, module, method) VALUES")
    out.append(",\n".join(f"  ('G{g}', '{m}', '{me}')" for g, m, me in grants) + ";")
    out.append("")

    dst = pathlib.Path(r"D:\SE\campus-ai-delivery\database\group_priv_seed.sql")
    dst.write_text("\n".join(out), encoding="utf-8")
    print(f"endpoints={len(uniq)} grants={len(grants)} -> {dst}")
    for r in uniq[:5]:
        print(r)


if __name__ == "__main__":
    main()