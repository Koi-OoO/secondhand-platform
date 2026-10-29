package com.secondhand.constant;

/**
 * 商品状态常量，取值与数据库 product.status 一致（1 在售 2 下架 3 已售出 4 已删除）。
 */
public final class ProductStatus {

    public static final int ON_SALE = 1;    // 在售
    public static final int OFF_SHELF = 2;  // 已下架
    public static final int SOLD_OUT = 3;   // 已售出
    public static final int DELETED = 4;    // 已删除

    private ProductStatus() {
    }

    public static boolean isOnSale(Integer status) {
        return Integer.valueOf(ON_SALE).equals(status);
    }

    public static boolean isOffShelf(Integer status) {
        return Integer.valueOf(OFF_SHELF).equals(status);
    }

    public static boolean isSoldOut(Integer status) {
        return Integer.valueOf(SOLD_OUT).equals(status);
    }

    public static boolean isDeleted(Integer status) {
        return Integer.valueOf(DELETED).equals(status);
    }
}
