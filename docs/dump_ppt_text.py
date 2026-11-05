"""导出 PPT 每页文本到 UTF-8 文件，用于与代码实现逐条对照。

用法：python dump_ppt_text.py <pptx路径> <输出txt路径>
"""

import sys

from pptx import Presentation


def dump(path: str, out_path: str) -> None:
    prs = Presentation(path)
    lines = [f"文件: {path}", f"幻灯片数: {len(prs.slides)}"]

    for index, slide in enumerate(prs.slides, start=1):
        lines.append("")
        lines.append("=" * 70)
        lines.append(f"=== 第 {index} 页 ===")
        for shape in slide.shapes:
            if shape.has_text_frame:
                text = "\n".join(p.text for p in shape.text_frame.paragraphs if p.text.strip())
                if text.strip():
                    lines.append(text)
            if shape.has_table:
                for row in shape.table.rows:
                    cells = [cell.text.strip().replace("\n", " / ") for cell in row.cells]
                    lines.append(" | ".join(cells))
            if shape.has_chart:
                chart = shape.chart
                try:
                    lines.append(f"[图表] 类别: {list(chart.plots[0].categories)}")
                    for series in chart.series:
                        lines.append(f"[图表] {series.name}: {list(series.values)}")
                except Exception as exc:  # noqa: BLE001
                    lines.append(f"[图表] 读取失败: {exc}")

    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    print(f"written: {out_path}, slides={len(prs.slides)}")


if __name__ == "__main__":
    dump(sys.argv[1], sys.argv[2])
