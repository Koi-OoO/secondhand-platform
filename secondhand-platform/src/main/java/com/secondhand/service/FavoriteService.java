package com.secondhand.service;

import com.secondhand.util.Result;

/**
 * 收藏服务接口
 * 定义用户收藏模块的核心业务能力，
 * 包括添加收藏、取消收藏和收藏列表查询。
 */
public interface FavoriteService {

    /**
     * 收藏商品
     *
     * @param userId 用户 ID
     * @param productId 商品 ID
     * @return Result 收藏结果
     */
    Result addFavorite(Long userId, Long productId);

    /**
     * 取消收藏商品
     *
     * @param userId 用户 ID
     * @param productId 商品 ID
     * @return Result 取消收藏结果
     */
    Result removeFavorite(Long userId, Long productId);

    /**
     * 分页查询当前用户的收藏列表
     *
     * @param userId 用户 ID
     * @param page 页码
     * @param size 每页条数
     * @return Result 收藏分页结果
     */
    Result getMyFavorites(Long userId, Integer page, Integer size);

    /**
     * 查询当前用户是否已收藏某商品
     *
     * @param userId 用户 ID
     * @param productId 商品 ID
     * @return Result data 为 true 表示已收藏
     */
    Result isFavorited(Long userId, Long productId);
}
