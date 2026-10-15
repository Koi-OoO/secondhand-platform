package com.secondhand.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将商品关键词拆为品牌、商品类型和型号约束，供AI和商品广场共用。 */
public final class ProductKeywordQuery {
    private ProductKeywordQuery() { }

    /** 同组词为同义词（OR），不同组为必须同时满足的条件（AND）。 */
    public static final class Plan {
        private final List<List<String>> requiredGroups;
        private final List<String> excludedTerms;

        private Plan(List<List<String>> groups, List<String> excluded) {
            this.requiredGroups = List.copyOf(groups);
            this.excludedTerms = List.copyOf(excluded);
        }

        /** 品牌、类型和剩余型号分别形成一个必须满足的匹配组。 */
        public List<List<String>> getRequiredGroups() { return requiredGroups; }
        /** 查询整机时排除明确的配件主商品名称。 */
        public List<String> getExcludedTerms() { return excludedTerms; }
    }

    /** 品牌的中英文名称与常见品牌专属产品名称。 */
    private static final List<List<String>> BRANDS = List.of(
            List.of("苹果", "apple", "iphone", "ipad", "macbook"),
            List.of("华为", "huawei"), List.of("小米", "xiaomi", "红米", "redmi"),
            List.of("三星", "samsung"), List.of("欧珀", "oppo"), List.of("维沃", "vivo"),
            List.of("荣耀", "honor"), List.of("联想", "lenovo"),
            List.of("华硕", "asus"), List.of("戴尔", "dell"));

    /** 识别词、标题同义词及整机查询中需排除的配件。 */
    private record Kind(List<String> queryTerms, List<String> titleTerms, List<String> excluded) { }

    private static final List<String> PHONE_ACCESSORIES = List.of(
            "充电器", "充电线", "充电宝", "数据线", "手机壳", "保护壳", "保护套", "钢化膜",
            "贴膜", "手机支架", "转接头", "耳机", "charger", "phone case", "screen protector", "airpods");
    private static final List<String> COMPUTER_ACCESSORIES = List.of(
            "充电器", "电源适配器", "电脑包", "保护壳", "保护套", "支架", "转接头", "扩展坞", "贴膜", "charger");

    // 配件先于整机识别：“苹果手机充电器”应明确查询充电器，而不是手机。
    private static final List<Kind> KINDS = List.of(
            new Kind(List.of("手机保护壳", "手机壳", "保护壳", "保护套", "phone case"),
                    List.of("手机壳", "保护壳", "保护套", "phone case"), List.of()),
            new Kind(List.of("充电器", "电源适配器", "charger"),
                    List.of("充电器", "电源适配器", "charger"), List.of()),
            new Kind(List.of("充电线", "数据线", "charging cable"),
                    List.of("充电线", "数据线", "charging cable"), List.of()),
            new Kind(List.of("充电宝", "移动电源", "power bank"),
                    List.of("充电宝", "移动电源", "power bank"), List.of()),
            new Kind(List.of("耳机", "airpods", "earphones", "headphones"),
                    List.of("耳机", "airpods", "earphones", "headphones"), List.of()),
            new Kind(List.of("笔记本电脑", "笔记本", "macbook", "laptop"),
                    List.of("笔记本", "macbook", "laptop"), COMPUTER_ACCESSORIES),
            new Kind(List.of("平板电脑", "平板", "ipad", "tablet"),
                    List.of("平板", "ipad", "tablet"), PHONE_ACCESSORIES),
            new Kind(List.of("智能手机", "手机", "iphone", "smartphone"),
                    List.of("手机", "iphone", "smartphone"), PHONE_ACCESSORIES));
    private static final Pattern REMAINING_TERMS = Pattern.compile("[a-z]+|[0-9]+|[^\\s0-9a-z]+");

    /**
     * 保留品牌与类型之间出现型号的商品，不要求用户输入的整句话连续出现在标题中。
     * 未识别的品类仍按普通关键词匹配，不推断用户没有提出的筛选条件。
     */
    public static Plan parse(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        String remainder = normalized;
        List<List<String>> groups = new ArrayList<>();
        Kind kind = KINDS.stream().filter(value -> value.queryTerms.stream().anyMatch(normalized::contains))
                .findFirst().orElse(null);
        for (List<String> brand : BRANDS) {
            if (brand.stream().anyMatch(normalized::contains)) {
                groups.add(brand);
                for (String name : brand) remainder = remainder.replace(name, " ");
                break;
            }
        }
        if (kind != null) {
            groups.add(kind.titleTerms);
            for (String term : kind.queryTerms) remainder = remainder.replace(term, " ");
            if (kind.excluded.isEmpty()) {
                // 配件名称中的“手机”等是兼容对象，不是另一件必须售卖的整机。
                remainder = remainder.replace("手机", " ").replace("智能", " ");
            }
        }
        if (kind == null && groups.isEmpty()) {
            // 普通教材等连续中文关键词仍完整保留。
            for (String term : remainder.split("\\s+")) groups.add(List.of(term));
        } else {
            Matcher terms = REMAINING_TERMS.matcher(remainder);
            while (terms.find()) {
                String term = terms.group();
                // 常见专业版在中英文标题中使用不同表示。
                if (term.equals("pro")) groups.add(List.of("pro", "专业"));
                else if (term.matches("[0-9]{1,2}") && Integer.parseInt(term) >= 1 && Integer.parseInt(term) <= 30)
                    groups.add(List.of(term, chineseNumber(Integer.parseInt(term))));
                else groups.add(List.of(term));
            }
        }
        return new Plan(groups, kind == null ? List.of() : kind.excluded);
    }

    /** 支持“iPhone13”匹配模拟数据中“第十三代”的相同型号。 */
    private static String chineseNumber(int number) {
        String digits = "零一二三四五六七八九";
        if (number < 10) return String.valueOf(digits.charAt(number));
        return (number < 20 ? "" : String.valueOf(digits.charAt(number / 10))) + "十"
                + (number % 10 == 0 ? "" : String.valueOf(digits.charAt(number % 10)));
    }
}
