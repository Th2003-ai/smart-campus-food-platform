# -*- coding: utf-8 -*-
"""
生成《内存数据库技术与本项目应用》技术分享 PPT。
内容严格对齐项目真实代码与实测结果。

v2 修复：
  - code_box 按行数与字号自动计算高度，避免文字溢出黑框
  - table 同时设置列宽与单元格宽度，避免列宽被渲染器自动撑开
  - 重构第 9/16/19/20 页布局，消除遮挡与不可见文字
"""
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

# ---------- 主题 ----------
NAVY   = RGBColor(0x16, 0x32, 0x4F)
NAVY2  = RGBColor(0x24, 0x4B, 0x73)
REDIS  = RGBColor(0xD8, 0x2C, 0x20)
ORANGE = RGBColor(0xE8, 0x61, 0x1A)
GREEN  = RGBColor(0x2E, 0x7D, 0x32)
BROWN  = RGBColor(0x8A, 0x4B, 0x08)
GRAY   = RGBColor(0x59, 0x59, 0x59)
MIDGRAY= RGBColor(0x7A, 0x7A, 0x7A)
LGRAY  = RGBColor(0xF2, 0xF4, 0xF7)
MGRAY  = RGBColor(0xD9, 0xDE, 0xE5)
WHITE  = RGBColor(0xFF, 0xFF, 0xFF)
CODEBG = RGBColor(0x1E, 0x1E, 0x1E)
CODEFG = RGBColor(0xD4, 0xD4, 0xD4)
CODECM = RGBColor(0x7F, 0xA8, 0x6A)
LIGHTBLUE = RGBColor(0xC5, 0xD6, 0xE6)
ACCENTBLUE = RGBColor(0x8F, 0xB8, 0xD8)

FONT = "微软雅黑"
MONO = "Consolas"

SW, SH = Inches(13.333), Inches(7.5)
BOTTOM_LIMIT = 7.28   # 正文下边界（留出页码空间）

prs = Presentation()
prs.slide_width, prs.slide_height = SW, SH
BLANK = prs.slide_layouts[6]

warnings = []


def to_in(v):
    """把 Emu 统一换算成英寸（所有坐标都是 Emu，直接除即可）。"""
    return float(v) / 914400.0


def set_font(run, name=FONT, size=18, bold=False, color=GRAY):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    rPr = run._r.get_or_add_rPr()
    ea = rPr.find(qn("a:ea"))
    if ea is None:
        ea = rPr.makeelement(qn("a:ea"), {})
        rPr.append(ea)
    ea.set("typeface", name)


def textbox(slide, left, top, width, height, wrap=True):
    tb = slide.shapes.add_textbox(left, top, width, height)
    tf = tb.text_frame
    tf.word_wrap = wrap
    tf.margin_left = tf.margin_right = Emu(0)
    tf.margin_top = tf.margin_bottom = Emu(0)
    return tb


def write(slide, left, top, width, height, text, size=18, bold=False,
          color=GRAY, align=PP_ALIGN.LEFT, name=FONT, line=1.25):
    tb = textbox(slide, left, top, width, height)
    p = tb.text_frame.paragraphs[0]
    p.alignment = align
    p.line_spacing = line
    r = p.add_run()
    r.text = text
    set_font(r, name, size, bold, color)
    return tb


def rect(slide, left, top, width, height, fill, line=None, shape=MSO_SHAPE.RECTANGLE):
    sp = slide.shapes.add_shape(shape, left, top, width, height)
    sp.fill.solid()
    sp.fill.fore_color.rgb = fill
    if line is None:
        sp.line.fill.background()
    else:
        sp.line.color.rgb = line
        sp.line.width = Pt(1)
    sp.shadow.inherit = False
    return sp


def header(slide, title, kicker=None, num=None):
    rect(slide, Emu(0), Emu(0), SW, Inches(0.09), NAVY)
    if kicker:
        write(slide, Inches(0.62), Inches(0.30), Inches(9), Inches(0.3),
              kicker, 11.5, True, REDIS)
        write(slide, Inches(0.62), Inches(0.58), Inches(11.4), Inches(0.62),
              title, 27, True, NAVY)
    else:
        write(slide, Inches(0.62), Inches(0.45), Inches(11.4), Inches(0.62),
              title, 27, True, NAVY)
    rect(slide, Inches(0.62), Inches(1.24), Inches(12.1), Pt(2.2), MGRAY)
    if num:
        write(slide, Inches(12.1), Inches(6.95), Inches(0.7), Inches(0.3),
              str(num), 10.5, False, MIDGRAY, align=PP_ALIGN.RIGHT)


def bullets(slide, left, top, width, items, size=16, gap=0.52, color=GRAY,
            bullet_color=REDIS, line=1.3):
    tb = textbox(slide, left, top, width, Inches(0.4 * len(items) + 0.4))
    tf = tb.text_frame
    for i, it in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.line_spacing = line
        p.space_after = Pt(gap * 12)
        r0 = p.add_run(); r0.text = "▪  "
        set_font(r0, FONT, size, True, bullet_color)
        if isinstance(it, tuple):
            r1 = p.add_run(); r1.text = it[0]
            set_font(r1, FONT, size, True, NAVY)
            r2 = p.add_run(); r2.text = it[1]
            set_font(r2, FONT, size, False, color)
        else:
            r1 = p.add_run(); r1.text = it
            set_font(r1, FONT, size, False, color)
    return tb


def code_height(code, size, has_title=True):
    """估算代码框所需高度（英寸）。中文字符回退字体会抬高行距，故用 1.52 系数。"""
    n = len(code.split("\n"))
    line_in = size * 1.52 / 72.0
    return (0.30 if has_title else 0.0) + n * line_in + 0.24


def code_box(slide, left, top, width, code, size=10.5, title=None, min_height=None):
    """绘制代码框，高度按内容自动计算；返回底部 y（Emu）。"""
    need = code_height(code, size, has_title=bool(title))
    h = need if min_height is None else max(need, min_height)
    top_in = to_in(top)
    if top_in + h > 7.30:
        warnings.append(f"code_box 溢出到 {top_in + h:.2f}in（上限 7.30）: {title}")
    height = Inches(h)
    rect(slide, left, top, width, height, CODEBG)
    y = top + Inches(0.13)
    if title:
        write(slide, left + Inches(0.22), y, width - Inches(0.4), Inches(0.24),
              title, 9.5, True, RGBColor(0x8A, 0x8A, 0x8A), name=MONO)
        y = y + Inches(0.30)
    tb = textbox(slide, left + Inches(0.22), y, width - Inches(0.44),
                 height - (y - top) - Inches(0.10))
    tf = tb.text_frame
    for i, ln in enumerate(code.split("\n")):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.line_spacing = 1.15
        run = p.add_run()
        run.text = ln if ln else " "
        s = ln.strip()
        if s.startswith("//") or s.startswith("--") or s.startswith("#"):
            set_font(run, MONO, size, False, CODECM)
        else:
            set_font(run, MONO, size, False, CODEFG)
    return top + height


