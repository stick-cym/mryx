package com.person.mryx.cart.controller;


import com.person.mryx.activity.client.ActivityFeignClient;
import com.person.mryx.cart.service.CartInfoService;
import com.person.mryx.common.auth.AuthContextHolder;
import com.person.mryx.common.result.Result;
import com.person.mryx.model.order.CartInfo;
import com.person.mryx.vo.order.OrderConfirmVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@Api(tags = "购物车")
@RestController
@RequestMapping("api/cart")
public class CartApiController {
    @Autowired
    private CartInfoService cartInfoService;

    @Autowired
    private ActivityFeignClient activityFeignClient;
    //添加商品到购物车
    //添加内容：当前登录用户id，skuId，商品数量
    @ApiOperation(value = "添加商品到购物车")
    @GetMapping("addToCart/{skuid}/{skuNum}")
    public Result addToCart(@PathVariable Long skuid,@PathVariable Integer skuNum){
        cartInfoService.addToCart(AuthContextHolder.getUserId(),skuid,skuNum);
        return Result.ok(null);
    }

    @ApiOperation(value = "根据商品id删除购物车")
    @DeleteMapping("deleteCart/{skuid}")
    public Result deleteCart(@PathVariable Long skuid){
        cartInfoService.deleteCart(skuid,AuthContextHolder.getUserId());
        return Result.ok(null);
    }

    @ApiOperation("清空购物车")
    @DeleteMapping("deleteAllCart")
    public Result deleteAllCart(){
        cartInfoService.deleteAllCart(AuthContextHolder.getUserId());
        return Result.ok(null);
    }

    @ApiOperation(value="批量删除购物车")
    @PostMapping("batchDeleteCart")
    public Result batchDeleteCart(@RequestBody List<Long> skuIdList, HttpServletRequest request){
        cartInfoService.batchDeleteCart(skuIdList, AuthContextHolder.getUserId());
        return Result.ok(null);
    }
    //购物车列表
    @GetMapping("cartList")
    public Result<List<CartInfo>> cartList(){
        List<CartInfo> cartList = cartInfoService.getCartList(AuthContextHolder.getUserId());
        return Result.ok(cartList);
    }

    /**
     * 查询优惠券购物车
     * @return {@link Result}
     */
    @GetMapping("activityCartList")
    public Result activityCartList() {
        // 获取用户Id
        Long userId = AuthContextHolder.getUserId();
        List<CartInfo> cartInfoList = cartInfoService.getCartList(userId);

        OrderConfirmVo orderTradeVo = activityFeignClient.findCartActivityAndCoupon(cartInfoList, userId);
        return Result.ok(orderTradeVo);
    }

}
