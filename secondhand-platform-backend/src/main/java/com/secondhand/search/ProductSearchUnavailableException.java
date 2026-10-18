package com.secondhand.search;

/** 搜索暂时不可用，与“没有匹配商品”区分，供接口返回 HTTP 503。 */
public class ProductSearchUnavailableException extends RuntimeException {
    public ProductSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