def table(slide, left, top, width, data, col_widths=None, header_fill=NAVY,
          font_size=11.5, header_size=11.5, row_h=0.38, first_col_bold=False,
          zebra=True, align_center=(), name="table"):
    rows, cols = len(data), len(data[0])
    height = Inches(row_h * rows)
    top_in = to_in(top)
    if top_in + row_h * rows > 7.34:
        warnings.append(f"{name} 底部 {top_in + row_h*rows:.2f}in 超出 7.34")
    shape = slide.shapes.add_table(rows, cols, left, top, width, height)
    tbl = shape.table
    tbl.first_row = True
    tbl.horz_banding = False

    if col_widths:
        total = sum(col_widths)
        widths = [Emu(int(width * w / total)) for w in col_widths]
    else:
        widths = [Emu(int(width / cols))] * cols
    # 同时设置列宽与单元格宽，避免渲染器把列自动撑开
    for i, w in enumerate(widths):
        tbl.columns[i].width = w
    for r in range(rows):
        tbl.rows[r].height = Inches(row_h)
        for c in range(cols):
            cell = tbl.cell(r, c)
            cell.width = widths[c]
            cell.margin_left = Inches(0.09)
            cell.margin_right = Inches(0.06)
            cell.margin_top = Inches(0.02)
            cell.margin_bottom = Inches(0.02)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.fill.solid()
            if r == 0:
                cell.fill.fore_color.rgb = header_fill
            elif zebra and r % 2 == 0:
                cell.fill.fore_color.rgb = LGRAY
            else:
                cell.fill.fore_color.rgb = WHITE
            tf = cell.text_frame
            tf.word_wrap = True
            p = tf.paragraphs[0]
            p.alignment = PP_ALIGN.CENTER if (r == 0 or c in align_center) else PP_ALIGN.LEFT
            run = p.add_run()
            run.text = str(data[r][c])
            if r == 0:
                set_font(run, FONT, header_size, True, WHITE)
            elif first_col_bold and c == 0:
                set_font(run, FONT, font_size, True, NAVY)
            else:
                set_font(run, FONT, font_size, False, GRAY)
    return top + height


def cards(slide, left, top, width, items, height=1.1, gap=0.18):
    n = len(items)
    cw = (width - Inches(gap) * (n - 1)) / n
    for i, (big, label) in enumerate(items):
        x = left + (cw + Inches(gap)) * i
        rect(slide, x, top, cw, Inches(height), LGRAY)
        rect(slide, x, top, Inches(0.055), Inches(height), REDIS)
        write(slide, x + Inches(0.16), top + Inches(0.13), cw - Inches(0.26),
              Inches(0.42), big, 21, True, NAVY)
        write(slide, x + Inches(0.16), top + Inches(0.60), cw - Inches(0.26),
              Inches(0.42), label, 10.5, False, GRAY, line=1.15)


def note(slide, left, top, width, text, size=11.5, color=ORANGE, icon="⚠", bg=None):
    b = bg or RGBColor(0xFF, 0xF6, 0xEC)
    rect(slide, left, top, width, Inches(0.42), b)
    rect(slide, left, top, Inches(0.05), Inches(0.42), color)
    write(slide, left + Inches(0.18), top + Inches(0.11), width - Inches(0.3),
          Inches(0.26), f"{icon} {text}", size, False, BROWN)


# =====================================================================
# 1. 封面
# =====================================================================
s = prs.slides.add_slide(BLANK)
rect(s, Emu(0), Emu(0), SW, SH, NAVY)
rect(s, Emu(0), Inches(4.30), SW, Inches(0.05), REDIS)
write(s, Inches(1.0), Inches(1.62), Inches(11), Inches(0.4),
      "技术分享  ·  第 7 题", 14, True, ACCENTBLUE)
write(s, Inches(1.0), Inches(2.12), Inches(11.4), Inches(1.0),
      "内存数据库技术与本项目应用", 46, True, WHITE)
write(s, Inches(1.0), Inches(3.22), Inches(11.4), Inches(0.6),
      "以 Redis 为核心：优缺点对比、读写一致性方案、秒杀与抢单编程技巧",
      17, False, LIGHTBLUE)
write(s, Inches(1.0), Inches(4.72), Inches(11.4), Inches(0.4),
      "智能 AI 校园外卖平台", 16, True, REDIS)
write(s, Inches(1.0), Inches(5.22), Inches(11.4), Inches(0.4),
      "汇报人：____________        日期：2026 年 10 月", 13, False, RGBColor(0x9F, 0xB5, 0xC9))

# =====================================================================
# 2. 大纲
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "本次分享内容", "AGENDA", num=2)
items = [
    ("01  内存数据库是什么  ", "—— 定义与和「数据库 + 缓存」的本质区别"),
    ("02  主流产品优缺点  ", "—— Redis / Memcached / Ignite / Hazelcast / Couchbase"),
    ("03  读写一致性问题  ", "—— 三大来源与五种解决方案（重点）"),
    ("04  本项目的 Redis 落地  ", "—— 9 个真实场景与对应代码位置（重点）"),
    ("05  秒杀场景编程技巧  ", "—— 超卖、分布式锁、多层削峰"),
    ("06  实测验证与总结  ", "—— 真实运行数据 + 一句话结论"),
]
y = Inches(1.62)
for i, (a, b) in enumerate(items):
    rect(s, Inches(0.62), y, Inches(0.5), Inches(0.5), NAVY)
    write(s, Inches(0.62), y + Inches(0.11), Inches(0.5), Inches(0.3),
          f"0{i+1}", 13, True, WHITE, align=PP_ALIGN.CENTER)
    tb = textbox(s, Inches(1.32), y + Inches(0.06), Inches(11.2), Inches(0.44))
    p = tb.text_frame.paragraphs[0]
    r1 = p.add_run(); r1.text = a; set_font(r1, FONT, 17, True, NAVY)
    r2 = p.add_run(); r2.text = b; set_font(r2, FONT, 13, False, GRAY)
    y = y + Inches(0.80)

# =====================================================================
# 3. 什么是内存数据库
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "什么是内存数据库", "01", num=3)
rect(s, Inches(0.62), Inches(1.50), Inches(12.1), Inches(0.80), LGRAY)
rect(s, Inches(0.62), Inches(1.50), Inches(0.055), Inches(0.80), REDIS)
write(s, Inches(0.86), Inches(1.64), Inches(11.6), Inches(0.54),
      "把数据主体放在内存里、以内存访问速度提供读写能力的数据库。", 17, True, NAVY)

write(s, Inches(0.62), Inches(2.58), Inches(8), Inches(0.32),
      "与「MySQL + Redis 当缓存用」的本质区别", 13.5, True, REDIS)
table(s, Inches(0.62), Inches(2.98), Inches(12.1), [
    ["对比项", "关系库 + 缓存", "内存数据库（Redis 当库用）"],
    ["数据权威来源", "磁盘库", "内存（可持久化，但内存为准）"],
    ["读写路径", "写库 → 失效缓存", "直接读写内存"],
    ["延迟量级", "磁盘随机 I/O，毫秒级", "内存访问，微秒级"],
    ["数据结构", "表 / 行 / 索引", "String / Hash / List / Set / ZSet / 位图 / HLL / Stream"],
], col_widths=[1.55, 3.05, 5.10], row_h=0.46, font_size=12, first_col_bold=True,
   name="s3-table")

note(s, Inches(0.62), Inches(5.62), Inches(12.1),
     "关键认知：Redis 既是「缓存」也是「内存数据库」，取决于你把它放在什么位置。本项目两种身份都用到了。",
     size=12.5, color=NAVY2, icon="▶", bg=RGBColor(0xEE, 0xF3, 0xF8))

