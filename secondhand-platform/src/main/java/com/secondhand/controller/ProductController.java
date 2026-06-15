package com.secondhand.controller;

import com.secondhand.entity.Product;
import com.secondhand.service.FavoriteService;
import com.secondhand.service.ProductService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品模块控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/product")  // 请求路径前缀为 /product
@Tag(name = "商品管理", description = "提供商品浏览、发布、编辑、上下架、收藏和举报等接口")  // Swagger文档标签
@ApiResponse(responseCode = "200", description = "统一Result响应",
        content = @Content(schema = @Schema(implementation = Result.class)))
public class ProductController {

    @Autowired  // 自动注入ProductService
    private ProductService productService;
    @Autowired  // 自动注入FavoriteService
    private FavoriteService favoriteService;

    /**
     * 商品分页查询（支持多条件筛选）
     */
    @GetMapping("/page")  // GET请求，路径 /product/page
    @Operation(summary = "搜索商品", description = "公开接口，支持按分类、卖家、关键字、价格区间、成色和排序方式筛选商品")
    public Result page(
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
            @Parameter(description = "每页条数，默认12，最大50", example = "12")
            @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
            @Parameter(description = "卖家用户ID", example = "6")
            @RequestParam(required = false) Long sellerId,  // 卖家ID，可选
            @Parameter(description = "分类ID", example = "1")
            @RequestParam(required = false) Integer categoryId,  // 分类ID，可选
            @Parameter(description = "搜索关键字", example = "iPhone")
            @RequestParam(required = false) String keyword,  // 搜索关键字，可选
            @Parameter(description = "最低价格", example = "100.00")
            @RequestParam(required = false) BigDecimal minPrice,  // 最低价格，可选
            @Parameter(description = "最高价格", example = "5000.00")
            @RequestParam(required = false) BigDecimal maxPrice,  // 最高价格，可选
            @Parameter(description = "成色等级", example = "2",
                    schema = @Schema(allowableValues = {"1", "2", "3", "4"}))
            @RequestParam(required = false) Integer conditionLevel,  // 成色等级，可选
            @Parameter(description = "排序方式", example = "latest",
                    schema = @Schema(allowableValues = {"latest", "price_asc", "price_desc", "hottest"}))
            @RequestParam(defaultValue = "latest") String sort) {  // 排序方式，默认最新
        // 调用service分页查询
        return productService.pageProducts(page, size, sellerId, categoryId,
                keyword, minPrice, maxPrice, conditionLevel, sort);
    }

    /**
     * 商品详情
     */
    @GetMapping("/detail/{id}")  // GET请求，路径 /product/detail/{id}
    @Operation(summary = "查询商品详情", description = "公开接口，返回商品主体、图片、卖家和分类等信息")
    public Result detail(@Parameter(description = "商品ID", example = "1", required = true)
                         @PathVariable Long id) {  // 商品ID，从路径获取
        // 调用service查询详情
        return productService.getDetail(id);
    }

    /**
     * 获取热门搜索词
     */
    @GetMapping("/hot-keywords")  // GET请求，路径 /product/hot-keywords
    @Operation(summary = "查询热门关键词", description = "公开接口，返回最近搜索频率最高的关键词")
    public Result hotKeywords() {
        // 调用service获取热门关键词
        return productService.getHotKeywords();
    }

    /**
     * 查询卖家的商品（访客看在售，卖家本人看全部）
     */
    @GetMapping("/seller/{sellerId}")  // GET请求，路径 /product/seller/{sellerId}
    @Operation(summary = "查询卖家商品",
            description = "公开接口。访客只返回该卖家的在售商品；卖家本人查看自己主页时返回全部状态（含下架、已售）")
    public Result sellerProducts(
            @Parameter(description = "卖家用户ID", example = "6", required = true)
            @PathVariable Long sellerId,  // 卖家ID，从路径获取
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
            @Parameter(description = "每页条数", example = "12")
            @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
            @Parameter(hidden = true) HttpSession session) {  // HttpSession，用于获取当前查看者ID
        // 获取当前查看者ID（可能为null，表示未登录访客）
        Long viewerId = SessionUtil.getUserId(session);
        // 调用service查询卖家商品
        return productService.getSellerProducts(sellerId, page, size, viewerId);
    }

