package com.person.mryx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.person.mryx.model.order.OrderInfo;
import com.person.mryx.vo.order.OrderConfirmVo;
import com.person.mryx.vo.order.OrderSubmitVo;
import org.springframework.stereotype.Service;

public interface OrderInfoService extends IService<OrderInfo> {
    //确认订单
    OrderConfirmVo confirmOrder();

    //提交订单
    Long submitOrder(OrderSubmitVo orderParamVo);

    //通过Id得到订单信息
    OrderInfo getOrderInfoById(Long orderId);

    //通过订单号获取订单信息
    OrderInfo getOrderInfoByOrderNo(String orderNo);

    void orderPay(String orderNo);
}