# =====================================================================
# 4. 主流内存数据库对比
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "主流内存数据库一览", "02", num=4)
table(s, Inches(0.62), Inches(1.48), Inches(12.1), [
    ["产品", "定位", "数据模型", "持久化", "典型场景"],
    ["Redis", "通用内存数据库", "KV + 丰富结构", "RDB / AOF", "缓存、计数、排行榜、分布式锁、限流"],
    ["Memcached", "纯缓存", "仅 KV（字符串）", "无", "简单大对象缓存，无排序 / 持久化需求"],
    ["Apache Ignite", "内存计算网格", "KV + SQL", "原生持久化", "内存加速数据库、分布式计算"],
    ["Hazelcast", "内存数据网格", "KV + 分布式集合", "支持", "分布式缓存、集群计算"],
    ["Couchbase", "文档型内存库", "JSON + N1QL", "支持", "高并发文档读写"],
    ["SAP HANA", "内存列式数据库", "关系表（列存）", "支持", "OLAP 实时分析"],
], col_widths=[1.55, 2.05, 2.30, 1.35, 4.85], row_h=0.50, font_size=11.5,
   first_col_bold=True, name="s4-table")

note(s, Inches(0.62), Inches(5.12), Inches(12.1),
     "本项目选型结论：业务是「校园外卖」，核心诉求是热点读加速 + 并发协调 + 排行榜，Redis 的丰富数据结构与原子原语最贴合，故选 Redis。",
     size=12, color=GREEN, icon="✔", bg=RGBColor(0xEC, 0xF5, 0xEC))
write(s, Inches(0.62), Inches(5.78), Inches(12.1), Inches(0.7),
      "对比维度建议：① 数据结构是否贴合业务　② 是否原生分布式　③ 持久化与丢数据风险　④ 生态与团队熟悉度",
      12.5, False, GRAY)

# =====================================================================
# 5. Redis 优点
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "Redis 的优点：为什么快", "02", num=5)
bullets(s, Inches(0.62), Inches(1.48), Inches(12.1), [
    ("消除磁盘 I/O  ", "数据常驻内存，读写不必等待磁盘寻道 —— 这是性能优势的根本原因"),
    ("数据结构贴近业务  ", "ZSet 天然做排行榜，HLL 天然做去重计数，省掉应用层排序与聚合"),
    ("单线程串行执行  ", "命令串行，天然无锁竞争，单条命令的原子性「免费」获得"),
    ("原子操作原语  ", "SETNX / INCR / ZADD / SET NX EX 是分布式锁、限流器的基础积木"),
    ("协议简单、连接开销小  ", "RESP 协议 + 长连接复用，海量并发下连接成本低"),
], size=15.5, gap=0.50)

rect(s, Inches(0.62), Inches(4.42), Inches(5.9), Inches(1.92), LGRAY)
write(s, Inches(0.86), Inches(4.58), Inches(5.4), Inches(0.32),
      "为什么单线程还快？", 14, True, NAVY)
bullets(s, Inches(0.86), Inches(4.98), Inches(5.4), [
    "纯内存操作，单命令微秒级",
    "避免多线程锁竞争与上下文切换",
    "IO 多路复用（epoll）处理海量连接",
], size=11.5, gap=0.30, bullet_color=NAVY)

rect(s, Inches(6.82), Inches(4.42), Inches(5.9), Inches(1.92), RGBColor(0xFF, 0xF6, 0xEC))
rect(s, Inches(6.82), Inches(4.42), Inches(0.055), Inches(1.92), ORANGE)
write(s, Inches(7.06), Inches(4.58), Inches(5.4), Inches(0.32),
      "真正的瓶颈不是「单线程」", 14, True, ORANGE)
write(s, Inches(7.06), Inches(4.98), Inches(5.5), Inches(1.2),
      "而是「大 key 与慢命令」：\nKEYS *、大 ZSet 全量遍历、大 Hash 删除\n都会阻塞整个实例，必须禁用 / 拆分。",
      11.5, False, BROWN, line=1.35)

# =====================================================================
# 6. Redis 缺点
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "Redis 的缺点与应对", "02", num=6)
table(s, Inches(0.62), Inches(1.48), Inches(12.1), [
    ["缺点", "具体表现", "应对手段"],
    ["内存贵、容量有限", "内存单价远高于磁盘，单实例容易打满", "只放热数据、设 TTL、合理 maxmemory-policy"],
    ["数据易失", "进程崩溃 / 宕机，未持久化数据丢失", "RDB + AOF；关键数据仍落磁盘库"],
    ["持久化有代价", "RDB fork 有内存翻倍风险；AOF always 拖慢写", "按业务选 appendfsync；从库做持久化"],
    ["单线程瓶颈", "大 key 操作阻塞整个实例", "禁用危险命令、改用 SCAN、拆分大 key"],
    ["分布式一致性弱", "主从异步复制，故障切换可能丢数据", "不把「绝不能丢」的数据只放内存库"],
], col_widths=[2.05, 4.35, 5.70], row_h=0.60, font_size=11.5,
   first_col_bold=True, name="s6-table")

rect(s, Inches(0.62), Inches(5.30), Inches(12.1), Inches(1.10), NAVY)
write(s, Inches(0.90), Inches(5.48), Inches(11.5), Inches(0.30),
      "一句话取舍", 12.5, True, ACCENTBLUE)
write(s, Inches(0.90), Inches(5.82), Inches(11.6), Inches(0.44),
      "内存数据库用「贵、易失、容量小」换「快、原子、结构丰富」—— 适合承载「热、可重建、强并发」的数据，而不是「冷、不可丢、强一致」的数据。",
      13, True, WHITE)

# =====================================================================
# 7. 读写一致性 - 三大来源
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "读写一致性：不一致从哪来", "03  重点分析", num=7)
write(s, Inches(0.62), Inches(1.42), Inches(12.1), Inches(0.4),
      "讨论「怎么解决」之前，先把「不一致」拆成可分析的三类来源：", 13.5, False, GRAY)

srcs = [
    ("①  缓存与数据库双写", "写库成功，但失效缓存失败\n→ 缓存长期是旧值", REDIS),
    ("②  并发竞态", "读-改-写不是原子操作\n→ 超卖 / 丢失更新", ORANGE),
    ("③  多节点异步复制", "主从延迟\n→ 读到旧数据", NAVY2),
]
cw = Inches(3.86)
for i, (t, d, c) in enumerate(srcs):
    x = Inches(0.62) + (cw + Inches(0.26)) * i
    rect(s, x, Inches(1.90), cw, Inches(1.50), LGRAY)
    rect(s, x, Inches(1.90), cw, Inches(0.06), c)
    write(s, x + Inches(0.22), Inches(2.08), cw - Inches(0.4), Inches(0.36), t, 14, True, NAVY)
    write(s, x + Inches(0.22), Inches(2.50), cw - Inches(0.4), Inches(0.78), d, 11.5, False, GRAY, line=1.35)

write(s, Inches(0.62), Inches(3.62), Inches(12.1), Inches(0.32),
      "对应的五种解决手段", 14, True, REDIS)
