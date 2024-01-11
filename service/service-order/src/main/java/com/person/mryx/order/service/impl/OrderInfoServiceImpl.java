package com.person.mryx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.person.mryx.activity.client.ActivityFeignClient;
import com.person.mryx.cart.client.CartFeignClient;
import com.person.mryx.client.product.ProductFeignClient;
import com.person.mryx.client.user.UserFeignClient;
import com.person.mryx.common.auth.AuthContextHolder;
import com.person.mryx.common.constant.RedisConst;
import com.person.mryx.common.exception.MryxException;
import com.person.mryx.common.result.ResultCodeEnum;
import com.person.mryx.common.utils.DateUtil;
import com.person.mryx.enums.*;
import com.person.mryx.model.activity.ActivityRule;
import com.person.mryx.model.activity.CouponInfo;
import com.person.mryx.model.order.CartInfo;
import com.person.mryx.model.order.OrderInfo;
import com.person.mryx.model.order.OrderItem;
import com.person.mryx.mq.constant.MqConst;
import com.person.mryx.mq.service.RabbitService;
import com.person.mryx.order.mapper.OrderInfoMapper;
import com.person.mryx.order.service.OrderInfoService;
import com.person.mryx.order.service.OrderItemService;
import com.person.mryx.vo.order.CartInfoVo;
import com.person.mryx.vo.order.OrderConfirmVo;
import com.person.mryx.vo.order.OrderSubmitVo;
import com.person.mryx.vo.product.SkuStockLockVo;
import com.person.mryx.vo.user.LeaderAddressVo;
import io.jsonwebtoken.lang.Collections;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {
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

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private OrderItemService orderItemService;

    @Override
    public OrderConfirmVo confirmOrder() {
        Long userId = AuthContextHolder.getUserId();
        LeaderAddressVo userAddressByUserId = userFeignClient.getUserAddressByUserId(userId);
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        String order = System.currentTimeMillis() + "";
        redisTemplate.opsForValue().set(RedisConst.ORDER_REPEAT + order, order, 24, TimeUnit.SECONDS);
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
        if (StringUtils.isEmpty(orderNo)) {
            throw new MryxException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        //使用lua脚本判断用户是否重复提交,lua保证了其原子性,但无法保证一致性
        String script = "if(redis.call('get', KEYS[1]) == ARGV[1])" +
                " then return redis.call('del', KEYS[1]) else return 0 end";
        Boolean flag = (Boolean) redisTemplate.execute(new DefaultRedisScript(script, Boolean.class)
                , java.util.Collections.singletonList(RedisConst.ORDER_REPEAT + orderNo), orderNo);
        if (!flag) {
            throw new MryxException(ResultCodeEnum.REPEAT_SUBMIT);
        }
        //2.验库存并锁定库存 思路:购物车里面有很多商品,它的思路是将商品分为普通商品和秒杀商品
        //这是该用户购物车所有商品
        List<CartInfo> cartInfoList = cartFeignClient.getCartCheckedList(AuthContextHolder.getUserId());
        //2.1 将普通商品验库存并上锁
        List<CartInfo> commonCollect = cartInfoList.stream()
                .filter(cartInfo -> SkuType.COMMON.getCode().equals(cartInfo.getSkuType()))
                .collect(Collectors.toList());
        if (Collections.isEmpty(commonCollect)) {
            List<SkuStockLockVo> stockLockVosList = commonCollect.stream().map(item -> {
                SkuStockLockVo skuStockLockVo = new SkuStockLockVo();
                skuStockLockVo.setSkuId(item.getSkuId());
                skuStockLockVo.setSkuNum(item.getSkuNum());
                return skuStockLockVo;
            }).collect(Collectors.toList());
            Boolean isLockCommon = productFeignClient.checkAndLock(stockLockVosList, orderParamVo.getOrderNo());
            if (!isLockCommon) {
                throw new MryxException(ResultCodeEnum.ORDER_STOCK_FALL);
            }
        }
//        //2.2 秒杀商品验库存并上锁
//        List<CartInfo> seckillSkuList = cartInfoList.stream().filter(cartInfo -> SkuType.SECKILL.getCode().equals(cartInfo.getSkuType()))
//                .collect(Collectors.toList());
//        productFeignClient.checkAndLock()
        //下单完成
        Long orderId = this.saveOrder(orderParamVo, cartInfoList);
        //发送mq消息
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER_DIRECT, MqConst.ROUTING_DELETE_CART, orderParamVo.getUserId());
        return orderId;
    }

    @Transactional(rollbackFor = {Exception.class})
    //保存订单
    public Long saveOrder(OrderSubmitVo orderParamVo, List<CartInfo> cartInfoList) {
        Long userId = AuthContextHolder.getUserId();
        LeaderAddressVo userAddressByUserId = userFeignClient.getUserAddressByUserId(userId);
        //查询提货点和团长信息
        if (userAddressByUserId == null) {
            throw new MryxException(ResultCodeEnum.DATA_ERROR);
        }
        //计算金额
        Map<String, BigDecimal> computeActivitySplitAmount = this.computeActivitySplitAmount(cartInfoList);
        Map<String, BigDecimal> couponInfoSplitAmount = this.computeCouponInfoSplitAmount(cartInfoList, orderParamVo.getCouponId());

        //封装订单项
        List<OrderItem> orderItemList = new ArrayList<>();
        for (CartInfo cartInfo : cartInfoList) {
            OrderItem orderItem = new OrderItem();
            orderItem.setId(null);
            orderItem.setCategoryId(cartInfo.getCategoryId());
            if (SkuType.COMMON.getCode().equals(cartInfo.getSkuType())) {
                orderItem.setSkuType(SkuType.COMMON);
            } else {
                orderItem.setSkuType(SkuType.SECKILL);
            }
            orderItem.setSkuId(cartInfo.getSkuId());
            orderItem.setSkuName(cartInfo.getSkuName());
            orderItem.setSkuPrice(cartInfo.getCartPrice());
            orderItem.setImgUrl(cartInfo.getImgUrl());
            orderItem.setSkuNum(cartInfo.getSkuNum());
            orderItem.setLeaderId(orderParamVo.getLeaderId());
            BigDecimal activityAmount = computeActivitySplitAmount.get("activity:" + cartInfo.getSkuId());
            //促销活动分摊金额
            if (activityAmount == null) {
                activityAmount = new BigDecimal(0);
            }
            BigDecimal couponAmount = couponInfoSplitAmount.get("coupon:" + cartInfo.getSkuId());
            //优惠券分摊金额
            if (couponAmount == null) {
                couponAmount = new BigDecimal(0);
            }
            //优惠后的总金额
            BigDecimal skuTotalAmount = orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum()));
            BigDecimal splitTotalAmount = skuTotalAmount.subtract(activityAmount).subtract(couponAmount);
            orderItem.setSplitTotalAmount(splitTotalAmount);
            orderItem.setSplitCouponAmount(couponAmount);
            orderItem.setSplitActivityAmount(activityAmount);
            orderItemList.add(orderItem);
        }
        //封装订单数据
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setUserId(userId);
        orderInfo.setOrderNo(orderParamVo.getOrderNo());
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        orderInfo.setCouponId(orderParamVo.getCouponId());
        orderInfo.setLeaderId(orderParamVo.getLeaderId());
        orderInfo.setLeaderName(userAddressByUserId.getLeaderName());
        orderInfo.setLeaderPhone(userAddressByUserId.getLeaderPhone());
        orderInfo.setTakeName(userAddressByUserId.getTakeName());
        orderInfo.setReceiverName(orderParamVo.getReceiverName());
        orderInfo.setReceiverPhone(orderParamVo.getReceiverPhone());
        orderInfo.setReceiverProvince(userAddressByUserId.getProvince());
        orderInfo.setReceiverCity(userAddressByUserId.getCity());
        orderInfo.setReceiverDistrict(userAddressByUserId.getDistrict());
        orderInfo.setReceiverAddress(userAddressByUserId.getDetailAddress());
        orderInfo.setWareId(cartInfoList.get(0).getWareId());
        //保存订单项
        orderItemList.forEach(item -> {
            item.setOrderId(orderInfo.getId());
        });
        orderItemService.saveBatch(orderItemList);
        //计算订单金额
        BigDecimal originalTotalAmount = this.computeTotalAmount(cartInfoList);
        BigDecimal activityAmount = computeActivitySplitAmount.get("activity:total");
        if (null == activityAmount) {
            activityAmount = new BigDecimal(0);
        }
        BigDecimal couponAmount = couponInfoSplitAmount.get("coupon:total");
        if (null == couponAmount) {
            couponAmount = new BigDecimal(0);
        }
        BigDecimal totalAmount = originalTotalAmount.subtract(activityAmount).subtract(couponAmount);
        orderInfo.setOriginalTotalAmount(originalTotalAmount);
        orderInfo.setActivityAmount(activityAmount);
        orderInfo.setCouponAmount(couponAmount);
        orderInfo.setTotalAmount(totalAmount);
        // TODO 计算团长佣金