    /**
     * 发布商品
     */
    @PostMapping("/publish")  // POST请求，路径 /product/publish
    @Operation(summary = "发布商品", description = "需要登录。发布商品时需提交库存和imageUrls")
    public Result publish(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "发布商品请求体。stock和imageUrls为必填，首张图片会作为封面图",
                    required = true,
                    content = @Content(schema = @Schema(implementation = Product.class)))
            @RequestBody Product product,  // 商品信息，从请求体获取
            @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service发布商品
        return productService.publish(userId, product, product.getImageUrls());
    }

    /**
     * 编辑商品
     */
    @PutMapping("/update")  // PUT请求，路径 /product/update
    @Operation(summary = "编辑商品", description = "需要登录，且只能修改自己的商品。传入imageUrls时会覆盖原图列表")
    public Result update(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "编辑商品请求体，id为必填，可修改价格、库存、成色和图片列表",
                    required = true,
                    content = @Content(schema = @Schema(implementation = Product.class)))
            @RequestBody Product product,  // 商品信息（包含要修改的字段）
            @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service更新商品
        return productService.update(userId, product);
    }

    /**
     * 下架商品
     */
    @PutMapping("/off-shelf/{id}")  // PUT请求，路径 /product/off-shelf/{id}
    @Operation(summary = "下架商品", description = "需要登录，且只有卖家可以执行该操作")
    public Result offShelf(@PathVariable Long id,  // 商品ID，从路径获取
                           @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service下架商品
        return productService.offShelf(userId, id);
    }

    /**
     * 上架商品
     */
    @PutMapping("/on-shelf/{id}")  // PUT请求，路径 /product/on-shelf/{id}
    @Operation(summary = "上架商品", description = "需要登录，且只有卖家可以执行该操作，库存必须大于0")
    public Result onShelf(@PathVariable Long id,  // 商品ID，从路径获取
                          @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service上架商品
        return productService.onShelf(userId, id);
    }

    /**
     * 收藏商品
     */
    @PostMapping("/{id}/favorite")  // POST请求，路径 /product/{id}/favorite
    @Operation(summary = "添加收藏", description = "需要登录，只能收藏当前在售商品")
    public Result addFavorite(@PathVariable Long id,  // 商品ID
                              @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service添加收藏
        return favoriteService.addFavorite(userId, id);
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{id}/favorite")  // DELETE请求，路径 /product/{id}/favorite
    @Operation(summary = "取消收藏", description = "需要登录，删除当前用户对该商品的收藏记录")
    public Result removeFavorite(@PathVariable Long id,  // 商品ID
                                 @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service取消收藏
        return favoriteService.removeFavorite(userId, id);
    }

    /**
     * 查询我的收藏
     */
    @GetMapping("/my-favorites")  // GET请求，路径 /product/my-favorites
    @Operation(summary = "查询我的收藏", description = "需要登录，分页返回当前用户的收藏记录")
    public Result myFavorites(@RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                              @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
                              @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service查询我的收藏
        return favoriteService.getMyFavorites(userId, page, size);
    }

    /**
     * 查询当前用户是否已收藏该商品
     */
    @GetMapping("/{id}/favorite/status")  // GET请求，路径 /product/{id}/favorite/status
    @Operation(summary = "查询收藏状态", description = "需要登录，返回当前用户是否已收藏该商品")
    public Result favoriteStatus(@PathVariable Long id,  // 商品ID
                                 @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service查询收藏状态
        return favoriteService.isFavorited(userId, id);
    }

    /**
     * 举报商品
     */
    @PostMapping("/{id}/report")  // POST请求，路径 /product/{id}/report
    @Operation(summary = "举报商品", description = "需要登录，同一用户24小时内对同一商品最多举报一次")
    public Result report(@PathVariable Long id,  // 商品ID
                         @RequestParam String reason,  // 举报原因
                         @RequestParam(required = false) String detail,  // 举报详情，可选
                         @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service举报商品
        return productService.reportProduct(userId, id, reason, detail);
    }
}