table(s, Inches(0.62), Inches(4.00), Inches(12.1), [
    ["方案", "针对的问题", "核心做法"],
    ["Cache Aside（旁路缓存）", "① 双写不一致", "读时回填；写时「先更新库、再删除缓存」"],
    ["延迟双删", "① 并发读写窗口", "删缓存 → 写库 → 延迟 N 毫秒再删一次"],
    ["原子操作 / 分布式锁", "② 并发竞态", "条件更新、Lua 脚本、SET NX EX 锁"],
    ["只让内存库做「可重建」的事", "② 正确性风险", "硬判断交给数据库原子语义，Redis 只加速"],
    ["接受最终一致 + 兜底", "③ 复制延迟", "短 TTL 收敛；关键读一律回源数据库"],
], col_widths=[3.30, 2.30, 6.50], row_h=0.46, font_size=11.5,
   first_col_bold=True, name="s7-table")

# =====================================================================
# 8. Cache Aside
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "方案一：Cache Aside（本项目采用）", "03  重点分析", num=8)

rect(s, Inches(0.62), Inches(1.48), Inches(5.9), Inches(1.90), LGRAY)
write(s, Inches(0.86), Inches(1.62), Inches(5.4), Inches(0.30), "读", 15, True, GREEN)
write(s, Inches(0.86), Inches(2.00), Inches(5.5), Inches(1.25),
      "1  查缓存\n2  命中 → 直接返回\n3  未命中 → 查数据库 → 回填缓存（带 TTL）",
      12.5, False, GRAY, line=1.45)

rect(s, Inches(6.82), Inches(1.48), Inches(5.9), Inches(1.90), LGRAY)
write(s, Inches(7.06), Inches(1.62), Inches(5.4), Inches(0.30), "写", 15, True, REDIS)
write(s, Inches(7.06), Inches(2.00), Inches(5.5), Inches(1.25),
      "1  先更新数据库\n2  再【删除】缓存（不是更新缓存）\n3  下次读时自动回填",
      12.5, False, GRAY, line=1.45)

rect(s, Inches(0.62), Inches(3.54), Inches(12.1), Inches(1.32), RGBColor(0xEC, 0xF5, 0xEC))
rect(s, Inches(0.62), Inches(3.54), Inches(0.055), Inches(1.32), GREEN)
write(s, Inches(0.88), Inches(3.68), Inches(11.6), Inches(0.30),
      "为什么必须「先写库、后删缓存」？", 13, True, GREEN)
write(s, Inches(0.88), Inches(4.02), Inches(11.7), Inches(0.74),
      "若先删缓存再写库：删完、库还没写完的窗口里，另一个读请求会把旧值重新载入缓存，并一直留到 TTL 过期 → 长期脏数据。\n"
      "先写库后删缓存，最坏只是「短时间读到旧值」，下次读即修正，不一致窗口短得多。",
      11.5, False, RGBColor(0x2E, 0x5A, 0x2E), line=1.35)

write(s, Inches(0.62), Inches(5.02), Inches(12.1), Inches(0.30),
      "删除失败怎么办（双写不一致的典型坑）", 13, True, ORANGE)
write(s, Inches(0.62), Inches(5.36), Inches(12.1), Inches(0.9),
      "① 删除重试（消息队列 / 本地重试表，保证至少删成功一次）\n"
      "② 订阅数据库 binlog（Canal）异步删除，把「删缓存」做成准实时可靠投递",
      11.5, False, GRAY, line=1.4)

write(s, Inches(0.62), Inches(6.28), Inches(12.1), Inches(0.5),
      "补充：方案二「延迟双删」= 删缓存 → 写库 → 延迟 N 毫秒再删一次，用于清除并发窗口内被误回填的旧值。",
      11, False, MIDGRAY)

# =====================================================================
# 9. 本项目 Cache Aside 代码（重构：代码框加高、字号缩小）
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "本项目实现：DishService", "03  重点分析", num=9)
write(s, Inches(0.62), Inches(1.40), Inches(12.1), Inches(0.3),
      "DishService.java — 读走缓存、写后删缓存", 12.5, False, GRAY)

code_box(s, Inches(0.62), Inches(1.76), Inches(6.55), """// 读：缓存优先，未命中回源并回填
public Dish getDetail(String dishId) {
    Object cached = redis.opsForValue().get(cacheKey);
    if (cached instanceof Dish dish) return dish;

    // 防击穿：只允许一个线程回源重建
    boolean locked = redis.opsForValue()
            .setIfAbsent(lockKey, "1", 5, SECONDS);
    // 抢到锁 → 查库 → 回填 → finally 释放锁
    // 抢不到 → 短暂等待读缓存，仍无则查库兜底
    redis.opsForValue().set(cacheKey, dish,
            jitterMinutes(10), MINUTES);   // TTL 抖动
    return dish;
}

// 写：先改库，再删缓存（delete 而非 update）
public void updateStatus(String dishId, int status) {
    dish.setStatus(status);
    dishMapper.updateById(dish);      // ① 先写库
    evictCache(dishId);               // ② 再删缓存
    hotRankService.touchDishRank(dish);
}""", size=9.3, title="DishService.java")

rect(s, Inches(7.42), Inches(1.76), Inches(5.3), Inches(2.10), LGRAY)
write(s, Inches(7.66), Inches(1.90), Inches(4.8), Inches(0.30),
      "为什么「删除」而不是「更新」缓存", 12.5, True, NAVY)
write(s, Inches(7.66), Inches(2.26), Inches(4.9), Inches(1.45),
      "更新缓存要先算出新值；若此时数据库已被其他事务改动，\n写入的就是错值。\n而删除只会让下次读重新加载最新值，更安全。",
      11, False, GRAY, line=1.4)

rect(s, Inches(7.42), Inches(4.02), Inches(5.3), Inches(2.35), RGBColor(0xFF, 0xF6, 0xEC))
rect(s, Inches(7.42), Inches(4.02), Inches(0.055), Inches(2.35), ORANGE)
write(s, Inches(7.66), Inches(4.16), Inches(4.8), Inches(0.30),
      "本项目额外加固的两点", 12.5, True, ORANGE)
write(s, Inches(7.66), Inches(4.52), Inches(4.9), Inches(1.7),
      "① 互斥锁重建\n热点 key 过期瞬间只允许一个线程回源，\n其余线程等待缓存，避免并发压库。\n\n"
      "② TTL 加 ±20% 抖动\n把过期时间打散，缓解缓存雪崩。",
      10.5, False, BROWN, line=1.4)

# =====================================================================
# 10. 方案三 四
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "方案三 ~ 四：并发与正确性", "03  重点分析", num=10)