//        BigDecimal profitRate = orderSetService.getProfitRate();
        BigDecimal profitRate = new BigDecimal(0);
        BigDecimal commissionAmount = orderInfo.getTotalAmount().multiply(profitRate);
        orderInfo.setCommissionAmount(commissionAmount);
        baseMapper.insert(orderInfo);
        //更新优惠券使用状态
        if (null != orderInfo.getCouponId()) {
            activityFeignClient.updateCouponInfoUseStatus(orderInfo.getCouponId(), userId, orderInfo.getId());
        }
        //下单成功，记录用户商品购买个数
        String orderSkuKey = RedisConst.ORDER_SKU_MAP + orderParamVo.getUserId();
        BoundHashOperations<String, String, Integer> hashOperations = redisTemplate.boundHashOps(orderSkuKey);
        cartInfoList.forEach(cartInfo -> {
            if (hashOperations.hasKey(cartInfo.getSkuId().toString())) {
                Integer orderSkuNum = hashOperations.get(cartInfo.getSkuId().toString()) + cartInfo.getSkuNum();
                hashOperations.put(cartInfo.getSkuId().toString(), orderSkuNum);
            }
        });
        redisTemplate.expire(orderSkuKey, DateUtil.getCurrentExpireTimes(), TimeUnit.SECONDS);
        //发送消息

        return orderInfo.getId();
    }

    @Override
    public OrderInfo getOrderInfoById(Long orderId) {
        //根据订单号查询订单信息
        OrderInfo orderInfo = baseMapper.selectById(orderId);
        //据订单号查询订单项信息
        List<OrderItem> orderItemList = orderItemService.getBaseMapper().selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderInfo.getId()));
        //将所有订单项封装到订单对象中
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    @Override
    public OrderInfo getOrderInfoByOrderNo(String orderNo) {
        //通过订单号来查询
        return baseMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getOrderNo, orderNo));
    }


    @Override
    public void orderPay(String orderNo) {
        OrderInfo orderInfo = baseMapper.selectById(orderNo);
        this.updateOrderStatus(orderInfo, ProcessStatus.WAITING_DELEVER);
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER_DIRECT, MqConst.ROUTING_MINUS_STOCK, orderInfo);
    }

    public void updateOrderStatus(OrderInfo orderInfo, ProcessStatus processStatus) {
        orderInfo.setProcessStatus(processStatus);
        orderInfo.setOrderStatus(processStatus.getOrderStatus());
        if (processStatus == ProcessStatus.WAITING_DELEVER) {
            orderInfo.setPaymentTime(new Date());
        } else if (processStatus == ProcessStatus.WAITING_LEADER_TAKE) {
            orderInfo.setDeliveryTime(new Date());
        } else if (processStatus == ProcessStatus.WAITING_USER_TAKE) {
            orderInfo.setTakeTime(new Date());
        }
        baseMapper.updateById(orderInfo);
    }

    private BigDecimal computeTotalAmount(List<CartInfo> cartInfoList) {
        BigDecimal total = new BigDecimal(0);
        for (CartInfo cartInfo : cartInfoList) {
            BigDecimal itemTotal = cartInfo.getCartPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
            total = total.add(itemTotal);
        }
        return total;
    }

    /**
     * 计算购物项分摊的优惠减少金额
     * 打折：按折扣分担
     * 现金：按比例分摊
     *
     * @param cartInfoParamList
     * @return
     */
    private Map<String, BigDecimal> computeActivitySplitAmount(List<CartInfo> cartInfoParamList) {
        Map<String, BigDecimal> activitySplitAmountMap = new HashMap<>();

        //促销活动相关信息
        List<CartInfoVo> cartInfoVoList = activityFeignClient.findCartActivityList(cartInfoParamList);

        //活动总金额
        BigDecimal activityReduceAmount = new BigDecimal(0);
        if (!CollectionUtils.isEmpty(cartInfoVoList)) {
            for (CartInfoVo cartInfoVo : cartInfoVoList) {
                ActivityRule activityRule = cartInfoVo.getActivityRule();
                List<CartInfo> cartInfoList = cartInfoVo.getCartInfoList();
                if (null != activityRule) {
                    //优惠金额， 按比例分摊
                    BigDecimal reduceAmount = activityRule.getReduceAmount();
                    activityReduceAmount = activityReduceAmount.add(reduceAmount);
                    if (cartInfoList.size() == 1) {
                        activitySplitAmountMap.put("activity:" + cartInfoList.get(0).getSkuId(), reduceAmount);
                    } else {
                        //总金额
                        BigDecimal originalTotalAmount = new BigDecimal(0);
                        for (CartInfo cartInfo : cartInfoList) {
                            BigDecimal skuTotalAmount = cartInfo.getCartPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
                            originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                        }
                        //记录除最后一项是所有分摊金额， 最后一项=总的 - skuPartReduceAmount
                        BigDecimal skuPartReduceAmount = new BigDecimal(0);
                        if (activityRule.getActivityType() == ActivityType.FULL_REDUCTION) {
                            for (int i = 0, len = cartInfoList.size(); i < len; i++) {
                                CartInfo cartInfo = cartInfoList.get(i);
                                if (i < len - 1) {
                                    BigDecimal skuTotalAmount = cartInfo.getCartPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
                                    //sku分摊金额
                                    BigDecimal skuReduceAmount = skuTotalAmount.divide(originalTotalAmount, 2, RoundingMode.HALF_UP).multiply(reduceAmount);
                                    activitySplitAmountMap.put("activity:" + cartInfo.getSkuId(), skuReduceAmount);

                                    skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                                } else {
                                    BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                                    activitySplitAmountMap.put("activity:" + cartInfo.getSkuId(), skuReduceAmount);
                                }
                            }
                        } else {
                            for (int i = 0, len = cartInfoList.size(); i < len; i++) {
                                CartInfo cartInfo = cartInfoList.get(i);
                                if (i < len - 1) {
                                    BigDecimal skuTotalAmount = cartInfo.getCartPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));

                                    //sku分摊金额
                                    BigDecimal skuDiscountTotalAmount = skuTotalAmount.multiply(activityRule.getBenefitDiscount().divide(new BigDecimal("10")));
                                    BigDecimal skuReduceAmount = skuTotalAmount.subtract(skuDiscountTotalAmount);
                                    activitySplitAmountMap.put("activity:" + cartInfo.getSkuId(), skuReduceAmount);

                                    skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                                } else {
                                    BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                                    activitySplitAmountMap.put("activity:" + cartInfo.getSkuId(), skuReduceAmount);
                                }
                            }
                        }
                    }
                }
            }
        }
        activitySplitAmountMap.put("activity:total", activityReduceAmount);
        return activitySplitAmountMap;
    }

    private Map<String, BigDecimal> computeCouponInfoSplitAmount(List<CartInfo> cartInfoList, Long couponId) {
        Map<String, BigDecimal> couponInfoSplitAmountMap = new HashMap<>();

        if (null == couponId) {
            return couponInfoSplitAmountMap;
        }
        CouponInfo couponInfo = activityFeignClient.findRangeSkuIdList(cartInfoList, couponId);

        if (null != couponInfo) {
            //sku对应的订单明细
            Map<Long, CartInfo> skuIdToCartInfoMap = new HashMap<>();
            for (CartInfo cartInfo : cartInfoList) {
                skuIdToCartInfoMap.put(cartInfo.getSkuId(), cartInfo);
            }
            //优惠券对应的skuId列表
            List<Long> skuIdList = couponInfo.getSkuIdList();
            if (CollectionUtils.isEmpty(skuIdList)) {
                return couponInfoSplitAmountMap;
            }
            //优惠券优化总金额
            BigDecimal reduceAmount = couponInfo.getAmount();
            if (skuIdList.size() == 1) {
                //sku的优化金额
                couponInfoSplitAmountMap.put("coupon:" + skuIdToCartInfoMap.get(skuIdList.get(0)).getSkuId(), reduceAmount);
            } else {
                //总金额
                BigDecimal originalTotalAmount = new BigDecimal(0);
                for (Long skuId : skuIdList) {
                    CartInfo cartInfo = skuIdToCartInfoMap.get(skuId);
                    BigDecimal skuTotalAmount = cartInfo.getCartPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
                    originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                }
                //记录除最后一项是所有分摊金额， 最后一项=总的 - skuPartReduceAmount
                BigDecimal skuPartReduceAmount = new BigDecimal(0);
                if (couponInfo.getCouponType() == CouponType.CASH || couponInfo.getCouponType() == CouponType.FULL_REDUCTION) {
                    for (int i = 0, len = skuIdList.size(); i < len; i++) {
                        CartInfo cartInfo = skuIdToCartInfoMap.get(skuIdList.get(i));
                        if (i < len - 1) {
                            BigDecimal skuTotalAmount = cartInfo.getCartPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
                            //sku分摊金额
                            BigDecimal skuReduceAmount = skuTotalAmount.divide(originalTotalAmount, 2, RoundingMode.HALF_UP).multiply(reduceAmount);
                            couponInfoSplitAmountMap.put("coupon:" + cartInfo.getSkuId(), skuReduceAmount);

                            skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                        } else {
                            BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                            couponInfoSplitAmountMap.put("coupon:" + cartInfo.getSkuId(), skuReduceAmount);
                        }
                    }
                }
            }
            couponInfoSplitAmountMap.put("coupon:total", couponInfo.getAmount());
        }
        return couponInfoSplitAmountMap;
    }
}
