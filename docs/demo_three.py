#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PPT 第 14 页「三个实验、三组数字」的可复现脚本。

原理：用 MySQL 的 Com_select 全局计数增量，统计每组实验到底打了几次库。
零第三方依赖：MySQL / Redis 走命令行，HTTP 用标准库 + 线程。

用法（PowerShell）：
    python docs/demo_three.py
    python docs/demo_three.py --concurrency 50 --base http://localhost:8080/api
    python docs/demo_three.py --skip-switch        # 不改开关，只跑实验

输出：一张对照表（穿透关闭 / 穿透开启 / 击穿 / 雪崩 / 对照）。
"""

import argparse
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request

# ---------------------------------------------------------------- 环境参数
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
REDIS = r"D:\SE\redis-5.0.14.1\redis-cli.exe"
MYSQL_USER = "root"
MYSQL_PWD = os.environ.get("MYSQL_PWD", "Campus@123456")
DB = "campus_ai_delivery"

ADMIN_USER = "admin"
ADMIN_PWD = "Adm@Campus#2026"

TOKEN = None


# ---------------------------------------------------------------- 基础工具
def run(cmd, env=None):
    proc = subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", env=env)
    return proc.stdout.strip(), proc.stderr.strip()


def mysql_env():
    env = os.environ.copy()
    env["MYSQL_PWD"] = MYSQL_PWD
    return env


def com_select():
    """读 MySQL 全局 SELECT 计数"""
    out, err = run(
        [MYSQL, "-u", MYSQL_USER, "-N", "-B",
         "-e", "SHOW GLOBAL STATUS LIKE 'Com_select'"],
        env=mysql_env(),
    )
    for line in out.splitlines():
        if line.startswith("Com_select"):
            return int(line.split("\t")[1])
    raise RuntimeError(f"读不到 Com_select：{out} {err}")


def query_column(sql):
    out, _ = run([MYSQL, "-u", MYSQL_USER, "-N", "-B", "-D", DB, "-e", sql], env=mysql_env())
    return [line.strip() for line in out.splitlines() if line.strip()]


def redis_del(*keys):
    run([REDIS, "DEL", *keys])


def http_get(url, headers=None, timeout=15):
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.status


def http_json(url, method="GET", payload=None, headers=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    hdrs = {"Content-Type": "application/json"}
    hdrs.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def login(base, username, password):
    body = http_json(f"{base}/auth/login", "POST",
                     {"username": username, "password": password})
    if body.get("code") != 200:
        raise RuntimeError(f"登录失败：{body.get('message')}")
    return body["data"]["token"]


def burst(url, n):
    """n 个线程同时发一次 GET，模拟瞬时并发"""
    statuses = []
    lock = threading.Lock()

    def worker():
        try:
            code = http_get(url)
        except Exception as exc:      # 网络/HTTP 异常也记下来，不影响计数
            code = type(exc).__name__
        with lock:
            statuses.append(code)

    threads = [threading.Thread(target=worker) for _ in range(n)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return statuses


def measure(fn, noise, settle=0.4):
    """执行 fn 前后取 Com_select 差值，扣掉背景噪声"""
    before = com_select()
    fn()
    time.sleep(settle)
    return com_select() - before - noise


def calibrate_noise(samples=3, interval=1.0):
    """测出「两次读计数器之间」自然增长的 SELECT 次数。

    ⚠ 口径提醒：mysql 客户端每次连接都会执行一次 `select @@version_comment`，
    这会被计入 Com_select。所以实测出来的「1 次/秒」其实主要是**读计数器自身的开销**，
    真实后台噪声接近 0。measure() 减掉的正是这个固定开销。
    """
    deltas = []
    for _ in range(samples):
        before = com_select()
        time.sleep(interval)
        deltas.append(com_select() - before)
    return max(0, min(deltas)), deltas


# ---------------------------------------------------------------- 实验主体
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080/api")
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--skip-switch", action="store_true",
                        help="不调用管理端开关（默认会切两次：关闭→开启）")
    parser.add_argument("--admin-user", default=ADMIN_USER)
    parser.add_argument("--admin-pwd", default=ADMIN_PWD)
    args = parser.parse_args()

    base = args.base.rstrip("/")
    n = args.concurrency

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    print("=" * 72)
    print(f"三兄弟实验：并发 {n}，后端 {base}")
    print("=" * 72)

    # 0) 全程对账基线：从脚本一启动就记，
    #    这样「脚本报的全局增量」和「人工在 MySQL 里前后各查一次」是同一个口径
    global_baseline = com_select()

    # 1) 准备数据：取 4 个真实菜品 + 1 个不存在的 ID
    dish_ids = query_column(
        f"SELECT id FROM dish WHERE deleted = 0 ORDER BY id LIMIT 4")
    if len(dish_ids) < 4:
        raise RuntimeError(f"菜品不足 4 个，库里只有 {len(dish_ids)} 个")
    aux_ids = com_select() - global_baseline

    ghost_id = "NO-SUCH-DISH-" + str(int(time.time()))
    print(f"真实菜品：{', '.join(dish_ids)}")
    print(f"假 ID   ：{ghost_id}")

    # 2) 校准背景噪声
    noise, samples = calibrate_noise()
    print(f"背景噪声：{noise} 次/秒（样本 {samples}）")

    # 3) 拿管理端 token（用于切换防穿透开关）
    token = None
    aux_login = 0
    if not args.skip_switch:
        before_login = com_select()
        token = login(base, args.admin_user, args.admin_pwd)
        aux_login = com_select() - before_login
        print(f"管理端登录成功，可切换防穿透开关（登录消耗 {aux_login} 次 SELECT）")
    auth = {"Authorization": f"Bearer {token}"} if token else {}

    results = []

    # ---------------- 实验一 A：关闭防穿透 ----------------
    if token:
        http_json(f"{base}/admin/cache/switch/null-cache?enabled=false", "POST", {}, auth)
        redis_del(f"cache:dish:null:{ghost_id}")
        time.sleep(0.3)
        cnt_a = measure(lambda: burst(f"{base}/dishes/{ghost_id}", n), noise)
        results.append(("穿透（关闭防穿透）", n, cnt_a,
                        "每个请求都打库：缓存完全没挡住"))

    # ---------------- 实验一 B：开启防穿透 ----------------
    if token:
        http_json(f"{base}/admin/cache/switch/null-cache?enabled=true", "POST", {}, auth)
        redis_del(f"cache:dish:null:{ghost_id}")
        time.sleep(0.3)
        cnt_b = measure(lambda: burst(f"{base}/dishes/{ghost_id}", n), noise)
        results.append(("穿透（开启防穿透）", n, cnt_b,
                        "空值缓存生效：首个请求打库，其余命中空值标记"))

    # ---------------- 实验二：击穿 ----------------
    hot_id = dish_ids[0]
    redis_del(f"cache:dish:detail:{hot_id}", f"cache:dish:null:{hot_id}")
    time.sleep(0.3)
    cnt_hit = measure(lambda: burst(f"{base}/dishes/{hot_id}", n), noise)
    results.append(("击穿（删热点 key）", n, cnt_hit,
                    "互斥锁 + 双重检查：只有一个线程回源"))

    # ---------------- 实验三：雪崩 ----------------
    redis_del(*[f"cache:dish:detail:{d}" for d in dish_ids])
    time.sleep(0.3)

    def avalanche():
        def one(idx):
            http_get(f"{base}/dishes/{dish_ids[idx % len(dish_ids)]}")
        threads = [threading.Thread(target=one, args=(i,)) for i in range(n)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

    cnt_av = measure(avalanche, noise)
    results.append((f"雪崩（{len(dish_ids)} 个 key 同时失效）", n, cnt_av,
                    "回源次数 ≈ 同时失效的 key 数"))

    # ---------------- 对照：预热后 ----------------
    before_warm = com_select()
    for d in dish_ids:
        http_get(f"{base}/dishes/{d}")
    aux_warm = com_select() - before_warm
    time.sleep(0.5)
    cnt_warm = measure(lambda: burst(f"{base}/dishes/{dish_ids[0]}", n), noise)
    results.append(("对照（预热后）", n, cnt_warm, "全部命中缓存，一次库都不打"))

    # ---------------- 输出 ----------------
    print()
    print("-" * 72)
    print(f"{'实验':<28}{'并发':>6}{'打库次数':>10}   说明")
    print("-" * 72)
    for name, conc, cnt, note in results:
        print(f"{name:<28}{conc:>6}{cnt:>10}   {note}")
    print("-" * 72)
    print("解读：")
    print("  穿透关闭 → 每个请求都回源；开启后 → 只有第一个回源。")
    print("  击穿 → 锁生效，50 个请求只放 1 个进库。")
    print(f"  雪崩 → 打库次数等于同时失效的 key 数（这里 {len(dish_ids)} 个）。")
    print("  对照 → 预热后全部命中，打库为 0。")

    # ---------------- 总账：把「脚本统计」和「全局计数」对上 ----------------
    global_delta = com_select() - global_baseline
    reported_sum = sum(cnt for _, _, cnt, _ in results)
    background = global_delta - reported_sum - aux_ids - aux_login - aux_warm
    print()
    print("-" * 72)
    print("总账（口径：脚本一启动到最后一次计数，等同于人工前后各查一次）：")
    print(f"  全局 Com_select 增量        = {global_delta} 次")
    print(f"  ├─ 实验组净增量（已扣噪声）  = {reported_sum} 次")
    print(f"  ├─ 取 4 个菜品 ID            = {aux_ids} 次")
    print(f"  ├─ 管理端登录鉴权            = {aux_login} 次")
    print(f"  ├─ 对照前的预热              = {aux_warm} 次")
    print(f"  └─ 读计数器自身开销 + 后台噪声 = {background} 次")
    print(f"  说明：每次用 mysql 客户端读一次 Com_select，连接时它自己就会 +1；")
    print(f"        脚本全程读了约 20 次计数器，所以这 {background} 次基本是「测量工具的开销」，不是业务查询。")
    print("-" * 72)

    if token:
        # 演示结束把开关恢复默认（开启）
        http_json(f"{base}/admin/cache/switch/null-cache?enabled=true", "POST", {}, auth)
        print("\n已恢复：防穿透开关 = 开启")


if __name__ == "__main__":
    main()