blocks = [
    ("方案三  原子操作 / 分布式锁", ORANGE,
     "能用一条原子命令解决，就不要用锁\n\n"
     "· 条件更新：UPDATE ... WHERE stock >= 1\n"
     "· 计数：INCR / DECR\n"
     "· 多命令原子：Lua 脚本\n  （Redis 内单线程执行）\n"
     "· 必须跨多步：SET NX EX 分布式锁"),
    ("方案四  让内存库只做可重建的事", GREEN,
     "最省事也最稳的一条\n\n"
     "把「绝对不能错」的判断交给磁盘库的原子语义，Redis 只承担「降低压力」的职责。\n\n"
     "好处：Redis 挂了业务正确性不受影响，只是变慢。"),
    ("方案五  接受最终一致 + 兜底", NAVY2,
     "适合「点赞数、浏览量、推荐结果」这类数据\n\n"
     "· 缓存带 TTL，过期自然收敛\n"
     "· 关键状态变更以数据库为准\n"
     "· 缓存失效时回源数据库\n\n"
     "核心：明确业务能容忍多大的不一致"),
]
cw = Inches(3.86)
for i, (t, c, body) in enumerate(blocks):
    x = Inches(0.62) + (cw + Inches(0.26)) * i
    rect(s, x, Inches(1.48), cw, Inches(3.55), LGRAY)
    rect(s, x, Inches(1.48), cw, Inches(0.06), c)
    write(s, x + Inches(0.22), Inches(1.66), cw - Inches(0.4), Inches(0.34), t, 13, True, NAVY)
    write(s, x + Inches(0.22), Inches(2.10), cw - Inches(0.40), Inches(2.8), body, 10.5, False, GRAY, line=1.42)

note(s, Inches(0.62), Inches(5.24), Inches(12.1),
     "本项目的取舍：方案一（Cache Aside）+ 方案三（条件更新 / 分布式锁）+ 方案四（硬判断落库），不追求强一致，而是把不一致窗口压到业务可接受范围。",
     size=12, color=NAVY2, icon="▶", bg=RGBColor(0xEE, 0xF3, 0xF8))

# =====================================================================
# 11. 本项目库存扣减
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "关键设计：库存不放在 Redis", "03  重点分析", num=11)
write(s, Inches(0.62), Inches(1.40), Inches(12.1), Inches(0.4),
      "「内存库只做加速、不做最终正确性」在本项目最典型的体现 —— 库存扣减靠 MySQL 条件更新：",
      13, False, GRAY)

code_box(s, Inches(0.62), Inches(1.88), Inches(6.5), """-- DishMapper.deductStock
UPDATE dish
   SET stock = stock - #{quantity},
       update_time = NOW()
 WHERE id = #{dishId}
   AND deleted = 0
   AND stock >= #{quantity}
-- 影响行数 0 → 库存不足；1 → 扣减成功""", size=10.5, title="DishMapper.java")

code_box(s, Inches(7.32), Inches(1.88), Inches(5.4), """// ❌ 错误：先查后改
int stock = query("SELECT stock ...");
if (stock > 0) {
    // 两线程可能都读到 1
    update("UPDATE ... stock - 1");
    // → 都扣 → 超卖
}""", size=10, title="竞态写法（会超卖）")

note(s, Inches(0.62), Inches(4.42), Inches(6.5),
     "判断与扣减在同一条 SQL 内完成，中间没有窗口 —— 库存永不为负。",
     size=11, color=GREEN, icon="✔", bg=RGBColor(0xEC, 0xF5, 0xEC))
note(s, Inches(7.32), Inches(4.42), Inches(5.4),
     "SELECT 与 UPDATE 之间的时间差，就是超卖的来源。",
     size=11, color=REDIS, icon="✘", bg=RGBColor(0xFD, 0xEC, 0xEC))

rect(s, Inches(0.62), Inches(5.06), Inches(12.1), Inches(1.30), NAVY)
write(s, Inches(0.90), Inches(5.22), Inches(11.5), Inches(0.30),
      "为什么这是对的取舍", 12, True, ACCENTBLUE)
write(s, Inches(0.90), Inches(5.56), Inches(11.6), Inches(0.66),
      "Redis 只承担缓存、抢单池、协调职责；即使 Redis 完全宕机，订单与库存的正确性也不受影响 —— 只是变慢。\n这是「加速」与「正确」的职责分离。",
      12, False, WHITE, line=1.35)

# =====================================================================
# 12. 选型速查表
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "一致性方案选型速查表", "03  重点分析", num=12)
table(s, Inches(0.62), Inches(1.48), Inches(12.1), [
    ["业务场景", "推荐方案", "原因", "本项目"],
    ["详情页 / 热点读", "Cache Aside + TTL", "读多写少，短时间旧值可接受", "✔ 已实现"],
    ["库存扣减 / 秒杀", "原子条件更新 或 Redis 预扣 + 异步补偿", "绝不能超卖", "✔ 条件更新"],
    ["抢单（一单只能一人）", "分布式锁 + 数据库兜底条件", "并发写同一资源", "✔ 已实现"],
    ["推荐结果", "缓存 + TTL（最终一致）", "允许过期，过期重算即可", "✔ 已实现"],
    ["会话上下文", "内存库 + TTL", "可丢，Redis 为准", "✔ 已实现"],
    ["金额 / 余额", "只落磁盘库 + 事务，禁止只放缓存", "资金绝不能错", "✔ 已实现"],
], col_widths=[2.45, 4.30, 3.65, 1.70], row_h=0.54, font_size=11,
   first_col_bold=True, align_center=(3,), name="s12-table")
note(s, Inches(0.62), Inches(5.36), Inches(12.1),
     "核心判断顺序：先问「这份数据丢了会怎样」→ 再问「这个判断能不能用一条原子语句完成」→ 最后才考虑缓存策略。",
     size=12, color=NAVY2, icon="▶", bg=RGBColor(0xEE, 0xF3, 0xF8))

# =====================================================================
# 13. 本项目 Redis 落地全景
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "本项目 Redis 落地全景（9 个场景）", "04  本项目实践", num=13)
table(s, Inches(0.62), Inches(1.42), Inches(12.1), [
    ["#", "场景", "数据结构", "代码位置", "一致性策略"],
    ["1", "菜品详情缓存", "String(JSON)", "DishService.getDetail", "写后删缓存 + 击穿防护"],
    ["2", "店铺详情缓存", "String(JSON)", "ShopService.getDetail", "写后删缓存"],
    ["3", "抢单分布式锁", "String", "DeliveryService.grab", "锁 + 库条件兜底"],
    ["4", "骑手抢单池", "ZSet", "OrderService.pushToGrabPool", "以库为准，Redis 仅加速"],
    ["5", "AI 推荐结果缓存", "String(JSON)", "RecommendService", "TTL 10min（最终一致）"],
    ["6", "客服多轮上下文", "List（窗口）", "ChatbotService", "TTL 60min，可丢"],
    ["7", "Token 黑名单", "String", "AuthService.logout", "TTL = Token 剩余有效期"],
    ["8", "热门店铺 / 菜品榜", "ZSet", "rank/HotRankService", "score=月销量 + 主动失效"],
    ["9", "骑手在线状态", "String", "delivery/RiderOnlineService", "TTL 60s 表达离线窗口"],
], col_widths=[0.55, 2.45, 1.60, 3.85, 3.65], row_h=0.49, font_size=10.5,
   header_size=11, first_col_bold=True, align_center=(0,), name="s13-table")
write(s, Inches(0.62), Inches(6.58), Inches(12.1), Inches(0.3),
      "注：第 7~9 项为本次迭代新增实现；第 1~6 项为原有实现。", 11, False, MIDGRAY)

# =====================================================================
# 14. 场景一 Token 黑名单
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "场景一：Token 黑名单（安全缺口修复）", "04  本项目实践", num=14)

