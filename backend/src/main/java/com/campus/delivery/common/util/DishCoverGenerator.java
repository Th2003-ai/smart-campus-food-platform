package com.campus.delivery.common.util;

import org.springframework.util.StringUtils;

/**
 * 菜品封面生成器：根据「菜品名称 + 标签」生成一张 SVG 封面。
 *
 * <p><b>为什么要自动生成</b>：本项目没有接入对象存储，也没有真实菜品照片，
 * {@code dish.image} 全部为空，前端只能显示一个灰色占位图。
 * 这里用「名称关键字 → 食物图标 + 名称哈希 → 配色」的方式生成确定性封面，
 * 让任何菜品（包括商户新加的）都能立即有图，且不依赖任何外部图片源或网络。
 *
 * <p><b>可替换</b>：后端一旦有了真实图片（对象存储 / 商户上传），
 * 只要给 {@code dish.image} 赋值，前端就会优先用真实图片，本生成器自动退居兜底。
 *
 * <p>所有输出都是纯 SVG 文本，安全做法：对菜品名做 XML 转义，颜色只用数字计算。
 */
public final class DishCoverGenerator {

    private static final int WIDTH = 480;
    private static final int HEIGHT = 360;

    /** 关键字 → 食物图标；顺序有意义，先匹配到的生效（更具体的放前面） */
    private static final String[][] ICONS = {
            {"豆浆", "🥛"}, {"奶茶", "🧋"}, {"咖啡", "☕"}, {"果汁", "🧃"}, {"饮", "🥤"},
            {"粥", "🍚"}, {"汤", "🍲"},
            {"面", "🍜"}, {"粉", "🍜"},
            {"盖饭", "🍛"}, {"炒饭", "🍚"}, {"饭", "🍚"},
            {"饺子", "🥟"}, {"包子", "🥟"}, {"馒头", "🍞"},
            {"烧烤", "🍢"}, {"串", "🍢"}, {"炸", "🍟"}, {"薯", "🍟"},
            {"鸡", "🍗"}, {"牛", "🥩"}, {"猪", "🥩"}, {"肉", "🍖"}, {"排", "🍖"},
            {"鱼", "🐟"}, {"虾", "🦐"},
            {"蛋", "🥚"}, {"面包", "🍞"}, {"三明治", "🥪"},
            {"蛋糕", "🍰"}, {"甜", "🍰"}, {"冰", "🍧"},
            {"沙拉", "🥗"}, {"蔬", "🥬"}, {"菜", "🥬"},
            {"果", "🍎"}
    };

    /** 暖色系配色池，看着像食物，也不会互相撞色太厉害 */
    private static final String[][] PALETTES = {
            {"#FF9A56", "#FF6A3D"}, {"#F7B733", "#FC4A1A"},
            {"#FFB75E", "#ED8F03"}, {"#F79D00", "#64A6A6"},
            {"#FF7E5F", "#FEB47B"}, {"#E96443", "#904E95"},
            {"#FDC830", "#F37335"}, {"#56AB2F", "#A8E063"},
            {"#FF512F", "#DD2476"}, {"#1FA2FF", "#12D8FA"}
    };

    private DishCoverGenerator() {
    }

    /**
     * 生成 SVG 封面。
     *
     * @param dishName 菜品名称（用于选图标、配色、以及底部标题）
     * @param tags     菜品标签（逗号分隔，参与图标匹配，可为空）
     */
    public static String generate(String dishName, String tags) {
        String name = StringUtils.hasText(dishName) ? dishName.trim() : "菜品";
        String matchedText = name + "," + (tags == null ? "" : tags);

        String icon = pickIcon(matchedText);
        String[] palette = PALETTES[Math.floorMod(name.hashCode(), PALETTES.length)];
        String title = escapeXml(abbreviate(name, 12));

        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" role="img">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="%s"/>
                      <stop offset="100%%" stop-color="%s"/>
                    </linearGradient>
                  </defs>
                  <rect width="100%%" height="100%%" fill="url(#bg)"/>
                  <circle cx="%d" cy="%d" r="%d" fill="#ffffff" fill-opacity="0.16"/>
                  <circle cx="%d" cy="%d" r="%d" fill="#ffffff" fill-opacity="0.10"/>
                  <text x="50%%" y="52%%" font-size="150" text-anchor="middle" dominant-baseline="central">%s</text>
                  <text x="50%%" y="88%%" font-size="30" font-family="Microsoft YaHei, PingFang SC, sans-serif"
                        font-weight="700" fill="#ffffff" fill-opacity="0.96" text-anchor="middle">%s</text>
                </svg>
                """.formatted(
                WIDTH, HEIGHT, WIDTH, HEIGHT,
                palette[0], palette[1],
                WIDTH - 60, 70, 110,
                80, HEIGHT - 40, 80,
                icon, title);
    }

    /** 按关键字匹配食物图标，匹配不到给一个通用餐盘 */
    private static String pickIcon(String text) {
        for (String[] entry : ICONS) {
            if (text.contains(entry[0])) {
                return entry[1];
            }
        }
        return "🍽";
    }

    /** 名称过长时截断，避免 SVG 里文字溢出 */
    private static String abbreviate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    /** XML 转义，防止菜品名里的 & < > 破坏 SVG 结构 */
    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
