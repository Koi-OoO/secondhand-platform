package com.secondhand.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 验证真实Mapper生成的查询条件，防止型号导致漏查或把手机配件当作整机。 */
class ProductKeywordSearchTest {
    private record Query(String sql, List<Object> values) { }

    /** 从生产XML获得SQL及实际绑定值，不用测试中的伪查询代替生产实现。 */
    private Query query(String keyword) {
        Configuration config = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/ProductMapper.xml")) {
            new XMLMapperBuilder(input, config, "mapper/ProductMapper.xml", config.getSqlFragments()).parse();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("sellerId", null);
        params.put("categoryId", null);
        params.put("minPrice", BigDecimal.ZERO);
        params.put("maxPrice", new BigDecimal("5000"));
        params.put("conditionLevel", null);
        params.put("status", 1);
        params.put("sort", "latest");
        BoundSql bound = config.getMappedStatement("com.secondhand.mapper.ProductMapper.selectPageWithDetails")
                .getBoundSql(params);
        List<Object> values = new ArrayList<>();
        bound.getParameterMappings().forEach(mapping -> values.add(
                bound.hasAdditionalParameter(mapping.getProperty())
                        ? bound.getAdditionalParameter(mapping.getProperty())
                        : params.get(mapping.getProperty())));
        return new Query(bound.getSql(), values);
    }

    @Test
    void separatesBrandAndPhoneInsteadOfRequiringContinuousPhrase() {
        Query query = query("苹果手机");
        assertThat(query.values).contains("苹果", "手机", "充电器").doesNotContain("苹果手机");
        assertThat(query.sql).contains("NOT LIKE");
        assertThat(query.values).contains(BigDecimal.ZERO, new BigDecimal("5000"), 1);
    }

    @Test
    void iphoneSearchAlsoRecognizesChineseApplePhoneTitles() {
        assertThat(query("iPhone").values).contains("苹果", "手机");
    }

    @Test
    void explicitChargerSearchDoesNotApplyPhoneAccessoryExclusions() {
        Query query = query("苹果手机充电器");
        assertThat(query.values).contains("苹果", "充电器");
        assertThat(query.sql).doesNotContain("NOT LIKE");
    }

    @Test
    void preservesOrdinaryKeywordAndStablePaginationOrder() {
        Query query = query("高等数学");
        assertThat(query.values).contains("高等数学");
        assertThat(query.sql).contains("p.create_time DESC, p.id DESC");
    }

    @Test
    void batteryHealthAndAttachedGiftsDoNotTurnPhonesIntoAccessories() {
        Query query = query("苹果手机");
        assertThat(query.values).doesNotContain("电池");
        assertThat(query.sql).contains("'赠', 1");
    }

    /** 显式开启才连接本机MySQL：用CTE临时数据验证SQL语义，完全不写业务表。 */
    @Test
    @EnabledIfEnvironmentVariable(named = "CAMPUS_SEARCH_MYSQL_URL", matches = ".+")
    void executesProductionSearchAgainstPhoneAndAccessoryFixtures() throws Exception {
        List<String> titles = List.of(
                "苹果第十三代专业版手机", "iPhone 15 Pro", "苹果手机附赠原装充电器",
                "苹果手机充电器", "苹果手机保护壳", "苹果无线蓝牙耳机", "苹果十四英寸笔记本电脑",
                "苹果充电器", "华为Mate60手机", "HUAWEI P40 Smartphone",
                "iPhone 15 Pro 电池健康95%", "苹果手机赠原装充电器");
        try (Connection connection = DriverManager.getConnection(System.getenv("CAMPUS_SEARCH_MYSQL_URL"),
                System.getenv("CAMPUS_SEARCH_MYSQL_USER"), System.getenv("CAMPUS_SEARCH_MYSQL_PASSWORD"))) {
            connection.setReadOnly(true);
            assertThat(findFixtureIds(connection, titles, "苹果手机")).containsExactlyInAnyOrder(1, 2, 3, 11, 12);
            assertThat(findFixtureIds(connection, titles, "iPhone")).containsExactlyInAnyOrder(1, 2, 3, 11, 12);
            assertThat(findFixtureIds(connection, titles, "苹果手机充电器")).containsExactlyInAnyOrder(4, 8);
            assertThat(findFixtureIds(connection, titles, "华为手机")).containsExactlyInAnyOrder(9, 10);
            assertThat(findFixtureIds(connection, titles, "iPhone13Pro")).containsExactlyInAnyOrder(1);
            Query liveQuery = query("苹果手机");
            List<String> actualTitles = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(liveQuery.sql)) {
                for (int index = 0; index < liveQuery.values.size(); index++) statement.setObject(index + 1, liveQuery.values.get(index));
                try (var result = statement.executeQuery()) {
                    while (result.next()) actualTitles.add(result.getString("title"));
                }
            }
            assertThat(actualTitles).as("真实库应至少返回一条苹果手机").isNotEmpty();
            assertThat(actualTitles).noneMatch(title -> title.contains("手机充电器") || title.contains("手机保护壳"));
            System.out.println("真实库苹果手机查询结果：" + actualTitles.size() + "条，已核对整机和配件。");
        }
    }

    /** CTE遮蔽product表，只使用生产Mapper的WHERE和ORDER BY，测试无插入、更新或删除。 */
    private List<Integer> findFixtureIds(Connection connection, List<String> titles, String keyword) throws Exception {
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < titles.size(); index++) {
            rows.add("SELECT " + (index + 1) + " AS id, ? AS title, 0 AS seller_id, 0 AS category_id, "
                    + "1 AS status, 100 AS price, 2 AS condition_level, 0 AS view_count, NOW() AS create_time");
        }
        Query query = query(keyword);
        try (PreparedStatement statement = connection.prepareStatement(
                "WITH product AS (" + String.join(" UNION ALL ", rows) + ") " + query.sql)) {
            int position = 1;
            for (String title : titles) statement.setString(position++, title);
            for (Object value : query.values) statement.setObject(position++, value);
            List<Integer> ids = new ArrayList<>();
            try (var result = statement.executeQuery()) {
                while (result.next()) ids.add(result.getInt("id"));
            }
            return ids;
        }
    }
}
