package com.person.mryx.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.person.mryx.activity.client.ActivityFeignClient;
import com.person.mryx.cart.client.CartFeignClient;
import com.person.mryx.client.product.ProductFeignClient;
import com.person.mryx.client.user.UserFeignClient;
import com.person.mryx.common.auth.AuthContextHolder;
import com.person.mryx.common.constant.RedisConst;
import com.person.mryx.common.exception.MryxException;
import com.person.mryx.common.result.ResultCodeEnum;
import com.person.mryx.enums.SkuType;
import com.person.mryx.model.order.CartInfo;
import com.person.mryx.model.order.OrderInfo;
import com.person.mryx.order.mapper.OrderInfoMapper;
import com.person.mryx.order.service.OrderInfoService;
import com.person.mryx.vo.order.OrderConfirmVo;
import com.person.mryx.vo.order.OrderSubmitVo;
import com.person.mryx.vo.product.SkuStockLockVo;
import com.person.mryx.vo.user.LeaderAddressVo;
import io.jsonwebtoken.lang.Collections;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper,OrderInfo> implements OrderInfoService {
    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ActivityFeignClient activityFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public OrderConfirmVo confirmOrder() {
        Long userId = AuthContextHolder.getUserId();
        LeaderAddressVo userAddressByUserId = userFeignClient.getUserAddressByUserId(userId);
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        String order = System.currentTimeMillis() + "";
        redisTemplate.opsForValue().set(RedisConst.ORDER_REPEAT+order,order,24, TimeUnit.SECONDS);
        OrderConfirmVo orderConfirmVo = activityFeignClient.findCartActivityAndCoupon(cartCheckedList, userId);
        orderConfirmVo.setOrderNo(order);
        orderConfirmVo.setLeaderAddressVo(userAddressByUserId);
        return orderConfirmVo;
    }

    @Override
    public Long submitOrder(OrderSubmitVo orderParamVo) {
        //找到当前用户,1.使用redis进行防重,避免用户的重复提交,2.验库存并锁定库存,3.下单
        orderParamVo.setUserId(AuthContextHolder.getUserId());
        String orderNo = orderParamVo.getOrderNo();
        //订单号为空时,直接报错
        if(StringUtils.isEmpty(orderNo)){
            throw new MryxException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        //使用lua脚本判断用户是否重复提交,lua保证了其原子性,但无法保证一致性
        String script = "if(redis.call('get', KEYS[1]) == ARGV[1])" +
                " then return redis.call('del', KEYS[1]) else return 0 end";
        Boolean flag = (Boolean) redisTemplate.execute(new DefaultRedisScript(script, Boolean.class)
                , java.util.Collections.singletonList(RedisConst.ORDER_REPEAT + orderNo), orderNo);
        if(!flag){
            throw new MryxException(ResultCodeEnum.REPEAT_SUBMIT);
        }
        //2.验库存并锁定库存 思路:购物车里面有很多商品,它的思路是将商品分为普通商品和秒杀商品
        //这是该用户购物车所有商品
        List<CartInfo> cartInfoList = cartFeignClient.getCartCheckedList(AuthContextHolder.getUserId());
        //2.1 将普通商品验库存并上锁
        List<CartInfo> commonCollect = cartInfoList.stream()
                .filter(cartInfo -> SkuType.COMMON.getCode().equals(cartInfo.getSkuType()))
                .collect(Collectors.toList());
        if(Collections.isEmpty(commonCollect)){
            List<SkuStockLockVo> stockLockVosList = commonCollect.stream().map(item -> {
                SkuStockLockVo skuStockLockVo = new SkuStockLockVo();
                skuStockLockVo.setSkuId(item.getSkuId());
                skuStockLockVo.setSkuNum(item.getSkuNum());
                return skuStockLockVo;
            }).collect(Collectors.toList());
            Boolean isLockCommon = productFeignClient.checkAndLock(stockLockVosList, orderParamVo.getOrderNo());
            if(!isLockCommon){
                throw new MryxException(ResultCodeEnum.ORDER_STOCK_FALL);
            }
        }
        //2.2 秒杀商品验库存并上锁
        List<CartInfo> seckillSkuList = cartInfoList.stream().filter(cartInfo -> SkuType.SECKILL.getCode().equals(cartInfo.getSkuType()))
                .collect(Collectors.toList());

        return null;
    }

    @Override
    public OrderInfo getOrderInfoById(Long orderId) {
        return null;
    }

    @Override
    public OrderInfo getOrderInfoByOrderNo(String orderNo) {
        return null;
    }
}
