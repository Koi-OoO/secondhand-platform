package com.secondhand.search;

import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证业务数据投影，避免价格精度、旧分类和封面在导入后发生变化。 */
class ProductSearchDocumentFactoryTest {
    @Test
    void promotionAndInvoicePrefixesDoNotEraseTheMainProduct() {
        for (String title : List.of("买一赠一 苹果手机", "附带原装发票的苹果手机", "苹果手机附带发票 256G")) {
            Product p = product();
            p.setTitle(title);
            assertThat(new ProductSearchDocumentFactory().build(p, List.of(), categories()))
                    .containsEntry("primaryTitle", title);
        }
    }

    @Test
    void primaryTitleExcludesGiftsWithoutChangingDisplayTitle() {
        Product p = product();
        p.setTitle("苹果 iPhone13 手机附赠充电器");
        var document = new ProductSearchDocumentFactory().build(p, List.of(), categories());
        assertThat(document).containsEntry("primaryTitle", "苹果 iPhone13 手机")
                .containsEntry("title", "苹果 iPhone13 手机附赠充电器");
    }

    private final ProductSearchDocumentFactory factory = new ProductSearchDocumentFactory();

    @Test
    void convertsYuanToExactCentsAndDatesToEpochWithoutIndexingPrivateFields() {
        Product p = product();
        p.setOriginalPrice(new BigDecimal("2999.90"));
        p.setCreateTime(new Date(1700000000123L));
        p.setSellerUsername("not-a-search-field");
        Map<String, Object> doc = factory.build(p, List.of(), categories());
        assertThat(doc).containsEntry("id", 1L).containsEntry("priceCents", 179999L)
                .containsEntry("originalPriceCents", 299990L).containsEntry("createTime", 1700000000123L);
        assertThat(doc).doesNotContainKeys("sellerUsername", "imageUrls", "images");
        assertThat(p.getPrice()).isEqualByComparingTo("1799.99");
    }

    @Test
    void preservesLeafAndLegacyParentCategories() {
        Product p = product();
        assertThat(factory.build(p, List.of(), categories()).get("categoryPathIds")).isEqualTo(List.of(10, 11));
        p.setCategoryId(10);
        assertThat(factory.build(p, List.of(), categories()).get("categoryPathIds")).isEqualTo(List.of(10));
        p.setCategoryId(null);
        assertThat(factory.build(p, List.of(), categories()).get("categoryPathIds")).isEqualTo(List.of());
    }

    @Test
    void rejectsUnknownOrCyclicCategoriesInsteadOfInventingPaths() {
        Product p = product();
        assertThatThrownBy(() -> factory.build(p, List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class);
        Category leaf = category(11, 99);
        assertThatThrownBy(() -> factory.build(p, List.of(), Map.of(11, leaf))).isInstanceOf(IllegalArgumentException.class);
        Category parent = category(10, 11);
        assertThatThrownBy(() -> factory.build(p, List.of(), Map.of(10, parent, 11, category(11, 10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coverUsesFirstValidImageAndStableIdOrderWithoutMutatingInput() {
        ProductImage second = image(3, 1, 1, "/second.jpg");
        ProductImage cover = image(2, 1, 0, "/cover.jpg");
        ProductImage empty = image(1, 1, 0, " ");
        ProductImage unrelated = image(0, 99, 0, "/other.jpg");
        List<ProductImage> images = List.of(second, cover, empty, unrelated);
        assertThat(factory.build(product(), images, categories())).containsEntry("coverUrl", "/cover.jpg");
        assertThat(images.get(0)).isSameAs(second);
    }

    @Test
    void rejectsFractionalCentsOverflowAndMissingIdentity() {
        Product p = product();
        p.setPrice(new BigDecimal("1.001"));
        assertThatThrownBy(() -> factory.build(p, List.of(), categories())).isInstanceOf(IllegalArgumentException.class);
        p.setPrice(new BigDecimal("99999999999999999999"));
        assertThatThrownBy(() -> factory.build(p, List.of(), categories())).isInstanceOf(IllegalArgumentException.class);
        p.setPrice(new BigDecimal("10.00"));
        p.setId(null);
        assertThatThrownBy(() -> factory.build(p, List.of(), categories())).isInstanceOf(IllegalArgumentException.class);
    }

    static Product product() {
        Product p = new Product();
        p.setId(1L); p.setSellerId(3L); p.setTitle("二手苹果手机 iPhone 13");
        p.setPrice(new BigDecimal("1799.99")); p.setCategoryId(11); p.setStatus(1);
        p.setStock(1); p.setConditionLevel(2); p.setViewCount(5); p.setLikeCount(0);
        return p;
    }

    static Map<Integer, Category> categories() {
        return Map.of(10, category(10, 0), 11, category(11, 10));
    }

    static Category category(int id, int parent) {
        Category c = new Category(); c.setId(id); c.setParentId(parent); return c;
    }

    static ProductImage image(long id, long productId, int sort, String url) {
        ProductImage image = new ProductImage();
        image.setId(id); image.setProductId(productId); image.setSort(sort); image.setUrl(url);
        return image;
    }
}
