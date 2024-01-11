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
import com.person.mryx.order.mapper.OrderItemMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements OrderItemService {

}