rect(s, Inches(0.62), Inches(1.44), Inches(12.1), Inches(0.66), RGBColor(0xFD, 0xEC, 0xEC))
rect(s, Inches(0.62), Inches(1.44), Inches(0.055), Inches(0.66), REDIS)
write(s, Inches(0.88), Inches(1.58), Inches(11.6), Inches(0.40),
      "此前问题：JWT 无状态，logout() 里只有一句 TODO —— 退出登录后旧 token 仍然可用。",
      12.5, False, RGBColor(0x8A, 0x1A, 0x1A))

code_box(s, Inches(0.62), Inches(2.24), Inches(6.55), """public void logout(String header) {
    String token = resolveToken(header);
    long remaining =
        jwt.getRemainingSeconds(token);
    if (remaining <= 0) return;  // 已过期

    redis.opsForValue().set(
        "auth:token:blacklist:" + token,
        System.currentTimeMillis(),
        remaining, TimeUnit.SECONDS);
}""", size=9.8, title="AuthService.java")

code_box(s, Inches(7.37), Inches(2.24), Inches(5.35), """// AuthInterceptor 每次请求先查黑名单
if (authService
        .isTokenBlacklisted(token)) {
    log.debug("Token 已在黑名单中");
    return null;       // → 401
}""", size=9.8, title="AuthInterceptor.java", min_height=2.55)

write(s, Inches(0.62), Inches(5.02), Inches(12.1), Inches(0.30),
      "为什么 TTL 要等于「Token 剩余有效期」", 12.5, True, NAVY)
write(s, Inches(0.62), Inches(5.36), Inches(12.1), Inches(0.9),
      "Token 自然过期后，黑名单条目也不再有意义。若设固定长 TTL，Redis 会堆积大量永远用不到的 key；\n"
      "用剩余有效期，条目随 token 一起消失，无需任何定时清理任务。",
      11.5, False, GRAY, line=1.4)

note(s, Inches(0.62), Inches(6.30), Inches(12.1),
     "实测：logout 后同 token 再访问 → code=401，日志「Token 已在黑名单中，拒绝访问」，黑名单 TTL=43199 秒。",
     size=11.5, color=GREEN, icon="✔", bg=RGBColor(0xEC, 0xF5, 0xEC))

# =====================================================================
# 15. 场景二 热门榜单 ZSet
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "场景二：热门榜单（ZSet 的典型用法）", "04  本项目实践", num=15)

write(s, Inches(0.62), Inches(1.42), Inches(6.4), Inches(0.34),
      "ZSet 的 score 直接存「月销量」", 13.5, True, NAVY)
bullets(s, Inches(0.62), Inches(1.82), Inches(6.4), [
    "取 TopN 就是 ZREVRANGE，排序在 Redis 内完成",
    "不需要把全表捞到应用层再排序",
    "配套 List 缓存实体快照，避免每次回表",
    "score 变化后只需更新一条，无需全量重建",
], size=12, gap=0.42)

code_box(s, Inches(0.62), Inches(3.46), Inches(6.4), """// HotRankService：查库后同时刷新 ZSet 与明细
for (Dish dish : dishes) {
    double score = dish.getMonthlySales();
    redis.opsForZSet()
         .add("cache:dish:hot", dish.getId(), score);
}
redis.opsForValue().set("cache:dish:hot:list",
        dishes, jitterMinutes(5), MINUTES);""", size=9.8, title="HotRankService.java")

rect(s, Inches(7.22), Inches(1.42), Inches(5.5), Inches(2.55), LGRAY)
write(s, Inches(7.46), Inches(1.56), Inches(5.0), Inches(0.30),
      "实测结果（真实运行）", 13, True, REDIS)
write(s, Inches(7.46), Inches(1.92), Inches(5.1), Inches(1.9),
      "ZREVRANGE cache:dish:hot 0 -1 WITHSCORES\n\n"
      "\"DI…004\"  500   现磨豆浆\n"
      "\"DI…001\"  320   清炒时蔬盖饭\n"
      "\"DI…002\"  280   番茄鸡蛋盖饭\n"
      "\"DI…003\"  210   红烧牛肉面\n\n"
      "TTL cache:dish:hot:list → 360 秒",
      9.8, False, GRAY, name=MONO, line=1.3)

write(s, Inches(7.22), Inches(4.14), Inches(5.5), Inches(0.30),
      "写操作主动失效（保持新鲜）", 12.5, True, NAVY)
write(s, Inches(7.22), Inches(4.48), Inches(5.5), Inches(1.3),
      "· 菜品上下架 / 改库存 → touchDishRank()\n"
      "· 店铺改资料 / 营业状态 → invalidateShopRank()\n"
      "· 兜底：TTL 5 分钟自动收敛",
      11, False, GRAY, line=1.45)

note(s, Inches(0.62), Inches(6.28), Inches(12.1),
     "设计取舍：榜单属于「允许短暂过期」的数据，因此用短 TTL + 主动失效即可，不需要强一致。",
     size=11.5, color=NAVY2, icon="▶", bg=RGBColor(0xEE, 0xF3, 0xF8))

# =====================================================================
# 16. 场景三 击穿防护（重构：去掉冗余、压缩表格）
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "场景三：缓存击穿防护 + TTL 抖动", "04  本项目实践", num=16)

code_box(s, Inches(0.62), Inches(1.42), Inches(6.3), """// 冷 key 只允许一个线程回源重建
boolean locked = redis.opsForValue()
        .setIfAbsent(lockKey, "1", 5, SECONDS);
if (locked) {
    try {
        // 双重检查：等锁期间可能已被回填
        if (命中缓存) return cached;
        Dish dish = dishMapper.selectById(id);
        redis.set(cacheKey, dish,
                jitterMinutes(10), MINUTES);
        return dish;
    } finally { redis.delete(lockKey); }
}
// 未抢到锁：短暂等待读缓存，仍无则查库兜底""", size=9.0, title="DishService.getDetail")

rect(s, Inches(7.10), Inches(1.42), Inches(5.62), Inches(1.68), LGRAY)
write(s, Inches(7.34), Inches(1.56), Inches(5.1), Inches(0.30),
      "问题：热点 key 过期瞬间的并发洪峰", 12, True, REDIS)
write(s, Inches(7.34), Inches(1.90), Inches(5.2), Inches(1.12),
      "热点菜品缓存过期那一刻，若 20 个请求同时未命中，\n就会同时压到数据库 —— 这就是缓存击穿。",
      10.5, False, GRAY, line=1.4)

rect(s, Inches(7.10), Inches(3.22), Inches(5.62), Inches(1.33), RGBColor(0xEC, 0xF5, 0xEC))
rect(s, Inches(7.10), Inches(3.22), Inches(0.055), Inches(1.33), GREEN)
write(s, Inches(7.34), Inches(3.34), Inches(5.1), Inches(0.30),
      "实测证据", 12, True, GREEN)
write(s, Inches(7.34), Inches(3.66), Inches(5.2), Inches(0.82),
      "清空缓存 → 20 并发打同一菜品详情 → 20/20 全部成功\n→ 日志中只有 1 条 SELECT；TTL=360 秒（抖动生效）",
      10.5, False, RGBColor(0x2E, 0x5A, 0x2E), line=1.4)

write(s, Inches(0.62), Inches(4.74), Inches(12.1), Inches(0.28),
      "缓存三兄弟：穿透 / 击穿 / 雪崩", 12.5, True, NAVY)
