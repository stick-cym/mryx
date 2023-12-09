package com.person.mryx.client.product;

import com.person.mryx.model.product.Category;
import com.person.mryx.model.product.SkuInfo;
import com.person.mryx.vo.product.SkuInfoVo;
import com.person.mryx.vo.product.SkuStockLockVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "service-product")
public interface ProductFeignClient {

    //根据skuId获取sku信息
    @GetMapping("/api/product/inner/getSkuInfoVo/{skuId}")
    SkuInfoVo getSkuInfoVo(@PathVariable Long skuId);

    //获取新人专享商品
    @GetMapping("/api/product/inner/findNewPersonSkuInfoList")
    List<SkuInfo> findNewPersonSkuInfoList();

    //所有分类
    @GetMapping("/api/product/inner/findAllCategoryList")
    List<Category> findAllCategoryList();

    @GetMapping("/api/product/inner/getCategory/{categoryId}")
    Category getCategory(@PathVariable("categoryId") Long categoryId);

    @GetMapping("/api/product/inner/getSkuInfo/{skuId}")
    SkuInfo getSkuInfo(@PathVariable("skuId") Long skuId);

    //根据skuId列表得到sku信息列表
    @PostMapping("/api/product/inner/findSkuInfoList")
    List<SkuInfo> findSkuInfoList(@RequestBody List<Long> skuIdList);

    //根据分类id获取分类列表
    @PostMapping("/api/product/inner/findCategoryList")
    List<Category> findCategoryList(@RequestBody List<Long> categoryIdList);

    //根据关键字匹配sku列表
    @GetMapping("/api/product/inner/findSkuInfoByKeyword/{keyword}")
    List<SkuInfo> findSkuInfoByKeyword(@PathVariable("keyword") String keyword);

    //验证和锁定库存
    @PostMapping("/api/product/inner/checkAndLock/{orderNo}")
    Boolean checkAndLock(@RequestBody List<SkuStockLockVo> skuStockLockVoList,
                                @PathVariable("orderNo") String orderNo);
}
