package com.secondhand.search;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductSearchCriteriaTest {
    @Test
    void validatesPageWindowPriceOrderAndAllowedSorts() {
        assertThatThrownBy(() -> criteria(0, 20, null, null, "latest")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(1, 51, null, null, "latest")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(Integer.MAX_VALUE, 50, null, null, "latest")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(1, 20, "10", "5", "latest")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(1, 20, "-1", null, "latest")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(1, 20, null, null, "anything")).isInstanceOf(IllegalArgumentException.class);
        assertThat(criteria(200, 50, null, null, "relevance").offset()).isEqualTo(9950);
    }

    @Test
    void decimalPriceBoundsRoundInwardsInsteadOfAcceptingOutOfBudgetProducts() {
        ProductSearchCriteria c = criteria(1, 20, "10.001", "20.999", "price_asc");
        assertThat(c.minCents()).isEqualTo(1001L);
        assertThat(c.maxCents()).isEqualTo(2099L);
        assertThatThrownBy(() -> criteria(1, 20, "999999999999999999999", null, "latest"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsKeywordAndRejectsInvalidCategoriesConditionsAndLongQueries() {
        assertThat(new ProductSearchCriteria(1, 20, " 苹果 手机 ", 10, null, null, 2, "relevance").keyword())
                .isEqualTo("苹果 手机");
        assertThatThrownBy(() -> new ProductSearchCriteria(1, 20, "x".repeat(201), null, null, null, null, "latest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSearchCriteria(1, 20, null, -1, null, null, null, "latest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSearchCriteria(1, 20, null, null, null, null, 5, "latest"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProductSearchCriteria criteria(int page, int size, String min, String max, String sort) {
        return new ProductSearchCriteria(page, size, null, null, min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max), null, sort);
    }
}