table(s, Inches(0.62), Inches(5.06), Inches(12.1), [
    ["问题", "现象", "解决"],
    ["缓存穿透", "查不存在的数据，每次都打库", "缓存空值（短 TTL）、布隆过滤器"],
    ["缓存击穿", "热点 key 过期瞬间大量请求打库", "互斥锁重建、逻辑过期 + 异步刷新"],
    ["缓存雪崩", "大量 key 同一时刻过期", "TTL 随机抖动、多级缓存、Redis 高可用"],
], col_widths=[1.55, 5.05, 5.50], row_h=0.42, font_size=10.5,
   first_col_bold=True, name="s16-table")

# =====================================================================
# 17. 场景四 骑手在线
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "场景四：骑手在线状态（用 TTL 代替定时扫描）", "04  本项目实践", num=17)

write(s, Inches(0.62), Inches(1.42), Inches(12.1), Inches(0.32),
      "为什么这个数据适合放 Redis 而不是数据库字段", 13.5, True, NAVY)
cards(s, Inches(0.62), Inches(1.82), Inches(12.1), [
    ("高频写", "每次心跳都要刷新状态"),
    ("低频读", "只在抢单、看板时查询"),
    ("可丢失", "丢了最坏是一次误判"),
    ("自清理", "TTL 到期自动失效"),
], height=1.05)

write(s, Inches(0.62), Inches(3.10), Inches(12.1), Inches(0.32),
      "核心技巧：让「过期」承担「离线判定」", 13.5, True, REDIS)
write(s, Inches(0.62), Inches(3.46), Inches(12.1), Inches(0.86),
      "rider:online:{riderId} 的值是最后心跳时间戳，键的有效期即「离线判定窗口」（60 秒）。\n"
      "骑手进程崩溃或断网时不会主动上报下线 —— 靠 TTL 自然过期即可判定离线，无需任何定时扫描任务。",
      11.5, False, GRAY, line=1.4)

code_box(s, Inches(0.62), Inches(4.42), Inches(6.3), """// WebSocket 建立 → 标记在线
riderOnlineService.markOnlineByUserId(userId);

// 收到 ping 心跳 → 续期
redis.opsForValue().set(key, now,
    ONLINE_TTL_SECONDS, SECONDS);   // 60s

// 断开 / 异常 → 清除
riderOnlineService.markOfflineByUserId(userId);""", size=9.5, title="OrderNotifyHandler")

rect(s, Inches(7.10), Inches(4.42), Inches(5.62), Inches(2.10), LGRAY)
write(s, Inches(7.34), Inches(4.56), Inches(5.1), Inches(0.30),
      "实测结果", 12.5, True, GREEN)
write(s, Inches(7.34), Inches(4.92), Inches(5.2), Inches(1.4),
      "心跳前　online = false\n心跳后　online = true，TTL = 60\n删除 key（模拟超时）\n　　　　→ online = false",
      10.5, False, GRAY, line=1.5)

# =====================================================================
# 18. 秒杀 - 超卖
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "秒杀场景：为什么难", "05  秒杀与抢单", num=18)

write(s, Inches(0.62), Inches(1.42), Inches(12.1), Inches(0.32),
      "瞬时高并发 + 有限库存 → 三个必须解决的问题", 13.5, True, NAVY)
probs = [("① 超卖", "库存被扣成负数", REDIS), ("② 击穿数据库", "连接池瞬间打满", ORANGE),
         ("③ 一单多抢", "同一资源被多次占用", NAVY2)]
cw = Inches(3.86)
for i, (t, d, c) in enumerate(probs):
    x = Inches(0.62) + (cw + Inches(0.26)) * i
    rect(s, x, Inches(1.84), cw, Inches(0.92), LGRAY)
    rect(s, x, Inches(1.84), Inches(0.055), Inches(0.92), c)
    write(s, x + Inches(0.24), Inches(1.98), cw - Inches(0.4), Inches(0.32), t, 13.5, True, c)
    write(s, x + Inches(0.24), Inches(2.32), cw - Inches(0.4), Inches(0.3), d, 11, False, GRAY)

write(s, Inches(0.62), Inches(2.96), Inches(12.1), Inches(0.30),
      "超卖的根源：把「判断」和「扣减」拆成了两步", 13, True, REDIS)

code_box(s, Inches(0.62), Inches(3.34), Inches(6.0), """// ❌ 先查后改 —— 有竞态窗口
int stock = query("SELECT stock ...");
if (stock > 0) {          // 都读到 1
    update("UPDATE dish SET stock=stock-1");
    // 两个线程都进这个分支 → 都扣
}""", size=9.8, title="对照组")

code_box(s, Inches(6.82), Inches(3.34), Inches(5.9), """-- ✅ 条件更新 —— 判断与扣减原子完成
UPDATE dish
   SET stock = stock - #{quantity}
 WHERE id = #{dishId}
   AND stock >= #{quantity}
-- 依赖 InnoDB 行锁，无中间窗口""", size=9.8, title="实验组（本项目）")

note(s, Inches(0.62), Inches(5.32), Inches(12.1),
     "对照组成功笔数可能超过库存总量（超卖）；实验组稳定只有 5 单成功，其余被 stock >= ? 挡回。",
     size=12, color=NAVY2, icon="▶", bg=RGBColor(0xEE, 0xF3, 0xF8))
write(s, Inches(0.62), Inches(5.92), Inches(12.1), Inches(0.7),
      "仓库内附对照实验脚本 docs/StockRaceDemo.java：20 线程抢 5 份库存，可直接对比两种写法的结果。",
      11.5, False, GRAY)

# =====================================================================
# 19. 秒杀 - 分布式锁（重构：缩小代码字号、表格上移压缩）
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "秒杀技巧二：Redis 分布式锁", "05  秒杀与抢单", num=19)
write(s, Inches(0.62), Inches(1.40), Inches(12.1), Inches(0.30),
      "场景：同一订单只能被一个骑手抢到（DeliveryService.grab）", 12, False, GRAY)

code_box(s, Inches(0.62), Inches(1.74), Inches(6.3), """String lockKey = "lock:order:grab:" + orderId;
// SET key value NX EX seconds —— 一条命令完成
Boolean locked = redis.opsForValue()
    .setIfAbsent(lockKey, riderId, 10, SECONDS);
if (!Boolean.TRUE.equals(locked))
    throw new BusinessException(40002, "手慢了…");

try {
    Order order = orderService.bindRider(orderId, riderId);
    riderMapper.increaseDelivering(riderId);
} finally {
    redisTemplate.delete(lockKey);   // 释放锁
}""", size=8.8, title="DeliveryService.grab")

write(s, Inches(7.10), Inches(1.72), Inches(5.6), Inches(0.28),
      "四个必踩的坑", 12.5, True, REDIS)
table(s, Inches(7.10), Inches(2.04), Inches(5.62), [
    ["坑", "后果", "正确做法"],
    ["不加过期时间", "进程崩溃 → 死锁", "NX + EX 同时设"],
    ["误删他人锁", "A 删掉了 B 的锁", "value 存 UUID，删前校验"],
    ["锁过期业务未完成", "并发进入临界区", "估足 TTL / 看门狗续期"],
    ["只靠锁", "Redis 故障锁全失效", "数据库条件兜底"],
], col_widths=[1.55, 1.95, 2.12], row_h=0.42, font_size=9, header_size=9.5,
   first_col_bold=True, name="s19-table")

rect(s, Inches(0.62), Inches(4.42), Inches(12.1), Inches(2.05), NAVY)
write(s, Inches(0.90), Inches(4.58), Inches(11.5), Inches(0.32),
      "本项目的分层设计：锁防并发，库保正确", 14, True, WHITE)
write(s, Inches(0.90), Inches(4.96), Inches(11.6), Inches(1.40),
      "锁只是「减少并发冲突」，真正的正确性由三层数据库约束兜底：\n"
      "① bindRider 校验订单状态必须为「待取餐」\n"
      "② rider_id IS NULL —— 条件更新，只有第一个写入者成功\n"
      "③ increaseDelivering 带 delivering_count < max_delivering —— 不超接单上限\n\n"
      "这样即使 Redis 锁因故障失效，也不会出现一单多抢或超上限接单。",
      11.5, False, RGBColor(0xDD, 0xE7, 0xF0), line=1.35)

# =====================================================================
# 20. 多层削峰（修复：前两层文字颜色不可见）
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "秒杀技巧三：多层削峰（生产级完整链路）", "05  秒杀与抢单", num=20)
write(s, Inches(0.62), Inches(1.40), Inches(12.1), Inches(0.30),
      "本项目是课程原型，实现了前两层；这里把完整链路讲清楚，作为知识面补充", 12, False, GRAY)

layers = [
    ("① 前端限流", "按钮置灰 / 验证码", "挡住重复点击", MIDGRAY),
    ("② 网关限流", "Nginx / 网关限速", "挡住超量流量", MIDGRAY),
    ("③ Redis 预扣", "DECR / Lua 原子扣减", "内存里判掉 99% 失败请求", REDIS),
    ("④ 异步下单", "消息队列削峰", "数据库按自己节奏消费", ORANGE),
    ("⑤ 事务校验", "数据库最终校验", "保证不超卖", GREEN),
    ("⑥ 失败补偿", "超时回补库存", "保证不少卖", GREEN),
]
y = Inches(1.82)
for i, (t, how, why, c) in enumerate(layers):
    rect(s, Inches(0.62), y, Inches(7.7), Inches(0.62), LGRAY)
    rect(s, Inches(0.62), y, Inches(0.055), Inches(0.62), c)
    write(s, Inches(0.86), y + Inches(0.06), Inches(2.0), Inches(0.30), t, 12, True, NAVY)
    write(s, Inches(2.82), y + Inches(0.07), Inches(2.6), Inches(0.30), how, 10.5, False, GRAY)
    write(s, Inches(5.42), y + Inches(0.07), Inches(2.8), Inches(0.30), why, 10.5, False,
          c if c != MIDGRAY else NAVY2)
    y = y + Inches(0.70)

code_box(s, Inches(8.56), Inches(1.82), Inches(4.16), """-- Lua：多条命令原子执行
local stock = tonumber(
    redis.call('GET', KEYS[1]))
if stock == nil
   or stock < tonumber(ARGV[1])
then return 0 end
redis.call('DECRBY', KEYS[1], ARGV[1])
return 1""", size=9.2, title="为什么用 Lua")

write(s, Inches(8.56), Inches(4.62), Inches(4.16), Inches(1.3),
      "Lua 脚本在 Redis 内单线程执行，天然原子 —— 多命令需「一起成功或一起失败」时的标准做法。",
      10.5, False, GRAY, line=1.4)

# =====================================================================
# 21. 实测验证
# =====================================================================
s = prs.slides.add_slide(BLANK)
header(s, "实测验证：不是「设计如此」，而是「确实如此」", "06  验证与总结", num=21)
table(s, Inches(0.62), Inches(1.48), Inches(12.1), [
    ["场景", "验证方法", "结果"],
    ["热门榜 ZSet", "ZREVRANGE cache:dish:hot", "4 条，score = 月销量，排序正确"],
    ["榜单 TTL 抖动", "TTL cache:dish:hot:list", "360 秒（300 基准 + 20% 抖动）"],
    ["Token 黑名单", "登录 → 访问 200 → logout → 再访问", "code=401，TTL=43199 秒"],
    ["骑手在线状态", "心跳前 / 后 / 删 key", "false → true（TTL=60）→ false"],
    ["缓存击穿防护", "清缓存后 20 并发打同一 key", "20/20 成功，日志仅 1 条 SELECT"],
    ["榜单联动失效", "PUT /merchant/shop 后查 key", "hot=0、list=0，详情缓存也失效"],
    ["完整链路回归", "下单→接单→出餐→抢单→取餐→送达", "抢单池 0→1→移除，订单已完成，库存 60→59"],
], col_widths=[2.15, 4.60, 5.35], row_h=0.56, font_size=11,
   first_col_bold=True, name="s21-table")

note(s, Inches(0.62), Inches(5.86), Inches(12.1),
     "所有结论均来自本机真实运行：MySQL 8.0 + Redis 5.0.14.1 + Spring Boot 3.2.5，非纸面推演。",
     size=12, color=GREEN, icon="✔", bg=RGBColor(0xEC, 0xF5, 0xEC))

# =====================================================================
# 22. 总结
# =====================================================================
s = prs.slides.add_slide(BLANK)
rect(s, Emu(0), Emu(0), SW, SH, NAVY)
rect(s, Emu(0), Inches(1.55), SW, Inches(0.05), REDIS)
write(s, Inches(0.95), Inches(0.80), Inches(11), Inches(0.40),
      "06  总结", 13, True, ACCENTBLUE)
write(s, Inches(0.95), Inches(1.90), Inches(11.4), Inches(0.50),
      "一句话总结", 19, True, WHITE)
write(s, Inches(0.95), Inches(2.42), Inches(11.4), Inches(1.3),
      "内存数据库不是「更快的数据库」，而是用「易失与贵」换取「快与原子」的工具。",
      25, True, RGBColor(0xFF, 0xD8, 0xA8))
write(s, Inches(0.95), Inches(3.56), Inches(11.4), Inches(1.2),
      "用好它的关键不是会不会敲 SET / GET，而是想清楚三件事：\n"
      "哪些数据可以丢、哪些判断必须原子、不一致窗口业务能否容忍。",
      15, False, LIGHTBLUE, line=1.5)

rect(s, Inches(0.95), Inches(4.86), Inches(11.4), Inches(1.10), RGBColor(0x24, 0x4B, 0x73))
write(s, Inches(1.20), Inches(5.02), Inches(11.0), Inches(0.30),
      "本项目的最终取舍", 11.5, True, ACCENTBLUE)
write(s, Inches(1.20), Inches(5.38), Inches(11.0), Inches(0.44),
      "Redis 做加速与协调，MySQL 做最终正确性 —— 即使 Redis 完全宕机，业务正确性不受影响，只是变慢。",
      14, True, WHITE)

write(s, Inches(0.95), Inches(6.42), Inches(11.4), Inches(0.40),
      "感谢聆听  ·  欢迎提问", 15.5, True, LIGHTBLUE)

out = "内存数据库技术与本项目应用.pptx"
prs.save(out)
print("saved:", out, "slides:", len(prs.slides._sldIdLst))
if warnings:
    print("--- 布局告警 ---")
    for w in warnings:
        print("  " + w)
else:
    print("布局自检：无溢出告警")
